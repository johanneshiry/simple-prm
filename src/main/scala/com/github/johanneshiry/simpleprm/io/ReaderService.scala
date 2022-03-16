/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import ezvcard.VCard

object ReaderService extends LazyLogging {

  // external api
  sealed trait ReaderServiceCmd

  /// requests
  sealed trait ReaderServiceRequest extends ReaderServiceCmd

  final case class ReadContacts(replyTo: ActorRef[ReadContactsResponse])
      extends ReaderServiceRequest

  /// responses
  sealed trait ReaderServiceResponse

  //// read contacts responses
  sealed trait ReadContactsResponse extends ReaderServiceResponse

  final case class ReadContactsSuccessful(contacts: Seq[VCard])
      extends ReadContactsResponse

  final case class ReadContactsFailed(exception: Throwable)
      extends ReadContactsResponse

  def apply(): Behavior[ReaderServiceCmd] = Behaviors.receiveMessage {
    case request: ReaderServiceRequest =>
      request match {
        case ReadContacts(replyTo) =>
          replyTo ! ReadContactsSuccessful(Seq.empty) // todo
          logger.info(s"Received request from $replyTo")
          Behaviors.same
      }
  }

}
