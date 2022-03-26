/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.main

import com.github.johanneshiry.simpleprm.cfg.ArgsParser
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Run extends LazyLogging {

  def main(args: Array[String]): Unit = {

    ArgsParser.prepareConfig(args) match {
      case Failure(exception) =>
        logger.error(s"Initialization failed! Error: $exception")
      case Success((_, config)) =>

    }

    // todo as config from carueda
    val syncInterval: FiniteDuration = 15 minutes

  }

}
