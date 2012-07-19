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
package com.precog.daze

import org.specs2.mutable._

import com.precog.yggdrasil._
import com.precog.yggdrasil.memoization._

import scalaz._
import scalaz.effect._
import scalaz.iteratee._
import scalaz.std.list._
import Iteratee._

import com.precog.common.VectorCase
import com.precog.util.IdGen

class ReductionLibSpec extends Specification
    with Evaluator
    with TestConfigComponent 
    with ReductionLib 
    with StatsLib
    with InfixLib
    with MemoryDatasetConsumer { self =>
      
  import Function._
  
  import dag._
  import instructions._

  val testUID = "testUID"

  def testEval(graph: DepGraph): Set[SEvent] = withContext { ctx =>
    consumeEval(testUID, graph, ctx) match {
      case Success(results) => results
      case Failure(error) => throw error
    }
  }

  "reduce homogeneous sets" >> {
    "count" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Count,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(5)
    }
    
    "geometricMean" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, GeometricMean,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d.toDouble
      }
      
      result2 must contain(13.822064739747386)
    }
    
    "mean" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Mean,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(29)
    }
    
    "max" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Max,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(77)
    }
    
    "min" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Min,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(1)
    }
    
    "standard deviation" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, StdDev,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d.toDouble
      }
      
      result2 must contain(27.575351312358652)
    }
    
    "sum" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Sum,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d.toInt
      }
      
      result2 must contain(145)
    }

    "sumSq" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, SumSq,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d.toDouble
      }
      
      result2 must contain(8007)
    }

    "variance" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Variance,
        dag.LoadLocal(line, Root(line, PushString("/hom/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d.toDouble
      }
      
      result2 must contain(760.4)
    }

  }

  "reduce heterogeneous sets" >> {
    "count" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Count,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(10)
    }    
    
    "geometricMean" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, GeometricMean,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(13.822064739747386)
    }
    
    "mean" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Mean,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(29)
    }
    
    "max" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Max,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(77)
    }
    
    "min" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Min,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(1)
    }
    
    "standard deviation" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, StdDev,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d.toDouble
      }
      
      result2 must contain(27.575351312358652)
    }
    
    "sum" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Sum,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(145)
    }      

    "sumSq" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, SumSq,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(8007)
    } 

    "variance" >> {
      val line = Line(0, "")
      
      val input = dag.Reduce(line, Variance,
        dag.LoadLocal(line, Root(line, PushString("/het/numbers"))))
        
      val result = testEval(input)
      
      result must haveSize(1)
      
      val result2 = result collect {
        case (VectorCase(), SDecimal(d)) => d
      }
      
      result2 must contain(760.4)
    }
  }
}
