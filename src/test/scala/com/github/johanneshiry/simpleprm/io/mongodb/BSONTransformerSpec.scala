/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.{CsvTransformer, StayInTouch}
import com.github.johanneshiry.simpleprm.io.mongodb.BSONTransformer
import org.scalatest.*
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike
import reactivemongo.api.bson.{BSONDocument, BSONString}
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact
import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid

import java.time.temporal.ChronoUnit
import java.time.{Duration, ZonedDateTime}
import scala.language.postfixOps
import scala.util.{Failure, Success}

class BSONTransformerSpec extends should.Matchers with AnyWordSpecLike {

  "A BSONTransformer" should {

    "transform a String w/o a field name correctly" in {

      val test = "test"
      BSONTransformer.transform(test) shouldBe BSONDocument(
        "" -> BSONString(test)
      )

    }

    "transform a String with a field name correctly" in {

      val test = "test"
      val fieldName = "id"
      BSONTransformer.transform(test, Some(fieldName)) shouldBe BSONDocument(
        fieldName -> BSONString(test)
      )

    }

    "transform a case class w/o a field name correctly" in {
      val zdt = ZonedDateTime.now()
      val duration = Duration.parse("P2D")
      val stayInTouchUid = new Uid("bc2b7c8d-1b18-43f7-87ce-42d7489fae76")
      val vCard = Ezvcard
        .parse(
          "BEGIN:VCARD\r\nVERSION:3.0\r\nPRODID:-//Sabre//Sabre VObject 4.3.5//EN\r\nUID:bc2b7c8d-1b18-43f7-87ce-42d7489fae76\r\nFN:Testkontakt\r\nADR;TYPE=HOME:\r\nEMAIL;TYPE=HOME:\r\nTEL;TYPE=HOME,VOICE:01234567\r\nREV:2022-02-08T20:44:47Z\r\nEND:VCARD\r\n"
        )
        .first()
      Contact(
        vCard,
        Some(
          StayInTouch(
            stayInTouchUid,
            zdt,
            duration
          )
        )
      ) match {
        case Failure(exception) =>
          fail(exception)
        case Success(contact) =>
          val transformed = BSONTransformer.transform(contact)
          val expected = BSONDocument(
            "_id" -> BSONString(vCard.getUid.getValue),
            "vCard" -> BSONString(
              Ezvcard.write(vCard).prodId(false).version(vCard.getVersion).go()
            ),
            "stayInTouch" -> BSONDocument(
              "contactId" -> BSONString(stayInTouchUid.getValue),
              "lastContacted" -> BSONString(zdt.toString),
              "contactInterval" -> BSONString(duration.toString)
            )
          )
          transformed shouldBe expected
      }

    }

  }

}
