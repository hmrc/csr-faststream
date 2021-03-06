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

package services.sift

import connectors.OnlineTestEmailClient
import factories.DateTimeFactoryMock
import model.ApplicationStatus.{ ApplicationStatus => _ }
import model.ProgressStatuses.{ ProgressStatus, SIFT_ENTERED }
import model._
import model.command.{ ApplicationForSift, ApplicationForSiftExpiry }
import model.exchange.sift.SiftState
import model.persisted.sift.{ NotificationExpiringSift, SiftTestGroup }
import model.persisted.{ ContactDetails, ContactDetailsExamples, SchemeEvaluationResult }
import model.sift.{ FixUserStuckInSiftEntered, SiftFirstReminder, SiftSecondReminder }
import org.joda.time.{ DateTime, LocalDate }
import reactivemongo.bson.BSONDocument
import repositories.application.GeneralApplicationRepository
import repositories.contactdetails.ContactDetailsRepository
import repositories.sift.ApplicationSiftRepository
import repositories.{ BSONDateTimeHandler, TestSchemeRepository }
import testkit.ScalaMockImplicits._
import testkit.ScalaMockUnitWithAppSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.duration.TimeUnit

class ApplicationSiftServiceSpec extends ScalaMockUnitWithAppSpec {

  trait TestFixture  {
    val appId = "applicationId"
    val userId = "userId"
    val expiryDate = DateTime.now()
    val expiringSift = NotificationExpiringSift(appId, userId, "TestUser", expiryDate)

    val mockApplicationSiftRepo = mock[ApplicationSiftRepository]
    val mockApplicationRepo = mock[GeneralApplicationRepository]
    val mockContactDetailsRepo = mock[ContactDetailsRepository  ]
    val mockEmailClient = mock[OnlineTestEmailClient]

    val stubSchemeRepo = new TestSchemeRepository {
      override lazy val schemes: Seq[Scheme] =
        Seq(
          Scheme(id = "Commercial", code = "GCS", name = "Commercial", civilServantEligible = false, degree = None,
            siftRequirement = Some(SiftRequirement.NUMERIC_TEST), siftEvaluationRequired = true, fsbType = None,
            schemeGuide = None, schemeQuestion = None
          ),
          Scheme(id = "DigitalAndTechnology", code = "DaT", name = "Digital and Technology", civilServantEligible = false,
            degree = None, siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None,
            schemeGuide = None, schemeQuestion = None
          ),
          Scheme(id = "Generalist", code = "GFS", name = "Generalist", civilServantEligible = true, degree = None, siftRequirement = None,
            siftEvaluationRequired = false, fsbType = None, schemeGuide = None, schemeQuestion = None
          ),
          Scheme(id = "GovernmentEconomicsService", code = "GES", name = "Government Economics Service", civilServantEligible = false,
            degree = None, siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None, schemeGuide = None,
            schemeQuestion = None
          ),
          Scheme(id = "GovernmentSocialResearchService", code = "GSR", name = "GovernmentSocialResearchService", civilServantEligible = false,
            degree = None, siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = true, fsbType = None,  schemeGuide = None,
            schemeQuestion = None
          ),
          Scheme(id = "HousesOfParliament", code = "HOP", name = "Houses of Parliament", civilServantEligible = true, degree = None,
            siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None,
            schemeQuestion = None
          ),
          Scheme(id = "ProjectDelivery", code = "PDFS", name = "Project Delivery", civilServantEligible = true, degree = None,
            siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None,
            schemeQuestion = None
          ),
          Scheme(id = "ScienceAndEngineering", code = "SEFS", name = "Science And Engineering", civilServantEligible = true, degree = None,
            siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None,
            schemeQuestion = None
          ),
          Scheme(id = "Edip", code = "EDIP", name = "Early Diversity Internship Programme", civilServantEligible = true, degree = None,
            siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None,
            schemeQuestion = None
          ),
          Scheme(id = "Sdip", code = "SDIP", name = "Summer Diversity Internship Programme", civilServantEligible = true, degree = None,
            siftRequirement = Some(SiftRequirement.FORM), siftEvaluationRequired = false, fsbType = None, schemeGuide = None,
            schemeQuestion = None
          )
        )
      override lazy val siftableSchemeIds: Seq[SchemeId] = Seq(SchemeId("GovernmentSocialResearchService"), SchemeId("Commercial"))
      override lazy val siftableAndEvaluationRequiredSchemeIds: Seq[SchemeId] =
        schemes.collect { case s if s.siftRequirement.isDefined && s.siftEvaluationRequired => s.id }
      override lazy val nonSiftableSchemeIds: Seq[SchemeId] = schemes.collect { case s if s.siftRequirement.isEmpty => s.id }
      override lazy val numericTestSiftRequirementSchemeIds: Seq[SchemeId] = schemes.collect {
        case s if s.siftRequirement.contains(SiftRequirement.NUMERIC_TEST) && s.siftEvaluationRequired => s.id
      }
      override lazy val formMustBeFilledInSchemeIds: Seq[SchemeId] = schemes.collect {
        case s if s.siftRequirement.contains(SiftRequirement.FORM) => s.id
      }
    }

