/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.routes.v1

import akka.http.scaladsl.marshalling
import akka.http.scaladsl.marshalling.{Marshal, Marshaller, Marshalling}
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpResponse,
  ResponseEntity,
  StatusCode
}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}
import io.circe.generic.auto.*
import io.circe.syntax.*

import scala.language.postfixOps

trait MarshalSupport {

  val emptyHttpJsonObjResponse: HttpEntity.Strict = HttpEntity.Strict(
    ContentTypes.`application/json`,
    data = ByteString.fromString("{}")
  )

  val emptyHttpJsonArrayResponse: HttpEntity.Strict = HttpEntity.Strict(
    ContentTypes.`application/json`,
    data = ByteString.fromString("[]")
  )

  def marshal[T](value: T, statusCode: StatusCode)(implicit
      marshaller: marshalling.Marshaller[
        T,
        akka.http.scaladsl.model.ResponseEntity
      ],
      ec: ExecutionContext
  ): Future[List[Marshalling.Opaque[HttpResponse]]] =
    Marshal(value).to[ResponseEntity].map { entity =>
      Marshalling.Opaque { () =>
        HttpResponse(statusCode, entity = entity)
      } :: Nil
    }

  def marshal(
      statusCode: StatusCode,
      entity: ResponseEntity = emptyHttpJsonObjResponse
  )(implicit
      ec: ExecutionContext
  ): Future[List[Marshalling.Opaque[HttpResponse]]] =
    Future(
      List(
        Marshalling.Opaque { () =>
          HttpResponse(statusCode, entity = entity)
        }
      )
    )

}
