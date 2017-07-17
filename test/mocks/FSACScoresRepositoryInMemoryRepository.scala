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

package mocks

import model.FSACScores.FSACAllExercisesScoresAndFeedback
import model.UniqueIdentifier
import repositories.FSACScoresRepository

import scala.concurrent.Future

object FSACScoresRepositoryInMemoryRepository extends FSACScoresRepositoryInMemoryRepository

class FSACScoresRepositoryInMemoryRepository extends FSACScoresRepository {

  def save(scores: FSACAllExercisesScoresAndFeedback): Future[Unit] = ???

  def find(applicationId: UniqueIdentifier): Future[Option[FSACAllExercisesScoresAndFeedback]] = ???

  def findAll: Future[List[FSACAllExercisesScoresAndFeedback]] = ???

  //def nextCandidateScoresReadyForEvaluation: Future[Option[FSACAllExercisesScoresAndFeedback]] = ???
}
