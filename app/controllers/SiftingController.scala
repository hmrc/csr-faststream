/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import javax.inject.{ Inject, Singleton }
import model.Exceptions.{ PassMarkEvaluationNotFound, SiftResultsAlreadyExistsException }
import model.exchange.ApplicationSifting
import model.persisted.SchemeEvaluationResult
import model.{ EvaluationResults, SchemeId }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent }
import services.sift.ApplicationSiftService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SiftingController @Inject() (siftService: ApplicationSiftService) extends BaseController {

  def findApplicationsReadyForSchemeSifting(schemeId: String): Action[AnyContent] = Action.async { implicit request =>
    siftService.findApplicationsReadyForSchemeSift(SchemeId(schemeId)).map { candidates =>
      Ok(Json.toJson(candidates))
    }
  }

  def siftCandidateApplication: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[ApplicationSifting] { appForSift =>
      siftService.siftApplicationForScheme(appForSift.applicationId,
        SchemeEvaluationResult(appForSift.schemeId, EvaluationResults.Result.fromPassFail(appForSift.result).toString)
      ).map(_ => Ok)
        .recover {
          case _: PassMarkEvaluationNotFound => Ok
          case ex: SiftResultsAlreadyExistsException => Conflict(ex.m)
          case ex => InternalServerError(ex.getMessage)
        }
    }
  }
}
