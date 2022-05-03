/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Reminder.{
  Birthday,
  ReminderType,
  StayInTouch
}
import com.github.johanneshiry.simpleprm.io.model.{Contact, Reminder}
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.VCard as MongoDbVCard
import ezvcard.property.Uid
import ezvcard.{Ezvcard, VCard}
import reactivemongo.api.bson.{BSONDocument, BSONDocumentReader}

import java.text.{DateFormat, SimpleDateFormat}
import java.time.{Duration, LocalDate, Period, ZonedDateTime}
import java.util.{Date, UUID}
import scala.util.{Failure, Success, Try}

private[mongodb] trait BSONReader {

  // todo central place for field names!

  implicit val contactReader: BSONDocumentReader[Contact] =
    BSONDocumentReader.from[Contact] { bson =>
      for {
        vCard <- bson
          .getAsTry[String]("vCard")
          .map(vCardString => Ezvcard.parse(vCardString).first())
      } yield Contact(vCard)
    }

  implicit val reminderReader: BSONDocumentReader[Reminder] =
    BSONDocumentReader.from[Reminder] { bson =>
      // depending on the type, different fields are expected

      final case class SharedFields(
          uuid: UUID,
          contactId: Uid,
          reminderDate: LocalDate,
          lastTimeReminded: LocalDate
      )

      def stayInTouchReminder(sharedFields: SharedFields): Try[StayInTouch] =
        for {
          reason <- bson.getAsUnflattenedTry[String]("reason")
          reminderInterval <- bson
            .getAsTry[String]("reminderInterval")
            .map(Period.parse(_))
        } yield StayInTouch(
          sharedFields.uuid,
          reason,
          sharedFields.contactId,
          sharedFields.reminderDate,
          sharedFields.lastTimeReminded,
          reminderInterval
        )

      def sharedFields(): Try[SharedFields] =
        for {
          uuid <- bson.getAsTry[String]("uuid").map(UUID.fromString)
          contactId <- bson.getAsTry[String]("contactId").map(new Uid(_))
          reminderDate <- bson
            .getAsTry[String]("reminderDate")
            .map(LocalDate.parse)
          lastTimeReminded <- bson
            .getAsTry[String]("lastTimeReminded")
            .map(LocalDate.parse)
        } yield SharedFields(uuid, contactId, reminderDate, lastTimeReminded)

      bson
        .getAsTry[String]("reminderType")
        .map(Reminder.ReminderType.valueOf)
        .flatMap {
          case ReminderType.StayInTouch =>
            sharedFields().flatMap(stayInTouchReminder)
          case ReminderType.Birthday =>
            sharedFields().map {
              case SharedFields(
                    uuid,
                    contactId,
                    reminderDate,
                    lastTimeReminded
                  ) =>
                Birthday(uuid, contactId, reminderDate, lastTimeReminded)
            }
        }
    }

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
