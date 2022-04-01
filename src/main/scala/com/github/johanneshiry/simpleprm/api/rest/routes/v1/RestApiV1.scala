/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.routes.v1

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{Http, ServerBuilder}
import com.github.johanneshiry.simpleprm.api.rest.RestApi
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.StayInTouchApi.{
  CreateStayInTouchResponseOK,
  StayInTouchHandler
}
import com.github.johanneshiry.simpleprm.io.model.StayInTouch

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

final case class RestApiV1(httpServer: ServerBuilder)(implicit
    actorSystem: ActorSystem[Any],
    ec: ExecutionContext
) extends RestApi("v1") {

  protected def apiRoute: Route = StayInTouchApi.routes(new StayInTouchHandler {
    override def createStayInTouch(
        stayInTouch: StayInTouch
    ): Future[StayInTouchApi.CreateStayInTouchResponse] =
      println("We stay in touch")
      println(stayInTouch)
      Future.successful(
        CreateStayInTouchResponseOK(stayInTouch)
      )

  })

}
