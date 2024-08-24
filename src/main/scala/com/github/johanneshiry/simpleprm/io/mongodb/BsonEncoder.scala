/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Encoder
import com.github.johanneshiry.simpleprm.io.mongodb.BsonEncoder.encode
import ezvcard.property.Uid
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDouble,
  BSONInteger,
  BSONJavaScript,
  BSONJavaScriptWS,
  BSONLong,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONObjectID,
  BSONRegex,
  BSONString,
  BSONSymbol,
  BSONTimestamp,
  BSONUndefined,
  BSONValue
}

import java.time.LocalDate
import java.util.UUID
import scala.compiletime.summonAll
import scala.deriving.Mirror

private[mongodb] object BsonEncoder extends Encoder[BSONValue] {

  implicit final class EncoderOps[A](private val value: A) extends AnyVal {
    // whenever a field name is provided, a document can be expected per convention
    final def asBson(fieldName: String)(using
        encoder: Encoder[A]
    ): BSONDocument =
      encoder.f(value, Some(fieldName)) match {
        case document: BSONDocument => document
        case bsonVal                => BSONDocument(fieldName -> bsonVal)
      }

    final def asBson(using encoder: Encoder[A]): BSONValue =
      encoder.f(value)
  }

  override protected def productHandler[P <: Product](
      a: P,
      fieldName: Option[String] = None
  )(implicit elemTransformers: Seq[Encoder[Any]]): BSONValue = {

    def deepMerge(v1: BSONValue, c2: BSONValue): BSONValue = {
      (v1, c2) match
        case (doc1: BSONDocument, doc2: BSONDocument) =>
          doc1.elements.foldLeft(doc2) { case (acc, bsonElement) =>
            doc2
              .get(bsonElement.name)
              .fold(
                acc.++(BSONDocument(bsonElement.name -> bsonElement.value))
              ) { r =>
                acc.++(
                  BSONDocument(
                    bsonElement.name -> deepMerge(bsonElement.value, r)
                  )
                )
              }
          }
        case _ =>
          v1
    }

    val transformed: Seq[BSONValue] =
      fieldNames(a).zip(fieldValues(a).zip(elemTransformers)) map {
        case (fieldName, (elem, transformer)) =>
          transformer.f(elem, Some(fieldName))
      }

    maybeBsonDoc(transformed.reverse.reduce(deepMerge), fieldName)

  }

  override protected def stringHandler(
      x: String,
      fieldName: Option[String]
  ): BSONValue =
    maybeBsonDoc(BSONString(x), fieldName)

  override protected def doubleHandler(
      x: Double,
      fieldName: Option[String]
  ): BSONValue =
    maybeBsonDoc(BSONDouble(x), fieldName)

  override protected def intHandler(
      x: Int,
      fieldName: Option[String]
  ): BSONValue =
    maybeBsonDoc(BSONInteger(x), fieldName)

  override protected def optionHandler[T](
      x: Option[T],
      fieldName: Option[String]
  )(using t: Encoder[T]): BSONValue =
    x match
      case None =>
        maybeBsonDoc(BSONUndefined, fieldName)
      case Some(x) =>
        t.f(x, fieldName)

  override def seqHandler[T](x: Seq[T], fieldName: Option[String])(using
      t: BsonEncoder.Encoder[T]
  ): BSONValue =
    maybeBsonDoc(BSONArray(x.map(t.f(_))), fieldName)

  private def maybeBsonDoc(
      bson: BSONValue,
      fieldName: Option[String]
  ): BSONValue =
    fieldName.map(fieldName => BSONDocument(fieldName -> bson)).getOrElse(bson)

}
