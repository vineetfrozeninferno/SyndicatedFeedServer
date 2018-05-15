package com.syndicatedhub.syndicatedfeedserver.datastorage

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import com.syndicatedhub.syndicatedfeedserver.messages.FeedItem
import spray.json._
import spray.json.DefaultJsonProtocol._

object FeedDataDb extends LocalDynamoDbDataStorage[DynamoDbKey, Seq[FeedItem]] {
  override def partitionKeyName: String = "FeedUrl"
  override def partitionKeyType: ScalarAttributeType = ScalarAttributeType.S
  override def sortKeyName: String = "EpochTimeStamp"
  override def sortKeyType: ScalarAttributeType = ScalarAttributeType.N
  override def scanForward: Boolean = false
  override implicit val jsonFormatForKey: JsonFormat[DynamoDbKey] = DynamoDbKey.jsonFormatForDynamoKey
  override implicit val jsonFormatForItem: JsonFormat[Seq[FeedItem]] = seqFormat[FeedItem]
  override def dataAttribute: String = "dataItems"
  override def tableName: String = "FeedData"
}
