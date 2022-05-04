/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.json

import com.github.johanneshiry.simpleprm.io.model.Reminder
import com.github.johanneshiry.simpleprm.io.model.Reminder.{
  Birthday,
  ReminderType,
  StayInTouch
}
import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid
import io.circe.Decoder
import io.circe.parser.decode

import java.time.{LocalDate, Period}
import java.util.UUID
import scala.util.{Failure, Success, Try}

trait JsonDecoder {

  import io.circe._, io.circe.generic.semiauto._

  // Decoder.decodeUUID alters uuid, to comply with RFC-4122
  // as card dav tends to ignore upper/lower case rules of the standard, we need decodeString here
  implicit val decUid: Decoder[Uid] =
    Decoder.decodeString.emapTry(uidString => Try(new Uid(uidString)))

  implicit val decVCard: Decoder[VCard] =
    Decoder.decodeString.emapTry(vCardString =>
      Try(Ezvcard.parse(vCardString).first())
    )

  // todo reminderType field to central place!
  implicit val decReminder: Decoder[Reminder] =
    Decoder.decodeJson.emap(reminderJson => {
      reminderJson.hcursor.downField("reminderType").as[String] match
        case Left(error) =>
          Left(error.getMessage)
        case Right(reminderTypeString) =>
          Try(ReminderType.valueOf(reminderTypeString)) match
            case Failure(exception) =>
              Left(
                exception.getMessage + "\nPlease provide a supported reminder type!\n" +
                  s"Supported types: ${ReminderType.values.mkString(", ")}"
              )
            case Success(reminderType) =>
              reminderType match
                case ReminderType.StayInTouch =>
                  leftString(reminderJson.as[StayInTouch])
                case ReminderType.Birthday =>
                  leftString(reminderJson.as[Birthday])
    })

  // todo field names to central place
  implicit val decStayInTouch: Decoder[StayInTouch] = (c: HCursor) =>
    for {
      uuid <- c.downField("uuid").as[UUID]
      reason <- c.downField("reason").as[Option[String]]
      contactId <- c.downField("contactId").as[Uid]
      reminderDate <- c.downField("reminderDate").as[LocalDate]
      lastTimeReminded <- c.downField("lastTimeReminded").as[LocalDate]
      reminderInterval <- c.downField("reminderInterval").as[Period]
    } yield StayInTouch(
      uuid,
      reason,
      contactId,
      reminderDate,
      lastTimeReminded,
      reminderInterval
    )

  // todo field names to central place
  implicit val decBirthday: Decoder[Birthday] = (c: HCursor) =>
    for {
      uuid <- c.downField("uuid").as[UUID]
      contactId <- c.downField("contactId").as[Uid]
      reminderDate <- c.downField("reminderDate").as[LocalDate]
      lastTimeReminded <- c.downField("lastTimeReminded").as[LocalDate]
    } yield Birthday(uuid, contactId, reminderDate, lastTimeReminded)

  private def leftString[A](res: Decoder.Result[A]) = {
    import cats.implicits._
    res.leftMap[String](er => er.toString)
  }

}
