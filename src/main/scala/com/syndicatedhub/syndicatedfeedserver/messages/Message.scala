package com.syndicatedhub.syndicatedfeedserver.messages

import java.util.Locale

import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, DateTimeFormatterBuilder}
import spray.json.DefaultJsonProtocol._
import spray.json.JsonFormat

import scala.xml.Node

trait Message

case class PollFeedRequest(url: String, lastModified: Option[Long])
case class ChannelDetailsRequest(url: String)

case class FeedItem(
  title: String,
  link: String,
  description: String,
  author: Option[String],
  category: Option[String],
  source: Option[String],
  pubDate: Option[Long],
  thumbNailUrl: Option[String])

object FeedItem {
  val parsers = Seq(
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss Z").getParser,
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss ZZZ").getParser
  )

  val fmt: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, parsers.toArray).toFormatter
    .withLocale(Locale.ENGLISH).withOffsetParsed()

  def apply(itemNode: Node, sourceFeed: String): FeedItem =
    FeedItem(
      (itemNode \ "title").head.text,
      (itemNode \ "link").head.text,
      (itemNode \ "description").head.text,
      (itemNode \ "author").headOption.orElse((itemNode \ "creator").headOption).map(_.text),
      (itemNode \ "category").headOption.map(_.text),
      (itemNode \ "source").headOption.map(_.text).orElse(Some(sourceFeed)),
      (itemNode \ "pubDate").headOption.map(dateElement => fmt.parseDateTime(dateElement.text).getMillis),
      (itemNode \ "thumbnail").flatMap(x => x.attribute("url")).flatten.headOption.map(_.text)
    )

  implicit val jsonFormat: JsonFormat[FeedItem] = jsonFormat8(FeedItem.apply)
}

case class ChannelDetailsResponse(request: ChannelDetailsRequest, title: String, link: String, description: String)


object ChannelDetailsResponse {
  def apply(request: ChannelDetailsRequest, channelNode: Node): ChannelDetailsResponse =
    ChannelDetailsResponse(
      request,
      (channelNode \ "title").head.text,
      (channelNode \ "link").head.text,
      (channelNode \ "description").head.text
    )
}
case class PollFeedResponse(request: PollFeedRequest, items: Seq[FeedItem]) extends Message