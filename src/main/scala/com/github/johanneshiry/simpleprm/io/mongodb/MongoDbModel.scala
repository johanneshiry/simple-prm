/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.StayInTouch
import ezvcard.VCard
import ezvcard.property.Uid

import scala.util.{Failure, Success, Try}

private[mongodb] object MongoDbModel {

  final case class Contact private (
      _id: Uid,
      vCard: VCard,
      stayInTouch: Option[StayInTouch]
  )

  object Contact {
    def apply(vCard: VCard, stayInTouch: Option[StayInTouch]): Try[Contact] =
      checkAndBuild(vCard.getUid, vCard, stayInTouch)

    private def checkAndBuild(
        _id: Uid,
        vCard: VCard,
        stayInTouch: Option[StayInTouch]
    ): Try[Contact] = {

      def allUidEqual(
          _id: Uid,
          vCardUid: Uid,
          stayInTouchUid: Option[Uid] = None
      ): Boolean =
        _id.getValue.equals(vCardUid.getValue) && _id.getValue.equals(
          stayInTouchUid.getOrElse(_id).getValue
        )

      stayInTouch match {
        case Some(stayInTouch)
            if allUidEqual(_id, vCard.getUid, Some(stayInTouch.contactId)) =>
          Success(Contact(_id, vCard, Some(stayInTouch)))
        case None if allUidEqual(_id, vCard.getUid) =>
          Success(Contact(_id, vCard, stayInTouch))
        case _ =>
          Failure(
            new IllegalArgumentException(
              s"Cannot construct Contact instance with different Uids.\n" +
                s"_id = ${_id.getValue}\n" +
                s"vCardUid = ${vCard.getUid.getValue}" +
                s"${if (stayInTouch.isDefined) "\nstayInTouchContactId: " + stayInTouch.get.contactId.getValue
                else ""}"
            )
          )
      }

    }

  }

}
