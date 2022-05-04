/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.DbConnector.{SortBy, SortableField}
import com.github.johanneshiry.simpleprm.io.model.Contact
import reactivemongo.api.bson.BSONDocument

private[mongodb] object MongoDbFunctions {

  import BsonEncoder.*

  // defaults
  val defaultBatchSize: Int = 101

  val defaultOffsetNo: Int = 0

  val contactByUidSelector: Contact => BSONDocument = (contact: Contact) =>
    contact.uid.asBson("_id")

  // { "$set": { <field1> : <value1>, ... } }
  val set: BSONDocument => BSONDocument = (doc: BSONDocument) =>
    BSONDocument("$set" -> doc)

  // { $push: { <field1>: <value1>, ... } }
  // https://www.mongodb.com/docs/manual/reference/operator/update/push/
  val push: BSONDocument => BSONDocument = (doc: BSONDocument) =>
    BSONDocument("$push" -> doc)

  val pull: BSONDocument => BSONDocument = (doc: BSONDocument) =>
    BSONDocument("$pull" -> doc)

  // { $addToSet: { <field1>: <value1>, ... } }
  val addToSet: BSONDocument => BSONDocument = (doc: BSONDocument) =>
    BSONDocument("$addToSet" -> doc)

  // { "fieldName" : { $ne: null } }
  val notNull: String => BSONDocument = (fieldName: String) =>
    not(fieldName, "null")

  val not: (String, String) => BSONDocument =
    (fieldName: String, notValue: String) =>
      BSONDocument(fieldName -> BSONDocument("$ne" -> notValue))

  val sortBy: Option[SortBy] => BSONDocument =
    (maybeSortBy: Option[SortBy]) =>
      maybeSortBy match {
        case Some(sortBy) =>
          BSONDocument(toMongoField(sortBy.fieldName) -> {
            if (sortBy.desc) -1
            else
              1 // 1 is ascending, -1 is descending -> https://www.mongodb.com/docs/manual/reference/method/cursor.sort/
          })
        case None =>
          BSONDocument.empty
      }

  // ensures, that the fields that can be used for sorting are mapped correctly
  private def toMongoField(fieldName: SortableField) = {
    fieldName match {
      case SortableField.FN => "vCard.FN"
    }
  }
}
