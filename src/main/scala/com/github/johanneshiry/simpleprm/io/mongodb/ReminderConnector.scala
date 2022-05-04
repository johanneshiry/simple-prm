/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.model.Reminder
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbFunctions.notNull
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import com.typesafe.scalalogging.LazyLogging
import ezvcard.property.Uid
import reactivemongo.api.Cursor
import reactivemongo.api.bson.collection.BSONCollection

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

private[mongodb] trait ReminderConnector extends BsonDecoder with LazyLogging {

  import BsonEncoder.*

  protected def findReminder(
      collection: BSONCollection,
      contactUid: Uid
  )(implicit ec: ExecutionContext): Future[Option[Reminder]] = {
    // query reminder by contact uid
    val query = contactUid.asBson("reminder.contactId") // todo central place!
    collection.find(query).one[MongoDbContact].map(_.flatMap(_.reminder))
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
      .map(_.flatMap(_.reminder))
  }

  protected def removeReminder(
      collection: BSONCollection,
      reminderUuid: UUID
  ) = ???

}
