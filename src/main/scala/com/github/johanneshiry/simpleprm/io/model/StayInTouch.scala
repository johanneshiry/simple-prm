/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import ezvcard.property.Uid

import java.time.{Duration, ZonedDateTime}

final case class StayInTouch private ( // todo rename + different subclasses!
    contactId: Uid,
    lastContacted: ZonedDateTime,
    contactInterval: java.time.Period
) {

  def lastContactedToNow: StayInTouch =
    this.copy(lastContacted = ZonedDateTime.now())

}
object StayInTouch {

  def apply(
      contactId: Uid,
      lastContacted: ZonedDateTime,
      contactInterval: java.time.Period
  ): StayInTouch =
    new StayInTouch(
      contactId,
      lastContacted,
      contactInterval
    )
}
