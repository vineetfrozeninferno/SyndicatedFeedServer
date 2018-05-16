package com.syndicatedhub.syndicatedfeedserver.actors

import java.net.{HttpURLConnection, URL}

import akka.actor.Actor
import com.syndicatedhub.syndicatedfeedserver.datastorage.{FeedUrlData, FeedUrlsDb, FeedsFetchOneKey}
import com.syndicatedhub.syndicatedfeedserver.messages._
import javax.net.ssl.HttpsURLConnection
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object PollFeedActor {
  val ifModifiedSinceHeaderKey = "If-Modified-Since"

  def getConnection(requestUrl: String, lastModifiedOpt: Option[Long]): HttpURLConnection = {
    val url = new URL(requestUrl)
    val connection = url.getProtocol.toLowerCase match {
      case "http" => url.openConnection().asInstanceOf[HttpURLConnection]
      case "https" => url.openConnection().asInstanceOf[HttpsURLConnection]
    }

    lastModifiedOpt.foreach(lastMod => connection.setIfModifiedSince(lastMod))
    connection.setRequestProperty("User-Agent", "curl/7.54.0")
    connection.connect()
    val debug = connection.getResponseCode
    connection
  }
}

// Actor that pings the feed urls, fetches the data and parses it into `FeedItem` objects.
// This actor can also be used to fetch the channel details.
class PollFeedActor extends Actor {
  val logger = Logger(getClass)
  val itemXmlElement = "item"
  val channelXmlElement = "channel"

  implicit val ec: ExecutionContext = this.context.dispatcher

  override def receive: Receive = {
    // Fetch the items in the feed and convert into `FeedItem` objects. Channel details are ignored.
    // Some feed servers support the `if-modified-since` header. The actor uses this feature, if possible, to reduce data processing.
    case request: PollFeedRequest =>
      Try {
        val connection = PollFeedActor.getConnection(request.url, request.lastModified)

        connection.getResponseCode match {
          // if the server responds with 304, there are no updates to process.
          case 304 =>
            logger.info(s"No updates for ${request.url} since ${request.lastModified.get}")
            Seq.empty

          // process the updates
          case _ =>
            val lastModified = Option(connection.getLastModified)
            FeedUrlsDb
              .insertEntry(new FeedsFetchOneKey(request.url), FeedUrlData(FeedUrlsDb.defaultRefreshIntervalInMins, lastModified))
            val xmlElement = scala.xml.XML.load(connection.getInputStream)
            val itemsXml = xmlElement \ channelXmlElement \ itemXmlElement

            val lastTimeStamp = request.lastStoredArticleTimeStamp.getOrElse(0L)

            itemsXml
              .filter(itemXML => FeedItem.getPubDate(itemXML).forall(_ >= lastTimeStamp)) // parse new feed-items only.
              .map(FeedItem(_, request.url))
        }
      } match {
        case Success(items) => sender() ! Option(PollFeedResponse(request, items))
        case Failure(exception) =>
          logger.error(s"No data obtained. Url=${request.url} suspicious.", exception)
          sender() ! None
      }

    // fetch only the details of the channel and does nothing about the feed items.
    case request: ChannelDetailsRequest =>
      Try {
        val connection = PollFeedActor.getConnection(request.url, None)
        val xmlElement = scala.xml.XML.load(connection.getInputStream)
        val channelXml = xmlElement \ channelXmlElement
        ChannelDetailsResponse(request, channelXml.head)
      } match {
        case Success(response) => sender() ! Option(response)
        case Failure(exception) =>
          logger.error(s"No data obtained or unable to parse content. Url=${request.url} suspicious.", exception)
          sender() ! None
      }
  }
}
