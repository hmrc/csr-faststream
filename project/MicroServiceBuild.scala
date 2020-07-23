/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {
  override val appName = "fset-faststream"
  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

//scalastyle:off line.size.limit
private object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc"       %% "simple-reactivemongo"     % "7.30.0-play-25" excludeAll ExclusionRule("com.typesafe.play", "play-iteratees_2.11"),
//    "uk.gov.hmrc"       %% "microservice-bootstrap"   % "10.6.0",
    "uk.gov.hmrc"       %% "bootstrap-play-25"        % "5.3.0",
    // Needed to get an Enumerator of documents from ReactiveMongo. Note the version specified is the version of the ReactiveMongo driver
    // which matches the current version used in the HMRC simple-reactivemongo lib
    // ReactiveMongo version used is here: https://github.com/hmrc/simple-reactivemongo/blob/master/project/LibraryDependencies.scala
    "org.reactivemongo" %% "reactivemongo-iteratees"  % "0.18.8",
    "uk.gov.hmrc"       %% "play-scheduling"          % "5.4.0",
    "org.webjars"       %% "webjars-play"             % "2.3.0",
    "org.webjars"       %  "bootstrap"                % "3.1.1",
    "org.webjars"       %  "jquery"                   % "1.11.0",
    "net.ceedubs"       %% "ficus"                    % "1.1.2",
    compilerPlugin("com.github.ghik" %% "silencer-plugin" % "0.5"),
    "com.github.ghik"   %% "silencer-lib"             % "0.5",
    "org.yaml"          %  "snakeyaml"                % "1.16",
    "com.jsuereth"      %% "scala-arm"                % "1.4",
    "net.jcazevedo"     %% "moultingyaml"             % "0.4.0",
    filters,
    cache,
    ws
  )

  abstract class TestDependencies(scope: String) {
    lazy val test: Seq[ModuleID] = Seq(
      "de.leanovate.play-mockws"  %% "play-mockws"                  % "2.5.1"             % "test",
      "uk.gov.hmrc"               %% "hmrctest"                     % "2.3.0"             % scope,
      "org.mockito"               %  "mockito-core"                 % "2.2.17"            % scope,
      "org.scalatestplus.play"    %% "scalatestplus-play"           % "2.0.1"             % scope,
      "com.typesafe.play"         %% "play-test"                    % PlayVersion.current % scope excludeAll ExclusionRule(organization = "org.specs2"),
      "org.scalamock"             %% "scalamock-scalatest-support"  % "3.5.0"             % scope,
      "org.scalacheck"            %% "scalacheck"                   % "1.13.4"            % "test"
    )
  }

  object Test extends TestDependencies("test")
  object IntegrationTest extends TestDependencies("it")

  def apply(): Seq[ModuleID] = compile ++ Test.test ++ IntegrationTest.test
}
