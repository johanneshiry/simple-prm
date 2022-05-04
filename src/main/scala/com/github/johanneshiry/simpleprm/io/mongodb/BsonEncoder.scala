/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Encoder
import com.github.johanneshiry.simpleprm.io.mongodb.BsonEncoder.encode
import ezvcard.property.Uid
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson.{
  BSONDocument,
  BSONDouble,
  BSONInteger,
  BSONString,
  BSONValue
}

import java.time.LocalDate
import java.util.UUID
import scala.compiletime.summonAll
import scala.deriving.Mirror

object BsonEncoder extends Encoder[BSONDocument] {

  implicit final class EncoderOps[A](private val value: A) extends AnyVal {
    final def asBson(fieldName: String)(using
        encoder: Encoder[A]
    ): BSONDocument =
      encoder.f(value, Some(fieldName))

    final def asBson(using encoder: Encoder[A]): BSONDocument =
      encoder.f(value)
  }

  override protected def productHandler[P <: Product](
      a: P,
      fieldName: Option[String] = None
  )(implicit elemTransformers: Seq[Encoder[Any]]): BSONDocument = {
    def handleSingleOption(
        fieldToBsonVals: Seq[(String, BSONValue)],
        fieldName: Option[String]
    ): Option[BSONDocument] = {
      // field name in optional is "value"
      fieldToBsonVals.toMap.get("value") match {
        case Some(doc: BSONDocument) if fieldToBsonVals.size == 1 =>
          fieldName
            .map(fieldName => BSONDocument(fieldName -> doc))
            .orElse(Some(doc))
        case _ =>
          None
      }
    }

    val transformed: Seq[(String, BSONValue)] =
      fieldNames(a).zip(fieldValues(a).zip(elemTransformers)) flatMap {
        case (fieldName, (elem, transformer)) =>
          val doc = transformer
            .f(elem, Some(fieldName)) match {
            case document: BSONDocument if document.elements.size > 1 =>
              // options and nested objects
              Some(document)
            case document =>
              // everything else
              document.get(fieldName)
          }
          doc.map(bsonVal => fieldName -> bsonVal)
      }
    handleSingleOption(transformed, fieldName).getOrElse(
      fieldName
        .map(providedFieldName =>
          BSONDocument(providedFieldName -> BSONDocument(transformed))
        )
        .getOrElse(BSONDocument(transformed))
    )
  }

  override protected def stringHandler(
      x: String,
      fieldName: Option[String]
  ): BSONDocument =
    BSONDocument(fieldName.getOrElse("") -> BSONString(x))

  override protected def doubleHandler(
      x: Double,
      fieldName: Option[String]
  ): BSONDocument =
    BSONDocument(fieldName.getOrElse("") -> BSONDouble(x))

  override protected def intHandler(
      x: Int,
      fieldName: Option[String]
  ): BSONDocument =
    BSONDocument(fieldName.getOrElse("") -> BSONInteger(x))

  override protected def optionHandler[T](
      x: Option[T],
      fieldName: Option[String]
  )(using t: Encoder[T]): BSONDocument =
    x match
      case None =>
        BSONDocument.empty
      case Some(x) =>
        t.f(x, fieldName)

  override def seqHandler[T](x: Seq[T], fieldName: Option[String])(using
      t: BsonEncoder.Encoder[T]
  ): BSONDocument =
    ???

}
