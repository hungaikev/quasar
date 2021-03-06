/*
 * Copyright 2014–2018 SlamData Inc.
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

package quasar.yggdrasil

import quasar.blueeyes._, json._
import quasar.common.data.RValue
import quasar.pkg.tests._
import quasar.yggdrasil.table.CScanner

import cats.effect.IO

trait TestLib extends TableModule {
  def lookupScanner(namespace: List[String], name: String): CScanner
}

trait TableModuleTestSupport extends FNModule with TestLib {
  def fromJson(data: Stream[JValue], maxBlockSize: Option[Int] = None): Table
  def toJson(dataset: Table): IO[Stream[RValue]] = dataset.toJson.map(_.toStream)

  def fromSample(sampleData: SampleData, maxBlockSize: Option[Int] = None): Table =
    fromJson(sampleData.data.map(JValue.fromRValueRaw(_)), maxBlockSize)
}

trait TableModuleSpec extends SpecificationLike with ScalaCheck {
  import SampleData._

  def checkMappings(testSupport: TableModuleTestSupport) = {
    implicit val gen = sample(schema)
    prop { (sample: SampleData) =>
      val dataset = testSupport.fromSample(sample)
      testSupport.toJson(dataset).unsafeRunSync.toSet must_== sample.data.toSet
    }
  }
}
