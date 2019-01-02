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
import model.exchange.campaignmanagement.{ AfterDeadlineSignupCode, AfterDeadlineSignupCodeUnused }
import model.persisted.CampaignManagementAfterDeadlineCode
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.any
import repositories.{ MediaRepository, QuestionnaireRepository }
import repositories.application.GeneralApplicationRepository
import repositories.campaignmanagement.CampaignManagementAfterDeadlineSignupCodeRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.{ Phase1TestRepository, Phase2TestRepository }
import services.BaseServiceSpec
import testkit.MockitoImplicits._

class CampaignManagementServiceSpec extends BaseServiceSpec {

  "afterDeadlineSignupCodeUnusedAndValid" should {
    "return true with an expiry if code is unused and unexpired" in new TestFixture {
      val expiryTime = DateTime.now

      when(mockAfterDeadlineCodeRepository.findUnusedValidCode("1234")
      ).thenReturnAsync(Some(CampaignManagementAfterDeadlineCode("1234", "userId1", expiryTime, None)))

      val response = service.afterDeadlineSignupCodeUnusedAndValid("1234").futureValue
      response mustBe AfterDeadlineSignupCodeUnused(unused = true, Some(expiryTime))
    }

    "return false without an expiry if code is used or expired"  in new TestFixture {
      val expiryTime = DateTime.now

      when(mockAfterDeadlineCodeRepository.findUnusedValidCode("1234")
      ).thenReturnAsync(None)

      val response = service.afterDeadlineSignupCodeUnusedAndValid("1234").futureValue
      response mustBe AfterDeadlineSignupCodeUnused(unused = false, None)
    }
  }

  "generateAfterDeadlineSignupCode" should {
    "save and return a new signup code" in new TestFixture {
      when(mockAfterDeadlineCodeRepository.save(any[CampaignManagementAfterDeadlineCode]()))
        .thenReturnAsync()
      when(mockUuidFactory.generateUUID()).thenReturn("1234")

      val response = service.generateAfterDeadlineSignupCode("userId1", 48).futureValue

      response mustBe AfterDeadlineSignupCode("1234")
    }
  }

  trait TestFixture  {
    val mockAfterDeadlineCodeRepository = mock[CampaignManagementAfterDeadlineSignupCodeRepository]
    val mockUuidFactory = mock[UUIDFactory]
    val mockApplicationRepository = mock[GeneralApplicationRepository]
    val mockPhase1TestRepository = mock[Phase1TestRepository]
    val mockPhase2TestRepository = mock[Phase2TestRepository]
    val mockQuestionnaireRepository = mock[QuestionnaireRepository]
    val mockMediaRepository = mock[MediaRepository]
    val mockContactDetailsRepository = mock[ContactDetailsRepository]

    val service = new CampaignManagementService {
      val afterDeadlineCodeRepository = mockAfterDeadlineCodeRepository
      val uuidFactory = mockUuidFactory
      val appRepo = mockApplicationRepository
      val phase1TestRepo = mockPhase1TestRepository
      val phase2TestRepo = mockPhase2TestRepository
      val questionnaireRepo = mockQuestionnaireRepository
      val mediaRepo = mockMediaRepository
      val contactDetailsRepo = mockContactDetailsRepository
    }
  }
}
