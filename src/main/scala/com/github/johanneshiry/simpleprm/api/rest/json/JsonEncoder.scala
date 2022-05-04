/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.api.rest.json

import com.github.johanneshiry.simpleprm.io.model.Encoder
import io.circe.{Json, JsonNumber, JsonObject}

object JsonEncoder extends Encoder[Json] {

  implicit final class EncoderOps[A](private val value: A) extends AnyVal {
    final def asJson(fieldName: String)(using
        encoder: Encoder[A]
    ): Json =
      encoder.f(value, Some(fieldName))

    final def asJson(using encoder: Encoder[A]): Json =
      encoder.f(value)
  }

  override protected def stringHandler(
      x: String,
      fieldName: Option[String]
  ): Json =
    maybeJsonObj(Json.fromString(x), fieldName)

  override protected def intHandler(x: Int, fieldName: Option[String]): Json =
    maybeJsonObj(Json.fromInt(x), fieldName)

  override protected def doubleHandler(
      x: Double,
      fieldName: Option[String]
  ): Json =
    val finiteNumber = if (isReal(x)) x else Double.MaxValue
    Json
      .fromDouble(finiteNumber)
      .map(maybeJsonObj(_, fieldName))
      .getOrElse(
        Json.fromJsonNumber(
          JsonNumber.fromDecimalStringUnsafe(finiteNumber.toString)
        )
      )

  override protected def optionHandler[T](
      x: Option[T],
      fieldName: Option[String]
  )(using t: Encoder[T]): Json =
    x match
      case None =>
        Json.Null
      case Some(x) =>
        t.f(x, fieldName)

  override protected def seqHandler[T](x: Seq[T], fieldName: Option[String])(
      using t: Encoder[T]
  ): Json =
    Json.fromValues(x.map(t.f(_)))

  override protected def productHandler[P <: Product](
      a: P,
      fieldName: Option[String]
  )(implicit elemTransformers: Seq[Encoder[Any]]): Json = {
    val transformed: Seq[JsonObject] =
      fieldNames(a).zip(fieldValues(a).zip(elemTransformers)) flatMap {
        case (fieldName, (elem, transformer)) =>
          transformer.f(elem, Some(fieldName)).asObject
      }

    // to keep object field order, a reverse is required
    Json.fromJsonObject(transformed.reverse.reduce(_.deepMerge(_)))
  }

  private def maybeJsonObj(json: Json, fieldName: Option[String]) =
    fieldName.map(fieldName => Json.obj((fieldName, json))).getOrElse(json)

  private def isReal(value: Double): Boolean = java.lang.Double.isFinite(value)
}
