/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import com.github.sardine.Sardine
import com.typesafe.scalalogging.LazyLogging
import ezvcard.VCard
import ezvcard.io.text.VCardReader

import java.io.InputStream
import java.net.URI
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object CardDavService extends LazyLogging {

  sealed trait CardDavServiceCmd

  // external api
  /// requests
  sealed trait CardDavServiceRequest extends CardDavServiceCmd

  final case class Get(replyTo: ActorRef[GetResponse])
      extends CardDavServiceRequest

  /// responses
  sealed trait CardDavServiceResponse

  //// get responses
  sealed trait GetResponse extends CardDavServiceResponse

  final case class GetSuccessful(contacts: Seq[VCard]) extends GetResponse

  final case class GetFailed(exception: Throwable) extends GetResponse

  // service configuration data
  final case class ConfigParams(
      serverUri: URI,
      username: String,
      password: String
  )

  private final case class StateData(client: SardineClientWrapper)

  def apply(configParams: ConfigParams): Behavior[CardDavServiceCmd] = {

    // setup state data
    val stateData = StateData(
      SardineClientWrapper(
        configParams.serverUri,
        configParams.username,
        configParams.password
      )
    )

    idle(stateData)
  }

  private def idle(stateData: StateData): Behavior[CardDavServiceCmd] =
    Behaviors.receive { case (ctx, msg) =>
      msg match {
        case request: CardDavServiceRequest =>
          request match {
            case Get(replyTo) =>
              logger.info(
                s"Getting contacts from CardDav server '${stateData.client.serverUri}' ...'"
              )
              stateData.client.listDir.map(
                _.flatMap(get(_, stateData.client))
              ) match {
                case Failure(exception) =>
                  replyTo ! GetFailed(exception)
                case Success(serverContacts) =>
                  replyTo ! GetSuccessful(serverContacts)
              }
              Behaviors.same
            // todo log debug information about fetching + either failure or success
          }
      }
    }

  private def get(
      davResourceWrapper: DavResourceWrapper,
      sardineClientWrapper: SardineClientWrapper
  ): Seq[VCard] =
    readVCard(sardineClientWrapper.sardine.get(davResourceWrapper.fullPath))

  // potential for parallelization -> concat multiple input streams into one and create worker actors
  private def readVCard(vCardStream: InputStream): Seq[VCard] =
    new VCardReader(vCardStream).readAll().asScala.toSeq

}
