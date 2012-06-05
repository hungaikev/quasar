/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog.common
package security

import blueeyes._
import blueeyes.bkka.AkkaDefaults
import blueeyes.json.JsonAST._
import blueeyes.persistence.mongo._
import blueeyes.persistence.cache._
import blueeyes.json.xschema.{ ValidatedExtraction, Extractor, Decomposer }
import blueeyes.json.xschema.DefaultSerialization._
import blueeyes.json.xschema.Extractor._

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.dispatch.Future
import akka.dispatch.ExecutionContext

import org.joda.time.DateTime

import java.util.concurrent.TimeUnit._

import com.weiglewilczek.slf4s.Logging

import org.streum.configrity.Configuration

import scala.collection.mutable

import scalaz._
import scalaz.Validation._
import Scalaz._


class InMemoryTokenManager(tokens: mutable.Map[TokenID, Token] = mutable.Map.empty, 
                           grants: mutable.Map[GrantID, Grant] = mutable.Map.empty)(implicit val execContext: ExecutionContext) extends TokenManager {

  private val deletedTokens = mutable.Map.empty[TokenID, Token]
  private val deletedGrants = mutable.Map.empty[GrantID, Grant]

  def newToken(name: String, grants: Set[GrantID]) = Future {
    val newToken = Token(newTokenID, name, grants)
    tokens.put(newToken.tid, newToken)
    newToken
  }

  def newGrant(issuer: Option[GrantID], perm: Permission) = Future {
    val newGrant = Grant(newGrantID, issuer, perm)
    grants.put(newGrant.gid, newGrant)
    newGrant
  }

  def listTokens() = Future[Seq[Token]] { tokens.values.toList }
  def listGrants() = Future[Seq[Grant]] { grants.values.toList }

  def findToken(tid: TokenID) = Future { tokens.get(tid) }

  def findGrant(gid: GrantID) = Future { grants.get(gid) }
  def findGrantChildren(gid: GrantID) = Future[Set[Grant]] {
    grants.values.toSet.filter{ _.issuer.map { _ == gid }.getOrElse(false) }
  }

  def addGrants(tid: TokenID, add: Set[GrantID]) = Future {
    tokens.get(tid).map { t =>
      val updated = t.addGrants(add)
      tokens.put(tid, updated)
      updated
    }
  }

  def listDeletedTokens() = Future[Seq[Token]] { deletedTokens.values.toList }
  def listDeletedGrants() = Future[Seq[Grant]] { deletedGrants.values.toList }

  def findDeletedToken(tid: TokenID) = Future { deletedTokens.get(tid) }

  def findDeletedGrant(gid: GrantID) = Future { deletedGrants.get(gid) }
  def findDeletedGrantChildren(gid: GrantID) = Future[Set[Grant]] {
    deletedGrants.values.toSet.filter{ _.issuer.map { _ == gid }.getOrElse(false) }
  }

 def removeGrants(tid: TokenID, remove: Set[GrantID]) = Future {
    tokens.get(tid).map { t =>
      val updated = t.removeGrants(remove)
      tokens.put(tid, updated)
      updated
    }
  }

  def deleteToken(tid: TokenID) = Future {
    tokens.get(tid).flatMap { t =>
      deletedTokens.put(tid, t)
      tokens.remove(tid)
    }
  }
  def deleteGrant(gid: GrantID) = Future { grants.remove(gid) match {
    case Some(x) =>
      deletedGrants.put(gid, x)
      Set(x)
    case _       =>
      Set.empty
  }}

  def close() = Future(())
}

object TestTokenManager extends AkkaDefaults {
  val rootUID = "root"

  val testUID = "unittest"
  val usageUID = "usage"

  val cust1UID = "user1"
  val cust2UID = "user2"

  val expiredUID = "expired"

  def standardAccountPerms(prefix: String, issuerPrefix: Option[String], path: String, owner: TokenID, expiration: Option[DateTime]): List[Grant] = {
    val config = List[(String, (Path, Option[DateTime]) => Permission)](
      ("write", WritePermission(_, _)),
      ("owner", OwnerPermission(_, _)),
      ("read", ReadPermission(_, owner, _)),
      ("reduce", ReducePermission(_, owner, _)),
      ("modify", ModifyPermission(_, owner, _)),
      ("transform", TransformPermission(_, owner, _))
    )
    
    config map {
      case (suffix, f) => Grant(prefix + "_" + suffix, issuerPrefix.map { _ + "_" + suffix }, f(Path(path), expiration))
    }
  }

  def publishPathPerms(prefix: String, issuerPrefix: Option[String], path: String, owner: TokenID, expiration: Option[DateTime]) = {
    val config = List[(String, (Path, Option[DateTime]) => Permission)](
      ("read", ReadPermission(_, owner, _)),
      ("reduce", ReducePermission(_, owner, _))
    )
    
    config map {
      case (suffix, f) => Grant(prefix + "_" + suffix, issuerPrefix.map { _ + "_" + suffix }, f(Path(path), expiration))
    }
  }

  val grantList = List(
    standardAccountPerms("root", None, "/", "root", None),
    standardAccountPerms("unittest", Some("root"), "/unittest/", "unittest", None),
    standardAccountPerms("usage", Some("root"), "/__usage_tracking__", "usage_tracking", None),
    standardAccountPerms("user1", Some("root"), "/user1", "user1", None),
    standardAccountPerms("user2", Some("root"), "/user2", "user2", None),
    standardAccountPerms("expired", Some("root"), "/expired", "expired", Some(new DateTime().minusYears(1000))),
    publishPathPerms("public", Some("root"), "/public", "public", None),
    publishPathPerms("opublic", Some("root"), "/opublic", "opublic", None)
  )

  val grants: mutable.Map[GrantID, Grant] = grantList.flatten.map { g => (g.gid -> g) }(collection.breakOut)

  val tokens: mutable.Map[TokenID, Token] = List[Token](
    Token("root", "root", grantList(0).map { _.gid }(collection.breakOut)),
    Token("unittest", "unittest", grantList(1).map { _.gid }(collection.breakOut)),
    Token("usage", "usage", grantList(2).map { _.gid }(collection.breakOut)),
    Token("user1", "user1", (grantList(3) ++ grantList(6)).map{ _.gid}(collection.breakOut)),
    Token("user2", "user2", (grantList(4) ++ grantList(6)).map{ _.gid}(collection.breakOut)),
    Token("expired", "expired", (grantList(5) ++ grantList(6)).map{ _.gid}(collection.breakOut))
  ).map { t => (t.tid -> t) }(collection.breakOut)

  def testTokenManager(): TokenManager = new InMemoryTokenManager(tokens, grants)

}

// vim: set ts=4 sw=4 et:
