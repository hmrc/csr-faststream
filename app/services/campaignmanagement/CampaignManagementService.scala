/*
 * Copyright 2019 HM Revenue & Customs
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

package services.campaignmanagement

import factories.UUIDFactory
import model.command.SetTScoreRequest
import model.exchange.campaignmanagement.{ AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused }
import model.persisted._
import org.joda.time.DateTime
import repositories._
import repositories.application.{ GeneralApplicationMongoRepository, GeneralApplicationRepository }
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.{ Phase1TestRepository, Phase1TestRepository2, Phase2TestRepository, Phase2TestRepository2 }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CampaignManagementService extends CampaignManagementService{
  val afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository = campaignManagementAfterDeadlineSignupCodeRepository
  val uuidFactory: UUIDFactory = UUIDFactory
  val appRepo: GeneralApplicationMongoRepository = applicationRepository
  val phase1TestRepo: Phase1TestRepository = phase1TestRepository
  val phase1TestRepo2: Phase1TestRepository2 = phase1TestRepository2
  val phase2TestRepo: Phase2TestRepository = phase2TestRepository
  val phase2TestRepo2: Phase2TestRepository2 = phase2TestRepository2
  val questionnaireRepo: QuestionnaireRepository = questionnaireRepository
  val mediaRepo: MediaRepository = mediaRepository
  val contactDetailsRepo: ContactDetailsRepository = faststreamContactDetailsRepository
}

trait CampaignManagementService {
  val afterDeadlineCodeRepository: CampaignManagementAfterDeadlineSignupCodeRepository
  val uuidFactory: UUIDFactory
  val appRepo: GeneralApplicationRepository
  val phase1TestRepo: Phase1TestRepository
  val phase1TestRepo2: Phase1TestRepository2
  val phase2TestRepo: Phase2TestRepository
  val phase2TestRepo2: Phase2TestRepository2
  val questionnaireRepo: QuestionnaireRepository
  val mediaRepo: MediaRepository
  val contactDetailsRepo: ContactDetailsRepository

  def afterDeadlineSignupCodeUnusedAndValid(code: String): Future[AfterDeadlineSignupCodeUnused] = {
    afterDeadlineCodeRepository.findUnusedValidCode(code).map(storedCodeOpt =>
      AfterDeadlineSignupCodeUnused(storedCodeOpt.isDefined, storedCodeOpt.map(_.expires))
    )
  }

  def markSignupCodeAsUsed(code: String, applicationId: String): Future[Unit] = {
    afterDeadlineCodeRepository.markSignupCodeAsUsed(code, applicationId).map(_ => ())
  }

  def generateAfterDeadlineSignupCode(createdByUserId: String, expiryInHours: Int): Future[AfterDeadlineSignupCode] = {
    val newCode = CampaignManagementAfterDeadlineCode(
        uuidFactory.generateUUID(),
      createdByUserId,
      DateTime.now().plusHours(expiryInHours)
    )

    afterDeadlineCodeRepository.save(newCode).map { _ =>
      AfterDeadlineSignupCode(newCode.code)
    }
  }

  def listCollections: Future[String] = {
    appRepo.listCollections.map(_.mkString("\n"))
  }

  def removeCollection(name: String): Future[Unit] = {
    appRepo.removeCollection(name)
  }

  def removeCandidate(applicationId: String, userId: String): Future[Unit] = {
    for {
      _ <- appRepo.removeCandidate(applicationId)
      _ <- contactDetailsRepo.removeContactDetails(userId)
      _ <- mediaRepo.removeMedia(userId)
      _ <- questionnaireRepo.removeQuestions(applicationId)
    } yield ()
  }

  private def verifyPhase1TestScoreData(tScoreRequest: SetTScoreRequest): Future[Boolean] = {
    for {
      phase1TestProfileOpt <- phase1TestRepo2.getTestGroup(tScoreRequest.applicationId)
    } yield {
      val testsPresentWithResultsSaved = phase1TestProfileOpt.exists { phase1TestProfile =>
        val allTestsPresent = phase1TestProfile.activeTests.size == 4
        val allTestsHaveATestResult = phase1TestProfile.activeTests.forall(_.testResult.isDefined)
        allTestsPresent && allTestsHaveATestResult
      }
      testsPresentWithResultsSaved
    }
  }

  private def updatePhase1TestProfile(tScoreRequest: SetTScoreRequest, phase1TestProfile: Phase1TestProfile2): Phase1TestProfile2 = {
    phase1TestProfile.copy(tests = updateTests(tScoreRequest, phase1TestProfile.tests))
  }

  private def updateTests(tScoreRequest: SetTScoreRequest, tests: List[PsiTest]) :List[PsiTest] = {
    tests.map { test =>
      val testResultOpt = test.testResult.map { testResult =>
        testResult.copy(tScore = tScoreRequest.tScore)
      }
      test.copy(testResult = testResultOpt)
    }
  }

  def setPhase1TScore(tScoreRequest: SetTScoreRequest): Future[Unit] = {
    (for {
      dataIsValid <- verifyPhase1TestScoreData(tScoreRequest)
    } yield {
      val msg = "Phase1 data is not in the correct state to set tScores"
      if (dataIsValid) {
        for {
          phase1TestProfileOpt <- phase1TestRepo2.getTestGroup(tScoreRequest.applicationId)
          _ <- phase1TestRepo2.insertOrUpdateTestGroup(
            tScoreRequest.applicationId, updatePhase1TestProfile(tScoreRequest, phase1TestProfileOpt
              .getOrElse(throw new IllegalStateException(msg))))
        } yield ()
      } else {
        throw new IllegalStateException(msg)
      }
    }).flatMap(identity)
  }

  def setPhase2TScore(tScoreRequest: SetTScoreRequest): Future[Unit] = {
    (for {
      dataIsValid <- verifyPhase2TestScoreData(tScoreRequest)
    } yield {
      val msg = "Phase2 data is not in the correct state to set tScores"
      if (dataIsValid) {
        for {
          phase2TestGroupOpt <- phase2TestRepo2.getTestGroup(tScoreRequest.applicationId)
          _ <- phase2TestRepo2.insertOrUpdateTestGroup(
            tScoreRequest.applicationId, updatePhase2TestGroup(tScoreRequest, phase2TestGroupOpt
              .getOrElse(throw new IllegalStateException(msg))))
        } yield ()
      } else {
        throw new IllegalStateException(msg)
      }
    }).flatMap(identity)
  }

  private def verifyPhase2TestScoreData(tScoreRequest: SetTScoreRequest): Future[Boolean] = {
    for {
      phase2TestProfileOpt <- phase2TestRepo2.getTestGroup(tScoreRequest.applicationId)
    } yield {
      val testsPresentWithResultsSaved = phase2TestProfileOpt.exists { phase2TestProfile =>
        val allTestsPresent = phase2TestProfile.activeTests.size == 2
        val allTestsHaveATestResult = phase2TestProfile.activeTests.forall ( _.testResult.isDefined )
        allTestsPresent && allTestsHaveATestResult
      }
      testsPresentWithResultsSaved
    }
  }

  private def updatePhase2TestGroup(tScoreRequest: SetTScoreRequest, phase2TestGroup: Phase2TestGroup2): Phase2TestGroup2 = {
    phase2TestGroup.copy(tests = updateTests(tScoreRequest, phase2TestGroup.tests))
  }
}
