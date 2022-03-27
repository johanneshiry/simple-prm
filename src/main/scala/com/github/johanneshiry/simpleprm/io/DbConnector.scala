/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io

import com.github.johanneshiry.simpleprm.io.model.Contact
import ezvcard.VCard
import ezvcard.property.Uid

import scala.concurrent.Future
import scala.util.Try

trait DbConnector {

  def getAllContacts: Future[Vector[Contact]]

  def upsertContacts(contacts: Seq[Contact]): Future[Unit]

  def delContacts(contacts: Seq[Contact]): Future[Unit]

}
