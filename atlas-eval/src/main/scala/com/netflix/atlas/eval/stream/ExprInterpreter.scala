/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.atlas.eval.stream

import akka.http.scaladsl.model.Uri
import com.netflix.atlas.core.model.CustomVocabulary
import com.netflix.atlas.core.model.DataExpr
import com.netflix.atlas.core.model.ModelExtractors
import com.netflix.atlas.core.model.StyleExpr
import com.netflix.atlas.core.stacklang.Interpreter
import com.netflix.atlas.eval.stream.Evaluator.DataSource
import com.netflix.atlas.eval.stream.Evaluator.DataSources
import com.netflix.atlas.eval.util.HostRewriter
import com.typesafe.config.Config

private[stream] class ExprInterpreter(config: Config) {

  private val interpreter = Interpreter(new CustomVocabulary(config).allWords)

  private val hostRewriter = new HostRewriter(config.getConfig("atlas.eval.host-rewrite"))

  def eval(expr: String): List[StyleExpr] = {
    interpreter.execute(expr).stack.map {
      case ModelExtractors.PresentationType(t) => t
      case v                                   => throw new MatchError(v)
    }
  }

  def eval(uri: Uri): List[StyleExpr] = {
    val expr = uri.query().get("q").getOrElse {
      throw new IllegalArgumentException(s"missing required URI parameter `q`: $uri")
    }

    // Check that data expressions are supported. The streaming path doesn't support
    // time shifts.
    val results = eval(expr).flatMap(_.perOffset)
    results.foreach { result =>
      result.expr.dataExprs.foreach { dataExpr =>
        if (!dataExpr.offset.isZero) {
          throw new IllegalArgumentException(
            s":offset not supported for streaming evaluation [[$dataExpr]]"
          )
        }
      }
    }

    // Perform host rewrites based on the Atlas hostname
    val host = uri.authority.host.toString()
    hostRewriter.rewrite(host, results)
  }

  def dataExprMap(ds: DataSources): Map[DataExpr, List[DataSource]] = {
    import scala.jdk.CollectionConverters._
    ds.getSources.asScala.toList
      .flatMap { s =>
        val exprs = eval(Uri(s.getUri)).flatMap(_.expr.dataExprs).distinct
        exprs.map(_ -> s)
      }
      .groupBy(_._1)
      .map {
        case (expr, vs) => expr -> vs.map(_._2)
      }
  }
}
