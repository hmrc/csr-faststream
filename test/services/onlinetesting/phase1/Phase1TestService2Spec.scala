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

package services.onlinetesting.phase1

import akka.actor.ActorSystem
import config._
import connectors.ExchangeObjects._
import connectors.{ CSREmailClient, OnlineTestsGatewayClient }
import factories.{ DateTimeFactory, UUIDFactory }
import model.Commands.PostCode
import model.Exceptions.ConnectorException
import model.OnlineTestCommands._
import model.ProgressStatuses.{ toString => _, _ }
import model.persisted._
import model.stc.StcEventTypes.{ toString => _ }
import model.{ ProgressStatuses, _ }
import org.joda.time.{ DateTime, LocalDate }
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import org.scalatest.PrivateMethodTester
import play.api.mvc.RequestHeader
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.onlinetesting.{ Phase1TestRepository, Phase1TestRepository2 }
import services.AuditService
import services.sift.ApplicationSiftService
import services.stc.{ StcEventService, StcEventServiceFixture }
import testkit.{ ExtendedTimeout, UnitSpec }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class Phase1TestService2Spec extends UnitSpec with ExtendedTimeout
  with PrivateMethodTester {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val scheduleCompletionBaseUrl = "http://localhost:9284/fset-fast-stream/online-tests/phase1"

  val inventoryIds: Map[String, String] = Map[String, String](
  "test1" -> "test1-uuid",
  "test2" -> "test2-uuid",
  "test3" -> "test3-uuid",
  "test4"->"test4-uuid")

  val testIds = NumericalTestIds("inventory-id", Option("assessment-id"), Option("report-id"), Option("norm-id"))
  val tests = Map[String, NumericalTestIds]("test1" -> testIds)

  val mockNumericalTestsConfig2 = NumericalTestsConfig2(tests, List("test1"))

  val integrationConfig = TestIntegrationGatewayConfig(
    url = "",
    phase1Tests = Phase1TestsConfig2(
      5, inventoryIds, List("test1", "test2", "test2", "test4"), List("test1", "test4")
    ),
    phase2Tests = Phase2TestsConfig2(5, 90, inventoryIds, List("test3", "test4")),
    numericalTests = mockNumericalTestsConfig2,
    reportConfig = ReportConfig(1, 2, "en-GB"),
    candidateAppUrl = "http://localhost:9284",
    emailDomain = "test.com"
  )

  val preferredName = "Preferred\tName"
  val preferredNameSanitized = "Preferred Name"
  val lastName = ""
  val userId = "testUserId"

  val onlineTestApplication = OnlineTestApplication(applicationId = "appId",
    applicationStatus = ApplicationStatus.SUBMITTED,
    userId = userId,
    testAccountId = "testAccountId",
    guaranteedInterview = false,
    needsOnlineAdjustments = false,
    needsAtVenueAdjustments = false,
    preferredName,
    lastName,
    None,
    None
  )

  def uuid: String = UUIDFactory.generateUUID()
  val orderId: String = uuid
  val accessCode = "fdkfdfj"
  val logonUrl = "http://localhost/logonUrl"
  val authenticateUrl = "http://localhost/authenticate"

  val invitationDate = DateTime.parse("2016-05-11")
  val startedDate = invitationDate.plusDays(1)
  val expirationDate = invitationDate.plusDays(5)

  val phase1Test = PsiTest(inventoryId = uuid, orderId = uuid, usedForResults = true,
  testUrl = authenticateUrl, invitationDate = invitationDate)

  val phase1TestProfile = Phase1TestProfile2(expirationDate, List(phase1Test))

  val candidate = model.Candidate(userId = "user123", applicationId = Some("appId123"), testAccountId = Some("testAccountId"),
    email = Some("test@test.com"), firstName = Some("Cid"),lastName = Some("Highwind"), preferredName = None,
    dateOfBirth = None, address = None, postCode = None, country = None,
    applicationRoute = None, applicationStatus = None
  )

  val postcode : Option[PostCode]= Some("WC2B 4")
  val emailContactDetails = "emailfjjfjdf@mailinator.com"
  val contactDetails = ContactDetails(outsideUk = false, Address("Aldwych road"), postcode, Some("UK"), emailContactDetails, "111111")

  val auditDetails = Map("userId" -> userId)
  val auditDetailsWithEmail = auditDetails + ("email" -> emailContactDetails)

  val connectorErrorMessage = "Error in connector"

  val result = OnlineTestCommands.PsiTestResult(status = "Completed", tScore = 23.9999d, raw = 66.9999d)

  val savedResult = persisted.PsiTestResult(tScore = 23.9999d, rawScore = 66.9999d, None)

  val applicationId = "31009ccc-1ac3-4d55-9c53-1908a13dc5e1"
  val expiredApplication = ExpiringOnlineTest(applicationId, userId, preferredName)
  val expiryReminder = NotificationExpiringOnlineTest(applicationId, userId, preferredName, expirationDate)
  val success = Future.successful(())

  "get online test" should {
    "return None if the application id does not exist" in new OnlineTest {
      when(otRepositoryMock2.getTestGroup(any())).thenReturn(Future.successful(None))
      val result = phase1TestService.getTestGroup2("nonexistent-userid").futureValue
      result mustBe None
    }

    val validExpireDate = new DateTime(2016, 6, 9, 0, 0)

    "return a valid set of aggregated online test data if the user id is valid" in new OnlineTest {
      when(appRepositoryMock.findCandidateByUserId(any[String]))
        .thenReturn(Future.successful(Some(candidate)))

      when(otRepositoryMock2.getTestGroup(any[String]))
        .thenReturn(Future.successful(
          Some(Phase1TestProfile2(expirationDate = validExpireDate, tests = List(phase1Test)))
      ))

      val result = phase1TestService.getTestGroup2("valid-userid").futureValue

      result.get.expirationDate must equal(validExpireDate)
    }
  }

  "register and invite application" should {
    "Invite to two tests and issue one email for GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock2.markTestAsInactive2(any[String])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.insertPsiTests(any[String], any[Phase1TestProfile2])).thenReturn(Future.successful(()))

      val result = phase1TestService
        .registerAndInvite(List(onlineTestApplication.copy(guaranteedInterview = true)))

      result.futureValue mustBe unit

      verify(onlineTestsGatewayClientMock, times(2)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(2)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "Invite to 4 tests and issue one email for non-GIS candidates" in new SuccessfulTestInviteFixture {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock2.markTestAsInactive2(any[String])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.insertPsiTests(any[String], any[Phase1TestProfile2])).thenReturn(Future.successful(()))

      val result = phase1TestService
        .registerAndInvite(List(onlineTestApplication.copy(guaranteedInterview = false)))

      result.futureValue mustBe unit

      verify(onlineTestsGatewayClientMock, times(4)).psiRegisterApplicant(any[RegisterCandidateRequest])
      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail if registration fails" in new OnlineTest {
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest])).
        thenReturn(Future.failed(new ConnectorException(connectorErrorMessage)))

      val result = phase1TestService.registerAndInvite(onlineTestApplication :: Nil)
      result.failed.futureValue mustBe a[ConnectorException]

      verify(auditServiceMock, times(0)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest' and audit 'OnlineTestInvited' " +
      "if there is an exception retrieving the contact details" in new OnlineTest  {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock2.markTestAsInactive2(any[String])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.insertPsiTests(any[String], any[Phase1TestProfile2])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturn(Future.successful(aoa))

      when(cdRepositoryMock.find(anyString())).thenReturn(Future.failed(new Exception))


      val result = phase1TestService.registerAndInvite(List(onlineTestApplication))
      result.failed.futureValue mustBe an[Exception]

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "fail, audit 'UserRegisteredForOnlineTest' and audit 'OnlineTestInvited'" +
      " if there is an exception sending the invitation email" in new OnlineTest {
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock2.markTestAsInactive2(any[String])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.insertPsiTests(any[String], any[Phase1TestProfile2])).thenReturn(Future.successful(()))
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturn(Future.successful(aoa))

      when(cdRepositoryMock.find(userId))
        .thenReturn(Future.successful(contactDetails))

      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier]))
        .thenReturn(Future.failed(new Exception))

      val result = phase1TestService.registerAndInvite(List(onlineTestApplication))
      result.failed.futureValue mustBe an[Exception]

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(5)).logEventNoRequest(any[String], any[Map[String, String]])
    }

    "audit 'OnlineTestInvitationProcessComplete' on success" in new OnlineTest {
      when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
        .thenReturn(Future.successful(aoa))
      when(otRepositoryMock2.getTestGroup(any[String])).thenReturn(Future.successful(Some(phase1TestProfile)))
      when(otRepositoryMock2.markTestAsInactive2(any[String])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.insertPsiTests(any[String], any[Phase1TestProfile2])).thenReturn(Future.successful(()))
      when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
      when(emailClientMock.sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
        any[HeaderCarrier]
      )).thenReturn(Future.successful(()))
      when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile2]))
        .thenReturn(Future.successful(()))

      val result = phase1TestService.registerAndInvite(List(onlineTestApplication))
      result.futureValue mustBe unit

      verify(emailClientMock, times(1)).sendOnlineTestInvitation(
        eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate)
      )(any[HeaderCarrier])

      verify(auditServiceMock, times(4)).logEventNoRequest("UserRegisteredForOnlineTest", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationEmailSent", auditDetailsWithEmail)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvitationProcessComplete", auditDetails)
      verify(auditServiceMock, times(1)).logEventNoRequest("OnlineTestInvited", auditDetails)
      verify(auditServiceMock, times(7)).logEventNoRequest(any[String], any[Map[String, String]])
    }
  }

  "mark as started" should {
    "change progress to started" in new OnlineTest {
      when(otRepositoryMock2.updateTestStartTime(any[String], any[DateTime])).thenReturn(Future.successful(()))
      when(otRepositoryMock2.getTestGroupByOrderId(anyString()))
        .thenReturn(Future.successful(Phase1TestGroupWithUserIds2("appId123", userId, phase1TestProfile)))
      when(otRepositoryMock2.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED))
        .thenReturn(Future.successful(()))

      phase1TestService.markAsStarted2(orderId).futureValue

      verify(otRepositoryMock2).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_STARTED)
    }
  }

  "mark as completed" should {
    "change progress to completed if there are all tests completed and the test profile hasn't expired" in new OnlineTest {
      when(otRepositoryMock2.updateTestCompletionTime2(any[String], any[DateTime])).thenReturn(Future.successful(()))
      val phase1Tests: Phase1TestProfile2 = phase1TestProfile.copy(
        tests = phase1TestProfile.tests.map(t => t.copy(orderId = orderId, completedDateTime = Some(DateTime.now()))),
        expirationDate = DateTime.now().plusDays(2)
      )
      when(otRepositoryMock2.getTestProfileByOrderId(anyString()))
        .thenReturn(Future.successful(phase1Tests))
      when(otRepositoryMock2.getTestGroupByOrderId(anyString()))
        .thenReturn(Future.successful(Phase1TestGroupWithUserIds2("appId123", userId, phase1Tests)))
      when(otRepositoryMock2.updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED))
        .thenReturn(Future.successful(()))

      phase1TestService.markAsCompleted2(orderId).futureValue

      verify(otRepositoryMock2).updateProgressStatus("appId123", ProgressStatuses.PHASE1_TESTS_COMPLETED)
    }
  }

  trait OnlineTest {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val rh: RequestHeader = mock[RequestHeader]

    val appRepositoryMock = mock[GeneralApplicationRepository]
    val cdRepositoryMock = mock[ContactDetailsRepository]
    val otRepositoryMock = mock[Phase1TestRepository]
    val otRepositoryMock2 = mock[Phase1TestRepository2]
    val onlineTestsGatewayClientMock = mock[OnlineTestsGatewayClient]
    val emailClientMock = mock[CSREmailClient]
    val auditServiceMock = mock[AuditService]
    val tokenFactoryMock = mock[UUIDFactory]
    val onlineTestInvitationDateFactoryMock = mock[DateTimeFactory]
    val eventServiceMock = mock[StcEventService]
    val siftServiceMock = mock[ApplicationSiftService]

    def aoa = AssessmentOrderAcknowledgement(
      customerId = "cust-id", receiptId = "receipt-id", orderId = orderId, testLaunchUrl = authenticateUrl,status =
        AssessmentOrderAcknowledgement.acknowledgedStatus, statusDetails = "", statusDate = LocalDate.now())


    when(tokenFactoryMock.generateUUID()).thenReturn(uuid)
    when(onlineTestInvitationDateFactoryMock.nowLocalTimeZone).thenReturn(invitationDate)
    when(otRepositoryMock2.resetTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))

    val phase1TestService = new Phase1TestService2 with StcEventServiceFixture {
      override val delaySecsBetweenRegistrations = 0
      val appRepository = appRepositoryMock
      val cdRepository = cdRepositoryMock
      val testRepository = otRepositoryMock
      val onlineTestsGatewayClient = onlineTestsGatewayClientMock
      val emailClient = emailClientMock
      val auditService = auditServiceMock
      val tokenFactory = tokenFactoryMock
      val dateTimeFactory = onlineTestInvitationDateFactoryMock
      val eventService = eventServiceMock
      val actor = ActorSystem()
      val siftService = siftServiceMock

      override val testRepository2 = otRepositoryMock2
      override val integrationGatewayConfig = integrationConfig
    }
  }

  trait SuccessfulTestInviteFixture extends OnlineTest {

    when(onlineTestsGatewayClientMock.psiRegisterApplicant(any[RegisterCandidateRequest]))
      .thenReturn(Future.successful(aoa))
    when(cdRepositoryMock.find(any[String])).thenReturn(Future.successful(contactDetails))
    when(emailClientMock.sendOnlineTestInvitation(
      eqTo(emailContactDetails), eqTo(preferredName), eqTo(expirationDate))(
      any[HeaderCarrier]
    )).thenReturn(Future.successful(()))
    when(otRepositoryMock2.insertOrUpdateTestGroup(any[String], any[Phase1TestProfile2]))
      .thenReturn(Future.successful(()))
    when(otRepositoryMock2.resetTestProfileProgresses(any[String], any[List[ProgressStatus]]))
      .thenReturn(Future.successful(()))
  }
}
