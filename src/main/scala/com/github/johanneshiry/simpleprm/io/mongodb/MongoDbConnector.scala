/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.cfg.SimplePrmCfg
import com.github.johanneshiry.simpleprm.io.mongodb.MongoDbModel.Contact as MongoDbContact
import com.github.johanneshiry.simpleprm.io.mongodb.{
  BSONReader,
  BSONTransformer
}
import com.github.johanneshiry.simpleprm.io.DbConnector
import com.github.johanneshiry.simpleprm.io.DbConnector.SortBy
import com.github.johanneshiry.simpleprm.io.model.{Contact, StayInTouch}
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

import java.time.{Duration, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

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

  def upsertStayInTouch(stayInTouch: StayInTouch): Future[StayInTouch] = {
    val handleMe = contactsCollection.flatMap(
      upsertStayInTouch(stayInTouch, _)
    )
    handleMe.map(_ => stayInTouch) // todo handle errors
  }

  def getAllStayInTouch: Future[Vector[StayInTouch]] =
    contactsCollection.flatMap(findStayInTouch(_))

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
    val modifierFunc = (contact: Contact) =>
      set(BSONTransformer.transform(contact.vCard, Some("vCard")))

    // q = selector, u = modifier
    val updates = Future.sequence(
      contacts.map(contact =>
        updateBuilder.element(
          q = contactByUidSelector(contact),
          u = modifierFunc(contact),
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
        .batchSize(limit.getOrElse(defaultBatchSize))
        .skip(offset.getOrElse(defaultOffsetNo))
        .sort(sortBy(sortOrder))
    queryBuilder
      .cursor[Contact]()
      .collect[Vector](
        maxDocs = limit.getOrElse(defaultBatchSize),
        err = Cursor.FailOnError[Vector[Contact]]()
      )
  }

  private def upsertStayInTouch(
      stayInTouch: StayInTouch,
      collection: BSONCollection
  ) = {

    val selector = BSONTransformer.transform(stayInTouch.contactId, Some("_id"))

    // only the stayInTouch field needs to be updated
    val modifier = set(
      BSONTransformer.transform(stayInTouch, Some("stayInTouch"))
    )

    collection.update.one(selector, modifier, upsert = true)

  }

  private def findStayInTouch(
      collection: BSONCollection,
      limit: Option[Int] = None
  ): Future[Vector[StayInTouch]] = {
    // batchSize == 0 -> unspecified batchSize
    val queryBuilder =
      collection.find(notNull("stayInTouch")).batchSize(limit.getOrElse(0))

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
      .map(_.flatMap(_.stayInTouch))
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
