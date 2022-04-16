/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.routes.v1

import akka.http.scaladsl.marshalling.{
  Marshal,
  Marshaller,
  Marshalling,
  ToResponseMarshaller
}
import akka.http.scaladsl.model.{
  HttpResponse,
  ResponseEntity,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.server.Route
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.GetContactsPaginatedResponse.PaginatedContacts
import com.github.johanneshiry.simpleprm.io.model.Contact
import com.github.johanneshiry.simpleprm.io.model.JSONCodecs.*
import akka.http.scaladsl.server.Directives.*
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.{ExecutionContext, Future}

object ContactApi extends FailFastCirceSupport with MarshalSupport {

  // handler interface containing all methods supported by the api
  trait ContactHandler {
    def getContacts(
        limit: Option[Int] = None
    ): Future[GetContactsPaginatedResponse]
  }

  object ContactHandler {
    final case class ContactHandler(dbConnector: DbConnector)(implicit
        ec: ExecutionContext
    ) extends ContactApi.ContactHandler {
      def getContacts(
          limit: Option[Int] = None
      ): Future[GetContactsPaginatedResponse] = dbConnector
        .getContacts(limit)
        .map(contacts =>
          GetContactsPaginatedResponseOK(PaginatedContacts(contacts))
        )
    }
  }

  // responses
  sealed abstract class GetContactsPaginatedResponse(val statusCode: StatusCode)

  final case class GetContactsPaginatedResponseOK(
      paginatedContacts: PaginatedContacts
  ) extends GetContactsPaginatedResponse(StatusCodes.OK)

  object GetContactsPaginatedResponse extends LazyLogging {

    import io.circe.generic.auto._
    import io.circe.syntax._
    import scala.language.postfixOps

    sealed case class PaginatedContacts(contacts: Vector[Contact])

    implicit def getContactsPaginatedResponseTRM
        : ToResponseMarshaller[GetContactsPaginatedResponse] = Marshaller {
      implicit ec => resp => getContactsPaginatedResponseTR(resp)
    }

    private implicit def createStayInTouchMarshaller
        : Marshaller[PaginatedContacts, ResponseEntity] =
      Marshaller.strict(paginatedContacts =>
        Marshalling.Opaque { () =>
          paginatedContacts.contacts.asJson.toString
        }
      )

    def getContactsPaginatedResponseTR(
        getContactsPaginatedResponse: GetContactsPaginatedResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      getContactsPaginatedResponse match {
        case c @ GetContactsPaginatedResponseOK(paginatedContact) =>
          marshal(paginatedContact, c.statusCode)
      }
  }

  // routes
  def routes(handler: ContactHandler): Route = pathPrefix("contact") {
    pathPrefix("get") {
      path("page") {
        get {
          parameters("limit".as[Int].?) { limit =>
            complete(handler.getContacts(limit))
          }
        }
      }
    }
  }

}
