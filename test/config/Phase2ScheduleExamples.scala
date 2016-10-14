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

package config

object Phase2ScheduleExamples {
  val DaroShedule = Phase2Schedule("daro", 1, 2, 3)
  val IradShedule = Phase2Schedule("irad", 4, 5, 6)
  val WardShedule = Phase2Schedule("ward", 7, 8, 9)
}