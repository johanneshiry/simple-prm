/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.VCard as MongoDbVCard
import com.github.johanneshiry.simpleprm.io.mongodb.{
  BSONReader,
  BSONTransformer
}
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.DbConnector.SortBy
import com.github.johanneshiry.simpleprm.io.model.Reminder.{
  Birthday,
  StayInTouch
}
import com.github.johanneshiry.simpleprm.io.model.{Contact, Reminder}
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbFunctions.*
import com.typesafe.scalalogging.LazyLogging
import ezvcard.{Ezvcard, VCard}
import ezvcard.property.Uid
import reactivemongo.api.MongoConnectionOptions.Credential
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONString
}
import reactivemongo.api.*
import reactivemongo.api.bson.BSONValue.pretty

import java.time.{Duration, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import reactivemongo.api.commands.WriteResult

import java.util.UUID
import scala.concurrent.impl.Promise

final case class MongoDbConnector(
    nodes: Seq[String],
    options: MongoConnectionOptions,
    db: String
)(implicit ec: ExecutionContext)
    extends DbConnector
    with BSONReader
    with LazyLogging {

  private val driver: AsyncDriver = new reactivemongo.api.AsyncDriver
  private val connection: Future[MongoConnection] = driver.connect(
    nodes = nodes,
    options = options
  )

  def getContacts(
      limit: Option[Int] = None,
      offset: Option[Int] = None,
      sortBy: Option[SortBy] = None
  ): Future[Vector[Contact]] =
    contactsCollection.flatMap(findContacts(_, limit, offset, sortBy))

  def getAllContacts: Future[Vector[Contact]] =
    contactsCollection.flatMap(findContacts(_))

  def upsertContacts(contacts: Seq[Contact]): Future[Unit] = {
    val handleMe = contactsCollection.flatMap(
      upsertContacts(contacts, _)
    ) // todo process errors
    handleMe.map(_ => {}) // todo adapt handling
  }

  def delContacts(contacts: Seq[Contact]): Future[Unit] = {
    val handleMe = contactsCollection.flatMap(
      deleteContacts(contacts, _)
    ) // todo process errors
    handleMe.map(_ => {}) // todo adapt handling
  }

  def upsertReminder(reminder: Reminder): Future[Reminder] = {
    val handleMe = contactsCollection.flatMap(
      upsertReminder(reminder, _)
    )
    handleMe.map(writeResult => {
      logger.debug(s"Upsert StayInTouch result: $writeResult")
      reminder
    }) // todo process errors correctly
    //todo: do not return input, but actually persisted output (may differs e.g. in terms of time zone)
  }

  def getAllReminders: Future[Vector[Reminder]] =
    contactsCollection.flatMap(findStayInTouch(_))

  def getStayInTouch(contactUid: Uid): Future[Option[Reminder]] =
    contactsCollection.flatMap(findStayInTouch(_, contactUid))

  def delReminder(reminderUuid: UUID): Future[Try[Unit]] = ???

  private def contactsCollection: Future[BSONCollection] =
    dbConnection.map(_.collection("contacts"))

  private def dbConnection: Future[DB] =
    connection.flatMap(_.database(db))

  private def deleteContacts(
      contacts: Seq[Contact],
      collection: BSONCollection
  ) = {
    val deleteBuilder = collection.delete(ordered = false)

    // q = selector
    val deletes = Future.sequence(
      contacts.map(contact =>
        deleteBuilder.element(q = contactByUidSelector(contact))
      )
    )
    deletes.flatMap(deleteBuilder.many(_))
  }

  private def upsertContacts(
      contacts: Seq[Contact],
      collection: BSONCollection
  ): Future[collection.MultiBulkWriteResult] = {

    val updateBuilder = collection.update(ordered = true)

    // only the vCard needs to be updated
    val modifierFunc = (vCard: MongoDbVCard) =>
      set(BSONTransformer.transform(vCard, Some("vCard")))

    // q = selector, u = modifier
    val updates = Future.sequence(
      contacts.map(contact =>
        updateBuilder.element(
          q = contactByUidSelector(contact),
          u = modifierFunc(MongoDbVCard(contact)),
          upsert = true
        )
      )
    )
    updates.flatMap(updateBuilder.many(_))
  }

  private def findContacts(
      collection: BSONCollection,
      limit: Option[Int] = None,
      offset: Option[Int] = None,
      sortOrder: Option[SortBy] = None
  ): Future[Vector[Contact]] = {
    // batchSize == 0 -> unspecified batchSize
    val queryBuilder =
      collection
        .find(BSONDocument())
        .batchSize(limit.getOrElse(Int.MaxValue))
        .skip(offset.getOrElse(defaultOffsetNo))
        .sort(sortBy(sortOrder))
    queryBuilder
      .cursor[MongoDbContact]()
      .collect[Vector](
        maxDocs = limit.getOrElse(Int.MaxValue),
        err = Cursor.FailOnError[Vector[MongoDbContact]]()
      )
      .map(_.map(mongoDbContact => Contact(mongoDbContact.vCard.value)))
  }

  private def upsertReminder(
      reminder: Reminder,
      collection: BSONCollection
  ): Future[WriteResult] = {

    val selector = BSONTransformer.transform(reminder.contactId, Some("_id"))

    // only the reminder field needs to be updated
    // due to BSON Transformer limitations, we need to ensure that we have a case class here
    val modifier = reminder match {
      case r: StayInTouch => set(BSONTransformer.transform(r, Some("reminder")))
      case r: Birthday    => set(BSONTransformer.transform(r, Some("reminder")))
    }

    println(pretty(modifier))

    // do not set upsert to true, as otherwise a stay in touch element is created as document!
    // the executed operation however is still an upsert within a contact if the contact exists!
    collection.update.one(selector, modifier, upsert = false)

  }

  private def findStayInTouch(
      collection: BSONCollection,
      limit: Option[Int] = None
  ): Future[Vector[Reminder]] = {
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

  private def findStayInTouch(
      collection: BSONCollection,
      contactUid: Uid
  ): Future[Option[Reminder]] = {
    // query reminder by contact uid
    val query =
      BSONTransformer.transform(contactUid, Some("reminder.contactId"))
    collection.find(query).one[MongoDbContact].map(_.flatMap(_.reminder))
  }

}

object MongoDbConnector {

  def apply(
      cfg: SimplePrmCfg.MongoDB
  )(implicit ec: ExecutionContext): MongoDbConnector = apply(
    s"${cfg.host}:${cfg.port}",
    cfg.user,
    cfg.password,
    cfg.authenticationDb
  )

  def apply(
      host: String,
      user: String,
      password: Option[String],
      authenticationDb: Option[String],
      db: String = "simple-prm"
  )(implicit ec: ExecutionContext): MongoDbConnector =
    new MongoDbConnector(
      Seq(host),
      MongoConnectionOptions(
        authenticationDatabase = authenticationDb,
        credentials = Map(db -> Credential(user, password))
      ),
      db
    )

}
