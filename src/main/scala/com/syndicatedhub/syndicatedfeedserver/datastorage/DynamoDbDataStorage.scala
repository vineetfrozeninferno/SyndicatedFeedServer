package com.syndicatedhub.syndicatedfeedserver.datastorage

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.{DeleteItemSpec, GetItemSpec, QuerySpec}
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder}
import play.api.Logger
import spray.json._
import spray.json.DefaultJsonProtocol._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait DynamoDbKey
case class CompleteKey(partitionKey: String, sortKey: String) extends DynamoDbKey
case class QueryKey(partitionKey: String, startSortKey: Option[String], stopSortKey: Option[String]) extends DynamoDbKey

object CompleteKey {
  implicit val jsonFormatForCompleteKey: JsonFormat[CompleteKey] = jsonFormat2(CompleteKey.apply)
}

trait DynamoDbDataStorage[KEY <: DynamoDbKey, ITEM] extends BaseDataStorage[KEY, ITEM] {
  val logger = Logger(getClass)

  def serviceEndpoint: String
  def serviceRegion: String

  def partitionKeyName: String
  def partitionKeyType: ScalarAttributeType

  def sortKeyName: String
  def sortKeyType: ScalarAttributeType

  implicit val jsonFormatForKey: JsonFormat[KEY]
  implicit val jsonFormatForItem: JsonFormat[ITEM]

  def dataAttribute: String
  final val rawDataAttribute: String = "data"
  final val rawKeyAttribute: String = "key"

  private val config = new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, serviceRegion)
  private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard.withEndpointConfiguration(config).build
  private val dynamoDB = new DynamoDB(client)
  private val table = createTable

  private def keyToRawKey(keyValue: String, keyType: ScalarAttributeType): Any = keyType match {
    case ScalarAttributeType.S => keyValue
    case ScalarAttributeType.N => keyValue.toInt
    case ScalarAttributeType.B => keyValue.toByte
  }

  private def partitionKeyToRawKey(partitionKeyValue: String) = keyToRawKey(partitionKeyValue, partitionKeyType)
  private def sortKeyToRawKey(sortKeyValue: String) = keyToRawKey(sortKeyValue, sortKeyType)

  private def dynamoDbKeyToPrimaryKey(key: DynamoDbKey): PrimaryKey = key match {
    case cKey: CompleteKey =>
      new PrimaryKey(partitionKeyName, partitionKeyToRawKey(cKey.partitionKey), sortKeyName, sortKeyToRawKey(cKey.sortKey))

    case qKey: QueryKey if qKey.startSortKey == qKey.stopSortKey && qKey.startSortKey.isDefined =>
      dynamoDbKeyToPrimaryKey(CompleteKey(partitionKeyName, qKey.startSortKey.get))

    case queryKey: QueryKey => new PrimaryKey(partitionKeyName, partitionKeyToRawKey(queryKey.partitionKey))
  }

  private def dynamoItemToItem(dynamoDbItem: Item): (KEY, ITEM) = {
    val baseJsonFields = dynamoDbItem.getJSON(rawDataAttribute).parseJson.asJsObject.fields
    val key = baseJsonFields(rawKeyAttribute).convertTo[KEY]
    val item = baseJsonFields(dataAttribute).convertTo[ITEM]
    (key, item)
  }

  private def itemToDynamoItem(key:KEY, item:ITEM): Item = {
    val rawItem = Map (
      rawKeyAttribute -> key.toJson,
      dataAttribute -> item.toJson
    ).toJson.compactPrint

    new Item().withPrimaryKey(dynamoDbKeyToPrimaryKey(key)).withJSON(rawDataAttribute, rawItem)
  }

  private def createTable: Table = {
    val keySchema = Seq(
      new KeySchemaElement(partitionKeyName, KeyType.HASH), // Partition key
      new KeySchemaElement(sortKeyName, KeyType.RANGE)      // Sort key
    ).asJava

    val attributeDefinitions = Seq(
      new AttributeDefinition(partitionKeyName, partitionKeyType),  // Partition key
      new AttributeDefinition(sortKeyName, sortKeyType)             // Sort key
    ).asJava

    val tableRequest = new CreateTableRequest()
      .withTableName(tableName)
      .withKeySchema(keySchema)
      .withAttributeDefinitions(attributeDefinitions)
      .withProvisionedThroughput(new ProvisionedThroughput(10L, 10L))

    try {
      val table = dynamoDB.createTable(tableRequest)

      logger.info(s"Awaiting the creation of table=$tableName")
      table.waitForActive()
      logger.info(s"Completed creation of table=$tableName")

      table
    }
    catch {
      case _: ResourceInUseException =>
        logger.info(s"Table=$tableName may already exist.")
        dynamoDB.getTable(tableName)
    }
  }

  override def insertEntry(key: KEY, item: ITEM)(implicit ec: ExecutionContext): Future[ITEM] = Future {
    val itemToInsert = itemToDynamoItem(key, item)
    table.putItem(itemToInsert)
    item
  }

  private def readEntry(key: KEY)(implicit ec: ExecutionContext): Future[Option[(KEY,ITEM)]] = Future {
    val spec = new GetItemSpec().withPrimaryKey(dynamoDbKeyToPrimaryKey(key))
    Option(table.getItem(spec)).map(dynamoItemToItem)
  }

  override def deleteEntry(key: KEY)(implicit ec: ExecutionContext): Future[Option[ITEM]] = {
    readEntry(key).map {
      case None =>
        logger.info(s"No value stored against key=$key")
        None
      case deletedRow =>
        val deleteItemSpec = new DeleteItemSpec().withPrimaryKey(dynamoDbKeyToPrimaryKey(key))
        Try(table.deleteItem(deleteItemSpec)).toOption.flatMap(x => deletedRow.map(_._2))
    }
  }

  override def query(key: KEY)(implicit ec: ExecutionContext): Future[Map[KEY,ITEM]] = key match {
    case _: CompleteKey => readEntry(key).map(_.map(row => Map(row._1 -> row._2)).getOrElse(Map.empty))

    case qKey: QueryKey if qKey.stopSortKey == qKey.startSortKey && qKey.startSortKey.isDefined =>
      readEntry(key).map(_.map(row => Map(row._1 -> row._2)).getOrElse(Map.empty))

    case qKey: QueryKey =>
      val baseQuery = new QuerySpec().withHashKey(partitionKeyName, partitionKeyToRawKey(qKey.partitionKey))

      val query = (qKey.startSortKey, qKey.stopSortKey) match {
        case (None, None) => baseQuery
        case (None, high) => baseQuery.withRangeKeyCondition(new RangeKeyCondition(sortKeyName).le(sortKeyToRawKey(high.get)))
        case (low, None) => baseQuery.withRangeKeyCondition(new RangeKeyCondition(sortKeyName).ge(sortKeyToRawKey(low.get)))
        case (low, high) =>
          val condition = new RangeKeyCondition(sortKeyName).between(sortKeyToRawKey(low.get), sortKeyToRawKey(high.get))
          baseQuery.withRangeKeyCondition(condition)
      }
      Future { table.query(query).iterator().asScala.toList.map(dynamoItemToItem).map(row => row._1 -> row._2).toMap }
  }
}

trait LocalDynamoDbDataStorage[KEY <: DynamoDbKey, ITEM] extends DynamoDbDataStorage[KEY, ITEM] {
  override def serviceEndpoint: String = "http://localhost:8000"
  override def serviceRegion: String = "local"
}
