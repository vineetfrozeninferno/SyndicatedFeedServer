name := """SyndicatedFeedServer"""

version := "0.1"

scalaVersion := "2.12.6"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

disablePlugins(PlayLayoutPlugin)
PlayKeys.playMonitoredFiles ++= (sourceDirectories in (Compile, TwirlKeys.compileTemplates)).value

externalResolvers := Seq(
  Resolver.mavenLocal,
  Resolver.DefaultMavenRepository
)

libraryDependencies ++= Seq(
  guice,
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.328",
  "com.typesafe.akka" %% "akka-actor"   % "2.5.12",
  "com.typesafe.play" %% "play"         % "2.6.13",
  "io.spray"          %%  "spray-json"  % "1.3.3"
)
