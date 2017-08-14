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

package services.testdata.allocation

import model.exchange.testdata.CreateCandidateAllocationResponse
import model.testdata.CreateCandidateAllocationData
import play.api.mvc.RequestHeader
import services.allocation.CandidateAllocationService
import uk.gov.hmrc.play.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CandidateAllocationGenerator extends CandidateAllocationGenerator {
  override val candidateAllocationService: CandidateAllocationService = CandidateAllocationService
}

trait CandidateAllocationGenerator {

  val candidateAllocationService: CandidateAllocationService

  def generate(
    generationId: Int,
    createData: CreateCandidateAllocationData)(implicit hc: HeaderCarrier, rh: RequestHeader): Future[CreateCandidateAllocationResponse] = {
    candidateAllocationService.allocateCandidates(createData.toCandidateAllocations, true).map { d =>
      CreateCandidateAllocationResponse(generationId, createData.copy(version = d.version)) }
  }

}
