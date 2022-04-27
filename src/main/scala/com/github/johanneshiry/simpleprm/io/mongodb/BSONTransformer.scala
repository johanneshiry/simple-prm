/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.StayInTouch
import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid
import reactivemongo.api.bson.*

import java.time.{Duration, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.compiletime.summonAll
import scala.deriving.Mirror

/** Implements required functions to write case classes to BSONDocuments
  */
object BSONTransformer {

  import java.net.URL
  import java.nio.file.Path
  import scala.compiletime.summonAll
  import scala.deriving.*

  /** Base function we use to convert case classes to BSONDocument
    *
    * @param a
    *   The object to convert
    */
  def transform[A: Transformer](
      a: A,
      fieldName: Option[String] = None
  ): BSONDocument =
    summon[Transformer[A]].f(a, fieldName)

  def bsonWriter[A: Transformer](
      fieldName: Option[String] = None
  ): BSONDocumentWriter[A] =
    BSONDocumentWriter[A] { x =>
      BSONTransformer.transform(x, fieldName)
    }

  // Base trait
  trait Transformer[T] {
    def f(t: T, fieldName: Option[String] = None): BSONDocument
  }

  // Create a type class of T => String for every type in your case class
  given Transformer[String] with
    def f(x: String, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(fieldName.getOrElse("") -> BSONString(x))

  given Transformer[Int] with
    def f(x: Int, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(fieldName.getOrElse("") -> BSONInteger(x))

  given Transformer[Double] with
    def f(x: Double, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(fieldName.getOrElse("") -> BSONDouble(x))

  given Transformer[URL] with
    def f(x: URL, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(
        fieldName.getOrElse("") -> BSONString(x.toExternalForm)
      )

  given Transformer[Path] with
    def f(x: Path, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(
        fieldName.getOrElse("") -> BSONString(x.getFileName.toString)
      )

  given Transformer[ZonedDateTime] with
    def f(x: ZonedDateTime, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(
        fieldName.getOrElse("") -> BSONString(
          x.withZoneSameLocal(ZoneId.of("UTC")).toString
        )
      )

  given Transformer[Duration] with
    def f(x: Duration, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(
        fieldName.getOrElse("") -> BSONString(x.toString)
      )

  given Transformer[UUID] with
    def f(x: UUID, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(fieldName.getOrElse("") -> BSONString(x.toString))

  given Transformer[VCard] with
    def f(x: VCard, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(
        fieldName.getOrElse("") ->
          BSONString(Ezvcard.write(x).prodId(false).version(x.getVersion).go())
      )

  given Transformer[Uid] with
    def f(x: Uid, fieldName: Option[String] = None): BSONDocument =
      BSONDocument(fieldName.getOrElse("") -> BSONString(x.getValue))

  given [T](using t: Transformer[T]): Transformer[Option[T]] with
    def f(x: Option[T], fieldName: Option[String] = None): BSONDocument =
      x match
        case None =>
          BSONDocument.empty
        case Some(x) =>
          t.f(x, fieldName)

  /** Turns a case class into an BSONDocument
    *
    * Fucking voodoo from
    * https://kavedaa.github.io/auto-ui-generation/auto-ui-generation.html
    */
  inline given [A <: Product](using m: Mirror.ProductOf[A]): Transformer[A] =
    new Transformer[A]:
      type ElemTransformers = Tuple.Map[m.MirroredElemTypes, Transformer]
      val elemTransformers: Seq[Transformer[Any]] =
        summonAll[ElemTransformers].toList.asInstanceOf[List[Transformer[Any]]]

      private def handleSingleOption(
          fieldToBsonVals: Seq[(String, BSONValue)],
          fieldName: Option[String]
      ): Option[BSONDocument] =
        // field name in optional is "value"
        fieldToBsonVals.toMap.get("value") match {
          case Some(doc: BSONDocument) if fieldToBsonVals.size == 1 =>
            fieldName
              .map(fieldName => BSONDocument(fieldName -> doc))
              .orElse(Some(doc))
          case _ =>
            None
        }

      def f(a: A, fieldName: Option[String] = None): BSONDocument = {
        val elems = a.productIterator.toList
        val transformed: Seq[(String, BSONValue)] =
          fieldNames(a).zip(elems.zip(elemTransformers)) flatMap {
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

  /** Returns the field names of a case class
    */
  def fieldNames[A <: Product](a: A): Seq[String] =
    a.productElementNames.toSeq
}
