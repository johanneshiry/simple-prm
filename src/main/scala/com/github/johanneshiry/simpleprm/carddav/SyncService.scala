/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.carddav

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.github.johanneshiry.simpleprm.carddav.CardDavService.{
  CardDavServiceCmd,
  GetFailed,
  GetSuccessful
}
import com.github.johanneshiry.simpleprm.io.{ReaderService, WriterService}
import com.github.johanneshiry.simpleprm.io.ReaderService.{
  ReadContactsFailed,
  ReadContactsSuccessful,
  ReaderServiceCmd
}
import com.github.johanneshiry.simpleprm.io.WriterService.WriterServiceCmd
import com.typesafe.scalalogging.LazyLogging
import ezvcard.VCard

import scala.concurrent.duration.*
import scala.util.{Failure, Success}

object SyncService extends LazyLogging {

  sealed trait SyncServiceCmd

  // internal api
  private case object Sync extends SyncServiceCmd

  private sealed trait ServerGetResponse extends SyncServiceCmd

  private final case class ServerGetSuccessful(contacts: Seq[VCard])
      extends ServerGetResponse

  private final case class ServerGetFailed(throwable: Throwable)
      extends ServerGetResponse

  private sealed trait LocalGetResponse extends SyncServiceCmd

  private final case class LocalGetSuccessful(contacts: Seq[VCard])
      extends LocalGetResponse

  private final case class LocalGetFailed(throwable: Throwable)
      extends LocalGetResponse

  // state data
  private final case class StateData(
      cardDavService: ActorRef[CardDavServiceCmd],
      readerService: ActorRef[ReaderServiceCmd],
      writerService: ActorRef[WriterServiceCmd]
  )

  def apply(
      syncInterval: FiniteDuration,
      cardDavService: ActorRef[CardDavServiceCmd],
      readerService: ActorRef[ReaderServiceCmd],
      writerService: ActorRef[WriterServiceCmd]
  ): Behavior[SyncServiceCmd] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        // setup timer with fixed delay for sync with card dav server
        timers.startTimerWithFixedDelay(Sync, syncInterval)

        idle(StateData(cardDavService, readerService, writerService))
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
          ctx.ask(stateData.readerService, ReaderService.ReadContacts.apply) {
            case Success(ReadContactsSuccessful(localContacts)) =>
              LocalGetSuccessful(localContacts)
            case Success(ReadContactsFailed(ex)) => LocalGetFailed(ex)
            case Failure(exception)              => LocalGetFailed(exception)
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
        handleGetResponses(stateData, Some(serverGetResponse), localGetResponse)
      case localGetResponse: LocalGetResponse =>
        handleGetResponses(stateData, serverGetResponse, Some(localGetResponse))
      case invalid =>
        logger.error(s"Invalid message in sync() received: $invalid")
        Behaviors.same
    }
  }

  private def handleGetResponses(
      stateData: StateData,
      serverGetResponse: Option[ServerGetResponse],
      localGetResponse: Option[LocalGetResponse]
  ) = {

    (serverGetResponse, localGetResponse) match {
      case (
            Some(ServerGetSuccessful(serverContacts)),
            Some(LocalGetSuccessful(localContacts))
          ) =>
        val addContacts = serverContacts.diff(localContacts)
        val delContacts = localContacts.diff(serverContacts)

        // one shot - handle retries etc. inside writer service globally
        if (addContacts.nonEmpty)
          stateData.writerService ! WriterService.AddContacts(addContacts)
        if (delContacts.nonEmpty)
          stateData.writerService ! WriterService.DelContacts(delContacts)

        idle(stateData)
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
