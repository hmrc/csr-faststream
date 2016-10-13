package repositories.onlinetesting

import java.util.UUID

import model.persisted.{ CubiksTest, Phase2TestGroup }
import org.joda.time.{ DateTime, DateTimeZone }
import testkit.MongoRepositorySpec

class Phase2TestRepositorySpec extends ApplicationDataFixture with MongoRepositorySpec {

  val Now =  DateTime.now(DateTimeZone.UTC)
  val DatePlus7Days = Now.plusDays(7)
  val CubiksUserId = 999
  val Token = UUID.randomUUID.toString

  val phase2Test = CubiksTest(
    scheduleId = 123,
    usedForResults = true,
    cubiksUserId = CubiksUserId,
    token = Token,
    testUrl = "test.com",
    invitationDate = Now,
    participantScheduleId = 456
  )

  val TestProfile = Phase2TestGroup(expirationDate = DatePlus7Days, tests = List(phase2Test))

  "Get online test" must {
    "return None if there is no test for the specific user id" in {
      val result = phase2TestRepo.getTestGroup("userId").futureValue
      result mustBe None
    }

    "return an online test for the specific user id" in {
      insertApplication("appId", "userId")
      phase2TestRepo.insertOrUpdateTestGroup("appId", TestProfile).futureValue
      val result = phase2TestRepo.getTestGroup("appId").futureValue
      result mustBe Some(TestProfile)
    }
  }

  "Next application ready for online testing" must {
    "return one application if there is only one" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS_PASSED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val results = phase2TestRepo.nextApplicationsReadyForOnlineTesting.futureValue

      results.length mustBe 1
      results(0).applicationId mustBe "appId"
      results(0).userId mustBe "userId"
    }

    "return more than one candidate for batch processing" in {
      pending
    }
  }

  "Insert a phase 2 test" must {
    "correctly insert a test" in {
      createApplicationWithAllFields("userId", "appId", "frameworkId", "PHASE1_TESTS_PASSED", needsAdjustment = false,
        adjustmentsConfirmed = false, timeExtensionAdjustments = false, fastPassApplicable = false,
        fastPassReceived = false
      ).futureValue

      val now =  DateTime.now(DateTimeZone.UTC)

      val input = Phase2TestGroup(expirationDate = now,
        tests = List(CubiksTest(scheduleId = 1,
          usedForResults = true,
          token = "token",
          cubiksUserId = 111,
          testUrl = "testUrl",
          invitationDate = now,
          participantScheduleId = 222
        ))
      )

      phase2TestRepo.insertOrUpdateTestGroup("appId", input).futureValue

      val result = phase2TestRepo.getTestGroup("appId").futureValue
      result.isDefined mustBe true
      result.get.expirationDate mustBe input.expirationDate
      result.get.tests mustBe input.tests
    }
  }
}