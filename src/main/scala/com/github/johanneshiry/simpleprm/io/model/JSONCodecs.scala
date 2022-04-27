/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid
import io.circe.*
import io.circe.syntax.*

import scala.language.implicitConversions
import scala.util.Try

// todo remove field names as not required
object JSONCodecs {

  // ezvcard.property.Uid
  implicit val encUid: Encoder[Uid] =
    (a: Uid) => Json.fromString(a.getValue)

  implicit def decUid(fieldName: String): Decoder[Uid] =
    Decoder.decodeString.emapTry {
      uidString => // Decoder.decodeUUID alters uuid, therefore we need decodeString here
        Try(new Uid(uidString))
    }

  // ezvcard.VCard
  implicit val encVCard: Encoder[VCard] =
    (x: VCard) =>
      Json.fromString(Ezvcard.write(x).prodId(false).version(x.getVersion).go())

  implicit def decVCard(fieldName: String): Decoder[VCard] =
    Decoder.decodeString.emapTry(vCardString =>
      Try(Ezvcard.parse(vCardString).first())
    )

  // throwable
  implicit val encThrowable: Encoder[Throwable] =
    (x: Throwable) => Json.fromString(x.toString)

}
