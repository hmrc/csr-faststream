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

package services.testdata

import model.PersistedObjects.{ PersistedAnswer, PersistedQuestion }
import model.persisted.{ AssistanceDetails }
import repositories._
import repositories.application.GeneralApplicationRepository
import repositories.assistancedetails.AssistanceDetailsRepository
import services.testdata.faker.DataFaker._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object InProgressQuestionnaireStatusGenerator extends InProgressQuestionnaireStatusGenerator {
  override val previousStatusGenerator = InProgressAssistanceDetailsStatusGenerator
  override val appRepository = applicationRepository
  override val qRepository = questionnaireRepository
}

trait InProgressQuestionnaireStatusGenerator extends ConstructiveGenerator {
  val appRepository: GeneralApplicationRepository
  val qRepository: QuestionnaireRepository

  // scalastyle:off method.length
  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    val didYouLiveInUkBetween14and18Answer = Random.yesNo
    def getWhatWasYourHomePostCodeWhenYouWere14 = {
      if (didYouLiveInUkBetween14and18Answer == "Yes") {
        Some(PersistedQuestion("What was your home postcode when you were 14?",
          PersistedAnswer(Some(Random.homePostcode), None, None)))
      } else {
        None
      }
    }

    def parentsOccupation = Random.parentsOccupation

    def getParentsOccupationDetail = {
      if (parentsOccupation == "Employeed/Self-employeed") {
        Some(PersistedQuestion("When you were 14, what kind of work did your highest-earning parent or guardian do?",
          PersistedAnswer(Some(Random.parentsOccupationDetails), None, None)))
      } else {
        None
      }
    }

    def getEmployeedOrSelfEmployeed = {
      if (parentsOccupation == "Employeed/Self-employeed") {
        Some(PersistedQuestion("Did they work as an employee or were they self employed?",
          PersistedAnswer(Some(Random.employeeOrSelf), None, None)))
      } else {
        None
      }
    }

    def getSizeParentsEmployeer = {
      if (parentsOccupation == "Employeed/Self-employeed") {
        Some(PersistedQuestion("Which size would best describe their place of work?",
          PersistedAnswer(Some(Random.sizeParentsEmployeer), None, None)))
      } else {
        None
      }
    }

    def getSuperviseEmployees = {
      if (parentsOccupation == "Employeed/Self-employeed") {
        Some(PersistedQuestion("Did they supervise employees?",
          PersistedAnswer(Some(Random.yesNoPreferNotToSay), None, None)))
      } else {
        None
      }
    }

    def getAllQuestionnaireQuestions = List(
      Some(PersistedQuestion("I understand this won't affect my application", PersistedAnswer(Some(Random.yesNo), None, None))),
      Some(PersistedQuestion("What is your gender identity?", PersistedAnswer(Some(Random.gender), None, None))),
      Some(PersistedQuestion("What is your sexual orientation?", PersistedAnswer(Some(Random.sexualOrientation), None, None))),
      Some(PersistedQuestion("What is your ethnic group?", PersistedAnswer(Some(Random.ethnicGroup), None, None))),
      Some(PersistedQuestion("Did you live in the UK between the ages of 14 and 18?", PersistedAnswer(
        Some(didYouLiveInUkBetween14and18Answer), None, None))),
      getWhatWasYourHomePostCodeWhenYouWere14,
      Some(PersistedQuestion("Aged 14 to 16 what was the name of your school?",
        PersistedAnswer(Some(Random.age14to16School), None, None))),
      Some(PersistedQuestion("Aged 16 to 18 what was the name of your school or college?",
        PersistedAnswer(Some(Random.age16to18School), None, None))),
      Some(PersistedQuestion("Were you at any time eligible for free school meals?",
        PersistedAnswer(Some(Random.yesNoPreferNotToSay), None, None))),
      Some(PersistedQuestion("What is the name of the university you received your degree from?",
        PersistedAnswer(Some(Random.university), None, None))),
      Some(PersistedQuestion("Which category best describes your degree?",
        PersistedAnswer(Some(Random.degree), None, None))),
      Some(PersistedQuestion("Do you have a parent or guardian that has completed a university degree course or equivalent?",
        PersistedAnswer(Some(Random.yesNoPreferNotToSay), None, None))),
      Some(PersistedQuestion("When you were 14, was your highest-earning parent or guardian?",
        PersistedAnswer(Some(Random.parentsOccupation), None, None))),
      getParentsOccupationDetail,
      getEmployeedOrSelfEmployeed,
      getSizeParentsEmployeer,
      getSuperviseEmployees
    ).filter(_.isDefined).map { someItem => someItem.get }


    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      _ <- qRepository.addQuestions(candidateInPreviousStatus.applicationId.get, getAllQuestionnaireQuestions)
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "start_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "education_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "diversity_questionnaire")
      _ <- appRepository.updateQuestionnaireStatus(candidateInPreviousStatus.applicationId.get, "occupation_questionnaire")
    } yield {
      candidateInPreviousStatus
    }
  }
  // scalastyle:on method.length
}