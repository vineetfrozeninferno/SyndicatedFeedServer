package com.syndicatedhub.syndicatedfeedserver.controllers

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject._
import com.google.inject.name.Named
import com.syndicatedhub.syndicatedfeedserver.GuiceModule
import com.syndicatedhub.syndicatedfeedserver.datastorage.{FeedUrlsDb, FeedsFetchOneKey}
import com.syndicatedhub.syndicatedfeedserver.messages.{ChannelDetailsRequest, ChannelDetailsResponse}
import play.api.Logger
import play.api.mvc._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class QueryController @Inject()(
  cc: ControllerComponents,
  @Named(GuiceModule.pollFeedActorName) pollFeedActor: ActorRef
)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  val logger = Logger(getClass)

  implicit val askDuration: Timeout = 15 seconds

  def addSubscription(): Action[AnyContent] = Action.async { implicit request =>
    val subscriptionUrls = request.body.asJson.map(x => x.as[Seq[String]]).getOrElse(Seq.empty)
    logger.info(s"The url to add is url=${subscriptionUrls.mkString(", ")}")

    val channelDetailsFuture = Future.sequence {
        subscriptionUrls.map(ChannelDetailsRequest)
          .map { request => (pollFeedActor ? request).mapTo[Option[ChannelDetailsResponse]] }
      }

    channelDetailsFuture.map(_.flatten).flatMap(channelList => Future.sequence {
      channelList.map(channel => {
        FeedUrlsDb.insertEntry(new FeedsFetchOneKey(channel.request.url), FeedUrlsDb.defaultRefreshIntervalInMins)
          .map(_ => channel.request.url)
      })
    }).map { results =>
      val successUrls = results
      val failUrls = subscriptionUrls.diff(successUrls)
      val response = Map(
        "success" -> successUrls.toJson,
        "failure" -> failUrls.toJson
      ).toJson.compactPrint
      Ok(response).as(JSON)
    }
  }
}
