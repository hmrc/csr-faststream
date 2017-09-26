package services.onlinetesting.phase2

import config.Phase2TestsConfig
import model.ApplicationRoute._
import model.ApplicationStatus._
import model.EvaluationResults._
import model.ProgressStatuses.ProgressStatus
import model.exchange.passmarksettings._
import model.persisted.{ ApplicationReadyForEvaluation, PassmarkEvaluation, SchemeEvaluationResult }
import model.{ ApplicationRoute, _ }
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.prop._
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import reactivemongo.json.collection.JSONCollection
import repositories.{ CollectionNames, CommonRepository }
import testkit.MongoRepositorySpec

import scala.concurrent.Future

class Phase2TestEvaluationSpec extends MongoRepositorySpec with CommonRepository
  with TableDrivenPropertyChecks {

  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION
  override val additionalCollections = List(CollectionNames.PHASE2_PASS_MARK_SETTINGS)

  def phase2TestEvaluationService = new EvaluatePhase2ResultService {
    val evaluationRepository = phase2EvaluationRepo
    val gatewayConfig = mockGatewayConfig
    val passMarkSettingsRepo = phase2PassMarkSettingRepo
    val phase2TestsConfigMock = mock[Phase2TestsConfig]
    val phase = Phase.PHASE2
    when(gatewayConfig.phase2Tests).thenReturn(phase2TestsConfigMock)
  }

  trait TestFixture {

    // format: OFF
    val phase2PassMarkSettingsTable = Table[SchemeId, Double, Double](
      ("Scheme Name", "Etray Fail Threshold", "Etray Pass threshold"),
      (SchemeId("Commercial"), 20.0, 80.0),
      (SchemeId("DigitalAndTechnology"), 20.001, 20.001),
      (SchemeId("DiplomaticService"), 20.01, 20.02),
      (SchemeId("DiplomaticServiceEconomics"), 30.0, 70.0),
      (SchemeId("DiplomaticServiceEuropean"), 30.0, 70.0),
      (SchemeId("European"), 40.0, 70.0),
      (SchemeId("Finance"), 25.01, 25.02),
      (SchemeId("Generalist"), 30.0, 30.0),
      (SchemeId("GovernmentCommunicationService"), 30.0, 70.0),
      (SchemeId("GovernmentEconomicsService"), 30.0, 70.0),
      (SchemeId("GovernmentOperationalResearchService"), 30.0, 70.0),
      (SchemeId("GovernmentSocialResearchService"), 30.0, 70.0),
      (SchemeId("GovernmentStatisticalService"), 30.0, 70.0),
      (SchemeId("HousesOfParliament"), 30.0, 79.999),
      (SchemeId("HumanResources"), 30.0, 50.0),
      (SchemeId("ProjectDelivery"), 30.0, 70.0),
      (SchemeId("ScienceAndEngineering"), 69.00, 69.00)
    )
    // format: ON

    var phase2PassMarkSettings: Phase2PassMarkSettings = _

    var applicationReadyForEvaluation: ApplicationReadyForEvaluation = _

    var passMarkEvaluation: PassmarkEvaluation = _

    var phase1PassMarkEvaluation: PassmarkEvaluation = _

    def applicationEvaluation(applicationId: String, etrayScore: Double, selectedSchemes: SchemeId*)
      (implicit applicationRoute: ApplicationRoute = ApplicationRoute.Faststream): TestFixture = {
      applicationReadyForEvaluation = insertApplicationWithPhase2TestResults(applicationId, etrayScore,
        phase1PassMarkEvaluation, applicationRoute = applicationRoute)(selectedSchemes: _*)
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    def mustResultIn(expApplicationStatus: ApplicationStatus.ApplicationStatus, expProgressStatus: Option[ProgressStatus],
      expSchemeResults: (SchemeId, Result)*): TestFixture = {
      passMarkEvaluation = phase2EvaluationRepo.getPassMarkEvaluation(applicationReadyForEvaluation.applicationId).futureValue
      val applicationDetails = applicationRepository.findStatus(applicationReadyForEvaluation.applicationId).futureValue
      val applicationStatus = ApplicationStatus.withName(applicationDetails.status)
      val progressStatus = applicationDetails.latestProgressStatus

      val schemeResults = passMarkEvaluation.result.map {
        SchemeEvaluationResult.unapply(_).map {
          case (schemeType, resultStr) => schemeType -> Result(resultStr)
        }.get
      }
      phase2PassMarkSettings.version mustBe passMarkEvaluation.passmarkVersion
      applicationStatus mustBe expApplicationStatus
      progressStatus mustBe expProgressStatus
      schemeResults must contain theSameElementsAs expSchemeResults
      passMarkEvaluation.previousPhasePassMarkVersion mustBe Some(phase1PassMarkEvaluation.passmarkVersion)
      this
    }

    def applicationReEvaluationWithSettings(newSchemeSettings: (SchemeId, Double, Double)*): TestFixture = {
      val schemePassMarkSettings = phase2PassMarkSettingsTable.filterNot(schemeSetting =>
        newSchemeSettings.map(_._1).contains(schemeSetting._1)) ++ newSchemeSettings
      phase2PassMarkSettings = createPhase2PassMarkSettings(schemePassMarkSettings).futureValue
      phase2TestEvaluationService.evaluate(applicationReadyForEvaluation, phase2PassMarkSettings).futureValue
      this
    }

    private def createPhase2PassMarkSettings(phase2PassMarkSettingsTable:
                                             TableFor3[SchemeId, Double, Double]): Future[Phase2PassMarkSettings] = {
      val schemeThresholds = phase2PassMarkSettingsTable.map {
        fields => Phase2PassMark(fields._1,
          Phase2PassMarkThresholds(PassMarkThreshold(fields._2, fields._3)))
      }.toList

      val phase2PassMarkSettings = Phase2PassMarkSettings(
        schemeThresholds,
        "version-1",
        DateTime.now,
        "user-1"
      )
      phase2PassMarkSettingRepo.create(phase2PassMarkSettings).flatMap { _ =>
        phase2PassMarkSettingRepo.getLatestVersion.map(_.get)
      }
    }

    val appCollection = mongo().collection[JSONCollection](collectionName)

    def createUser(userId: String, appId: String) = {
      appCollection.insert(BSONDocument("applicationId" -> appId, "userId" -> userId, "applicationStatus" -> CREATED))
    }

    Future.sequence(List(
      createUser("user-1", "application-1"),
      createUser("user-2", "application-2"),
      createUser("user-3", "application-3"),
      createPhase2PassMarkSettings(phase2PassMarkSettingsTable).map(phase2PassMarkSettings = _)
    )).futureValue
  }

}
