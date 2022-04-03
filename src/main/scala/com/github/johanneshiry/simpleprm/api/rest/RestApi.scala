/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.{Http, HttpExt, ServerBuilder}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives.*

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

private[rest] abstract class RestApi(version: String)(implicit
    actorSystem: ActorSystem[Nothing],
    ec: ExecutionContext
) {

  protected def apiRoute: Route

  val httpServer: ServerBuilder

  private val healthCheckRoute = pathPrefix("healthcheck") {
    get {
      complete("Make it so! — Jean-Luc Picard")
    }
  }

  val exceptionHandler: ExceptionHandler = ExceptionHandler { case _ =>
    extractUri { uri =>
      println(s"Request to $uri could not be handled normally")
      complete(
        HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!")
      )
    }
  }

  val route: Route = handleExceptions(exceptionHandler) {
    pathPrefix("api" / "rest" / version) {
      apiRoute ~
        healthCheckRoute
    }
  }

  // bind route to server
  val server: Future[Http.ServerBinding] = httpServer.bindFlow(route)

  def terminate(hardDeadline: FiniteDuration): Future[Http.HttpTerminated] =
    server.flatMap(_.terminate(hardDeadline))

}
