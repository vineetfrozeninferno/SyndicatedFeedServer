package com.syndicatedhub.syndicatedfeedserver.actors

import akka.actor.Actor
import com.syndicatedhub.syndicatedfeedserver.datastorage.{FeedUrlsDb, FeedsFetchAllKey, FeedsFetchOneKey}

import scala.concurrent.duration._

object RefreshFeeds

class RefreshFeedsActor extends Actor {
  private val system = this.context.system
  system.scheduler.schedule(0.seconds, FeedUrlsDb.defaultRefreshIntervalInMins.minutes, self, RefreshFeeds)

  override def receive: Receive = {
    case RefreshFeeds =>
      val feedsToRefreshFuture =
        FeedUrlsDb.query(FeedsFetchAllKey, None).map(_.keySet.collect { case x: FeedsFetchOneKey => x.getFeedUrl})
      feedsToRefreshFuture.map { feeds =>
        feeds.map { feedUrl =>

        }
      }
  }
}
