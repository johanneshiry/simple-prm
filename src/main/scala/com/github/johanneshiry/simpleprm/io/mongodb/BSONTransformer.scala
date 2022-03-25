/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid
import reactivemongo.api.bson.*

import java.time.ZonedDateTime
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
  def transform[A: Transformer](a: A): BSONDocument =
    summon[Transformer[A]].f(a)

  def bsonWriter[A: Transformer]: BSONDocumentWriter[A] =
    BSONDocumentWriter[A] { contact =>
      BSONTransformer.transform(contact)
    }

  // Base trait
  trait Transformer[T] {
    def f(t: T): BSONDocument
//    def fieldName: String // todo JH
  }

  // Create a type class of T => String for every type in your case class
  given Transformer[String] with
    def f(x: String): BSONDocument = BSONDocument("" -> BSONString(x))

  given Transformer[Int] with
    def f(x: Int): BSONDocument = BSONDocument("" -> BSONInteger(x))

  given Transformer[Double] with
    def f(x: Double): BSONDocument = BSONDocument("" -> BSONDouble(x))

  given Transformer[URL] with
    def f(x: URL): BSONDocument = BSONDocument(
      "" -> BSONString(x.toExternalForm)
    )

  given Transformer[Path] with
    def f(x: Path): BSONDocument = BSONDocument(
      "" -> BSONString(x.getFileName.toString)
    )

  given Transformer[ZonedDateTime] with
    def f(x: ZonedDateTime): BSONDocument = BSONDocument(
      "" -> BSONString(x.toString)
    )

  given Transformer[UUID] with
    def f(x: UUID): BSONDocument = BSONDocument("" -> BSONString(x.toString))

  given Transformer[VCard] with
    def f(x: VCard): BSONDocument = BSONDocument(
      "" ->
        BSONString(Ezvcard.write(x).prodId(false).version(x.getVersion).go())
    )

  given Transformer[Uid] with
    def f(x: Uid): BSONDocument = BSONDocument("" -> BSONString(x.getValue))

  given [T](using t: Transformer[T]): Transformer[Option[T]] =
    (x: Option[T]) =>
      x match
        case None    => BSONDocument("" -> BSONString(""))
        case Some(x) => t.f(x)

  //  /** Transforms a list of case classes into CSV data, including header row
  //   */
  //  given [A <: Product](using t: Transformer[A]): Transformer[List[A]] =
  //  (x: List[A]) =>
  //    x.headOption.map(
  //      asHeader(_) :: x.map(transform)
  //    ).map(_.mkString("\n")).getOrElse("")

  /** Turns a case class into an BSONDocument(""-> of BSONValues)
    *
    * Fucking voodoo from
    * https://kavedaa.github.io/auto-ui-generation/auto-ui-generation.html
    */
  inline given [A <: Product](using m: Mirror.ProductOf[A]): Transformer[A] =
    new Transformer[A]:
      type ElemTransformers = Tuple.Map[m.MirroredElemTypes, Transformer]
      val elemTransformers: Seq[Transformer[Any]] =
        summonAll[ElemTransformers].toList.asInstanceOf[List[Transformer[Any]]]

      def f(a: A): BSONDocument =
        val elems = a.productIterator.toList
        val transformed =
          fieldNames(a).zip(elems.zip(elemTransformers)) flatMap {
            case (fieldName, (elem, transformer)) =>
              transformer
                .f(elem)
                .get("")
                .filter {
                  // remove options that has been passed in as None
                  case x: BSONString =>
                    x.value.nonEmpty
                  case _ => true
                }
                .map(bsonVal => fieldName -> bsonVal)
          }
        BSONDocument(transformed)

  /** Returns the field names of a case class
    */
  def fieldNames[A <: Product](a: A): Seq[String] =
    a.productElementNames.toSeq
}
