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
import akka.http.scaladsl.server.{Directive, PathMatcher, Route}
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ReminderApi.CreateReminderResponse.CreateStayInTouchResponseOK
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ReminderApi.DelReminderResponse.{
  DelReminderResponseFailed,
  DelReminderResponseOK
}
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ReminderApi.GetReminderResponse.{
  GetReminderResponseNoContent,
  GetRemindersResponseOK
}
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.model.{JSONCodecs, Reminder}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ezvcard.property.Uid
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import com.github.johanneshiry.simpleprm.io.model.JSONCodecs.*

import java.util.UUID
import scala.util.{Failure, Success}

object ReminderApi extends FailFastCirceSupport with MarshalSupport {

  import io.circe.generic.auto._

  // routes
  implicit val decReminder: Decoder[Reminder] =
    JSONCodecs.decReminder(JSONCodecs.decUid("contactId"))
  private val uuidMatcher = PathMatcher(
    """[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r
  )

  def routes(handler: StayInTouchHandler): Route =
    pathPrefix("reminder") {
      post(
        entity(as[Reminder]) { reminder =>
          complete(handler.createReminder(reminder))
        }
      ) ~
        get {
          path(uuidMatcher) { uuid =>
            complete(handler.getReminder(new Uid(uuid)))
          }
        } ~
        delete {
          path(uuidMatcher) { uuid =>
            complete(handler.delReminder(UUID.fromString(uuid)))
          }
        }
    }

  // handler interface containing all methods supported by the api
  trait StayInTouchHandler {
    def createReminder(
        reminder: Reminder
    ): Future[CreateReminderResponse]

    def getReminder(contactUid: Uid): Future[GetReminderResponse]

    def delReminder(reminderUuid: UUID): Future[DelReminderResponse]

  }

  object StayInTouchHandler {

    final case class StayInTouchHandler(dbConnector: DbConnector)(implicit
        ec: ExecutionContext
    ) extends ReminderApi.StayInTouchHandler {
      def createReminder(
          reminder: Reminder
      ): Future[CreateReminderResponse] = dbConnector
        .updateReminder(reminder, false)
        .map(CreateStayInTouchResponseOK.apply) // todo return error on failure

      def getReminder(contactUid: Uid): Future[GetReminderResponse] =
        dbConnector.getReminder(contactUid).map {
          case Some(reminder) =>
            GetRemindersResponseOK(Vector(reminder))
          case None =>
            GetReminderResponseNoContent
        }

      def delReminder(reminderUuid: UUID): Future[DelReminderResponse] =
        dbConnector.delReminder(reminderUuid).map {
          case Success(_) =>
            DelReminderResponseOK
          case Failure(exception) =>
            DelReminderResponseFailed(exception)
        }

    }
  }

  // responses
  sealed abstract class CreateReminderResponse(val statusCode: StatusCode)

  object CreateReminderResponse {
    final case class CreateStayInTouchResponseOK(value: Reminder)
        extends CreateReminderResponse(StatusCodes.OK)

    implicit def createStayInTouchResponseTRM
        : ToResponseMarshaller[CreateReminderResponse] = Marshaller {
      implicit ec => resp => createResponseTR(resp)
    }

    import io.circe.syntax._
    import scala.language.postfixOps

    implicit def createStayInTouchMarshaller
        : Marshaller[Reminder, ResponseEntity] =
      Marshaller.strict(reminder =>
        Marshalling.Opaque { () => reminder.asJson.toString }
      )

    def createResponseTR(
        createStayInTouchResp: CreateReminderResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      createStayInTouchResp match {
        case c @ CreateStayInTouchResponseOK(stayInTouch) =>
          marshal(stayInTouch, c.statusCode)
      }

  }

  sealed abstract class GetReminderResponse(val statusCode: StatusCode)

  object GetReminderResponse {

    final case class GetRemindersResponseOK(value: Vector[Reminder])
        extends GetReminderResponse(StatusCodes.OK)

    case object GetReminderResponseNoContent
        extends GetReminderResponse(StatusCodes.OK)

    implicit def getRemindersResponseTRM
        : ToResponseMarshaller[GetReminderResponse] = Marshaller {
      implicit ec => resp => createResponseTR(resp)
    }

    import io.circe.syntax._
    import scala.language.postfixOps

    implicit def getRemindersMarshaller
        : Marshaller[Vector[Reminder], ResponseEntity] =
      Marshaller.strict(reminder =>
        Marshalling.Opaque { () =>
          reminder.asJson(JSONCodecs.encReminders).toString
        }
      )

    def createResponseTR(
        getReminderResp: GetReminderResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      getReminderResp match {
        case c @ GetRemindersResponseOK(stayInTouch) =>
          marshal(stayInTouch, c.statusCode)
        case c @ GetReminderResponseNoContent =>
          marshal(c.statusCode, emptyHttpJsonArrayResponse)
      }

  }

  sealed abstract class DelReminderResponse(val statusCode: StatusCode)

  object DelReminderResponse {
    case object DelReminderResponseOK
        extends DelReminderResponse(StatusCodes.OK)

    final case class DelReminderResponseFailed(error: Throwable)
        extends DelReminderResponse(StatusCodes.InternalServerError)

    implicit def delRemindersResponseTRM
        : ToResponseMarshaller[DelReminderResponse] = Marshaller {
      implicit ec => resp => createResponseTR(resp)
    }

    import io.circe.syntax._
    import scala.language.postfixOps

    implicit def delRemindersMarshaller: Marshaller[Throwable, ResponseEntity] =
      Marshaller.strict(exception =>
        Marshalling.Opaque { () => exception.asJson.toString }
      )

    def createResponseTR(
        getReminderResp: DelReminderResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      getReminderResp match {
        case c @ DelReminderResponseOK =>
          marshal(emptyHttpJsonObjResponse, c.statusCode)
        case c @ DelReminderResponseFailed(error) =>
          marshal(error, c.statusCode)

      }

  }

}
