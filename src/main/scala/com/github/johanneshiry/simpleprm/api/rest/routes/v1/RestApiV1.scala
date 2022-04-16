/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.routes.v1

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives.{
  pathPrefix,
  _enhanceRouteWithConcatenation
}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{Http, ServerBuilder}
import com.github.johanneshiry.simpleprm.api.rest.RestApi
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.GetContactsPaginatedResponse.PaginatedContacts
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.{
  ContactHandler,
  GetContactsPaginatedResponseOK
}
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.StayInTouchApi.{
  CreateStayInTouchResponseOK,
  StayInTouchHandler
}
import com.github.johanneshiry.simpleprm.io.model.StayInTouch

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

final case class RestApiV1(
    httpServer: ServerBuilder,
    contactHandler: ContactHandler,
    stayInTouchHandler: StayInTouchHandler
)(implicit
    actorSystem: ActorSystem[Nothing],
    ec: ExecutionContext
) extends RestApi("v1") {

  import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

  protected def apiRoute: Route = cors() { // todo configure + harden cors
    StayInTouchApi.routes(stayInTouchHandler) ~
      ContactApi.routes(contactHandler)
  }

}
