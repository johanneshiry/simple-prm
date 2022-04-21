/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.routes.v1

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives.{
  _enhanceRouteWithConcatenation,
  pass,
  pathPrefix
}
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequest
import akka.http.scaladsl.server.{Directive0, Route}
import akka.http.scaladsl.{Http, ServerBuilder}
import com.github.johanneshiry.simpleprm.api.rest.RestApi
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.GetContactsPaginatedResponse.PaginatedContacts
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.{
  ContactHandler,
  GetContactsPaginatedResponseOK
}
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.StayInTouchApi.StayInTouchHandler
import com.github.johanneshiry.simpleprm.io.model.StayInTouch
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

final case class RestApiV1(
    httpServer: ServerBuilder,
    contactHandler: ContactHandler,
    stayInTouchHandler: StayInTouchHandler
)(implicit
    actorSystem: ActorSystem[Nothing],
    ec: ExecutionContext
) extends RestApi("v1")
    with LazyLogging {

  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

  protected def apiRoute: Route =
    debugLogging {
      cors() { // todo configure + handle rejections (https://github.com/lomigmegard/akka-http-cors/issues/1) + harden cors
        StayInTouchApi.routes(stayInTouchHandler) ~
          ContactApi.routes(contactHandler)
      }
    }

  private def debugLogging: Directive0 = {
    extractRequest.flatMap { request =>
      logger.debug(s"Received request: $request")
      pass
    }
  }

}
