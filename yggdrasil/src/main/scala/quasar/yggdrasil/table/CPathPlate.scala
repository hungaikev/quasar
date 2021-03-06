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
package table

import quasar.common.{CPathField, CPathIndex, CPathMeta, CPathNode}

import tectonic.{DelegatingPlate, Plate, Signal}

private[table] trait CPathPlate[A] extends Plate[A] {
  protected var cursor: List[CPathNode] = Nil
  protected var nextIndex: List[Int] = 0 :: Nil

  abstract override def nestMap(pathComponent: CharSequence): Signal = {
    cursor ::= CPathField(pathComponent.toString)
    nextIndex ::= 0
    super.nestMap(pathComponent)
  }

  abstract override def nestArr(): Signal = {
    val idx = incrementIndex()
    nextIndex ::= 0

    cursor ::= CPathIndex(idx)
    super.nestArr()
  }

  abstract override def nestMeta(pathComponent: CharSequence): Signal = {
    cursor ::= CPathMeta(pathComponent.toString)
    nextIndex ::= 0
    super.nestMeta(pathComponent)
  }

  abstract override def unnest(): Signal = {
    nextIndex = nextIndex.tail
    cursor = cursor.tail
    super.unnest()
  }

  abstract override def finishRow(): Unit = {
    nextIndex = 0 :: Nil
    super.finishRow()
  }

  protected final def incrementIndex(): Int = {
    val idx :: tail = nextIndex
    nextIndex = (idx + 1) :: tail

    idx
  }
}

object CPathPlate {
  // this is kind of a hack for now. we should probably think about the meaning of enclosure()
  def wrapWithSoundEnclosure[A](plate: Plate[A]): Plate[A] =
    new DelegatingPlate(plate) with CPathPlate[A]
}
