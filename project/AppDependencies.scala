import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq("uk.gov.hmrc" %% "bootstrap-backend-play-28" % "5.16.0",
                    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"   % "0.56.0",
                    "com.typesafe.play" %% "play-json-joda"       % "2.6.14"
  )

  val test = Seq("uk.gov.hmrc" %% "bootstrap-test-play-28" % "5.16.0" % Test,
                 "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-27" % "0.56.0"   % Test,
                 "org.scalatest"          %% "scalatest"               % "3.2.5"    % Test,
                 "com.typesafe.play"      %% "play-test"               % current    % Test,
                 "org.mockito"             % "mockito-core"            % "3.9.0"    % Test,
                 "org.scalatestplus"      %% "scalatestplus-mockito"   % "1.0.0-M2" % Test,
                 "com.vladsch.flexmark"    % "flexmark-all"            % "0.36.8"   % "test, it",
                 "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"    % "test, it",
                 "com.github.tomakehurst"  % "wiremock-jre8"           % "2.26.3"   % "test, it"
  )

}
