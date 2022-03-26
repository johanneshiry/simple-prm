/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.cfg

import java.io.File
import java.nio.file.Paths
import com.typesafe.config.{ConfigFactory, Config as TypesafeConfig}
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser as scoptOptionParser

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

object ArgsParser extends LazyLogging {

  // case class for allowed arguments
  final case class Arguments(
      mainArgs: Array[String],
      configLocation: Option[String] = None,
      config: Option[TypesafeConfig] = None
  )

  // build the config parser using scopt library
  private def buildParser: scoptOptionParser[Arguments] = {
    new scoptOptionParser[Arguments]("simple-prm") {
      opt[String]("config")
        .action((value, args) =>
          args.copy(
            config = Some(parseTypesafeConfig(value)),
            configLocation = Option(value)
          )
        )
        .validate(value =>
          if (value.trim.isEmpty) failure("config location cannot be empty")
          else success
        )
        .validate(value =>
          if (value.contains("\\"))
            failure("invalid config path, expected: /, found: \\")
          else success
        )
        .text("Location of the simple-prm config file")
        .minOccurs(1)
    }
  }

  private def parse(
      parser: scoptOptionParser[Arguments],
      args: Array[String]
  ): Option[Arguments] =
    parser.parse(args, init = Arguments(args))

  def parse(args: Array[String]): Option[Arguments] = parse(buildParser, args)

  private def parseTypesafeConfig(fileName: String): TypesafeConfig = {
    val file = Paths.get(fileName).toFile
    if (!file.exists())
      throw new Exception(s"Missing config file on path $fileName")
    parseTypesafeConfig(file)
  }

  private def parseTypesafeConfig(file: File): TypesafeConfig = {
    ConfigFactory
      .parseFile(file)
      .withFallback(
        ConfigFactory.parseMap(
          Map("simona.inputDirectory" -> file.getAbsoluteFile.getParent).asJava
        )
      )
  }

  def prepareConfig(args: Array[String]): Try[(Arguments, TypesafeConfig)] = {

    parse(args) match {
      case Some(parsedArgs) =>
        parsedArgs.config match {
          case Some(parsedArgsConfig) =>
            Success((parsedArgs, parsedArgsConfig))
          case None =>
            Failure(
              new RuntimeException(
                "Config not found! Please provide a valid config file via --config <path-to-config-file>."
              )
            )
        }
      case None if args.nonEmpty =>
        Failure(
          new IllegalArgumentException(
            s"Unable to parse provided command line arguments:\n${args.mkString("\n")}"
          )
        )
      case None if args.isEmpty =>
        Failure(
          new IllegalArgumentException(
            s"No arguments provided. Please provide at least the path to the configuration file!"
          )
        )
    }

  }

}
