/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import com.github.johanneshiry.simpleprm.io.model.Reminder.{
  Birthday,
  ReminderType,
  StayInTouch
}
import com.github.johanneshiry.simpleprm.io.mongodb.BsonEncoder
import io.circe.parser.decode
import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid
import io.circe.*
import io.circe.syntax.*
import reactivemongo.api.bson.BSONValue.pretty

import java.time.{LocalDate, Period}
import java.util.UUID
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

// todo remove field names as not required
object JSONCodecs {

  // todo central place for field names!
  // todo combine decoder with BSON Reader to one single place for safety

  // todo remove


  implicit def decUid(fieldName: String): Decoder[Uid] =
    Decoder.decodeString.emapTry {
      uidString => // Decoder.decodeUUID alters uuid, therefore we need decodeString here
        Try(new Uid(uidString))
    }

  implicit def decVCard(fieldName: String): Decoder[VCard] =
    Decoder.decodeString.emapTry(vCardString =>
      Try(Ezvcard.parse(vCardString).first())
    )


  // Reminder trait
  // workaround due to currently not supported trait handling in circe for scala 3
  private final case class ReminderSharedFields()

  implicit def decReminder(implicit decUid: Decoder[Uid]): Decoder[Reminder] =
    Decoder.decodeJson.emap(reminderJson => {
      import io.circe.generic.auto._, io.circe.syntax._
      import cats.implicits._
      val cursor = reminderJson.hcursor
      cursor.downField("reminderType").as[String] match
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
                  decStayInTouch(decUid)(cursor).leftMap[String](er =>
                    er.toString
                  )
                case ReminderType.Birthday =>
                  decBirthday(decUid)(cursor).leftMap[String](er => er.toString)
    })

  implicit def decStayInTouch(implicit
      decUid: Decoder[Uid]
  ): Decoder[StayInTouch] = (c: HCursor) =>
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

  implicit def decBirthday(implicit decUid: Decoder[Uid]): Decoder[Birthday] =
    (c: HCursor) =>
      for {
        uuid <- c.downField("uuid").as[UUID]
        contactId <- c.downField("contactId").as[Uid]
        reminderDate <- c.downField("reminderDate").as[LocalDate]
        lastTimeReminded <- c.downField("lastTimeReminded").as[LocalDate]
      } yield Birthday(uuid, contactId, reminderDate, lastTimeReminded)
}
