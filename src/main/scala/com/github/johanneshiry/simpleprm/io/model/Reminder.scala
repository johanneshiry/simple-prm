/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.model

import com.github.johanneshiry.simpleprm.io.model.Reminder.ReminderType
import ezvcard.property.Uid

import java.time.{Duration, LocalDate, Period, ZonedDateTime}
import java.util.{Calendar, Date, UUID}

sealed trait Reminder {
  val uuid: UUID
  val reason: Option[String]
  val contactId: Uid
  val reminderDate: LocalDate
  val lastTimeReminded: LocalDate
  val reminderInterval: java.time.Period
  val reminderType: ReminderType

  def nextReminder: LocalDate

  def lastTimeRemindedToNow: Reminder

}

object Reminder {

  enum ReminderType(_id: String, _description: String):
    def id: String = _id

    def description: String = _description

    case StayInTouch
        extends ReminderType(
          "StayInTouch",
          "A reminder to keep in contact with someone."
        )
    case Birthday
        extends ReminderType(
          "Birthday",
          "A reminder for the contact's birthday."
        )
  end ReminderType

  final case class StayInTouch private (
      uuid: UUID,
      reason: Option[String],
      contactId: Uid,
      reminderDate: LocalDate,
      lastTimeReminded: LocalDate,
      reminderInterval: java.time.Period,
      reminderType: ReminderType = ReminderType.StayInTouch
  ) extends Reminder {

    override def nextReminder: LocalDate =
      lastTimeReminded
        .plusYears(reminderInterval.getYears)
        .plusMonths(reminderInterval.getMonths)
        .plusDays(reminderInterval.getDays)

    override def lastTimeRemindedToNow: Reminder =
      this.copy(lastTimeReminded = LocalDate.now())

  }

  object StayInTouch {

    def apply(
        uuid: UUID,
        reason: Option[String],
        contactId: Uid,
        reminderDate: LocalDate,
        lastTimeReminded: LocalDate,
        reminderInterval: Period
    ): StayInTouch =
      new StayInTouch(
        uuid,
        reason,
        contactId,
        reminderDate,
        lastTimeReminded,
        reminderInterval
      )

  }

  final case class Birthday private (
      uuid: UUID,
      contactId: Uid,
      reminderDate: LocalDate,
      lastTimeReminded: LocalDate,
      reminderType: ReminderType = ReminderType.Birthday,
      reason: Option[String] = Some("Birthday"),
      reminderInterval: Period = Period.ofYears(1)
  ) extends Reminder {

    override def nextReminder: LocalDate =
      lastTimeReminded.plusYears(reminderInterval.getYears)

    override def lastTimeRemindedToNow: Reminder =
      this.copy(lastTimeReminded = LocalDate.now())
  }

  object Birthday {

    def apply(
        uuid: UUID,
        contactId: Uid,
        reminderDate: LocalDate,
        lastTimeReminded: LocalDate
    ): Birthday =
      new Birthday(uuid, contactId, reminderDate, lastTimeReminded)

  }

}
