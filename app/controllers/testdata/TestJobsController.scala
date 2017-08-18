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

package controllers.testdata

import play.api.mvc.{ Action, AnyContent }
import scheduler.assessment.EvaluateAssessmentScoreJob
import scheduler.onlinetesting.{ EvaluatePhase1ResultJob, EvaluatePhase2ResultJob, EvaluatePhase3ResultJob }
import scheduler.{ NotifyAssessorsOfNewEventsJob, ProgressToAssessmentCentreJob, ProgressToSiftJob }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object TestJobsController extends TestJobsController

class TestJobsController extends BaseController {

  def evaluatePhase1OnlineTestsCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluatePhase1ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 1 result job started")
    }
  }

  def evaluatePhase2EtrayCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluatePhase2ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 2 result job started")
    }
  }

  def evaluatePhase3VideoInterviewCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluatePhase3ResultJob.tryExecute().map { _ =>
      Ok("Evaluate phase 3 result job started")
    }
  }

  def progressCandidatesToSift: Action[AnyContent] = Action.async { implicit request =>
    ProgressToSiftJob.tryExecute().map { _ =>
      Ok("Progress to sift result job started")
    }
  }

  def progressCandidatesToAssessmentCentre: Action[AnyContent] = Action.async { implicit request =>
    ProgressToAssessmentCentreJob.tryExecute().map { _ =>
      Ok("Progress to assessment centre result job started")
    }
  }

  def evaluateAssessmentScoresCandidate: Action[AnyContent] = Action.async { implicit request =>
    EvaluateAssessmentScoreJob.tryExecute().map { _ =>
      Ok("Evaluate assessment score job started")
    }
  }

  def notifyAssessorsOfNewEvents: Action[AnyContent] = Action.async { implicit request =>
    NotifyAssessorsOfNewEventsJob.tryExecute().map { _ =>
      Ok("Notify assessors of newly created events started")
    }
  }
}
