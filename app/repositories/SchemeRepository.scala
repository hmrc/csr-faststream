/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories

import config.MicroserviceAppConfig
import model.{ Scheme, SchemeId }
import net.jcazevedo.moultingyaml._
import net.jcazevedo.moultingyaml.DefaultYamlProtocol._
import play.api.Play
import resource._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source


object SchemeConfigProtocol extends DefaultYamlProtocol {
  implicit val schemeFormat = yamlFormat4((a: String, b: String ,c: String, d: Boolean) => Scheme(a,b,c, d))
}

trait SchemeRepositoryImpl {

  import play.api.Play.current

  private lazy val rawConfig = {
    val input = managed(Play.application.resourceAsStream(MicroserviceAppConfig.schemeConfig.yamlFilePath).get)
    input.acquireAndGet(stream => Source.fromInputStream(stream).mkString)
  }

  lazy val schemes: Seq[Scheme] = {
    import SchemeConfigProtocol._

    rawConfig.parseYaml.convertTo[List[Scheme]]
  }

  def siftableSchemeIds: Seq[SchemeId] = schemes.filter(_.requiresSift).map(_.id)
}

object SchemeYamlRepository extends SchemeRepositoryImpl
