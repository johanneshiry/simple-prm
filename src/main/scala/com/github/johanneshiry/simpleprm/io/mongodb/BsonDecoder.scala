/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.api.rest.json.JsonDecoder
import com.github.johanneshiry.simpleprm.io.model.{Contact, Reminder}
import io.circe.{Json, ParsingFailure}
import BsonDecoder.*
import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentReader,
  BSONElement,
  BSONString,
  BSONUndefined
}
import reactivemongo.api.bson.BSONValue.pretty
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import ezvcard.Ezvcard
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.VCard as MongoDbVCard

private[mongodb] trait BsonDecoder extends JsonDecoder {

  implicit val contactReader: BSONDocumentReader[Contact] =
    BSONDocumentReader.from[Contact](_.asJson.flatMap(_.as[Contact]).toTry)

  implicit val reminderReader: BSONDocumentReader[Reminder] =
    BSONDocumentReader.from[Reminder](_.asJson.flatMap(_.as[Reminder]).toTry)

  // todo field names to central place
  // as parsing vCardStrings with json is annoyingly unstable, we use a separate decoder here
  implicit val mongoDbContactReader: BSONDocumentReader[MongoDbContact] =
    BSONDocumentReader.from[MongoDbContact] { bson =>
      for {
        stayInTouch <- bson.getAsUnflattenedTry[Reminder]("reminder")(
          reminderReader
        )
        vCard <- bson
          .getAsTry[BSONDocument]("vCard")
          .flatMap(_.getAsTry[String]("value"))
          .map(vCardString => MongoDbVCard(Ezvcard.parse(vCardString).first()))
        mongoDbContact <- MongoDbContact(vCard, stayInTouch)
      } yield mongoDbContact
    }

}

private[mongodb] object BsonDecoder {

  implicit private final class StringOps(private val s: String) extends AnyVal {

    final def doubleQuotes: String =
      s.replace("\'", "\"")

    final def noCarriageReturn: String =
      s.replace("\r", "")

    final def noNewLine: String =
      s.replace("\n", " ")
  }

  implicit private[mongodb] final class DecoderOps(
      private val bsonDoc: BSONDocument
  ) extends AnyVal {

    import io.circe.parser.*
    import BsonDecoder.*

    private def undefinedToEmpty(bson: BSONDocument) = {
      val elems = bson.elements.map(element =>
        element.value match
          case _: BSONUndefined => BSONElement(element.name, BSONString(""))
          case _                => element
      )
      BSONDocument.newBuilder.addAll(elems).result()
    }

    private def clean(bson: BSONDocument) =
      pretty(
        undefinedToEmpty(bson)
      ).doubleQuotes.noCarriageReturn.noNewLine.trim

    final def asJson: Either[ParsingFailure, Json] = parse(clean(bsonDoc))
  }

}
