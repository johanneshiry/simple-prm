/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import com.github.johanneshiry.simpleprm.io.model.Contact
import com.github.sardine.Sardine
import com.typesafe.scalalogging.LazyLogging
import ezvcard.VCard
import ezvcard.io.text.VCardReader

import scala.util.Using
import java.io.InputStream
import java.net.URI
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}
import scala.collection.parallel.CollectionConverters.*

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

  final case class GetSuccessful(contacts: Seq[Contact]) extends GetResponse

  final case class GetFailed(exception: Throwable) extends GetResponse

  // service configuration data
  final case class ConfigParams(
      serverUri: URI,
      username: String,
      password: String,
      disableCertificateCheck: Boolean
  )

  object ConfigParams {

    def apply(cfg: SimplePrmCfg.SimplePrm.Carddav): ConfigParams =
      ConfigParams(
        new URI(cfg.uri),
        cfg.username,
        cfg.password,
        cfg.disableCertificateCheck
      )

  }

  private final case class StateData(client: SardineClientWrapper)

  def apply(configParams: ConfigParams): Behavior[CardDavServiceCmd] = {

    // setup state data
    val stateData = StateData(
      SardineClientWrapper(
        configParams.serverUri,
        configParams.username,
        configParams.password,
        configParams.disableCertificateCheck
      )
    )

    idle(stateData)
  }

  private def idle(stateData: StateData): Behavior[CardDavServiceCmd] =
    Behaviors.receiveMessage { case request: CardDavServiceRequest =>
      request match {
        case Get(replyTo) =>
          logger.info(
            s"Getting contacts from CardDav server '${stateData.client.serverUri}'...'"
          )
          stateData.client.listDir.map(
            _.par
              .flatMap(davResource =>
                getAsVCard(davResource, stateData.client) match {
                  case Failure(exception) =>
                    logger.error(
                      s"Cannot read vCard from Dav resource '${davResource.davResource.getName}'!",
                      exception
                    )
                    Seq.empty
                  case Success(vCards) =>
                    vCards
                }
              )
              .distinct
          ) match {
            case Failure(exception) =>
              logger.error(
                "Cannot get contacts from CardDav server!",
                exception
              )
              replyTo ! GetFailed(exception)
            case Success(serverContacts) =>
              logger.info(
                s"Successfully received ${serverContacts.size} contacts from CardDav server!"
              )
              replyTo ! GetSuccessful(
                serverContacts.seq.map(Contact.apply)
              )
          }
          Behaviors.same
      }
    }

  private def getAsVCard(
      davResourceWrapper: DavResourceWrapper,
      sardineClientWrapper: SardineClientWrapper
  ): Try[Seq[VCard]] =
    Using.Manager { use =>
      // potential for parallelization -> concat multiple input streams into one and create worker actors
      val vCardStream =
        use(sardineClientWrapper.sardine.get(davResourceWrapper.fullPath))
      val reader = use(new VCardReader(vCardStream))
      reader.readAll().asScala.toSeq
    }
}
