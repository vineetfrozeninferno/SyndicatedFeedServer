package com.syndicatedhub.syndicatedfeedserver.actors

import akka.actor.{Actor, ActorRef}
import com.google.inject.Inject
import com.google.inject.name.Named
import com.syndicatedhub.syndicatedfeedserver.GuiceModule
import com.syndicatedhub.syndicatedfeedserver.datastorage._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import com.syndicatedhub.syndicatedfeedserver.messages.{FeedItem, PollFeedRequest, PollFeedResponse}
import org.joda.time.DateTime
import play.api.Logger

object RefreshFeeds
case class RefreshFeed(feedUrl: String)

class RefreshFeedsActor @Inject()(@Named(GuiceModule.pollFeedActorName) pollFeedActor: ActorRef) extends Actor {
  implicit val askDuration: Timeout = 15 seconds

  val logger = Logger(getClass)
  private val system = this.context.system
  implicit private val executionContext: ExecutionContext = this.context.dispatcher

  // schedule periodic calls to refresh the feeds.
  system.scheduler.schedule(0.seconds, FeedUrlsDb.defaultRefreshIntervalInMins.minutes, self, RefreshFeeds)

  // given a feed url and lastModified, fetch the feed items
  private def fetchNewItems(feedUrl: String, lastModified: Option[Long]) = {
    val feedDataDbQuery = QueryKey(feedUrl, None, None)
    for {
      latestSavedArticle <- FeedDataDb.query(feedDataDbQuery, Some(1))
      lastStoredArticleTimeStampOpt = latestSavedArticle.headOption.map(_._2.flatMap(_.pubDate).max)
      pollFeedRequest = PollFeedRequest(feedUrl, lastModified, lastStoredArticleTimeStampOpt)
      latestFeedQueryResponse <- (pollFeedActor ? pollFeedRequest).mapTo[Option[PollFeedResponse]]
    } yield {
      val latestKnownEntryTimeStamp = lastStoredArticleTimeStampOpt.getOrElse(0L)
      val items = latestFeedQueryResponse.map(_.items).getOrElse(Seq.empty)
      items.filter(_.pubDate.exists(_ >= latestKnownEntryTimeStamp))
    }
  }

  // save the feed-items into the datastore if non-empty.
  private def saveFeedItems(feedUrl: String, feedItems: Seq[FeedItem]): Unit = {
    val currentTimeStamp = new DateTime().getMillis
    feedItems match {
      case items if items.isEmpty => logger.debug(s"No updates from $feedUrl")
      case items => FeedDataDb.insertEntry(CompleteKey(feedUrl, currentTimeStamp.toString), items)
    }
  }

  override def receive: Receive = {
    // update all the feeds
    case RefreshFeeds =>
      logger.info(s"Refreshing feeds at ${new DateTime().getMillis}.")

      // fetch all the feeds stored.
      val feedsToUpdate = FeedUrlsDb.query(FeedsFetchAllKey, None)

      // update the feeds
      feedsToUpdate.map(_.foreach {
        case (key: FeedsFetchOneKey, feedUrlData) =>
          val feedUrl = key.getFeedUrl
          val lastModified = feedUrlData.lastModified
          fetchNewItems(feedUrl, lastModified).map(items => saveFeedItems(feedUrl, items))
        case _ => throw new Exception("Cannot handle key of this type.")
      }).recover {
        case exception: Exception =>
          logger.error(s"Encountered an error while refreshing feeds.", exception)
      }

    // refresh a single feed.
    case refreshFeed: RefreshFeed =>
      val feedUrl = refreshFeed.feedUrl
      val lastModifiedFuture = FeedUrlsDb.query(new FeedsFetchOneKey(feedUrl), None).map(_.headOption.flatMap(_._2.lastModified))
      lastModifiedFuture
        .map(lastModified => fetchNewItems(feedUrl, lastModified).map(items => saveFeedItems(feedUrl, items)))
        .recover {
        case exception: Exception =>
          logger.error(s"Encountered an error while refreshing url=$feedUrl.", exception)
      }
  }
}
