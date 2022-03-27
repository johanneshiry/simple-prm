/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.StayInTouch
import ezvcard.VCard
import ezvcard.property.Uid

private[mongodb] object MongoDbModel {

  final case class Contact(
      _id: Uid,
      vCard: VCard,
      stayInTouch: Option[StayInTouch]
  )

  object Contact {
    def apply(vCard: VCard, stayInTouch: Option[StayInTouch]): Contact =
      Contact(vCard.getUid, vCard, stayInTouch)

  }

}
