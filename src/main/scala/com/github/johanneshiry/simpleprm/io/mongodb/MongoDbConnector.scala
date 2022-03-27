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
import com.github.johanneshiry.simpleprm.io.model.Contact
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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

final case class MongoDbConnector(
    nodes: Seq[String],
    options: MongoConnectionOptions,
    db: String
)(implicit ec: ExecutionContext)
    extends DbConnector
    with BSONReader {

  private val contactsColName = "contacts"

  private val driver: AsyncDriver = new reactivemongo.api.AsyncDriver
  private val connection: Future[MongoConnection] = driver.connect(
    nodes = nodes,
    options = options
  )

  def getAllContacts: Future[Vector[Contact]] =
    contactsCollection.flatMap(findAllContacts)

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

  private def contactsCollection: Future[BSONCollection] =
    dbConnection.map(_.collection(contactsColName))

  private def dbConnection: Future[DB] =
    connection.flatMap(_.database(db))

  private def deleteContacts(
      contacts: Seq[Contact],
      collection: BSONCollection
  ) = {
    val deleteBuilder = collection.delete(ordered = false)

    // select contact by its uid
    val selectorFunc = (contact: Contact) =>
      BSONDocument("_id" -> contact.uid.getValue)

    // q = selector
    val deletes = Future.sequence(
      contacts.map(contact => deleteBuilder.element(q = selectorFunc(contact)))
    )
    deletes.flatMap(deleteBuilder.many(_))
  }

  private def upsertContacts(
      contacts: Seq[Contact],
      collection: BSONCollection
  ): Future[collection.MultiBulkWriteResult] = {

    val updateBuilder = collection.update(ordered = true)

    // select contact by its uid
    val selectorFunc = (contact: Contact) =>
      BSONDocument("_id" -> contact.uid.getValue)

    // only the vCard needs to be update
    // todo move to transformer with field name
    val modifierFunc = (contact: Contact) =>
      BSONDocument(
        "vCard" -> BSONString(
          Ezvcard
            .write(contact.vCard)
            .prodId(false)
            .version(contact.vCard.getVersion)
            .go()
        )
      )

    // q = selector, u = modifier
    val updates = Future.sequence(
      contacts.map(contact =>
        updateBuilder.element(
          q = selectorFunc(contact),
          u = modifierFunc(contact),
          upsert = true
        )
      )
    )
    updates.flatMap(updateBuilder.many(_))
  }

  private def findAllContacts(
      collection: BSONCollection
  ): Future[Vector[Contact]] = {
    val queryBuilder = collection.find(BSONDocument())
    queryBuilder
      .cursor[Contact]()
      .collect[Vector](err = Cursor.FailOnError[Vector[Contact]]())
  }

}

object MongoDbConnector {

  def apply(
      cfg: SimplePrmCfg.MongoDB
  )(implicit ec: ExecutionContext): MongoDbConnector = apply(
    cfg.host,
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
