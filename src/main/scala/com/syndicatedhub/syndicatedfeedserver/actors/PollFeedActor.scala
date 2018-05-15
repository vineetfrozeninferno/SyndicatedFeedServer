package com.syndicatedhub.syndicatedfeedserver.actors

import java.net.URL

import akka.actor.Actor
import com.syndicatedhub.syndicatedfeedserver.messages._
import play.api.Logger

import scala.util.{Failure, Success, Try}

class PollFeedActor extends Actor {
  val logger = Logger(getClass)
  val itemXmlElement = "item"
  val channelXmlElement = "channel"

  override def receive: Receive = {
    case request: PollFeedRequest =>
      Try {
        val xmlElement = scala.xml.XML.load(new URL(request.url))
        val itemsXml = xmlElement \ channelXmlElement \ itemXmlElement
        itemsXml.map(FeedItem(_, request.url))
      } match {
        case Success(items) => sender() ! Option(PollFeedResponse(request, items))
        case Failure(exception) =>
          logger.error("No data obtained. Url suspicious.", exception)
          sender() ! None
      }

    case request: ChannelDetailsRequest =>
      Try {
        val debug = new URL(request.url).openConnection().getHeaderFields
        val xmlElement = scala.xml.XML.load(new URL(request.url))
        val channelXml = xmlElement \ channelXmlElement
        ChannelDetailsResponse(request, channelXml.head)
      } match {
        case Success(response) => sender() ! Option(response)
        case Failure(exception) =>
          logger.info("No data obtained or unable to parse content. Url suspicious.", exception)
          sender() ! None
      }
  }
}
