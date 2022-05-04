/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io

import com.github.johanneshiry.simpleprm.io.DbConnector.SortBy
import com.github.johanneshiry.simpleprm.io.model.{Contact, Reminder}
import ezvcard.VCard
import ezvcard.property.Uid

import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait DbConnector {

  def getContacts(
      limit: Option[Int] = None,
      offset: Option[Int] = None,
      sortBy: Option[SortBy] = None
  ): Future[Vector[Contact]]

  def getAllContacts: Future[Vector[Contact]]

  def updateContacts(contacts: Seq[Contact], upsert: Boolean): Future[Unit]

  def delContacts(contacts: Seq[Contact]): Future[Unit]

  def updateReminder(reminder: Reminder, upsert: Boolean): Future[Reminder]

  def getAllReminders: Future[Vector[Reminder]]

  def getReminder(
      contactUid: Uid
  ): Future[Seq[Reminder]]

  def delReminder(reminderUuid: UUID): Future[Try[Unit]]

}

object DbConnector {

  enum SortableField:
    case FN

  // very simple implementation of sorting, default sorting is considered to be ascending
  final case class SortBy(fieldName: SortableField, desc: Boolean = false)

  object SortBy {
    def apply(fieldName: String, order: String): Try[SortBy] =
      order.trim.toLowerCase match {
        case "desc" => Try(SortBy(SortableField.valueOf(fieldName), true))
        case "asc"  => Try(SortBy(SortableField.valueOf(fieldName)))
        case invalid =>
          Failure(
            new IllegalArgumentException(
              s"The provided order '$invalid' is unknown. Please provide either 'asc' or 'desc'!"
            )
          )
      }
  }

}
