/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import ezvcard.VCard
import ezvcard.property.Uid

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
//import slick.jdbc.PostgresProfile.api.*

import java.sql.Date
import scala.reflect.ClassTag
//import slick.lifted.Isomorphism

import java.util.UUID
//import slick.ast.{ScalaBaseType, TypedType}

import java.time.LocalDate

// todo deactivation field, scheduled for deletion field
final case class Contact private (uid: Uid, vCard: VCard)

object Contact {

  // setting the uid to lowercase is required to stay consistent, as sometimes its uppercase, sometimes its lowercase
  def apply(vCard: VCard): Contact =
    new Contact(new Uid(vCard.getUid.toString.toLowerCase), vCard)

}
