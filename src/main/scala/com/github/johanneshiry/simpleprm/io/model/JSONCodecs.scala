/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import ezvcard.property.Uid
import io.circe.*
import io.circe.syntax.*

import scala.language.implicitConversions
import scala.util.Try

object JSONCodecs {

  // ezvcard.property.Uid
  implicit val encUid: Encoder[Uid] =
    (a: Uid) => Json.fromString(a.getValue)

  implicit def decUid(fieldName: String): Decoder[Uid] =
    Decoder.decodeUUID.emapTry { uuid =>
      Try(new Uid(uuid.toString))
    }
}
