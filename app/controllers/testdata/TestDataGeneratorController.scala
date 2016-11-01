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

package controllers.testdata

import java.io.File

import com.typesafe.config.ConfigFactory
import connectors.AuthProviderClient
import connectors.testdata.ExchangeObjects.Implicits._
import controllers.testdata.TestDataGeneratorController.InvalidPostCodeFormatException
import model.ApplicationStatus._
import model.EvaluationResults.Result
import model.Exceptions.EmailTakenException
import model.{ ApplicationRoute, ProgressStatuses }
import model.command.testdata.CreateCandidateInStatusRequest
import model.command.testdata.CreateCandidateInStatusRequest._
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import play.api.Play
import play.api.libs.json.{ JsObject, JsString, Json }
import play.api.mvc.{ Action, RequestHeader }
import services.testdata._
import services.testdata.faker.DataFaker.Random
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object TestDataGeneratorController extends TestDataGeneratorController {

  case class InvalidPostCodeFormatException(message: String) extends Exception(message)

}

trait TestDataGeneratorController extends BaseController {

  def ping = Action.async { implicit request =>
    Future.successful(Ok("OK"))
  }

  def clearDatabase() = Action.async { implicit request =>
    TestDataGeneratorService.clearDatabase().map { _ =>
      Ok(Json.parse("""{"message": "success"}"""))
    }
  }

