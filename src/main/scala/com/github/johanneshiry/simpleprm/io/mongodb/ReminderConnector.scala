/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Reminder
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbFunctions.{
  addToSet,
  not,
  notNull,
  pull,
  push
}
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import com.typesafe.scalalogging.LazyLogging
import ezvcard.property.Uid
import reactivemongo.api.Cursor
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.BSONValue.pretty
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

private[mongodb] trait ReminderConnector extends BsonDecoder with LazyLogging {

  import BsonEncoder.*

  protected def findReminders(
      collection: BSONCollection,
      contactUid: Uid
  )(implicit ec: ExecutionContext): Future[Seq[Reminder]] = {
    // query reminder by contact uid
    val query = contactUid.asBson("reminders.contactId") // todo central place!

    collection
      .find(query)
      .one[MongoDbContact]
      .map(_.map(_.reminders).getOrElse(Seq.empty))
  }

  protected def findReminders(
      collection: BSONCollection,
      limit: Option[Int] = None
  )(implicit ec: ExecutionContext): Future[Vector[Reminder]] = {
    // batchSize == 0 -> unspecified batchSize
    val queryBuilder =
      collection.find(notNull("reminder")).batchSize(limit.getOrElse(0))

    queryBuilder
      .cursor[MongoDbContact]()
      .collect[Vector](err =
        Cursor.FailOnError[Vector[MongoDbContact]]((_, throwable) =>
          logger.error(
            s"Cannot execute mongo db query '${queryBuilder.toString}'",
            throwable
          )
        )
      )
      .map(_.flatMap(_.reminders))
  }

  protected def updateReminder(
      reminder: Reminder,
      collection: BSONCollection,
      upsert: Boolean
  )(implicit ec: ExecutionContext): Future[WriteResult] = {

    // if the uuid already exists, we pull it first and then add the provided one as "update"
    // this operation has no effect, if the uuid does not exist
    val pullSelector = reminder.contactId.asBson("_id")
    val pullModifier = pull(
      BSONDocument("reminders" -> reminder.uuid.asBson("uuid"))
    )

    // only select if we have
    // a) the contact and
    // b) the uuid of the reminder is not used yet (to prevent duplicate uuids) - should not happen anyway
    //    due to the deletion beforehand
    val selector = BSONDocument(
      reminder.contactId.asBson("_id"),
      not("reminders.uuid", reminder.uuid.toString)
    )

    // push reminder to reminders array, avoid duplicates of object
    val modifier = addToSet(reminder.asBson("reminders"))

    for {
      _ <- collection.update.one(pullSelector, pullModifier, upsert = upsert)
      writeResult <- collection.update.one(selector, modifier, upsert = upsert)
    } yield writeResult

  }

  protected def removeReminder(
      collection: BSONCollection,
      reminderUuid: UUID
  )(implicit ec: ExecutionContext): Future[WriteResult] = {
    val selector = reminderUuid.asBson("reminders.uuid")
    val modifier = pull(
      BSONDocument("reminders" -> reminderUuid.asBson("uuid"))
    )

    collection.update.one(selector, modifier)
  }

}
