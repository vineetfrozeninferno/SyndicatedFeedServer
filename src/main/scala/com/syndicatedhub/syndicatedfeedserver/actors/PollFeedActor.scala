package com.syndicatedhub.syndicatedfeedserver.actors

import java.net.{HttpURLConnection, URL}

import akka.actor.Actor
import com.syndicatedhub.syndicatedfeedserver.datastorage.{FeedUrlData, FeedUrlsDb, FeedsFetchOneKey}
import com.syndicatedhub.syndicatedfeedserver.messages._
import play.api.Logger

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object PollFeedActor {
  val ifModifiedSinceHeaderKey = "If-Modified-Since"
}

class PollFeedActor extends Actor {
  val logger = Logger(getClass)
  val itemXmlElement = "item"
  val channelXmlElement = "channel"

  implicit val ec: ExecutionContext = this.context.dispatcher

  override def receive: Receive = {
    case request: PollFeedRequest =>
      Try {
        val url = new URL(request.url)
        val connection = url.openConnection().asInstanceOf[HttpURLConnection]
        request.lastModified.foreach(lastMod => connection.setIfModifiedSince(lastMod))

        connection.connect()
        connection.getResponseCode match {
          case 304 =>
            logger.info(s"No updates for ${request.url} since ${request.lastModified.get}")
            Seq.empty
          case _ =>
            val lastModified = Option(connection.getLastModified)
            FeedUrlsDb
              .insertEntry(new FeedsFetchOneKey(request.url), FeedUrlData(FeedUrlsDb.defaultRefreshIntervalInMins, lastModified))
            val xmlElement = scala.xml.XML.load(connection.getInputStream)
            val itemsXml = xmlElement \ channelXmlElement \ itemXmlElement
            itemsXml.map(FeedItem(_, request.url))
        }
      } match {
        case Success(items) => sender() ! Option(PollFeedResponse(request, items))
        case Failure(exception) =>
          logger.error(s"No data obtained. Url=${request.url} suspicious.", exception)
          sender() ! None
      }

    case request: ChannelDetailsRequest =>
      Try {
        val url = new URL(request.url)
        val connection = url.openConnection().asInstanceOf[HttpURLConnection]

        connection.connect()
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
