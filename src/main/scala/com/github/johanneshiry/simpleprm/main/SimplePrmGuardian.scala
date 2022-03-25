/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.main

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.github.johanneshiry.simpleprm.carddav.{CardDavService, SyncService}
import com.github.johanneshiry.simpleprm.carddav.CardDavService.ConfigParams
import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDBConnector
import com.github.johanneshiry.simpleprm.io.{
  Connector,
  ReaderService,
  WriterService
}
import com.typesafe.scalalogging.LazyLogging

import java.net.URI
import scala.concurrent.duration.*

object SimplePrmGuardian extends LazyLogging {

  sealed trait SimplePrmGuardianCmd

  private case object Dummy extends SimplePrmGuardianCmd // todo remove

  def apply(cfg: SimplePrmCfg): Behavior[SimplePrmGuardianCmd] =
    Behaviors.setup[SimplePrmGuardianCmd] { ctx =>
      // setup services // todo build correctly

      idle()
    }

  private def idle(): Behavior[SimplePrmGuardianCmd] =
    Behaviors.receiveMessage { case Dummy =>
      logger.error("Received Dummy. This is invalid!") // todo adapt
      Behaviors.same
    }

}
