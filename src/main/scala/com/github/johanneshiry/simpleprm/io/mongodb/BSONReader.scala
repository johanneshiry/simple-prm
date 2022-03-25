/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Contact
import ezvcard.{Ezvcard, VCard}
import reactivemongo.api.bson.BSONDocumentReader

import scala.util.Try

object BSONReader {

  def contactReader: BSONDocumentReader[Contact] =
    BSONDocumentReader.from[Contact] { bson =>
      {
        for {
          vCard <- bson
            .getAsTry[String]("vCard")
            .map(vCardString => Ezvcard.parse(vCardString).first())
        } yield Contact(vCard)
      }
    }

}