  def createAdminUsers(numberToGenerate: Int, emailPrefix: Option[String], role: String) = Action.async { implicit request =>
    try {
      TestDataGeneratorService.createAdminUsers(numberToGenerate, emailPrefix, AuthProviderClient.getRole(role)).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case e: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
          JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }

  val secretsFileCubiksUrlKey = "microservice.services.cubiks-gateway.testdata.url"
  lazy val cubiksUrlFromConfig = Play.current.configuration.getString(secretsFileCubiksUrlKey)
    .getOrElse(fetchSecretConfigKeyFromFile("cubiks.url"))

  private def fetchSecretConfigKeyFromFile(key: String): String = {
    val path = System.getProperty("user.home") + "/.csr/.secrets"
    val testConfig = ConfigFactory.parseFile(new File(path))
    if (testConfig.isEmpty) {
      throw new IllegalArgumentException(s"No key found at '$secretsFileCubiksUrlKey' and .secrets file does not exist.")
    } else {
      testConfig.getString(s"testdata.$key")
    }
  }

  // scalastyle:off parameter.number
  // scalastyle:off method.length
  def createEdipCandidatesInStatusGET(applicationStatus: String,
                                  progressStatus: Option[String],
                                  numberToGenerate: Int,
                                  setGis: Boolean,
                                  emailPrefix: Option[String],
                                  firstName: Option[String],
                                  lastName: Option[String],
                                  preferredName: Option[String],
                                  isCivilServant: Option[Boolean],
                                  hasDegree: Option[Boolean],
                                  region: Option[String],
                                  loc1scheme1EvaluationResult: Option[String],
                                  loc1scheme2EvaluationResult: Option[String],
                                  previousStatus: Option[String],
                                  confirmedAllocation: Boolean,
                                  dateOfBirth: Option[String],
                                  postCode: Option[String],
                                  country: Option[String],
                                  phase1StartTime: Option[String],
                                  phase1ExpiryTime: Option[String],
                                  tscore: Option[Double]
                                 ) = Action.async { implicit request =>
    val initialConfig = GeneratorConfig(
      emailPrefix = emailPrefix,
      setGis = setGis,
      cubiksUrl = cubiksUrlFromConfig,
      firstName = firstName,
      lastName = lastName,
      preferredName = preferredName,
      isCivilServant = isCivilServant,
      hasDegree = hasDegree,
      region = region,
      loc1scheme1Passmark = loc1scheme1EvaluationResult.map(Result(_)),
      loc1scheme2Passmark = loc1scheme2EvaluationResult.map(Result(_)),
      previousStatus = previousStatus,
      confirmedAllocation = withName(applicationStatus) match {
        case ALLOCATION_UNCONFIRMED => false
        case ALLOCATION_CONFIRMED => true
        case _ => confirmedAllocation
      },
      dob = dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))),
      postCode = postCode.map(pc => validatePostcode(pc)),
      country = country,
      phase1StartTime = phase1StartTime.map(x => DateTime.parse(x)),
      phase1ExpiryTime = phase1ExpiryTime.map(x => DateTime.parse(x)),
      tscore = tscore,
      applicationRoute = ApplicationRoute.Edip
    )
    createCandidateInStatus(initialConfig, applicationStatus, progressStatus, numberToGenerate)
  }

  // scalastyle:off parameter.number
  // scalastyle:off method.length
  def createFaststreamCandidatesInStatusGET(applicationStatus: String,
                                  progressStatus: Option[String],
                                  numberToGenerate: Int,
                                  setGis: Boolean,
                                  emailPrefix: Option[String],
                                  firstName: Option[String],
                                  lastName: Option[String],
                                  preferredName: Option[String],
                                  isCivilServant: Option[Boolean],
                                  hasDegree: Option[Boolean],
                                  region: Option[String],
                                  loc1scheme1EvaluationResult: Option[String],
                                  loc1scheme2EvaluationResult: Option[String],
                                  previousStatus: Option[String],
                                  confirmedAllocation: Boolean,
                                  dateOfBirth: Option[String],
                                  postCode: Option[String],
                                  country: Option[String],
                                  phase1StartTime: Option[String],
                                  phase1ExpiryTime: Option[String],
                                  tscore: Option[Double]
                                 ) = Action.async { implicit request =>
    val initialConfig = GeneratorConfig(
      emailPrefix = emailPrefix,
      setGis = setGis,
      cubiksUrl = cubiksUrlFromConfig,
      firstName = firstName,
      lastName = lastName,
      preferredName = preferredName,
      isCivilServant = isCivilServant,
      hasDegree = hasDegree,
      region = region,
      loc1scheme1Passmark = loc1scheme1EvaluationResult.map(Result(_)),
      loc1scheme2Passmark = loc1scheme2EvaluationResult.map(Result(_)),
      previousStatus = previousStatus,
      confirmedAllocation = withName(applicationStatus) match {
        case ALLOCATION_UNCONFIRMED => false
        case ALLOCATION_CONFIRMED => true
        case _ => confirmedAllocation
      },
      dob = dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))),
      postCode = postCode.map(pc => validatePostcode(pc)),
      country = country,
      phase1StartTime = phase1StartTime.map(x => DateTime.parse(x)),
      phase1ExpiryTime = phase1ExpiryTime.map(x => DateTime.parse(x)),
      tscore = tscore,
      applicationRoute = ApplicationRoute.Faststream
    )
    createCandidateInStatus(initialConfig, applicationStatus, progressStatus, numberToGenerate)
  }

  // scalastyle:on

  def createCandidatesInStatusPOST() = Action.async(parse.json) { implicit request =>
    withJsonBody[CreateCandidateInStatusRequest] { body =>
      createCandidateInStatus(
        requestToGeneratorConfig(body),
        body.applicationStatus,
        body.progressStatus,
        body.numberToGenerate)
    }
  }

  private def createCandidateInStatus(config: GeneratorConfig,
                                      applicationStatus: String,
                                      progressStatus: Option[String],
                                      numberToGenerate: Int)
                                     (implicit hc: HeaderCarrier, rh: RequestHeader)
  = {
    try {
      TestDataGeneratorService.createCandidatesInSpecificStatus(
        numberToGenerate,
        StatusGeneratorFactory.getGenerator(withName(applicationStatus),
          progressStatus.map(ps => ProgressStatuses.nameToProgressStatus(ps)),
          config),
        config
      ).map { candidates =>
        Ok(Json.toJson(candidates))
      }
    } catch {
      case e: EmailTakenException => Future.successful(Conflict(JsObject(List(("message",
          JsString("Email has been already taken. Try with another one by changing the emailPrefix parameter"))))))
    }
  }

  private def validatePostcode(postcode: String) = {
    // putting this on multiple lines won't make this regex any clearer
    // scalastyle:off line.size.limit
    val postcodePattern =
      """^(?i)(GIR 0AA)|((([A-Z][0-9][0-9]?)|(([A-Z][A-HJ-Y][0-9][0-9]?)|(([A-Z][0-9][A-Z])|([A-Z][A-HJ-Y][0-9]?[A-Z])))) ?[0-9][A-Z]{2})$""".r
    // scalastyle:on line.size.limit

    postcodePattern.pattern.matcher(postcode).matches match {
      case true => postcode
      case false if postcode.isEmpty => throw InvalidPostCodeFormatException(s"Postcode $postcode is empty")
      case false => throw InvalidPostCodeFormatException(s"Postcode $postcode is in an invalid format")
    }
  }

  private def requestToGeneratorConfig(request: CreateCandidateInStatusRequest) = {
    GeneratorConfig(
      emailPrefix = request.emailPrefix,
      hasDisability = request.assistanceDetails.flatMap { ad => ad.hasDisability },
      hasDisabilityDescription = request.assistanceDetails.flatMap { ad => ad.hasDisabilityDescription },
      setGis = request.assistanceDetails.flatMap { ad => ad.setGis }.getOrElse(false),
      onlineAdjustments = request.assistanceDetails.flatMap { ad => ad.onlineAdjustments },
      onlineAdjustmentsDescription = request.assistanceDetails.flatMap { ad => ad.onlineAdjustmentsDescription },
      assessmentCentreAdjustments = request.assistanceDetails.flatMap { ad => ad.assessmentCentreAdjustments },
      assessmentCentreAdjustmentsDescription = request.assistanceDetails.flatMap { ad => ad.assessmentCentreAdjustmentsDescription },
      firstName = request.firstName,
      lastName = request.lastName,
      preferredName = request.preferredName,
      isCivilServant = request.isCivilServant,
      hasDegree = request.hasDegree,
      region = request.region,
      loc1scheme1Passmark = request.loc1scheme1EvaluationResult.map(Result(_)),
      loc1scheme2Passmark = request.loc1scheme2EvaluationResult.map(Result(_)),
      previousStatus = request.previousApplicationStatus,
      confirmedAllocation = request.confirmedAllocation.getOrElse(false),
      dob = request.dateOfBirth.map(x => LocalDate.parse(x, DateTimeFormat.forPattern("yyyy-MM-dd"))), postCode = request.postCode,
      phase1StartTime = request.phase1StartTime.map(x => DateTime.parse(x)),
      phase1ExpiryTime = request.phase1ExpiryTime.map(x => DateTime.parse(x)),
      tscore = request.tscore,
      cubiksUrl = cubiksUrlFromConfig,
      country = request.country
    )
  }
}
