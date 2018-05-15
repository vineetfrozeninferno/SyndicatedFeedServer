package com.syndicatedhub.syndicatedfeedserver

import akka.routing.BalancingPool
import com.google.inject.AbstractModule
import com.syndicatedhub.syndicatedfeedserver.GuiceModule._
import com.syndicatedhub.syndicatedfeedserver.actors.{PollFeedActor, RefreshFeedsActor}
import play.api.libs.concurrent.AkkaGuiceSupport

object GuiceModule {
  private val numberOfFeedPollActors = 7

  final val pollFeedActorName = "PollFeedActor"
  final val refreshFeedsActorName = "RefreshFeedsActor"
}

class GuiceModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bindActor[PollFeedActor](pollFeedActorName, props => props.withRouter(BalancingPool(numberOfFeedPollActors)))
    bindActor[RefreshFeedsActor](refreshFeedsActorName)
  }
}
