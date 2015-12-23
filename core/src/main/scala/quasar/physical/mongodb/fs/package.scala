/*
 * Copyright 2014 - 2015 SlamData Inc.
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

package quasar
package physical
package mongodb

import quasar.Predef._
import quasar.fp._
import quasar.fs.{Path => _, _}

import com.mongodb.async.client.MongoClient
import pathy._, Path._
import scalaz.~>
import scalaz.std.option._
import scalaz.syntax.std.option._
import scalaz.syntax.foldable._
import scalaz.syntax.monadPlus._
import scalaz.concurrent.Task

package object fs {
  type WFTask[A] = WorkflowExecErrT[Task, A]

  final case class DefaultDb(run: String) extends scala.AnyVal

  object DefaultDb {
    def fromPath[T](path: Path[Abs, T, Sandboxed]): Option[DefaultDb] =
      flatten(none, none, none, _.some, κ(none), path)
        .toIList.unite.headOption map (DefaultDb(_))
  }

  final case class TmpPrefix(run: String) extends scala.AnyVal

  def mongoDbFileSystem(
    client: MongoClient,
    defDb: DefaultDb
  ): EnvErr2T[Task, FileSystem ~> WFTask] =
    for {
      qfile  <- queryfile.run(client)(queryfile.interpret)
      rfile  <- readfile.run(client).liftM[EnvErr2T]
      wfile  <- writefile.run(client).liftM[EnvErr2T]
      mfile  <- managefile.run(client, defDb).liftM[EnvErr2T]
      liftWF =  liftMT[Task, WorkflowExecErrT]
    } yield {
      interpretFileSystem[WFTask](
        qfile,
        liftWF compose rfile compose readfile.interpret,
        liftWF compose wfile compose writefile.interpret,
        liftWF compose mfile compose managefile.interpret)
    }
}