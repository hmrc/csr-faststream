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

package services.testdata

import java.util.UUID

import connectors.testdata.ExchangeObjects.OnlineTestProfileResponse
import model.ApplicationStatuses
import model.OnlineTestCommands.{Phase1Test, Phase1TestProfile}
import org.joda.time.DateTime
import repositories._
import repositories.application.OnlineTestRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

object OnlineTestExpiredStatusGenerator extends OnlineTestExpiredStatusGenerator {
  // Theoretically previous status generator should be ONLINE_TEST_INVITED, ONLINE_TEST_STARTED, etc
  // However we need to generate an online test in the past, so that it is expired already at the moment of creation
  // So that we have consistent data.
  override val previousStatusGenerator = SubmittedStatusGenerator
  override val otRepository = onlineTestRepository
}

trait OnlineTestExpiredStatusGenerator extends ConstructiveGenerator {
  val otRepository: OnlineTestRepository

  def generate(generationId: Int, generatorConfig: GeneratorConfig)(implicit hc: HeaderCarrier) = {

    val phase1TestProfile = Phase1TestProfile(
      expirationDate = DateTime.now(),
      tests = List(Phase1Test(
        cubiksUserId = 117344,
        token = UUID.randomUUID().toString,
        testUrl = generatorConfig.cubiksUrl,
        invitationDate = DateTime.now().minusDays(7),
        participantScheduleId = 149245,
        scheduleId = 12345,
        usedForResults = true
      ))
    )

    for {
      candidateInPreviousStatus <- previousStatusGenerator.generate(generationId, generatorConfig)
      // TODO: in faststream
      //_ <- otRepository.storeOnlineTestProfileAndUpdateStatusToInvite(candidateInPreviousStatus.applicationId.get, onlineTestProfile)
      // - <- otRepository.updateStatus(candidateInPreviousStatus.userId, ApplicationStatuses.OnlineTestExpired)
    } yield {
      candidateInPreviousStatus.copy(onlineTestProfile = Some(
        OnlineTestProfileResponse(phase1TestProfile.tests.head.cubiksUserId, phase1TestProfile.tests.head.token,
        phase1TestProfile.tests.head.testUrl)
      ))
    }
  }
}
