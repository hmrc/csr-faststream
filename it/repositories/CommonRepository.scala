package repositories

import common.FutureEx
import config._
import factories.ITDateTimeFactoryMock
import model.ApplicationRoute.ApplicationRoute
import model.ApplicationStatus.ApplicationStatus
import model.Phase1TestExamples._
import model.Phase2TestProfileExamples._
import model.Phase3TestProfileExamples._
import model.ProgressStatuses.ProgressStatus
import model._
import model.persisted._
import model.persisted.phase3tests.{ LaunchpadTest, Phase3TestGroup }
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.concurrent.ScalaFutures
import reactivemongo.bson.BSONDocument
import repositories.application.GeneralApplicationMongoRepository
import repositories.assessmentcentre.AssessmentCentreMongoRepository
import repositories.assistancedetails.AssistanceDetailsMongoRepository
import repositories.fsb.FsbMongoRepository
import repositories.onlinetesting._
import repositories.passmarksettings.{ Phase1PassMarkSettingsMongoRepository, Phase2PassMarkSettingsMongoRepository }
import repositories.passmarksettings.Phase3PassMarkSettingsMongoRepository
import repositories.sift.ApplicationSiftMongoRepository
import testkit.MongoRepositorySpec

import scala.concurrent.Future

//scalastyle:off number.of.methods
trait CommonRepository extends CurrentSchemeStatusHelper {
  this: MongoRepositorySpec with ScalaFutures =>

  import reactivemongo.play.json.ImplicitBSONHandlers._

  val mockOnlineTestsGatewayConfig = mock[OnlineTestsGatewayConfig]

  val mockEventsConfig = mock[EventsConfig]

  val mockLaunchpadConfig = mock[LaunchpadGatewayConfig]

  val mockAppConfig = mock[MicroserviceAppConfig]

  val DiplomaticServiceEconomists: SchemeId = SchemeId("DiplomaticServiceEconomists")
  val Finance = SchemeId("Finance")
  val GovernmentEconomicsService = SchemeId("GovernmentEconomicsService")
  val DigitalAndTechnology = SchemeId("DigitalAndTechnology")
  val Sdip = SchemeId("Sdip")
  val Edip = SchemeId("Edip")
  val siftableSchemeDefinitions = List(DiplomaticServiceEconomists, Finance, GovernmentEconomicsService, Sdip)

  def applicationRepository = new GeneralApplicationMongoRepository(ITDateTimeFactoryMock, mockAppConfig, mongo)

  def schemePreferencesRepository = new schemepreferences.SchemePreferencesMongoRepository(mongo)

  def assistanceDetailsRepository = new AssistanceDetailsMongoRepository(mongo)

  def phase1TestRepository = new Phase1TestMongoRepository(ITDateTimeFactoryMock, mongo)

  def phase2TestRepository = new Phase2TestMongoRepository(ITDateTimeFactoryMock, mongo)

  def phase3TestRepository = new Phase3TestMongoRepository(ITDateTimeFactoryMock, mongo)

  def phase1EvaluationRepo = new Phase1EvaluationMongoRepository(ITDateTimeFactoryMock, mongo)

  def phase2EvaluationRepo = new Phase2EvaluationMongoRepository(ITDateTimeFactoryMock, mongo)
  // TODO:fix needs MicroserviceAppConfig2

  //  lazy val appConfig = app.injector.instanceOf(classOf[MicroserviceAppConfig2])

  def phase3EvaluationRepo = new Phase3EvaluationMongoRepository(mockAppConfig, ITDateTimeFactoryMock, mongo)

  def phase1PassMarkSettingRepo = new Phase1PassMarkSettingsMongoRepository(mongo)
  def phase2PassMarkSettingRepo = new Phase2PassMarkSettingsMongoRepository(mongo)
  def phase3PassMarkSettingRepo = new Phase3PassMarkSettingsMongoRepository(mongo)

  lazy val schemeRepository = app.injector.instanceOf(classOf[SchemeRepository])

