import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val bootstrapVersion = "5.20.0"
  val mongoVersion = "0.74.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % mongoVersion,
    "com.typesafe.play" %% "play-json-joda"            % "2.6.14"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % mongoVersion,
    "org.scalatest"          %% "scalatest"               % "3.2.5", // Note - updating this appears to break flexmark-all
    "org.mockito"            %% "mockito-scala"           % "1.17.12",
    "com.typesafe.play"      %% "play-test"               % current,
    "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2",
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.36.8", // Note - updating this requires newer version of JRE
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "com.github.tomakehurst"  % "wiremock-jre8"           % "2.26.3"
  ).map(_ % "test, it")

}
