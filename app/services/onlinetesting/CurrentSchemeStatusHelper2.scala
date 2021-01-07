/*
 * Copyright 2021 HM Revenue & Customs
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

package services.onlinetesting

import model.SchemeId
import model.persisted.{ ApplicationReadyForEvaluation2, SchemeEvaluationResult }
import repositories.application.GeneralApplicationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// Guice based repos PSI version
trait CurrentSchemeStatusHelper2 {

  val generalAppRepository: GeneralApplicationRepository

  def getSdipResults(application: ApplicationReadyForEvaluation2): Future[List[SchemeEvaluationResult]] =
    if (application.isSdipFaststream) {
      for {
        currentSchemeStatus <- generalAppRepository.getCurrentSchemeStatus(application.applicationId)
      } yield {
        currentSchemeStatus.find(_.schemeId == SchemeId("Sdip")).toList
      }
    } else {
      Future.successful(Nil)
    }
}
