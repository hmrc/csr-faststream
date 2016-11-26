package repositories

import model.ApplicationStatus.ApplicationStatus
import model.persisted._
import model.Phase1TestExamples._
import model.Phase2TestProfileExamples._
import model.SchemeType._
import model.{ ApplicationStatus, ProgressStatuses, SelectedSchemes }
import org.joda.time.{ DateTime, DateTimeZone }
import org.junit.Assert._
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.BSONDocument
import repositories.application.GeneralApplicationMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.onlinetesting.{ Phase1EvaluationMongoRepository, Phase1TestMongoRepository, Phase2TestMongoRepository }
import repositories.schemepreferences.SchemePreferencesMongoRepository
import testkit.MongoRepositorySpec


trait CommonRepository {
  this: MongoRepositorySpec with ScalaFutures =>

  import reactivemongo.json.ImplicitBSONHandlers._
  def applicationRepository: GeneralApplicationMongoRepository
  def schemePreferencesRepository: SchemePreferencesMongoRepository
  def assistanceDetailsRepository: AssistanceDetailsMongoRepository
  def phase1TestRepository: Phase1TestMongoRepository
  def phase1EvaluationRepo: Phase1EvaluationMongoRepository
  def phase2TestRepository: Phase2TestMongoRepository


  implicit val now = DateTime.now().withZone(DateTimeZone.UTC)

  def selectedSchemes(schemeTypes: List[SchemeType]) = SelectedSchemes(schemeTypes, orderAgreed = true, eligible = true)


  def insertApplicationWithPhase1TestResults(appId: String, sjq: Double, bq: Option[Double] = None, isGis: Boolean = false
                                            )(schemes:SchemeType*): ApplicationReadyForEvaluation = {
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(sjq), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", bq, None, None, None)))
    val phase1Tests = if(isGis) List(sjqTest) else List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests))
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE1_TESTS, isGis, Phase1TestProfile(now, phase1Tests).activeTests,
      None, None, selectedSchemes(schemes.toList))
  }

  def insertApplicationWithPhase2TestResults(appId: String, etray: Double,
                                             phase1PassMarkEvaluation: PassmarkEvaluation
                                            )(schemes:SchemeType*): ApplicationReadyForEvaluation = {
    assertNotNull("Phase1 pass mark evaluation must be set", phase1PassMarkEvaluation)
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    val etrayTest = getEtrayTest.copy(cubiksUserId = 3, testResult = Some(TestResult("Ready", "norm", Some(etray), None, None, None)))
    val phase1Tests = List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE2_TESTS, Some(phase1Tests), Some(List(etrayTest)))
    phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None)
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE2_TESTS, isGis = false,
      List(etrayTest), None, Some(phase1PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  def insertApplication(appId: String, applicationStatus: ApplicationStatus, phase1Tests: Option[List[CubiksTest]] = None,
                        phase2Tests: Option[List[CubiksTest]] = None, isGis: Boolean = false,
                        schemes: List[SchemeType] = List(Commercial)): Unit = {
    val gis = if (isGis) Some(true) else None
    applicationRepository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "userId" -> appId,
      "applicationStatus" -> applicationStatus
    )).futureValue

    val ad = AssistanceDetails("No", None, gis, needsSupportForOnlineAssessment = Some(false), None,
      needsSupportAtVenue = Some(false), None, needsSupportForPhoneInterview = None, needsSupportForPhoneInterviewDescription = None)
    assistanceDetailsRepository.update(appId, appId, ad).futureValue

    schemePreferencesRepository.save(appId, selectedSchemes(schemes)).futureValue

    phase1Tests.foreach { t =>
      phase1TestRepository.insertOrUpdateTestGroup(appId, Phase1TestProfile(now, t)).futureValue
      t.foreach { oneTest =>
        oneTest.testResult.foreach { result =>
          phase1TestRepository.insertTestResult(appId, oneTest, result).futureValue
        }
      }
      if (t.exists(_.testResult.isDefined)) {
        phase1TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
    phase2Tests.foreach { t =>
      phase2TestRepository.insertOrUpdateTestGroup(appId, Phase2TestGroup(now, t)).futureValue
      t.foreach { oneTest =>
        oneTest.testResult.foreach { result =>
          phase2TestRepository.insertTestResult(appId, oneTest, result).futureValue
        }
      }
      if (t.exists(_.testResult.isDefined)) {
        phase2TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
    applicationRepository.collection.update(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
  }

}
