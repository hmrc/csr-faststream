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

package controllers.fixdata

import model.Exceptions.NotFoundException
import model.SchemeId
import model.command.FastPassPromotion
import play.api.mvc.{ Action, AnyContent, Result }
import services.application.ApplicationService
import services.fastpass.FastPassService
import services.sift.ApplicationSiftService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object FixDataConsistencyController extends FixDataConsistencyController {
  override val applicationService = ApplicationService
  override val fastPassService = FastPassService
  override val siftService = ApplicationSiftService
}

trait FixDataConsistencyController extends BaseController {
  val applicationService: ApplicationService
  val fastPassService: FastPassService
  val siftService: ApplicationSiftService

  def removeETray(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingETray(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def removeProgressStatus(appId: String, progressStatus: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingProgressStatus(appId, progressStatus).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def promoteToFastPassAccepted(applicationId: String) = Action.async(parse.json) { implicit request =>
    withJsonBody[FastPassPromotion] { req =>
      fastPassService.promoteToFastPassCandidate(applicationId, req.triggeredBy).map { _ =>
        NoContent
      } recover {
        case _: NotFoundException => NotFound
      }
    }
  }

  def removeVideoInterviewFailed(appId: String) = Action.async { implicit request =>
    applicationService.fixDataByRemovingVideoInterviewFailed(appId).map { _ =>
      NoContent
    } recover {
      case _: NotFoundException => NotFound
    }
  }

  def rollbackToPhase2CompletedFromPhase2Failed(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackCandidateToPhase2CompletedFromPhase2Failed)
  }

  def rollbackToPhase1ResultsReceivedFromPhase1FailedNotified(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackToPhase1ResultsReceivedFromPhase1FailedNotified)
  }

  def rollbackToPhase2ResultsReceivedFromPhase2FailedNotified(applicationId: String): Action[AnyContent] = Action.async {
    rollbackApplicationState(applicationId, applicationService.rollbackToPhase2ResultsReceivedFromPhase2FailedNotified)
  }

  def rollbackToSubmittedWithFastPassFromOnlineTestsExpired(applicationId: String, fastPass: Int,
    sdipFaststream: Boolean): Action[AnyContent] = Action.async {
    for {
      _ <- applicationService.convertToFastStreamRouteWithFastpassFromOnlineTestsExpired(applicationId, fastPass, sdipFaststream)
      response <- rollbackApplicationState(applicationId, applicationService.rollbackToSubmittedFromOnlineTestsExpired)
    } yield response
  }

  def rollbackToInProgressFromFastPassAccepted(applicationId: String): Action[AnyContent] = Action.async {
    for {
      response <- rollbackApplicationState(applicationId, applicationService.rollbackToInProgressFromFastPassAccepted)
    } yield response
  }

  def removeSdipSchemeFromFaststreamUser(applicationId: String): Action[AnyContent] = Action.async {
    for {
      _ <- applicationService.removeSdipSchemeFromFaststreamUser(applicationId)
    } yield Ok
  }

  def rollbackApplicationState(applicationId: String, operator: String => Future[Unit]): Future[Result] = {
    operator(applicationId).map { _ =>
      Ok(s"Successfully rolled back $applicationId")
    }.recover { case _ =>
      InternalServerError(s"Unable to rollback $applicationId")
    }
  }

  def findUsersStuckInSiftReadyWithFailedPreSiftSiftableSchemes(): Action[AnyContent] = Action.async {
    siftService.findUsersInSiftReadyWhoShouldHaveBeenCompleted.map(resultList =>
        Ok((Seq("applicationId, timeEnteredSift, shouldBeCompleted") ++ resultList.map { case(user, result) =>
          s"${user.applicationId},${user.timeEnteredSift},$result"
        }).mkString("\n"))
      )
  }

  def fixUserStuckInSiftReadyWithFailedPreSiftSiftableSchemes(applicationId: String): Action[AnyContent] = Action.async {
    siftService.fixUserInSiftReadyWhoShouldHaveBeenCompleted(applicationId).map(_ => Ok)
  }

  def findUsersStuckInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase(): Action[AnyContent] = Action.async {
    siftService.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase.map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId,currentSchemeStatus") ++ resultList.map { user =>
          s"${user.applicationId},${user.currentSchemeStatus}"
        }).mkString("\n"))
      }
    )
  }

  def fixUserStuckInSiftEnteredWhoShouldBeInSiftReadyWhoHasFailedFormBasedSchemesInVideoPhase(applicationId: String): Action[AnyContent] =
    Action.async {
    siftService.fixUserInSiftEnteredWhoShouldBeInSiftReadyWhoHasFailedFormBasedSchemesInVideoPhase(applicationId).map(_ => Ok)
  }

  def findUsersStuckInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(): Action[AnyContent] = Action.async {
    siftService.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes.map(resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("applicationId,currentSchemeStatus") ++ resultList.map { user =>
          s"${user.applicationId},${user.currentSchemeStatus}"
        }).mkString("\n"))
      }
    )
  }

  def findSdipFaststreamFailedFaststreamInvitedToVideoInterview(): Action[AnyContent] = Action.async {
    applicationService.findSdipFaststreamFailedFaststreamInvitedToVideoInterview.map { resultList =>
      if (resultList.isEmpty) {
        Ok("No candidates found")
      } else {
        Ok((Seq("Email,Preferred Name (or first name if no preferred),Application ID,Failed at stage," +
          "Latest Progress Status,online test results,e-tray results") ++
          resultList.map { case (user, contactDetails, failedAtStage, latestProgressStatus, onlineTestPassMarks, etrayPassMarks) =>
            val onlineTestResultsAsString = "\"[" + onlineTestPassMarks.result.map(schemeResult =>
              s"${schemeResult.schemeId.toString} -> ${schemeResult.result}"
            ).mkString(", ") + "]\""

            val eTrayResultsAsString = "\"[" + etrayPassMarks.result.map(schemeResult =>
              s"${schemeResult.schemeId.toString} -> ${schemeResult.result}"
            ).mkString(", ") + "]\""

          s"${contactDetails.email},${user.preferredName.getOrElse(user.firstName)},${user.applicationId.get}," +
            s"$failedAtStage,${latestProgressStatus.toString},$onlineTestResultsAsString,$eTrayResultsAsString"
        }).mkString("\n"))
      }
    }
  }

  def moveSdipFaststreamFailedFaststreamInvitedToVideoInterviewToSift(applicationId: String): Action[AnyContent] =
    Action.async { implicit request =>
      applicationService.moveSdipFaststreamFailedFaststreamInvitedToVideoInterviewToSift(applicationId).map(_ =>
        Ok(s"Successfully fixed $applicationId")
      ).recover {
        case ex: Throwable =>
          InternalServerError(s"Could not fix $applicationId - message: ${ex.getMessage}")
      }
    }

  def fixUserStuckInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes(applicationId).map(_ => Ok)
    }

  def fixUserSiftedWithAFailByMistake(applicationId: String): Action[AnyContent] =
    Action.async {
      siftService.fixUserSiftedWithAFailByMistake(applicationId).map(_ => Ok(s"Successfully fixed $applicationId"))
    }

  def fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.fixSdipFaststreamCandidateWhoExpiredInOnlineTests(applicationId).map(_ => Ok(s"Successfully fixed $applicationId"))
  }

  def markSiftSchemeAsRed(applicationId: String, schemeId: model.SchemeId): Action[AnyContent] = Action.async {
    applicationService.markSiftSchemeAsRed(applicationId, schemeId).map(_ =>
      Ok(s"Successfully marked ${schemeId.value} as red for $applicationId")
    )
  }

  def rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId: String): Action[AnyContent] = Action.async {
    applicationService.rollbackToSiftReadyFromAssessmentCentreAwaitingAllocation(applicationId).map(_ =>
      Ok(s"Successfully rolled $applicationId back to sift ready")
    )
  }
}
