/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io

import com.github.johanneshiry.simpleprm.io.DbConnector.SortyBy
import com.github.johanneshiry.simpleprm.io.model.{Contact, StayInTouch}
import ezvcard.VCard
import ezvcard.property.Uid

import scala.concurrent.Future
import scala.util.Try

trait DbConnector {

  def getContacts(
      limit: Option[Int] = None,
      offset: Option[Int] = None,
      sortBy: Option[SortyBy] = None
  ): Future[Vector[Contact]]

  def getAllContacts: Future[Vector[Contact]]

  def upsertContacts(contacts: Seq[Contact]): Future[Unit]

  def delContacts(contacts: Seq[Contact]): Future[Unit]

  def upsertStayInTouch(stayInTouch: StayInTouch): Future[StayInTouch]

  def getAllStayInTouch: Future[Vector[StayInTouch]]

}

object DbConnector {

  // very simple implementation of sorting, default sorting is considered to be ascending
  final case class SortyBy(fieldName: String, desc: Boolean = false)

}
