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
import com.github.johanneshiry.simpleprm.io.{
  Connector,
  ReaderService,
  WriterService
}
import com.github.johanneshiry.simpleprm.io.ReaderService.{
  ReadContactsFailed,
  ReadContactsSuccessful,
  ReaderServiceCmd
}
import com.github.johanneshiry.simpleprm.io.WriterService.WriterServiceCmd
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

  private final case class SyncFailed() extends SyncResult

  // state data
  private final case class StateData(
      cardDavService: ActorRef[CardDavServiceCmd],
      connector: Connector
  )

  def apply(
      syncInterval: FiniteDuration,
      cardDavService: ActorRef[CardDavServiceCmd],
      connector: Connector
  ): Behavior[SyncServiceCmd] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        // setup timer with fixed delay for sync with card dav server
        timers.startTimerWithFixedDelay(Sync, syncInterval)

        idle(StateData(cardDavService, connector))
      }
    }

  private def idle(stateData: StateData): Behavior[SyncServiceCmd] =
    Behaviors.receive { case (ctx, msg) =>
      msg match {
        case Sync =>
          logger.info("Syncing contacts with server ...")
          implicit val timeout: Timeout = 10.seconds
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
        logger.info("Sync successful!") // todo
        idle(stateData)
      case _: SyncFailed =>
        logger.info("Sync failed!") // todo
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
        val removedContacts = localContacts.diff(serverContacts)

        ctx.pipeToSelf(
          Future.sequence(
            Seq(
              stateData.connector.upsertContacts(serverContacts),
              stateData.connector.delContacts(removedContacts)
            )
          )
        ) {
          case Success(value) =>
            SyncSuccessful() // todo add parameters
          case Failure(exception) =>
            SyncFailed() // todo add parameters
        }
        sync(stateData)()
      case (Some(serverGetFailed: ServerGetFailed), _) =>
        logger.error(
          "Get contacts from server failed!",
          serverGetFailed.throwable
        )
        idle(stateData)
      case (Some(_), Some(localGet: LocalGetFailed)) =>
        logger.error("Get local contacts failed!", localGet.throwable)
        idle(stateData)
      case (_, _) =>
        // not finished yet, go back to sync
        sync(stateData)(serverGetResponse, localGetResponse)
    }
  }

}
