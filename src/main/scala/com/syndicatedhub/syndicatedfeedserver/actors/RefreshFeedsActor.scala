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
import com.syndicatedhub.syndicatedfeedserver.messages.{PollFeedRequest, PollFeedResponse}
import org.joda.time.DateTime
import play.api.Logger

object RefreshFeeds
case class RefreshFeed(feedUrl: String)

class RefreshFeedsActor @Inject()(@Named(GuiceModule.pollFeedActorName) pollFeedActor: ActorRef) extends Actor {
  implicit val askDuration: Timeout = 15 seconds

  val logger = Logger(getClass)
  private val system = this.context.system
  implicit private val executionContext: ExecutionContext = this.context.dispatcher
  system.scheduler.schedule(0.seconds, FeedUrlsDb.defaultRefreshIntervalInMins.minutes, self, RefreshFeeds)

  private def processRefresh(feedUrl: String, lastModified: Option[Long]): Unit = {
    val feedDataDbQuery = QueryKey(feedUrl, None, None)
    val currentTimeStamp = new DateTime().getMillis
    for {
      latestSavedArticle <- FeedDataDb.query(feedDataDbQuery, Some(1))
      lastStoredArticleTimeStampOpt = latestSavedArticle.headOption.map(_._2.flatMap(_.pubDate).max)
      pollFeedRequest = PollFeedRequest(feedUrl, lastModified, lastStoredArticleTimeStampOpt)
      latestFeedQueryResponse <- (pollFeedActor ? pollFeedRequest).mapTo[Option[PollFeedResponse]]
    } yield {
      val latestKnownEntryTimeStamp = lastStoredArticleTimeStampOpt.getOrElse(0L)
      val items = latestFeedQueryResponse.map(_.items).getOrElse(Seq.empty)
      val filteredItems = items.filter(_.pubDate.exists(_ >= latestKnownEntryTimeStamp))

      filteredItems match {
        case feedItems if feedItems.isEmpty => logger.debug(s"No updates from $feedUrl")
        case feedItems => FeedDataDb.insertEntry(CompleteKey(feedUrl, currentTimeStamp.toString), feedItems)
      }
    }
  }

  override def receive: Receive = {
    case RefreshFeeds =>
      logger.info(s"Refreshing feeds at ${new DateTime().getMillis}.")
      FeedUrlsDb.query(FeedsFetchAllKey, None).map(_.foreach {
        case (key: FeedsFetchOneKey, feedUrlData) =>
          val feedUrl = key.getFeedUrl
          val lastModified = feedUrlData.lastModified
          processRefresh(feedUrl, lastModified)
        case _ => throw new Exception("Cannot handle key of this type.")
      }).recover {
        case exception: Exception =>
          logger.error(s"Encountered an error while refreshing feeds.", exception)
      }

    case refreshFeed: RefreshFeed =>
      val feedUrl = refreshFeed.feedUrl
      val lastModifiedFuture = FeedUrlsDb.query(new FeedsFetchOneKey(feedUrl), None).map(_.headOption.flatMap(_._2.lastModified))
      lastModifiedFuture.map(lastModified => processRefresh(feedUrl, lastModified)).recover {
        case exception: Exception =>
          logger.error(s"Encountered an error while refreshing url=$feedUrl.", exception)
      }
  }
}