    val service = new ApplicationSiftService(
      mockApplicationSiftRepo,
      mockApplicationRepo,
      mockContactDetailsRepo,
      stubSchemeRepo,
      DateTimeFactoryMock,
      mockEmailClient
    )
  }

  trait SiftUpdateTest extends TestFixture {
    val progressStatusUpdateBson: ProgressStatus => BSONDocument = (status: ProgressStatus) => BSONDocument(
      s"progress-status.$status" -> true,
      s"progress-status-timestamp.$status" -> BSONDateTimeHandler.write(DateTimeFactoryMock.nowLocalTimeZone)
    )

    def currentSchemeUpdateBson(schemeResult: SchemeEvaluationResult*) = BSONDocument(
        "currentSchemeStatus" -> schemeResult.map { s =>
          BSONDocument("schemeId" -> s.schemeId.value, "result" -> s.result)
        }
      )

    lazy val schemeSiftResult = SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString)
    val queryBson = BSONDocument("applicationId" -> appId)
    val updateBson = BSONDocument("test" -> "test")

    (mockApplicationSiftRepo.siftResultsExistsForScheme _).expects(appId, schemeSiftResult.schemeId).returningAsync(false)
  }

  "progressApplicationToSiftStage" must {
    "progress all applications regardless of failures" in new TestFixture {
      val applicationsToProgressToSift = List(
        ApplicationForSift("appId1", "userId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId2", "userId2", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString))),
        ApplicationForSift("appId3", "userId3", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
            List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)))
      )

      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_ENTERED).returningAsync
      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus _).expects("appId2", ProgressStatuses.SIFT_ENTERED)
        .returning(Future.failed(new Exception))
      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus _).expects("appId3", ProgressStatuses.SIFT_ENTERED).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationsToProgressToSift)) { results =>

        val failedApplications = Seq(applicationsToProgressToSift(1))
        val passedApplications = Seq(applicationsToProgressToSift.head, applicationsToProgressToSift(2))
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "progress candidate to SIFT_ENTERED (eligible to be sifted) when the candidate is only in the running for schemes " +
      "requiring a numeric test and form based schemes are failed" in new TestFixture {
      val applicationToProgressToSift = List(
        ApplicationForSift("appId1", "userId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
          )
        )
      )

      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_ENTERED).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationToProgressToSift)) { results =>

        val failedApplications = Nil
        val passedApplications = Seq(applicationToProgressToSift.head)
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "progress candidate to SIFT_ENTERED (eligible to be sifted) when the candidate is still in the running for schemes " +
      "requiring a numeric test and Generalist and form based schemes are failed" in new TestFixture {
      val applicationToProgressToSift = List(
        ApplicationForSift("appId1", "userId1", ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED,
          List(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString),
            SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString),
            SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString)
          )
        )
      )

      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus _).expects("appId1", ProgressStatuses.SIFT_ENTERED).returningAsync

      whenReady(service.progressApplicationToSiftStage(applicationToProgressToSift)) { results =>

        val failedApplications = Nil
        val passedApplications = Seq(applicationToProgressToSift.head)
        results mustBe SerialUpdateResult(failedApplications, passedApplications)
      }
    }

    "find relevant applications for scheme sifting" in new TestFixture {
      val candidates = Seq(Candidate("userId1", Some("appId1"), Some(""), Some(""), Some(""), Some(""), Some(""),
        Some(LocalDate.now), Some(Address("")),
        Some("E1 7UA"), Some("UK"), Some(ApplicationRoute.Faststream), Some("")))

      (mockApplicationSiftRepo.findApplicationsReadyForSchemeSift _).expects(*).returningAsync(candidates)

      whenReady(service.findApplicationsReadyForSchemeSift(SchemeId("scheme1"))) { result =>
        result mustBe candidates
      }
    }
  }

  "siftApplicationForScheme" must {
    "sift and update progress status for a candidate" in new SiftUpdateTest {
      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(schemeSiftResult),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )

      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString)
      ))
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync
      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift a faststream scheme to RED and update progress status for an SdipFaststream candidate whose other fast stream " +
      "schemes are Red or Withdrawn, all require a sift and have been sifted" in new SiftUpdateTest {

      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
      ))

      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString),
        SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))

      override lazy val schemeSiftResult = SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Red.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString) ::
          SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString) ::
          schemeSiftResult ::
          SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString) ::
           Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED),
        progressStatusUpdateBson(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN)
      )
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift a faststream to RED and update progress status for an SdipFaststream candidate whose other fast stream " +
      "schemes are Withdrawn and have not been sifted (failed before SIFT)" in new SiftUpdateTest {
      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      ))

      override lazy val schemeSiftResult = SchemeEvaluationResult(SchemeId("GovernmentEconomicsService"), EvaluationResults.Red.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString) ::
          SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Withdrawn.toString) ::
          schemeSiftResult ::
          SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString) ::
          Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED),
        progressStatusUpdateBson(ProgressStatuses.SIFT_FASTSTREAM_FAILED_SDIP_GREEN)
      )
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift and update progress status for an SdipFaststream candidate whose fast stream " +
      "schemes which require a sift are Red or Withdrawn and have been sifted, but also has a fast stream scheme that " +
      "does not require a sift" in new SiftUpdateTest {
      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString)
      ))

      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.SdipFaststream)

      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(Seq(
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString),
        SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString)
      ))

      override lazy val schemeSiftResult = SchemeEvaluationResult(SchemeId("Sdip"), EvaluationResults.Green.toString)
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Withdrawn.toString) ::
          SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Red.toString) ::
          schemeSiftResult ::
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString) :: Nil: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects(appId, schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift a candidate with remaining schemes to sift" in new SiftUpdateTest {
      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("GovernmentSocialResearchService"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus:_*)
      )

      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift candidate and update progress status if remaining schemes don't require sift" in new SiftUpdateTest {
      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("HousesOfParliament"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus:_*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )

      override lazy val schemeSiftResult = SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)

      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }

    "sift candidate and update progress status if remaining schemes are generalists and/or dont require sift" in new SiftUpdateTest {
      (mockApplicationSiftRepo.getSiftEvaluations _).expects(appId).returningAsync(Nil)

      val currentStatus = Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("HousesOfParliament"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      )
      val expectedUpdateBson = Seq(
        currentSchemeUpdateBson(currentStatus: _*),
        progressStatusUpdateBson(ProgressStatuses.SIFT_COMPLETED)
      )

      override lazy val schemeSiftResult = SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)

      (mockApplicationRepo.getApplicationRoute _).expects(appId).returningAsync(ApplicationRoute.Faststream)
      (mockApplicationRepo.getCurrentSchemeStatus _).expects(appId).returningAsync(currentStatus)
      (mockApplicationSiftRepo.siftApplicationForScheme _).expects("applicationId", schemeSiftResult, expectedUpdateBson).returningAsync

      whenReady(service.siftApplicationForScheme("applicationId", schemeSiftResult)) { result => result mustBe unit }
    }
  }

  "expired sift candidates" must {
    "be processed correctly" in new TestFixture {
      val applicationForSiftExpiry = ApplicationForSiftExpiry(appId, "userId", ApplicationStatus.SIFT)
      val candidate: Candidate = CandidateExamples.minCandidate("userId")
      val contactDetails: ContactDetails = ContactDetailsExamples.ContactDetailsUK

      (mockApplicationSiftRepo.nextApplicationsForSiftExpiry(_ : Int, _ : Int)).expects(*, *).returningAsync(List(applicationForSiftExpiry))

      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus(_ : String, _: ProgressStatus)).expects(appId, ProgressStatuses.SIFT_EXPIRED)
        .returningAsync
      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus(_ : String, _: ProgressStatus))
        .expects(appId, ProgressStatuses.SIFT_EXPIRED_NOTIFIED)
        .returningAsync

      (mockApplicationRepo.find(_ : String)).expects(appId).returningAsync(Some(candidate))
      (mockContactDetailsRepo.find _ ).expects("userId").returningAsync(contactDetails)
      (mockEmailClient.sendSiftExpired(_: String, _: String)(_: HeaderCarrier))
        .expects(contactDetails.email, candidate.name, *).returningAsync

      whenReady(service.processExpiredCandidates(batchSize = 1, gracePeriodInSecs = 0)(HeaderCarrier())) { result => result mustBe unit }
    }
  }

  "sendSiftEnteredNotification" must {
    "send email to the right candidate" in new TestFixture {
      val candidate: Candidate = CandidateExamples.minCandidate("userId")
      val contactDetails: ContactDetails = ContactDetailsExamples.ContactDetailsUK

      (mockApplicationRepo.find(_ : String)).expects(appId).returningAsync(Some(candidate))
      (mockContactDetailsRepo.find _ ).expects("userId").returningAsync(contactDetails)
      (mockEmailClient.notifyCandidateSiftEnteredAdditionalQuestions(_: String, _: String)(_: HeaderCarrier))
        .expects(contactDetails.email, candidate.name, *).returningAsync

      whenReady(service.sendSiftEnteredNotification(appId)(HeaderCarrier())) { result => result mustBe unit }
    }
  }

  "sendReminderNotification" must {
    "send a first reminder email" in new TestFixture {
      val contactDetails = ContactDetailsExamples.ContactDetailsUK

      (mockContactDetailsRepo.find _ ).expects(userId).returningAsync(contactDetails)
      (mockEmailClient.sendSiftReminder(_: String, _: String, _: Int, _: TimeUnit, _: DateTime)(_: HeaderCarrier))
        .expects(contactDetails.email, expiringSift.preferredName, SiftFirstReminder.hoursBeforeReminder,
          SiftFirstReminder.timeUnit, expiryDate, *)
        .returningAsync
      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus(_ : String, _: ProgressStatuses.ProgressStatus))
        .expects(appId, SiftFirstReminder.progressStatus).returningAsync

      whenReady(service.sendReminderNotification(expiringSift, SiftFirstReminder)(HeaderCarrier())) { result => result mustBe unit }
    }

    "send a second reminder email" in new TestFixture {
      val contactDetails = ContactDetailsExamples.ContactDetailsUK

      (mockContactDetailsRepo.find _ ).expects(userId).returningAsync(contactDetails)
      (mockEmailClient.sendSiftReminder(_: String, _: String, _: Int, _: TimeUnit, _: DateTime)(_: HeaderCarrier))
        .expects(contactDetails.email, expiringSift.preferredName, SiftSecondReminder.hoursBeforeReminder,
          SiftSecondReminder.timeUnit, expiryDate, *)
        .returningAsync
      (mockApplicationRepo.addProgressStatusAndUpdateAppStatus(_ : String, _: ProgressStatuses.ProgressStatus))
        .expects(appId, SiftSecondReminder.progressStatus).returningAsync

      whenReady(service.sendReminderNotification(expiringSift, SiftSecondReminder)(HeaderCarrier())) { result => result mustBe unit }
    }
  }

  "findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase" must {
    "return no candidates if the candidates have no numeric test schemes" in new TestFixture {
      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString)))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result => result mustBe Nil }
    }

    "return no candidates if the candidates have no green numeric test schemes" in new TestFixture {
      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(
          SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Red.toString)
        ))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result => result mustBe Nil }
    }

    "return candidates if the candidates have at least one green numeric test scheme" in new TestFixture {
      val oneCandidate = FixUserStuckInSiftEntered("app1", Seq(
        SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      ))

      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(oneCandidate))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result =>
        result mustBe Seq(oneCandidate) }
    }

    "return candidates if the candidates only have one green numeric test scheme" in new TestFixture {
      val oneCandidate = FixUserStuckInSiftEntered("app1", Seq(
        SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Red.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      ))

      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(oneCandidate))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyWhoHaveFailedFormBasedSchemesInVideoPhase) { result =>
        result mustBe Seq(oneCandidate) }
    }
  }

  "findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes" must {
    "return candidates if the candidates still have numeric test schemes and have withdrawn from all form schemes" in new TestFixture {
      val oneCandidate = FixUserStuckInSiftEntered("app1", Seq(
        SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Withdrawn.toString),
        SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
      ))

      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(oneCandidate))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes) { result =>
        result mustBe Seq(oneCandidate) }
    }

    "return no candidates if the candidates have no green numeric test schemes" in new TestFixture {
      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Withdrawn.toString),
          SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Red.toString)
        ))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes) { result =>
        result mustBe Nil }
    }

    "return no candidates if the candidates have no withdrawn schemes" in new TestFixture {
      (mockApplicationSiftRepo.findAllUsersInSiftEntered _).expects().returningAsync(Seq(
        FixUserStuckInSiftEntered("app1", Seq(
          SchemeEvaluationResult(SchemeId("DigitalAndTechnology"), EvaluationResults.Red.toString),
          SchemeEvaluationResult(SchemeId("Generalist"), EvaluationResults.Green.toString),
          SchemeEvaluationResult(SchemeId("Commercial"), EvaluationResults.Green.toString)
        ))
      ))
      whenReady(service.findUsersInSiftEnteredWhoShouldBeInSiftReadyAfterWithdrawingFromAllFormBasedSchemes) { result => result mustBe Nil }
    }
  }

  "fetching sift state" must {
    "return no state when the candidate has no sift entered progress status and no sift test group" in new TestFixture {
      (mockApplicationRepo.getProgressStatusTimestamps _).expects(appId).returningAsync(List.empty)
      (mockApplicationSiftRepo.getTestGroup _).expects(appId).returningAsync(None)

      whenReady(service.getSiftState(appId)) { results =>
        results mustBe None
      }
    }

    // This scenario should never happen but we test to make sure it's handled
    "return no state when the candidate has no sift entered progress status but has a sift test group" in new TestFixture {
      (mockApplicationRepo.getProgressStatusTimestamps _).expects(appId).returningAsync(List.empty)
      (mockApplicationSiftRepo.getTestGroup _).expects(appId).returningAsync(Some(SiftTestGroup(DateTime.now(), Some(List.empty))))

      whenReady(service.getSiftState(appId)) { results =>
        results mustBe None
      }
    }

    // This scenario also should never happen but we test to make sure it's handled
    "return no state when the candidate has sift entered progress status but has no sift test group" in new TestFixture {
      (mockApplicationRepo.getProgressStatusTimestamps _).expects(appId).returningAsync(List((SIFT_ENTERED.toString, DateTime.now())))
      (mockApplicationSiftRepo.getTestGroup _).expects(appId).returningAsync(None)

      whenReady(service.getSiftState(appId)) { results =>
        results mustBe None
      }
    }

    "return state when the candidate has sift entered progress status and the sift test group" in new TestFixture {
      val siftEnteredDateTime = DateTime.now()
      val siftExpiryDateTime = DateTime.now()
      val progressStatusInfo = List((SIFT_ENTERED.toString, siftEnteredDateTime))
      (mockApplicationRepo.getProgressStatusTimestamps _).expects(appId).returningAsync(progressStatusInfo)
      (mockApplicationSiftRepo.getTestGroup _).expects(appId).returningAsync(Some(SiftTestGroup(siftExpiryDateTime, Some(List.empty))))

      whenReady(service.getSiftState(appId)) { results =>
        results mustBe Some(SiftState(siftEnteredDate = siftEnteredDateTime, expirationDate = siftExpiryDateTime))
      }
    }
  }
}
