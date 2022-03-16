/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import ezvcard.VCard

object WriterService extends LazyLogging {

  // external api
  sealed trait WriterServiceCmd

  final case class AddContacts(contacts: Seq[VCard]) extends WriterServiceCmd
  final case class DelContacts(contacts: Seq[VCard]) extends WriterServiceCmd

  def apply(): Behavior[WriterServiceCmd] = Behaviors.receiveMessage {
    case AddContacts(contacts) =>
      logger.info(s"Received AddContacts($contacts).") // todo remove
      Behaviors.same
    case DelContacts(contacts) =>
      logger.info(s"Received DelContacts($contacts).") // todo remove
      Behaviors.same
  }

}
