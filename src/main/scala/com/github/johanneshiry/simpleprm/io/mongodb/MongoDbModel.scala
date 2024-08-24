/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import akka.util.Collections
import com.github.johanneshiry.simpleprm.io.model
import com.github.johanneshiry.simpleprm.io.model.Reminder
import ezvcard.VCard as EzvCard
import ezvcard.property.Uid

import scala.collection.immutable.{AbstractSeq, LinearSeq}
import scala.util.{Failure, Success, Try}

private[mongodb] object MongoDbModel {

  final case class Contact private (
      _id: Uid,
      vCard: VCard,
      reminders: Seq[Reminder]
  )

  object Contact {
    def apply(vCard: EzvCard, reminders: Seq[Reminder]): Try[Contact] =
      checkAndBuild(vCard.getUid, vCard, reminders)

    def apply(vCard: VCard, reminders: Seq[Reminder]): Try[Contact] =
      apply(vCard.value, reminders)

    private def checkAndBuild(
        _id: Uid,
        vCard: EzvCard,
        reminders: Seq[Reminder]
    ): Try[Contact] = {

      def allUidEqual(
          _id: Uid,
          vCardUid: Uid,
          reminderContactUids: Set[Uid]
      ): Boolean =
        _id.getValue.equals(vCardUid.getValue) &&
          reminderContactUids.size == 1 &&
          _id.getValue.equals(
            reminderContactUids.headOption.getOrElse(_id).getValue
          )

      reminders match {
        case Nil =>
          Success(Contact(_id, VCard(vCard), reminders))
        case _
            if allUidEqual(
              _id,
              vCard.getUid,
              reminders.map(_.contactId).toSet
            ) =>
          Success(Contact(_id, VCard(vCard), reminders))
        case _ =>
          Failure(
            new IllegalArgumentException(
              s"Cannot construct Contact instance with different Uids.\n" +
                s"_id = ${_id.getValue}\n" +
                s"vCardUid = ${vCard.getUid.getValue}" +
                s"${if (reminders.nonEmpty) "\nremindersContactId: [" + reminders.mkString(", ") + "]"
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

    private def tryAsOpt[X](x: () => X): Option[X] =
      Try(Option(x.apply)).toOption.flatten
  }

}
