package com.syndicatedhub.syndicatedfeedserver.datastorage
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import spray.json._
import spray.json.DefaultJsonProtocol._

trait FeedsDbKey extends DynamoDbKey
object FeedsFetchAllKey extends QueryKey(FeedUrlsDb.tableName, None, None) with FeedsDbKey
class FeedsFetchOneKey(feedUrl: String) extends CompleteKey(FeedUrlsDb.tableName, feedUrl) with FeedsDbKey {
  def getFeedUrl: String = feedUrl
}

case class FeedUrlData(refreshIntervalInMins: Int, lastModified: Option[Long])
object FeedUrlData {
  implicit val jsonFormatForFeedUrlData: JsonFormat[FeedUrlData] = jsonFormat2(FeedUrlData.apply)
}

object JsonFormatForFeedsDbKey extends JsonFormat[FeedsDbKey] {
  override def read(json: JsValue): FeedsDbKey = {
    val feedUrl = json.convertTo[CompleteKey].sortKey
    new FeedsFetchOneKey(feedUrl)
  }
  override def write(obj: FeedsDbKey): JsValue = obj match {
    case key: FeedsFetchOneKey => CompleteKey(FeedUrlsDb.tableName, key.getFeedUrl).toJson
    case FeedsFetchAllKey => throw new Exception(s"Cannot convert ${FeedsFetchAllKey.getClass} to json.")
  }
}

object FeedUrlsDb extends LocalDynamoDbDataStorage[FeedsDbKey, FeedUrlData] {
  override def tableName: String = "Feeds"
  override def partitionKeyName: String = tableName
  override def partitionKeyType: ScalarAttributeType = ScalarAttributeType.S
  override def sortKeyName: String = "FeedUrl"
  override def sortKeyType: ScalarAttributeType = ScalarAttributeType.S
  implicit val jsonFormatForItem: JsonFormat[FeedUrlData] = FeedUrlData.jsonFormatForFeedUrlData
  override def dataAttribute: String = "refreshIntervalInMinutes"

  val defaultRefreshIntervalInMins = 10
  override implicit val jsonFormatForKey: JsonFormat[FeedsDbKey] = JsonFormatForFeedsDbKey
  override def scanForward: Boolean = true
}
