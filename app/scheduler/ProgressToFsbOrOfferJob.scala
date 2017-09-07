/*
 * Copyright 2017 HM Revenue & Customs
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

package scheduler

import config.WaitingScheduledJobConfig
import play.api.Logger
import scheduler.ProgressToFsbOrOfferJobConfig.conf
import scheduler.clustering.SingleInstanceScheduledJob
import services.assessmentcentre.AssessmentCentreToFsbOrOfferProgressionService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

object ProgressToFsbOrOfferJob extends ProgressToFsbOrOfferJob {
  val assessmentCentreToFsbOrOfferService = AssessmentCentreToFsbOrOfferProgressionService
  val config = ProgressToFsbOrOfferJobConfig
}

trait ProgressToFsbOrOfferJob extends SingleInstanceScheduledJob[BasicJobConfig[WaitingScheduledJobConfig]] {
  val assessmentCentreToFsbOrOfferService: AssessmentCentreToFsbOrOfferProgressionService

  val batchSize: Int = conf.batchSize.getOrElse(1)

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc = HeaderCarrier()
    assessmentCentreToFsbOrOfferService.nextApplicationsForFsbOrJobOffer(batchSize).flatMap {
      case Nil => Future.successful(())
      case applications => assessmentCentreToFsbOrOfferService.progressApplicationsToFsbOrJobOffer(applications).map { result =>
        Logger.info(
          s"Progress to fsb or job offer complete - ${result.successes.size} processed successfully and ${result.failures.size} failed to update"
        )
      }
    }
  }
}

object ProgressToFsbOrOfferJobConfig extends BasicJobConfig[WaitingScheduledJobConfig](
  configPrefix = "scheduling.progress-to-fsb-or-offer-job",
  name = "ProgressToFsbOrOfferJob"
)
