/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import ezvcard.property.Uid

import java.time.ZonedDateTime

final case class StayInTouch(
    contactId: Uid,
    lastContacted: ZonedDateTime,
    contactInterval: java.time.Duration
) {

  def lastContactedToNow: StayInTouch =
    this.copy(lastContacted = ZonedDateTime.now())

}
