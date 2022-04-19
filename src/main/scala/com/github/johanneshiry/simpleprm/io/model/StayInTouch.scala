/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import ezvcard.property.Uid

import java.time.{Duration, ZonedDateTime}

final case class StayInTouch private (
    contactId: Uid,
    lastContacted: ZonedDateTime,
    contactInterval: java.time.Duration
) {

  def lastContactedToNow: StayInTouch =
    this.copy(lastContacted = ZonedDateTime.now())

}
object StayInTouch {

  // setting the uid to lowercase is required to stay consistent, as sometimes its uppercase, sometimes its lowercase
  def apply(
      contactId: Uid,
      lastContacted: ZonedDateTime,
      contactInterval: Duration
  ): StayInTouch =
    new StayInTouch(
      new Uid(contactId.getValue.toLowerCase),
      lastContacted,
      contactInterval
    )
}
