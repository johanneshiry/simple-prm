/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Reminder.StayInTouch
import com.github.johanneshiry.simpleprm.io.mongodb.BsonEncoder
import org.scalatest.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import reactivemongo.api.bson.{
  BSONArray,
  BSONDocument,
  BSONString,
  BSONUndefined
}
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact
import com.github.johanneshiry.simpleprm.test.common.CustomMatchers
import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid

import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDate, Period, ZonedDateTime}
import java.util.UUID
import scala.language.postfixOps
import scala.util.{Failure, Success}

class BsonEncoderSpec extends should.Matchers with AnyWordSpecLike {

  "A BSONTransformer" should {

    "transform a String w/o a field name correctly" in {

      val test = "test"
      BsonEncoder.encode(test) shouldBe BSONString(test)

    }

    "transform a String with a field name correctly" in {

      val test = "test"
      val fieldName = "id"
      BsonEncoder.encode(test, Some(fieldName)) shouldBe BSONDocument(
        fieldName -> BSONString(test)
      )

    }

    "transform a case class w/o a field name correctly" in {
      import com.github.johanneshiry.simpleprm.io.mongodb.BsonDecoder.DecoderOps

      val reminderDate = LocalDate.now()
      val reminderInterval = Period.parse("P2M")
      val contactId = new Uid("bc2b7c8d-1b18-43f7-87ce-42d7489fae76")
      val reminderId = UUID.randomUUID()
      val vCard = Ezvcard
        .parse(
          "BEGIN:VCARD\r\nVERSION:3.0\r\nPRODID:-//Sabre//Sabre VObject 4.3.5//EN\r\nUID:bc2b7c8d-1b18-43f7-87ce-42d7489fae76\r\nFN:Testkontakt\r\nADR;TYPE=HOME:\r\nEMAIL;TYPE=HOME:\r\nTEL;TYPE=HOME,VOICE:01234567\r\nREV:2022-02-08T20:44:47Z\r\nEND:VCARD\r\n"
        )
        .first()
      Contact(
        vCard,
        Seq(
          StayInTouch(
            reminderId,
            None,
            contactId,
            reminderDate,
            reminderDate,
            reminderInterval
          )
        )
      ) match {
        case Failure(exception) =>
          fail(exception)
        case Success(contact) =>
          val transformed = BsonEncoder.encode(contact)
          val expected = BSONDocument(
            "_id" -> BSONString(vCard.getUid.getValue),
            "vCard" -> BSONDocument(
              "value" -> BSONString(
                Ezvcard
                  .write(vCard)
                  .prodId(false)
                  .version(vCard.getVersion)
                  .go()
              ),
              "FN" -> "Testkontakt",
              "familyName" -> BSONUndefined,
              "givenName" -> BSONUndefined
            ),
            "reminders" -> BSONArray(
              BSONDocument(
                "uuid" -> BSONString(reminderId.toString),
                "reason" -> BSONUndefined,
                "contactId" -> BSONString(contactId.getValue),
                "reminderDate" -> BSONString(reminderDate.toString),
                "lastTimeReminded" -> BSONString(reminderDate.toString),
                "reminderInterval" -> BSONString(reminderInterval.toString),
                "reminderType" -> BSONString("StayInTouch")
              )
            )
          )
          import CustomMatchers.*
          transformed should beEqualPrettyPrint(expected)
      }

    }

  }

}
