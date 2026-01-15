import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val bootstrapVersion = "10.5.0"
  val mongoVersion     = "2.11.0"
  val playVersion      = "30"

  val compile = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-backend-play-$playVersion" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-play-$playVersion"        % mongoVersion
  )

  val test = Seq(
    "org.scalatestplus"      %% "scalacheck-1-15"                    % "3.2.11.0",
    "org.mockito"            %% "mockito-scala-scalatest"            % "2.0.0",
    "org.scalatestplus"      %% "mockito-4-11"                       % "3.2.18.0",
    "org.scalatestplus.play" %% "scalatestplus-play"                 % "7.0.2",
    "uk.gov.hmrc"            %% s"bootstrap-test-play-$playVersion"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-test-play-$playVersion" % mongoVersion,
    "org.scalatest"          %% "scalatest"                          % "3.2.19", // Note - updating this appears to break flexmark-all
    "org.mockito"            %% "mockito-scala"                      % "2.0.0",
    "org.playframework"      %% "play-test"                          % current,
    "org.scalatestplus"      %% "scalatestplus-mockito"              % "1.0.0-SNAP5",
    "com.vladsch.flexmark"    % "flexmark-all"                       % "0.64.6", // Note - updating this requires newer version of JRE
  ).map(_ % Test)

}
