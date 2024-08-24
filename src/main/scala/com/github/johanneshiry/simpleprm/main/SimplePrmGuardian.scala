/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.main

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.ContactHandler.ContactHandler
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.RestApiV1
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ReminderApi.ReminderHandler
import com.github.johanneshiry.simpleprm.carddav.{CardDavService, SyncService}
import com.github.johanneshiry.simpleprm.carddav.CardDavService.ConfigParams
import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbConnector
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.notifier.{
  EmailComposer,
  EmailNotifierService
}
import com.github.johanneshiry.simpleprm.notifier.EmailNotifierService.EmailNotifierConfig
import com.typesafe.scalalogging.LazyLogging

import java.net.URI
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.*

object SimplePrmGuardian extends LazyLogging {

  sealed trait SimplePrmGuardianCmd

  private case object Dummy extends SimplePrmGuardianCmd // todo remove

  def apply(cfg: SimplePrmCfg): Behavior[SimplePrmGuardianCmd] =
    Behaviors.setup[SimplePrmGuardianCmd] { ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system
      implicit val ec: ExecutionContextExecutor = ctx.system.executionContext

      logger.info("Starting SimplePrm ...")
      // setup services

      /// card dav server service
      val cardDavService = ctx.spawn(
        CardDavService(
          ConfigParams(cfg.simple_prm.carddav)
        ),
        "DavServerService"
      )

//      // db connector
//      val connector: DbConnector =
//        MongoDbConnector(cfg.simple_prm.database)(ctx.executionContext)
//
//      // card dav sync service
//      val syncService = ctx.spawn(
//        SyncService(
//          cfg.simple_prm.carddav.syncInterval,
//          cardDavService,
//          connector
//        ),
//        "SyncService"
//      )
//
      // email notifier
      val notifierService = ctx.spawn(
        EmailNotifierService(
          EmailNotifierConfig(
            cfg.simple_prm.emailServer,
            cfg.simple_prm.notifier,
            cardDavService
          ),
          EmailComposer(cfg.simple_prm.notifier.email)
        ),
        "EmailNotifierService"
      )

//      /// http server + rest api
//      RestApiV1(
//        Http().newServerAt(cfg.simple_prm.rest.host, cfg.simple_prm.rest.port),
//        ContactHandler(connector),
//        ReminderHandler.StayInTouchHandler(connector)
//      )

      logger.info("Startup complete!")
      idle()
    }

  private def idle(): Behavior[SimplePrmGuardianCmd] =
    Behaviors.receiveMessage { case Dummy =>
      logger.error("Received Dummy. This is invalid!") // todo adapt
      Behaviors.same
    }

}
