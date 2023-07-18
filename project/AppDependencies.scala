import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val bootstrapVersion = "7.19.0"
  val mongoVersion = "1.3.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % mongoVersion,
  )

  val test = Seq(

    "org.scalatestplus"      %% "scalacheck-1-15"         % "3.2.10.0",
    "org.mockito"            %% "mockito-scala-scalatest" % "1.17.14",
    "org.scalatestplus"      %% "mockito-4-11"            % "3.2.16.0",
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % mongoVersion,
    "org.scalatest"          %% "scalatest"               % "3.2.5", // Note - updating this appears to break flexmark-all
    "org.mockito"            %% "mockito-scala"           % "1.17.12",
    "com.typesafe.play"      %% "play-test"               % current,
    "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2",
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.36.8", // Note - updating this requires newer version of JRE
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0",
    "com.github.tomakehurst"  % "wiremock-jre8"           % "2.26.3",
    "com.vladsch.flexmark"    % "flexmark-all"            % "0.62.2"
  ).map(_ % "test, it")

}
