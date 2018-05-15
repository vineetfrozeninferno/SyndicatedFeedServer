package com.syndicatedhub.syndicatedfeedserver.controllers

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject._
import com.google.inject.name.Named
import com.syndicatedhub.syndicatedfeedserver.GuiceModule
import com.syndicatedhub.syndicatedfeedserver.actors.RefreshFeed
import com.syndicatedhub.syndicatedfeedserver.datastorage._
import com.syndicatedhub.syndicatedfeedserver.messages.{ChannelDetailsRequest, ChannelDetailsResponse}
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class QueryController @Inject()(
  cc: ControllerComponents,
  @Named(GuiceModule.pollFeedActorName) pollFeedActor: ActorRef,
  @Named(GuiceModule.refreshFeedsActorName) refreshFeedsActor: ActorRef
)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  val logger = Logger(getClass)

  implicit val askDuration: Timeout = 15 seconds

  def addSubscription(): Action[AnyContent] = Action.async { implicit request =>
    val subscriptionUrls = request.body.asJson.mkString.parseJson.convertTo[Seq[String]]
    logger.info(s"The url to add is url=${subscriptionUrls.mkString(", ")}")

    val channelDetailsFuture = Future.sequence {
        subscriptionUrls.map(ChannelDetailsRequest)
          .map { channelDetailsRequest => (pollFeedActor ? channelDetailsRequest).mapTo[Option[ChannelDetailsResponse]] }
      }

    channelDetailsFuture.map(_.flatten).flatMap(channelList => Future.sequence {
      channelList.map(channel => {
        FeedUrlsDb
          .insertEntry(new FeedsFetchOneKey(channel.request.url), FeedUrlData(FeedUrlsDb.defaultRefreshIntervalInMins, None))
          .map(_ => channel.request.url)
      })
    }).map { successUrls =>
      successUrls.foreach(url => refreshFeedsActor ! RefreshFeed(url))
      val failUrls = subscriptionUrls.diff(successUrls)
      val response = Map(
        "success" -> successUrls.toJson,
        "failure" -> failUrls.toJson
      ).toJson.compactPrint
      Ok(response).as(JSON)
    }
  }

  def querySubscriptions(): Action[AnyContent] = Action.async { implicit request =>
    val fields = request.body.asJson.mkString.parseJson.asJsObject.fields
    val feedUrls = fields("feeds").convertTo[Seq[String]]
    val lastUpdated = fields.get("lastUpdated").map(_.convertTo[Long].toString)

    val queryKeys = feedUrls.map(feed => QueryKey(feed, lastUpdated, None))
    val responseFuture = Future.sequence(queryKeys.map(key => FeedDataDb.query(key, None)))
    responseFuture.map(_.flatMap(_.values).flatten)
        .map { feeds =>
          Map(
            "lastUpdated" -> new DateTime().getMillis.toJson,
            "items" -> feeds.toSet.toJson
          ).toJson.compactPrint
        }.map(Ok(_).as(JSON))
  }
}
