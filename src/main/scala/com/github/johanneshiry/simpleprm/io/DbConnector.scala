/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io

import com.github.johanneshiry.simpleprm.io.model.{Contact, StayInTouch}
import ezvcard.VCard
import ezvcard.property.Uid

import scala.concurrent.Future
import scala.util.Try

trait DbConnector {

  def getContacts(limit: Option[Int] = None): Future[Vector[Contact]]

  def getAllContacts: Future[Vector[Contact]]

  def upsertContacts(contacts: Seq[Contact]): Future[Unit]

  def delContacts(contacts: Seq[Contact]): Future[Unit]

  def upsertStayInTouch(stayInTouch: StayInTouch): Future[StayInTouch]

}
