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

package scheduler.onlinetesting

import config.ScheduledJobConfig
import model.{ EmptyRequestHeader, Phase1FirstReminder, Phase2FirstReminder, Phase3FirstReminder, ReminderNotice }
import scheduler.BasicJobConfig
import scheduler.clustering.SingleInstanceScheduledJob
import services.onlinetesting.OnlineTestService
import services.onlinetesting.phase1.Phase1TestService
import services.onlinetesting.phase2.Phase2TestService
import services.onlinetesting.phase3.Phase3TestService

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

object FirstPhase1ReminderExpiringTestJob extends FirstReminderExpiringTestJob {
  override val service = Phase1TestService
  override val reminderNotice: ReminderNotice = Phase1FirstReminder
  val config = FirstPhase1ReminderExpiringTestJobConfig
}

object FirstPhase2ReminderExpiringTestJob extends FirstReminderExpiringTestJob {
  override val service = Phase2TestService
  override val reminderNotice: ReminderNotice = Phase2FirstReminder
  val config = FirstPhase2ReminderExpiringTestJobConfig
}

object FirstPhase3ReminderExpiringTestJob extends FirstReminderExpiringTestJob {
  override val service = Phase3TestService
  override val reminderNotice: ReminderNotice = Phase3FirstReminder
  val config = FirstPhase3ReminderExpiringTestJobConfig
}

trait FirstReminderExpiringTestJob extends SingleInstanceScheduledJob[BasicJobConfig[ScheduledJobConfig]] {
  val service: OnlineTestService
  val reminderNotice: ReminderNotice

  def tryExecute()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val rh = EmptyRequestHeader
    implicit val hc = HeaderCarrier()
    service.processNextTestForReminder(reminderNotice)
  }
}

object FirstPhase1ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.first-phase1-reminder-expiring-test-job",
  name = "FirstPhase1ReminderExpiringTestJob"
)

object FirstPhase2ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.first-phase2-reminder-expiring-test-job",
  name = "FirstPhase2ReminderExpiringTestJob"
)

object FirstPhase3ReminderExpiringTestJobConfig extends BasicJobConfig[ScheduledJobConfig](
  configPrefix = "scheduling.online-testing.first-phase3-reminder-expiring-test-job",
  name = "FirstPhase3ReminderExpiringTestJob"
)
