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

import scala.concurrent.{ExecutionContext, Future}

object ContactApi {

  // handler interface containing all methods supported by the api
  trait ContactHandler {
    def getContacts(
        limit: Option[Int] = None
    ): Future[GetContactsPaginatedResponse]
  }

  // responses
  sealed abstract class GetContactsPaginatedResponse(val statusCode: StatusCode)

  final case class GetContactsPaginatedResponseOK(
      paginatedContacts: PaginatedContacts
  ) extends GetContactsPaginatedResponse(StatusCodes.OK)

  object GetContactsPaginatedResponse {

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
        Marshalling.Opaque { () => paginatedContacts.asJson.toString }
      )

    def getContactsPaginatedResponseTR(
        getContactsPaginatedResponse: GetContactsPaginatedResponse
    )(implicit ec: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
      getContactsPaginatedResponse match {
        case c @ GetContactsPaginatedResponseOK(paginatedContact) =>
          Marshal(paginatedContact).to[ResponseEntity].map {
            stayInTouchEntity => // todo remove code duplicates if possible!
              Marshalling.Opaque { () =>
                HttpResponse(c.statusCode, entity = stayInTouchEntity)
              } :: Nil
          }
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
