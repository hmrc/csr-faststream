package repositories.sifting

import model.EvaluationResults.Green
import model.Phase3TestProfileExamples.phase3TestWithResult
import model.ProgressStatuses.PHASE3_TESTS_PASSED
import model.persisted.{ PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationStatus, SchemeType }
import repositories.onlinetesting.Phase2EvaluationMongoRepositorySpec.phase2TestWithResult
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

class SiftingRepositorySpec extends MongoRepositorySpec with CommonRepository {

  override val collectionName = CollectionNames.APPLICATION

  def repository = new SiftingMongoRepository()

  private val userId = "123"

  "Sifting repository" should {

    "find eligible candidates" in {
      createSiftEligibleCandidates(UserId, AppId)

      val candidates = repository.findSiftingEligible(SchemeType.Commercial).futureValue
      candidates.size mustBe 1
      val candidate = candidates.head
      candidate.applicationId mustBe Some(AppId)
    }

    "sift candidate as Passed" in {
      createSiftEligibleCandidates(UserId, AppId)
      repository.siftCandidate(AppId, SchemeEvaluationResult(SchemeType.Commercial, "Green")).futureValue
      val candidates = repository.findSiftingEligible(SchemeType.Commercial).futureValue
      candidates.size mustBe 0
    }

    "not able to submit sifting for the same scheme twice" in {
      createSiftEligibleCandidates(UserId, AppId)
      repository.siftCandidate(AppId, SchemeEvaluationResult(SchemeType.Commercial, "Green")).futureValue
      intercept[Exception] {
        repository.siftCandidate(AppId, SchemeEvaluationResult(SchemeType.Commercial, "Red")).futureValue
      }
    }
  }

  private def createSiftEligibleCandidates(userId: String, appId: String) = {
    val resultToSave = List(SchemeEvaluationResult(SchemeType.Commercial, Green.toString))

    val phase2Evaluation = PassmarkEvaluation("phase2_version1", None, resultToSave, "phase2_version2-res", None)
    insertApplication(appId, ApplicationStatus.PHASE3_TESTS, None, Some(phase2TestWithResult),
      Some(phase3TestWithResult), phase2Evaluation = Some(phase2Evaluation))

    val phase3Evaluation = PassmarkEvaluation("phase3_version1", Some("phase2_version1"), resultToSave,
      "phase3_version1-res", Some("phase2_version1-res"))
    phase3EvaluationRepo.savePassmarkEvaluation(appId, phase3Evaluation, Some(PHASE3_TESTS_PASSED)).futureValue

  }

}
