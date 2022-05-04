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
import akka.http.scaladsl.server.{
  MalformedQueryParamRejection,
  MissingQueryParamRejection,
  Rejection,
  Route
}
import com.github.johanneshiry.simpleprm.api.rest.routes.v1.ContactApi.GetContactsPaginatedResponse.PaginatedContacts
import com.github.johanneshiry.simpleprm.io.model.Contact
import com.github.johanneshiry.simpleprm.io.model.JSONCodecs.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.RouteResult.Rejected
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.DbConnector.SortBy
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.github.johanneshiry.simpleprm.api.rest.json.JsonEncoder.*

object ContactApi extends FailFastCirceSupport with MarshalSupport {

  // handler interface containing all methods supported by the api
  trait ContactHandler {
    def getContacts(
        limit: Option[Int] = None,
        offset: Option[Int] = None,
        sortBy: Option[SortBy] = None
    ): Future[GetContactsPaginatedResponse]
  }

  object ContactHandler {
    final case class ContactHandler(dbConnector: DbConnector)(implicit
        ec: ExecutionContext
    ) extends ContactApi.ContactHandler {
      def getContacts(
          limit: Option[Int] = None,
          offset: Option[Int] = None,
          sortBy: Option[SortBy] = None
      ): Future[GetContactsPaginatedResponse] = dbConnector
        .getContacts(limit, offset, sortBy)
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
          parameters(
            "limit".as[Int].?,
            "offset".as[Int].?,
            "sort_by".as[String].?,
            "order_by".as[String].?
          ) { (limit, offset, sortBy, orderBy) =>
            parameterCheck(sortBy, orderBy) match {
              case (Some(rejection), None) =>
                reject(rejection)
              case (None, Some(sortBy, orderBy)) =>
                SortBy(sortBy, orderBy) match {
                  case Failure(ex) =>
                    reject(
                      MalformedQueryParamRejection("order_by", ex.getMessage)
                    )
                  case sortBySuc @ Success(_) =>
                    complete(
                      handler.getContacts(limit, offset, sortBySuc.toOption)
                    )
                }
              case (_, _) =>
                complete(handler.getContacts(limit, offset))
            }
          }
        }
      }
    }
  }

  private def parameterCheck(
      sortBy: Option[String],
      orderBy: Option[String]
  ): (Option[Rejection], Option[(String, String)]) = {
    if (sortBy.isDefined && orderBy.isEmpty) {
      (Some(MissingQueryParamRejection("order_by")), None)
    } else if (sortBy.isEmpty && orderBy.isDefined) {
      (Some(MissingQueryParamRejection("sort_by")), None)
    } else (None, sortBy.zip(orderBy))
  }

}
