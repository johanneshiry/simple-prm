/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.{Http, HttpExt, ServerBuilder}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route}
import akka.http.scaladsl.server.Directives.*
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

private[rest] abstract class RestApi(version: String)(implicit
    actorSystem: ActorSystem[Nothing],
    ec: ExecutionContext
) extends LazyLogging {

  protected def apiRoute: Route

  val httpServer: ServerBuilder

  private val healthCheckRoute = pathPrefix("healthcheck") {
    get {
      complete("Make it so! — Jean-Luc Picard")
    }
  }

  val rejectionHandler: RejectionHandler =
    RejectionHandler.default.mapRejectionResponse {
      case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
        val statusCode = res.status.intValue()
        val msg =
          ent.data.utf8String.replaceAll("\"", """\"""").replaceAll("\n", " ")
        val rej =
          s"""
           |{
           |status: $statusCode,
           |message: $msg
           |}
           |""".stripMargin

        res.withEntity(HttpEntity(ContentTypes.`application/json`, rej))
      case x => x // pass through all other types of responses
    }

  val exceptionHandler: ExceptionHandler = ExceptionHandler { case e =>
    extractUri { uri =>
      logger.error(s"Request to $uri could not be handled normally!", e)
      complete(
        HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!")
      )
    }
  }

  val route: Route =
    cors() { // todo configure + handle rejections (https://github.com/lomigmegard/akka-http-cors/issues/1) + harden cors
      handleRejections(rejectionHandler) {
        handleExceptions(exceptionHandler) {
          pathPrefix("api" / "rest" / version) {
            apiRoute ~
              healthCheckRoute
          }
        }
      }
    }

  // bind route to server
  val server: Future[Http.ServerBinding] = httpServer.bindFlow(route)

  def terminate(hardDeadline: FiniteDuration): Future[Http.HttpTerminated] =
    server.flatMap(_.terminate(hardDeadline))

}
