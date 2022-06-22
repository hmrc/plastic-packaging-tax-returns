import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "plastic-packaging-tax-returns"

PlayKeys.devSettings := Seq("play.server.http.port" -> "8504")

val silencerVersion = "1.7.1"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(majorVersion := 0,
            scalaVersion := "2.12.11",
            libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
            // ***************
            // Use the silencer plugin to suppress warnings
            scalacOptions += "-P:silencer:pathFilters=routes",
            libraryDependencies ++= Seq(
              compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
              "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
            )
  )
  .settings(publishingSettings: _*)
  .settings(RoutesKeys.routesImport += "java.time.LocalDate")
  .settings(RoutesKeys.routesImport += "uk.gov.hmrc.plasticpackagingtaxreturns.controllers.query.QueryStringParams._")
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scoverageSettings)

lazy val scoverageSettings: Seq[Setting[_]] = Seq(
  coverageExcludedPackages := List("<empty>",
                                   "Reverse.*",
                                   "domain\\..*",
                                   "models\\..*",
                                   "metrics\\..*",
                                   ".*(BuildInfo|Routes|Options).*"
  ).mkString(";"),
  coverageMinimum := 90,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,
  parallelExecution in Test := false
)
