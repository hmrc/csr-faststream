/*
 * Copyright 2018 HM Revenue & Customs
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

package services.testdata.candidate.assessmentcentre

import model.UniqueIdentifier
import model.assessmentscores._
import model.exchange.testdata.CreateCandidateResponse.CreateCandidateResponse
import model.testdata.CreateCandidateData.CreateCandidateData
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.mvc.RequestHeader
import services.assessmentscores.{ AssessmentScoresService, AssessorAssessmentScoresService }
import services.testdata.candidate.ConstructiveGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AssessmentCentreScoresEnteredStatusGenerator extends AssessmentCentreScoresEnteredStatusGenerator {
  override val previousStatusGenerator = AssessmentCentreAllocationConfirmedStatusGenerator
  override val assessorAssessmentScoresService = AssessorAssessmentScoresService
}

trait AssessmentCentreScoresEnteredStatusGenerator extends ConstructiveGenerator {
  val assessorAssessmentScoresService: AssessmentScoresService

  val updatedBy = UniqueIdentifier.randomUniqueIdentifier

  // The scores awarded to the candidate by assessor/reviewer
  def analysisExerciseSample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    analysisAndDecisionMakingAverage = Some(5.0),
    leadingAndCommunicatingAverage = Some(4.0),
    strategicApproachToObjectivesAverage = Some(4.0),
    updatedBy = updatedBy,
    strategicApproachToObjectivesScores = Some(StrategicApproachToObjectivesScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    analysisAndDecisionMakingScores = Some(AnalysisAndDecisionMakingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    leadingAndCommunicatingScores = Some(LeadingAndCommunicatingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    strategicApproachToObjectivesFeedback = Some("Strategic approach feedback " + assessorOrReviewer),
    analysisAndDecisionMakingFeedback = Some("Analysis and Decision feedback" + assessorOrReviewer),
    leadingAndCommunicatingFeedback = Some("Leading and communicating feedback" + assessorOrReviewer)
  )
  def groupExerciseSample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    analysisAndDecisionMakingAverage = Some(5.0),
    buildingProductiveRelationshipsAverage = Some(2.0),
    leadingAndCommunicatingAverage = Some(4.0),
    updatedBy = updatedBy,
    analysisAndDecisionMakingScores = Some(AnalysisAndDecisionMakingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    buildingProductiveRelationshipsScores = Some(BuildingProductiveRelationshipsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    leadingAndCommunicatingScores = Some(LeadingAndCommunicatingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    analysisAndDecisionMakingFeedback = Some("Analysis and Decision feedback" + assessorOrReviewer),
    buildingProductiveRelationshipsFeedback = Some("Building Productive feedback" + assessorOrReviewer),
    leadingAndCommunicatingFeedback = Some("Leading and communicating feedback" + assessorOrReviewer)
  )
  def leadershipExerciseSample(assessorOrReviewer: String) = AssessmentScoresExercise(
    attended = true,
    buildingProductiveRelationshipsAverage = Some(4.0),
    leadingAndCommunicatingAverage = Some(4.0),
    strategicApproachToObjectivesAverage = Some(4.0),
    updatedBy = updatedBy,
    buildingProductiveRelationshipsScores = Some(BuildingProductiveRelationshipsScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    leadingAndCommunicatingScores = Some(LeadingAndCommunicatingScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    strategicApproachToObjectivesScores = Some(StrategicApproachToObjectivesScores(
      Some(1.0), Some(1.0), Some(1.0), Some(1.0), Some(1.0)
    )),
    buildingProductiveRelationshipsFeedback = Some("Building Productive feedback " + assessorOrReviewer),
    leadingAndCommunicatingFeedback = Some("Leading and communicating feedback " + assessorOrReviewer),
    strategicApproachToObjectivesFeedback = Some("Strategic approach feedback " + assessorOrReviewer)
  )
  def finalFeedbackSample(assessorOrReviewer: String) = AssessmentScoresFinalFeedback(
    "final feedback for " + assessorOrReviewer, updatedBy, DateTime.now(DateTimeZone.UTC)
  )

  def generate(generationId: Int, generatorConfig: CreateCandidateData)
    (implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateResponse] = {

    import model.command.AssessmentScoresCommands.AssessmentScoresSectionType._
    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      appId = UniqueIdentifier(candidateInPreviousStatus.applicationId.getOrElse(sys.error("Missed application id for candidate")))
      assessorOrReviewer = "assessor"
      _ <- assessorAssessmentScoresService.submitExercise(appId, analysisExercise, analysisExerciseSample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitExercise(appId, groupExercise, groupExerciseSample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitExercise(appId, leadershipExercise, leadershipExerciseSample(assessorOrReviewer))
      _ <- assessorAssessmentScoresService.submitFinalFeedback(appId, finalFeedbackSample(assessorOrReviewer))
    } yield {
      candidateInPreviousStatus
    }
  }
}