/*
 * Copyright 2014 Databricks
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
package com.databricks.spark.xml

import org.slf4j.LoggerFactory

import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.apache.spark.sql.sources.{BaseRelation, TableScan}
import org.apache.spark.sql.types._
import com.databricks.spark.xml.parsers.dom._
import com.databricks.spark.xml.util.{InferSchema, ParseModes}

case class XmlRelation protected[spark] (
    baseRDD: () => RDD[String],
    location: Option[String],
    parseMode: String,
    samplingRatio: Double,
    excludeAttributeFlag: Boolean,
    treatEmptyValuesAsNulls: Boolean,
    userSchema: StructType = null)(@transient val sqlContext: SQLContext)
  extends BaseRelation
  with TableScan {

  private val logger = LoggerFactory.getLogger(XmlRelation.getClass)

  // Parse mode flags
  if (!ParseModes.isValidMode(parseMode)) {
    logger.warn(s"$parseMode is not a valid parse mode. Using ${ParseModes.DEFAULT}.")
  }

  private val failFast = ParseModes.isFailFastMode(parseMode)
  private val dropMalformed = ParseModes.isDropMalformedMode(parseMode)
  private val permissive = ParseModes.isPermissiveMode(parseMode)

  override val schema: StructType = {
    Option(userSchema).getOrElse {
      InferSchema.infer(
        DomXmlPartialSchemaParser.parse(
          baseRDD(),
          samplingRatio,
          parseMode,
          excludeAttributeFlag,
          treatEmptyValuesAsNulls))
    }
  }

  override def buildScan: RDD[Row] = {
    DomXmlParser.parse(
      baseRDD(),
      schema,
      parseMode,
      excludeAttributeFlag,
      treatEmptyValuesAsNulls)
  }
}