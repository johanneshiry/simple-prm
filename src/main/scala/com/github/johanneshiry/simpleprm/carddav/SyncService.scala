/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import com.github.johanneshiry.simpleprm.carddav.CardDavService.{
  CardDavServiceCmd,
  GetFailed,
  GetSuccessful
}
import com.github.johanneshiry.simpleprm.io.DbConnector

import com.github.johanneshiry.simpleprm.io.model.Contact
import com.typesafe.scalalogging.LazyLogging
import ezvcard.VCard

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

object SyncService extends LazyLogging {

  sealed trait SyncServiceCmd

  // internal api
  private case object Sync extends SyncServiceCmd

  private sealed trait ServerGetResponse extends SyncServiceCmd

  private final case class ServerGetSuccessful(contacts: Seq[Contact])
      extends ServerGetResponse

  private final case class ServerGetFailed(throwable: Throwable)
      extends ServerGetResponse

  private sealed trait LocalGetResponse extends SyncServiceCmd

  private final case class LocalGetSuccessful(contacts: Seq[Contact])
      extends LocalGetResponse

  private final case class LocalGetFailed(throwable: Throwable)
      extends LocalGetResponse

  private sealed trait SyncResult extends SyncServiceCmd

  private final case class SyncSuccessful() extends SyncResult

  private final case class SyncFailed(throwable: Throwable) extends SyncResult

  // state data
  private final case class StateData(
      cardDavService: ActorRef[CardDavServiceCmd],
      connector: DbConnector
  )

  def apply(
      syncInterval: java.time.Duration,
      cardDavService: ActorRef[CardDavServiceCmd],
      connector: DbConnector
  ): Behavior[SyncServiceCmd] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        // setup timer with fixed delay for sync with card dav server
        timers.startTimerWithFixedDelay(
          Sync,
          FiniteDuration(syncInterval.toSeconds, SECONDS)
        )

        idle(StateData(cardDavService, connector))
      }
    }

  private def idle(stateData: StateData): Behavior[SyncServiceCmd] =
    Behaviors.receive { case (ctx, msg) =>
      msg match {
        case Sync =>
          logger.info("Syncing contacts with server ...")
          implicit val timeout: Timeout = 10000.seconds
          ctx.ask(stateData.cardDavService, CardDavService.Get.apply) {
            case Success(GetSuccessful(serverContacts)) =>
              ServerGetSuccessful(serverContacts)
            case Success(GetFailed(ex)) => ServerGetFailed(ex)
            case Failure(exception)     => ServerGetFailed(exception)
          }
          ctx.pipeToSelf(stateData.connector.getAllContacts) {
            case Success(localContacts) =>
              LocalGetSuccessful(localContacts)
            case Failure(exception) => LocalGetFailed(exception)
          }
          sync(stateData)()
        case invalid =>
          logger.error(s"Invalid message in idle() received: $invalid")
          Behaviors.same
      }
    }

  private def sync(stateData: StateData)(
      serverGetResponse: Option[ServerGetResponse] = None,
      localGetResponse: Option[LocalGetResponse] = None
  ): Behavior[SyncServiceCmd] = Behaviors.receive { case (ctx, msg) =>
    msg match {
      case serverGetResponse: ServerGetResponse =>
        handleGetResponses(
          stateData,
          Some(serverGetResponse),
          localGetResponse
        )(ctx)
      case localGetResponse: LocalGetResponse =>
        handleGetResponses(
          stateData,
          serverGetResponse,
          Some(localGetResponse)
        )(ctx)
      case _: SyncSuccessful =>
        logger.info("CardDav server sync successful!")
        idle(stateData)
      case SyncFailed(exception) =>
        logger.info("CardDav server sync failed!", exception)
        idle(stateData)
      case invalid =>
        logger.error(s"Invalid message in sync() received: $invalid")
        Behaviors.same
    }
  }

  private def handleGetResponses(
      stateData: StateData,
      serverGetResponse: Option[ServerGetResponse],
      localGetResponse: Option[LocalGetResponse]
  )(ctx: ActorContext[SyncServiceCmd]): Behavior[SyncServiceCmd] = {
    (serverGetResponse, localGetResponse) match {
      case (
            Some(ServerGetSuccessful(serverContacts)),
            Some(LocalGetSuccessful(localContacts))
          ) =>
        implicit val ec: ExecutionContext = ctx.executionContext

        // only delete contacts whose uid is not available at the server anymore
        val serverContactsUids = serverContacts.map(_.uid)
        val toBeDeletedContacts = localContacts
          .diff(serverContacts)
          .filterNot(contact => serverContactsUids.contains(contact.uid))
        logger.info(s"Upserting ${serverContacts.size} contacts!")
        logger.info(s"Removing ${toBeDeletedContacts.size} contacts!")

        ctx.pipeToSelf(
          Future.sequence(
            Seq(
              stateData.connector.updateContacts(serverContacts, true),
              stateData.connector.delContacts(toBeDeletedContacts)
            )
          )
        ) {
          case Success(value) =>
            SyncSuccessful() // todo add parameters
          case Failure(exception) =>
            SyncFailed(exception)
        }
        sync(stateData)()
      case (Some(serverGetFailed: ServerGetFailed), _) =>
        logger.error(
          "Getting contacts from server failed!",
          serverGetFailed.throwable
        )
        idle(stateData)
      case (Some(_), Some(localGet: LocalGetFailed)) =>
        logger.error("Getting local contacts failed!", localGet.throwable)
        idle(stateData)
      case (_, _) =>
        // not finished yet, go back to sync
        sync(stateData)(serverGetResponse, localGetResponse)
    }
  }

}
