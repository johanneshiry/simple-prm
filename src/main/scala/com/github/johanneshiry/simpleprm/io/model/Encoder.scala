/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import com.github.johanneshiry.simpleprm.io.model.Reminder.{
  Birthday,
  ReminderType,
  StayInTouch
}
import ezvcard.property.Uid
import ezvcard.{Ezvcard, VCard}
import io.circe.{Encoder, Json, JsonNumber, JsonObject}
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson.{
  BSONDocument,
  BSONDouble,
  BSONInteger,
  BSONString,
  BSONValue
}

import java.net.URL
import java.nio.file.Path
import java.time.{Duration, LocalDate, Period, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.Seq
import scala.compiletime.summonAll
import scala.deriving.Mirror

trait Encoder[A] {

  protected def stringHandler(x: String, fieldName: Option[String]): A

  protected def intHandler(x: Int, fieldName: Option[String]): A

  protected def doubleHandler(x: Double, fieldName: Option[String]): A

  protected def optionHandler[T](x: Option[T], fieldName: Option[String])(using
      t: Encoder[T]
  ): A

  protected def productHandler[P <: Product](
      a: P,
      fieldName: Option[String] = None
  )(implicit elemTransformers: Seq[Encoder[Any]]): A

  protected def seqHandler[T](x: Seq[T], fieldName: Option[String])(using
      t: Encoder[T]
  ): A

  final def encode[O: Encoder](
      a: O,
      fieldName: Option[String] = None
  ): A =
    summon[Encoder[O]].f(a, fieldName)

  trait Encoder[T] {
    def f(t: T, fieldName: Option[String] = None): A
  }

  given Encoder[String] with
    def f(x: String, fieldName: Option[String] = None): A =
      stringHandler(x, fieldName)

  given Encoder[Int] with
    def f(x: Int, fieldName: Option[String] = None): A =
      intHandler(x, fieldName)

  given Encoder[Double] with
    def f(x: Double, fieldName: Option[String] = None): A =
      doubleHandler(x, fieldName)

  given Encoder[URL] with
    def f(x: URL, fieldName: Option[String] = None): A =
      stringHandler(x.toExternalForm, fieldName)

  given Encoder[Path] with
    def f(x: Path, fieldName: Option[String] = None): A =
      stringHandler(x.getFileName.toString, fieldName)

  given Encoder[ZonedDateTime] with
    def f(x: ZonedDateTime, fieldName: Option[String] = None): A =
      stringHandler(x.withZoneSameLocal(ZoneId.of("UTC")).toString, fieldName)

  given Encoder[Duration] with
    def f(x: Duration, fieldName: Option[String] = None): A =
      stringHandler(x.toString, fieldName)

  given Encoder[Period] with
    def f(x: Period, fieldName: Option[String] = None): A =
      stringHandler(x.toString, fieldName)

  given Encoder[LocalDate] with
    def f(x: LocalDate, fieldName: Option[String] = None): A =
      stringHandler(x.toString, fieldName)

  given Encoder[UUID] with
    def f(x: UUID, fieldName: Option[String] = None): A =
      stringHandler(x.toString, fieldName)

  given Encoder[VCard] with
    def f(x: VCard, fieldName: Option[String] = None): A =
      stringHandler(
        Ezvcard.write(x).prodId(false).version(x.getVersion).go(),
        fieldName
      )

  given Encoder[Reminder] with
    def f(x: Reminder, fieldName: Option[String] = None): A =
      x match
        case stayInTouch: StayInTouch =>
          encode(stayInTouch, fieldName)
        case birthDay: Birthday =>
          encode(birthDay, fieldName)

  given [T](using t: Encoder[T]): Encoder[Seq[T]] with
    def f(x: Seq[T], fieldName: Option[String] = None): A =
      seqHandler(x, fieldName)

  given [T](using t: Encoder[T]): Encoder[Vector[T]] with
    def f(x: Vector[T], fieldName: Option[String] = None): A =
      seqHandler(x, fieldName)

  given Encoder[Uid] with
    def f(x: Uid, fieldName: Option[String] = None): A =
      stringHandler(x.getValue, fieldName)

  given Encoder[Throwable] with
    def f(x: Throwable, fieldName: Option[String] = None): A =
      stringHandler(x.toString, fieldName)

  given Encoder[ReminderType] with
    def f(x: ReminderType, fieldName: Option[String] = None): A =
      stringHandler(x.toString, fieldName)

  given [T](using t: Encoder[T]): Encoder[Option[T]] with
    def f(x: Option[T], fieldName: Option[String] = None): A =
      optionHandler(x, fieldName)

  /** Turns a case class into an instance of A
    *
    * Fucking voodoo from
    * https://kavedaa.github.io/auto-ui-generation/auto-ui-generation.html
    */
  inline given [P <: Product](using m: Mirror.ProductOf[P]): Encoder[P] =
    new Encoder[P] {
      type ElemTransformers = Tuple.Map[m.MirroredElemTypes, Encoder]
      implicit val elemTransformers: Seq[Encoder[Any]] =
        summonAll[ElemTransformers].toList.asInstanceOf[List[Encoder[Any]]]

      def f(a: P, fieldName: Option[String] = None): A =
        productHandler(a, fieldName)
    }

  /** Returns the field names of a case class
    */
  protected def fieldNames[A <: Product](a: A): Seq[String] =
    a.productElementNames.toSeq

  protected def fieldValues[A <: Product](a: A): Seq[Any] =
    a.productIterator.toSeq
}
