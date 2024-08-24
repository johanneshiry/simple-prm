/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.main

import akka.actor.typed.ActorSystem
import com.github.johanneshiry.simpleprm.cfg.{ArgsParser, SimplePrmCfg}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Run extends LazyLogging {

  def main(args: Array[String]): Unit = {

    ArgsParser.prepareConfig(args) match {
      case Failure(exception) =>
        logger.error(s"Initialization failed!", exception)
      case Success((_, config)) =>
        val system =
          ActorSystem(SimplePrmGuardian(SimplePrmCfg(config)), "SimplePrm")
    }

  }

}