  //TODO:fix guice just inject the list siftableSchemeDefinitions instead of the whole repo
  //  def applicationSiftRepository = new ApplicationSiftMongoRepository(DateTimeFactory, siftableSchemeDefinitions)
  def applicationSiftRepository = new ApplicationSiftMongoRepository(ITDateTimeFactoryMock, schemeRepository, mongo, mockAppConfig)

  def assessmentCentreRepository = new AssessmentCentreMongoRepository(ITDateTimeFactoryMock, schemeRepository, mongo)

  def fsbRepository = new FsbMongoRepository(ITDateTimeFactoryMock, mongo)

  implicit val now: DateTime = DateTime.now().withZone(DateTimeZone.UTC)

  def selectedSchemes(schemeTypes: List[SchemeId]) = SelectedSchemes(schemeTypes, orderAgreed = true, eligible = true)

  // TODO: this should be removed when we strip out cubiks code
/*
  def insertApplicationWithPhase1TestResults(appId: String, sjq: Double, bq: Option[Double] = None, isGis: Boolean = false,
                                             applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                            )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(sjq), None, None, None)))
    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", bq, None, None, None)))
    val phase1Tests = if(isGis) List(sjqTest) else List(sjqTest, bqTest)
    insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(applicationRoute))
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE1_TESTS, applicationRoute, isGis,
      Phase1TestProfile(now, phase1Tests).activeTests, None, None, selectedSchemes(schemes.toList)
    )
  }*/

