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
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.StayInTouchApi.CreateStayInTouchResponse.CreateStayInTouchResponseOK
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.StayInTouchApi.GetReminderResponse.{
  GetReminderResponseNoContent,
  GetReminderResponseOK
}
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.model.{JSONCodecs, StayInTouch}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ezvcard.property.Uid
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import com.github.johanneshiry.simpleprm.io.model.JSONCodecs.*

object StayInTouchApi extends FailFastCirceSupport with MarshalSupport {

  import io.circe.generic.auto._

  // routes
  implicit val decUid: Decoder[Uid] = JSONCodecs.decUid("contactId")
  private val uuidMatcher = PathMatcher(
    """[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r
  )

  def routes(handler: StayInTouchHandler): Route =
    pathPrefix("stayInTouch") { // todo switch to "reminder"
      post(
        entity(as[StayInTouch]) { stayInTouch =>
          complete(handler.createStayInTouch(stayInTouch))
        }
      ) ~
        get {
          // java uuid converts to lowercase, which is in fact valid according to the spec,
          // but is not valid for uid's and therefore we need to keep upper cases if provided :-(
          path(uuidMatcher) { uuid =>
            complete(handler.getReminder(new Uid(uuid)))
          }
        }
    }

  // handler interface containing all methods supported by the api
  trait StayInTouchHandler {
    def createStayInTouch(
        stayInTouch: StayInTouch
    ): Future[CreateStayInTouchResponse]
    def getReminder(contactUid: Uid): Future[GetReminderResponse]
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

      def getReminder(contactUid: Uid): Future[GetReminderResponse] =
        dbConnector.getStayInTouch(contactUid).map {
          case Some(reminder) =>
            GetReminderResponseOK(reminder)
          case None =>
            GetReminderResponseNoContent
        }
    }

  }

  // responses
  sealed abstract class CreateStayInTouchResponse(val statusCode: StatusCode)

  object CreateStayInTouchResponse {
    final case class CreateStayInTouchResponseOK(value: StayInTouch)
        extends CreateStayInTouchResponse(StatusCodes.OK)

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

  sealed abstract class GetReminderResponse(val statusCode: StatusCode)

  object GetReminderResponse {
    final case class GetReminderResponseOK(value: StayInTouch)
        extends GetReminderResponse(StatusCodes.OK)

    case object GetReminderResponseNoContent
        extends GetReminderResponse(StatusCodes.NotFound)

    implicit def getReminderResponseTRM
        : ToResponseMarshaller[GetReminderResponse] = Marshaller {
      implicit ec => resp => createResponseTR(resp)
    }

    import io.circe.syntax._
    import scala.language.postfixOps

    implicit def getReminderMarshaller
        : Marshaller[StayInTouch, ResponseEntity] =
      Marshaller.strict(stayInTouch =>
        Marshalling.Opaque { () => stayInTouch.asJson.toString }
      )

    def createResponseTR(
        getReminderResp: GetReminderResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      getReminderResp match {
        case c @ GetReminderResponseOK(stayInTouch) =>
          marshal(stayInTouch, c.statusCode)
        case c @ GetReminderResponseNoContent =>
          marshal(c.statusCode)
      }

  }

}
