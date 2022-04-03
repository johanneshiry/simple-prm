/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.routes.v1

import akka.http.scaladsl.marshalling
import akka.http.scaladsl.marshalling.{
  Marshal,
  Marshaller,
  Marshalling,
  ToResponseMarshaller
}

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.model.{
  ContentType,
  HttpEntity,
  HttpResponse,
  ResponseEntity,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.model.{JSONCodecs, StayInTouch}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ezvcard.property.Uid
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import com.github.johanneshiry.simpleprm.io.model.JSONCodecs.*

object StayInTouchApi extends FailFastCirceSupport with MarshalSupport {

  import io.circe.generic.auto._

  // handler interface containing all methods supported by the api
  trait StayInTouchHandler {
    def createStayInTouch(
        stayInTouch: StayInTouch
    ): Future[CreateStayInTouchResponse]
  }

  object StayInTouchHandler {

    final case class StayInTouchHandler(dbConnector: DbConnector)(implicit
        ec: ExecutionContext
    ) extends StayInTouchApi.StayInTouchHandler {
      def createStayInTouch(
          stayInTouch: StayInTouch
      ): Future[CreateStayInTouchResponse] = dbConnector
        .upsertStayInTouch(stayInTouch)
        .map(CreateStayInTouchResponseOK.apply)
    }

  }

  // responses
  sealed abstract class CreateStayInTouchResponse(val statusCode: StatusCode)

  final case class CreateStayInTouchResponseOK(value: StayInTouch)
      extends CreateStayInTouchResponse(StatusCodes.OK)

  object CreateStayInTouchResponse {
    implicit def createStayInTouchResponseTRM
        : ToResponseMarshaller[CreateStayInTouchResponse] = Marshaller {
      implicit ec => resp => createResponseTR(resp)
    }

    import io.circe.syntax._
    import scala.language.postfixOps

    implicit def createStayInTouchMarshaller
        : Marshaller[StayInTouch, ResponseEntity] =
      Marshaller.strict(stayInTouch =>
        Marshalling.Opaque { () => stayInTouch.asJson.toString }
      )

    def createResponseTR(
        createStayInTouchResp: CreateStayInTouchResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      createStayInTouchResp match {
        case c @ CreateStayInTouchResponseOK(stayInTouch) =>
          marshal(stayInTouch, c.statusCode)
      }

  }

  // routes
  implicit val decUid: Decoder[Uid] = JSONCodecs.decUid("contactId")

  def routes(handler: StayInTouchHandler): Route = pathPrefix("stayInTouch") {
    path("create")(
      post(
        entity(as[StayInTouch]) { stayInTouch =>
          complete(handler.createStayInTouch(stayInTouch))
        }
      )
    )
  }

}
