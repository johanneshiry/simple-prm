/*
 * © 2022. Johannes Hiry
 */

package com.github.johanneshiry.simpleprm.io.mongodb

import com.github.johanneshiry.simpleprm.io.DbConnector.SortyBy
import com.github.johanneshiry.simpleprm.io.model.Contact
import reactivemongo.api.bson.BSONDocument

private[mongodb] object MongoDbFunctions {

  // defaults
  val defaultBatchSize: Int = 101

  val defaultOffsetNo: Int = 0

  val contactByUidSelector: Contact => BSONDocument = (contact: Contact) =>
    BSONTransformer.transform(contact.uid, Some("_id"))

  // { "$set": { <field1> : <value1>, ... } }
  val set: BSONDocument => BSONDocument = (doc: BSONDocument) =>
    BSONDocument("$set" -> doc)

  // { "fieldName" : { $ne: null } }
  val notNull: String => BSONDocument = (fieldName: String) =>
    BSONDocument(fieldName -> BSONDocument("$ne" -> "null"))

  val sortBy: Option[SortyBy] => BSONDocument =
    (maybeSortBy: Option[SortyBy]) =>
      maybeSortBy match {
        case Some(sortBy) =>
          BSONDocument(sortBy.fieldName -> {
            if (sortBy.desc) -1
            else
              1 // 1 is ascending, -1 is descending -> https://www.mongodb.com/docs/manual/reference/method/cursor.sort/
          })
        case None =>
          BSONDocument.empty
      }

}
