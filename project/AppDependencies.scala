import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-27"  % "3.4.0",
    "uk.gov.hmrc"             %% "simple-reactivemongo"       % "7.31.0-play-27"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-27"   % "2.23.0"                % Test,
    "org.scalatest"           %% "scalatest"                % "3.2.3"                 % Test,
    "com.typesafe.play"       %% "play-test"                % current                 % Test,
    "org.mockito"             %  "mockito-core"             % "3.7.7"                 % Test,
    "org.scalatestplus"       %% "scalatestplus-mockito"    % "1.0.0-M2"              % Test,
    "com.vladsch.flexmark"    %  "flexmark-all"             % "0.35.10"               % "test, it",
    "org.scalatestplus.play"  %% "scalatestplus-play"       % "4.0.3"                 % "test, it"
  )
}
