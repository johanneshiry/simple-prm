/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.main

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.johanneshiry.simpleprm.carddav.{CardDavService, SyncService}
import com.github.johanneshiry.simpleprm.carddav.CardDavService.ConfigParams
import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbConnector
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.typesafe.scalalogging.LazyLogging

import java.net.URI
import scala.concurrent.duration.*

object SimplePrmGuardian extends LazyLogging {

  sealed trait SimplePrmGuardianCmd

  private case object Dummy extends SimplePrmGuardianCmd // todo remove

  def apply(cfg: SimplePrmCfg): Behavior[SimplePrmGuardianCmd] =
    Behaviors.setup[SimplePrmGuardianCmd] { ctx =>
      logger.info("Starting SimplePrm ...")
      // setup services

      /// card dav server service
      val cardDavService = ctx.spawn(
        CardDavService(
          ConfigParams(cfg.simple_prm.carddav)
        ),
        "DavServerService"
      )

      // db connector
      val connector: DbConnector =
        MongoDbConnector(cfg.simple_prm.database)(ctx.executionContext)

      // card dav sync service
      val syncService = ctx.spawn(
        SyncService(
          cfg.simple_prm.carddav.syncInterval,
          cardDavService,
          connector
        ),
        "SyncService"
      )

      logger.info("Startup complete!")
      idle()
    }

  private def idle(): Behavior[SimplePrmGuardianCmd] =
    Behaviors.receiveMessage { case Dummy =>
      logger.error("Received Dummy. This is invalid!") // todo adapt
      Behaviors.same
    }

}
