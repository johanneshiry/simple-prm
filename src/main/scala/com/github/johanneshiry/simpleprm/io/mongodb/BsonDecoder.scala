/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.api.rest.json.JsonDecoder
import com.github.johanneshiry.simpleprm.io.model.{Contact, Reminder}
import io.circe.{Json, ParsingFailure}
import BsonDecoder.*
import reactivemongo.api.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDocumentReader,
  BSONDouble,
  BSONElement,
  BSONInteger,
  BSONJavaScript,
  BSONJavaScriptWS,
  BSONLong,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONObjectID,
  BSONReader,
  BSONRegex,
  BSONString,
  BSONSymbol,
  BSONTimestamp,
  BSONUndefined,
  BSONValue,
  BSONWriter,
  Macros
}
import reactivemongo.api.bson.BSONValue.pretty
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import ezvcard.Ezvcard
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.VCard as MongoDbVCard

private[mongodb] trait BsonDecoder extends JsonDecoder {

  implicit val contactReader: BSONDocumentReader[Contact] =
    BSONDocumentReader.from[Contact](_.asJson.flatMap(_.as[Contact]).toTry)

  implicit val remindersReader: BSONReader[Seq[Reminder]] =
    BSONReader.sequence(_.asJson.flatMap(_.as[Reminder]).toTry)

  // todo field names to central place
  // as parsing vCardStrings with json is annoyingly unstable, we use a separate decoder here
  implicit val mongoDbContactReader: BSONDocumentReader[MongoDbContact] =
    BSONDocumentReader.from[MongoDbContact] { bson =>
      for {
        reminders <- bson.getAsUnflattenedTry[Seq[Reminder]]("reminders")(
          remindersReader
        )
        vCard <- bson
          .getAsTry[BSONDocument]("vCard")
          .flatMap(_.getAsTry[String]("value"))
          .map(vCardString => MongoDbVCard(Ezvcard.parse(vCardString).first()))
        mongoDbContact <- MongoDbContact(vCard, reminders.getOrElse(Seq.empty))
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
      private val bsonVal: BSONValue
  ) extends AnyVal {

    import io.circe.parser.*
    import BsonDecoder.*

    private def undefinedToNullString(bson: BSONValue): BSONValue = {
      bson match
        case document: BSONDocument =>
          val elems = document.elements.map(element =>
            element.value match
              case arr: BSONArray =>
                BSONElement(
                  element.name,
                  BSONArray(arr.values.map(undefinedToNullString))
                )
              case doc: BSONDocument =>
                BSONElement(element.name, undefinedToNullString(doc))
              case _: BSONUndefined =>
                BSONElement(element.name, BSONString("null"))
              case _ =>
                element
          )
          BSONDocument.newBuilder.addAll(elems).result()
        case nonDocument =>
          nonDocument
    }

    private def clean(bson: BSONValue) =
      pretty(
        undefinedToNullString(bson)
      ).doubleQuotes.noCarriageReturn.noNewLine.trim

    final def asJson: Either[ParsingFailure, Json] = parse(clean(bsonVal))
  }

}
