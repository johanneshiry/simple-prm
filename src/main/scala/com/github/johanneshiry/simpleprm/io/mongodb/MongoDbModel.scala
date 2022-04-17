/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model
import com.github.johanneshiry.simpleprm.io.model.StayInTouch
import ezvcard.VCard as EzvCard
import ezvcard.property.Uid

import scala.util.{Failure, Success, Try}

private[mongodb] object MongoDbModel {

  final case class Contact private (
      _id: Uid,
      vCard: VCard,
      stayInTouch: Option[StayInTouch]
  )

  object Contact {
    def apply(vCard: EzvCard, stayInTouch: Option[StayInTouch]): Try[Contact] =
      checkAndBuild(vCard.getUid, vCard, stayInTouch)

    def apply(vCard: VCard, stayInTouch: Option[StayInTouch]): Try[Contact] =
      apply(vCard.value, stayInTouch)

    private def checkAndBuild(
        _id: Uid,
        vCard: EzvCard,
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
          Success(Contact(_id, VCard(vCard), Some(stayInTouch)))
        case None if allUidEqual(_id, vCard.getUid) =>
          Success(Contact(_id, VCard(vCard), stayInTouch))
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

  // vCard wrapper to support database side search and order operation with its private fields
  // the one and only single source of truth for contact data is ALWAYS the vCard field!
  final case class VCard private (
      value: EzvCard,
      private val FN: Option[String],
      private val familyName: Option[String],
      private val givenName: Option[String]
  )

  object VCard {
    def apply(contact: model.Contact): VCard = fromVCard(contact.vCard)

    def apply(vCard: EzvCard): VCard = fromVCard(vCard)

    private def fromVCard(vCard: EzvCard) =
      new VCard(
        vCard,
        tryAsOpt(vCard.getFormattedName.getValue),
        tryAsOpt(vCard.getStructuredName.getFamily),
        tryAsOpt(vCard.getStructuredName.getGiven)
      )

    private def tryAsOpt[X](x: () => X): Option[X] = Try(x.apply).toOption
  }

}