  def insertApplicationWithPhase1TestResults2(appId: String, t1Score: Double, t2Score: Option[Double] = None,
                                              t3Score: Option[Double] = None, t4Score: Double, isGis: Boolean = false,
                                              applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                             )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val test1 = firstPsiTest.copy(testResult = Some(PsiTestResult(tScore = t1Score, rawScore = 10.0, None)))
    val test2 = secondPsiTest.copy(testResult = Some(PsiTestResult(tScore = t2Score.getOrElse(0.0), rawScore = 10.0, None)))
    val test3 = thirdPsiTest.copy(testResult = Some(PsiTestResult(tScore = t3Score.getOrElse(0.0), rawScore = 10.0, None)))
    val test4 = fourthPsiTest.copy(testResult = Some(PsiTestResult(tScore = t4Score, rawScore = 10.0, None)))
    val phase1Tests = if(isGis) List(test1, test4) else List(test1, test2, test3, test4)
    insertApplication2(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(applicationRoute))
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE1_TESTS, applicationRoute, isGis,
      Phase1TestProfile(now, phase1Tests).activeTests, None, None, selectedSchemes(schemes.toList)
    )
  }

  // TODO: this should be removed when we strip out cubiks code
  /*
    def insertApplicationWithPhase1TestNotifiedResults(appId: String, results: List[SchemeEvaluationResult],
                                                       appRoute: ApplicationRoute = ApplicationRoute.Sdip
                                                      ): Future[Unit] = {
      val phase1PassMarkEvaluation = PassmarkEvaluation("", Some(""), results, "", Some(""))

      val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
      val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
      val phase1Tests = List(sjqTest, bqTest)
      insertApplication(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(appRoute))

      phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None).futureValue

      updateApplicationStatus(appId, ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
    }*/

  def insertApplicationWithPhase1TestNotifiedResults(appId: String, results: List[SchemeEvaluationResult],
                                                     appRoute: ApplicationRoute = ApplicationRoute.Sdip
                                                    ): Future[Unit] = {
    val phase1PassMarkEvaluation = PassmarkEvaluation(passmarkVersion = "", previousPhasePassMarkVersion = Some(""), results,
      resultVersion = "", previousPhaseResultVersion = Some(""))

    //    val sjqTest = firstTest.copy(cubiksUserId = 1, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    //    val bqTest = secondTest.copy(cubiksUserId = 2, testResult = Some(TestResult("Ready", "norm", Some(45.0), None, None, None)))
    //    val phase1Tests = List(sjqTest, bqTest)

    val p1Test1 = firstPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val p1Test2 = secondPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val p1Test3 = thirdPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val p1Test4 = fourthPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val phase1Tests = List(p1Test1, p1Test2, p1Test3, p1Test4)

    insertApplication2(appId, ApplicationStatus.PHASE1_TESTS, Some(phase1Tests), applicationRoute = Some(appRoute))

    phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None).futureValue

    updateApplicationStatus(appId, ApplicationStatus.PHASE1_TESTS_PASSED_NOTIFIED)
  }

  def insertApplicationWithPhase2TestResults(appId: String, t1Score: Double, t2Score: Double,
                                             phase1PassMarkEvaluation: PassmarkEvaluation,
                                             applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                            )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val p1Test1 = firstPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val p1Test2 = secondPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val p1Test3 = thirdPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))
    val p1Test4 = fourthPsiTest.copy(testResult = Some(PsiTestResult(tScore = 45.0, rawScore = 10.0, None)))

    val p2Test1 = firstP2PsiTest.copy(testResult = Some(PsiTestResult(t1Score, 10.0, None)))
    val p2Test2 = secondP2PsiTest.copy(testResult = Some(PsiTestResult(t2Score, 10.0, None)))
    val phase1Tests = List(p1Test1, p1Test2, p1Test3, p1Test4)
    val phase2Tests = List(p2Test1, p2Test2)
    insertApplication2(appId, ApplicationStatus.PHASE2_TESTS, Some(phase1Tests), Some(phase2Tests))
    phase1EvaluationRepo.savePassmarkEvaluation(appId, phase1PassMarkEvaluation, None).futureValue
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE2_TESTS, applicationRoute, isGis = false,
      List(p2Test1, p2Test2), None, Some(phase1PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  def insertApplicationWithPhase3TestResults(appId: String, videoInterviewScore: Option[Double],
                                             phase2PassMarkEvaluation: PassmarkEvaluation,
                                             applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                            )(schemes: SchemeId*): ApplicationReadyForEvaluation = {
    val launchPadTests = phase3TestWithResults(videoInterviewScore).activeTests
    insertApplication2(appId, ApplicationStatus.PHASE3_TESTS, None, None, Some(launchPadTests))
    phase2EvaluationRepo.savePassmarkEvaluation(appId, phase2PassMarkEvaluation, None).futureValue
    ApplicationReadyForEvaluation(appId, ApplicationStatus.PHASE3_TESTS, applicationRoute, isGis = false,
      Nil, launchPadTests.headOption, Some(phase2PassMarkEvaluation), selectedSchemes(schemes.toList))
  }

  def insertApplicationWithPhase3TestNotifiedResults(appId: String, results: List[SchemeEvaluationResult],
                                                     videoInterviewScore: Option[Double] = None,
                                                     applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                                    ): Future[Unit] = {
    val schemes = results.map(_.schemeId)
    val phase3PassMarkEvaluation = PassmarkEvaluation("", Some(""), results, "", Some(""))

    val launchPadTests = phase3TestWithResults(videoInterviewScore).activeTests
    insertApplication2(appId, ApplicationStatus.PHASE3_TESTS, None, None, Some(launchPadTests), applicationRoute = Some(applicationRoute),
      schemes = schemes
    )

    phase3EvaluationRepo.savePassmarkEvaluation(appId, phase3PassMarkEvaluation, None).futureValue

    updateApplicationStatus(appId, ApplicationStatus.PHASE3_TESTS_PASSED_NOTIFIED)
  }

  def insertApplicationWithSiftComplete(appId: String, results: Seq[SchemeEvaluationResult],
                                        applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                       ): Unit = {
    insertApplicationWithPhase3TestNotifiedResults(appId, results.toList, applicationRoute = applicationRoute).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_ENTERED).futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, ProgressStatuses.SIFT_COMPLETED).futureValue
  }

  def insertApplicationAtFsbWithStatus(appId: String, results: Seq[SchemeEvaluationResult], progressStatus: ProgressStatus,
                                       applicationRoute: ApplicationRoute = ApplicationRoute.Faststream
                                      ): Unit = {
    insertApplicationWithSiftComplete(appId, results, applicationRoute)
    FutureEx.traverseSerial(results) { result => fsbRepository.saveResult(appId, result) }.futureValue
    applicationRepository.addProgressStatusAndUpdateAppStatus(appId, progressStatus).futureValue
  }

  def updateApplicationStatus(appId: String, newStatus: ApplicationStatus): Future[Unit] = {
    val application = BSONDocument("applicationId" -> appId)
    val update = BSONDocument(
      "$set" -> BSONDocument(s"applicationStatus" -> newStatus)
    )
    applicationRepository.collection.update(ordered = false).one(application, update).map {_ => ()}
  }

  //TODO: this should be removed when we strip out cubiks code
  // scalastyle:off
  /*
  def insertApplication(appId: String, applicationStatus: ApplicationStatus, phase1Tests: Option[List[CubiksTest]] = None,
                        phase2Tests: Option[List[CubiksTest]] = None, phase3Tests: Option[List[LaunchpadTest]] = None,
                        isGis: Boolean = false, schemes: List[SchemeId] = List(SchemeId("Commercial")),
                        phase1Evaluation: Option[PassmarkEvaluation] = None,
                        phase2Evaluation: Option[PassmarkEvaluation] = None,
                        additionalProgressStatuses: List[(ProgressStatus, Boolean)] = List.empty,
                        applicationRoute: Option[ApplicationRoute] = Some(ApplicationRoute.Faststream)
                       ): Unit = {
    val gis = if (isGis) Some(true) else None
    applicationRepository.collection.insert(ordered = false).one(
      BSONDocument(
        "applicationId" -> appId,
        "userId" -> appId,
        "applicationStatus" -> applicationStatus,
        "progress-status" -> progressStatus(additionalProgressStatuses)
      ) ++ {
        if (applicationRoute.isDefined) {
          BSONDocument("applicationRoute" -> applicationRoute.get)
        } else {
          BSONDocument.empty
        }
      }
    ).futureValue

    val ad = AssistanceDetails(hasDisability = "No", disabilityImpact = None, disabilityCategories = None,
      otherDisabilityDescription = None, guaranteedInterview = gis, needsSupportForOnlineAssessment = Some(false),
      needsSupportForOnlineAssessmentDescription = None, needsSupportAtVenue = Some(false),
      needsSupportAtVenueDescription = None, needsSupportForPhoneInterview = None,
      needsSupportForPhoneInterviewDescription = None)
    assistanceDetailsRepository.update(appId, appId, ad).futureValue

    schemePreferencesRepository.save(appId, selectedSchemes(schemes)).futureValue
//    insertPhase1Tests(appId, phase1Tests, phase1Evaluation)
//    insertPhase2Tests(appId, phase2Tests, phase2Evaluation)
    phase3Tests.foreach { t =>
      phase3TestRepository.insertOrUpdateTestGroup(appId, Phase3TestGroup(now, t)).futureValue
      if(t.headOption.exists(_.callbacks.reviewed.nonEmpty)) {
        phase3TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
    applicationRepository.collection.update(ordered = false).one(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
  }*/
  // scalastyle:on

  // scalastyle:off
  def insertApplication2(appId: String, applicationStatus: ApplicationStatus, phase1Tests: Option[List[PsiTest]] = None,
                         phase2Tests: Option[List[PsiTest]] = None, phase3Tests: Option[List[LaunchpadTest]] = None,
                         isGis: Boolean = false, schemes: List[SchemeId] = List(SchemeId("Commercial")),
                         phase1Evaluation: Option[PassmarkEvaluation] = None,
                         phase2Evaluation: Option[PassmarkEvaluation] = None,
                         additionalProgressStatuses: List[(ProgressStatus, Boolean)] = List.empty,
                         applicationRoute: Option[ApplicationRoute] = Some(ApplicationRoute.Faststream)
                        ): Unit = {
    val gis = if (isGis) Some(true) else None
    applicationRepository.collection.insert(ordered = false).one(
      BSONDocument(
        "applicationId" -> appId,
        "userId" -> appId,
        "applicationStatus" -> applicationStatus,
        "progress-status" -> progressStatus(additionalProgressStatuses)
      ) ++ {
        if (applicationRoute.isDefined) {
          BSONDocument("applicationRoute" -> applicationRoute.get)
        } else {
          BSONDocument.empty
        }
      }
    ).futureValue

    val ad = AssistanceDetails(hasDisability = "No", disabilityImpact = None, disabilityCategories = None,
      otherDisabilityDescription = None, guaranteedInterview = gis, needsSupportForOnlineAssessment = Some(false),
      needsSupportForOnlineAssessmentDescription = None, needsSupportAtVenue = Some(false),
      needsSupportAtVenueDescription = None, needsSupportForPhoneInterview = None,
      needsSupportForPhoneInterviewDescription = None)
    assistanceDetailsRepository.update(appId, appId, ad).futureValue

    schemePreferencesRepository.save(appId, selectedSchemes(schemes)).futureValue
    insertPhase1Tests(appId, phase1Tests, phase1Evaluation)
    insertPhase2Tests2(appId, phase2Tests, phase2Evaluation)
    phase3Tests.foreach { t =>
      phase3TestRepository.insertOrUpdateTestGroup(appId, Phase3TestGroup(now, t)).futureValue
      if(t.headOption.exists(_.callbacks.reviewed.nonEmpty)) {
        phase3TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE3_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
    applicationRepository.collection.update(ordered = false).one(
      BSONDocument("applicationId" -> appId),
      BSONDocument("$set" -> BSONDocument("applicationStatus" -> applicationStatus))).futureValue
  }
  // scalastyle:on

  // TODO: cubiks this is cubiks specific
/*
  private def insertPhase2Tests(appId: String, phase2Tests: Option[List[CubiksTest]], phase2Evaluation: Option[PassmarkEvaluation]): Unit = {
    phase2Tests.foreach { t =>
      phase2TestRepository.insertOrUpdateTestGroup(appId, Phase2TestGroup(now, t, phase2Evaluation)).futureValue
      if (t.exists(_.testResult.isDefined)) {
        phase2TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
  }*/

  private def insertPhase2Tests2(appId: String, phase2Tests: Option[List[PsiTest]], phase2Evaluation: Option[PassmarkEvaluation]): Unit = {
    phase2Tests.foreach { t =>
      phase2TestRepository.insertOrUpdateTestGroup(appId, Phase2TestGroup(now, t, phase2Evaluation)).futureValue
      if (t.exists(_.testResult.isDefined)) {
        phase2TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE2_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
  }

  private def insertPhase1Tests(appId: String, phase1Tests: Option[List[PsiTest]], phase1Evaluation: Option[PassmarkEvaluation]): Unit = {
    phase1Tests.foreach { t =>
      phase1TestRepository.insertOrUpdateTestGroup(appId, Phase1TestProfile(now, t, phase1Evaluation)).futureValue
      if (t.exists(_.testResult.isDefined)) {
        phase1TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
  }

  // TODO: cubiks delete this method
  /*
  def insertPhase1Tests2(appId: String, phase1Tests: Option[List[PsiTest]], phase1Evaluation: Option[PassmarkEvaluation]): Unit = {
    phase1Tests.foreach { t =>
      phase1TestRepository.insertOrUpdateTestGroup(appId, Phase1TestProfile(now, t, phase1Evaluation)).futureValue
      if (t.exists(_.testResult.isDefined)) {
        phase1TestRepository.updateProgressStatus(appId, ProgressStatuses.PHASE1_TESTS_RESULTS_RECEIVED).futureValue
      }
    }
  }*/

  private def questionnaire() = {
    BSONDocument(
      "start_questionnaire" -> true,
      "diversity_questionnaire" -> true,
      "education_questionnaire" -> true,
      "occupation_questionnaire" -> true
    )
  }

  def progressStatus(args: List[(ProgressStatus, Boolean)] = List.empty): BSONDocument = {
    val baseDoc = BSONDocument(
      "personal-details" -> true,
      "in_progress" -> true,
      "scheme-preferences" -> true,
      "assistance-details" -> true,
      "questionnaire" -> questionnaire(),
      "preview" -> true,
      "submitted" -> true
    )

    args.foldLeft(baseDoc)((acc, v) => acc.++(v._1.toString -> v._2))
  }
}
//scalastyle:on
