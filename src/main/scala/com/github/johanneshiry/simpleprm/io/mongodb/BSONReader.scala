/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.{Contact, StayInTouch}
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import ezvcard.property.Uid
import ezvcard.{Ezvcard, VCard}
import reactivemongo.api.bson.BSONDocumentReader

import java.time.{Duration, ZonedDateTime}
import scala.util.Try

private[mongodb] trait BSONReader {

  implicit val contactReader: BSONDocumentReader[Contact] =
    BSONDocumentReader.from[Contact] { bson =>
      for {
        vCard <- bson
          .getAsTry[String]("vCard")
          .map(vCardString => Ezvcard.parse(vCardString).first())
      } yield Contact(vCard)
    }

  implicit val stayInTouchReader: BSONDocumentReader[StayInTouch] =
    BSONDocumentReader.from[StayInTouch] { bson =>
      for {
        contactId <- bson.getAsTry[String]("contactId").map(new Uid(_))
        lastContacted <- bson
          .getAsTry[String]("lastContacted")
          .map(ZonedDateTime.parse(_))
        contactInterval <- bson
          .getAsTry[String]("contactInterval")
          .map(Duration.parse(_))
      } yield StayInTouch(contactId, lastContacted, contactInterval)
    }

  implicit val mongoDbContactReader: BSONDocumentReader[MongoDbContact] =
    BSONDocumentReader.from[MongoDbContact] { bson =>
      for {
        stayInTouch <- bson.getAsUnflattenedTry[StayInTouch]("stayInTouch")(
          stayInTouchReader
        )
        vCard <- bson
          .getAsTry[String]("vCard")
          .map(vCardString => Ezvcard.parse(vCardString).first())
      } yield MongoDbContact(vCard, stayInTouch)
    }

}
