/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package com.cloudera.workshop.solutions.classifier

// $example on$

import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature._
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.sql.types._

// $example off$
import org.apache.spark.sql.SparkSession


object SpamClassification {

  def main(args: Array[String]) {

    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val spark = SparkSession
      .builder
      .appName("SpamExample")
      .master("local[4]")
      .getOrCreate()

    val customSchema = StructType(Array(
      StructField("spam", StringType, true),
      StructField("message", StringType, true)

    ))

    // read in the data
    val ds = spark.read.option("inferSchema", "false").option("delimiter", "\t").schema(customSchema).csv("data/SMSSpamCollection.tsv")
    ds.printSchema()

    ds.show(8)

    // string indexer
    val indexer = new StringIndexer()
      .setInputCol("spam")
      .setOutputCol("label")
    val indexed = indexer.fit(ds).transform(ds)

    indexed.show(8)

    // tokenize
    val tokenizer = new Tokenizer().setInputCol("message").setOutputCol("tokens")
    val tokdata = tokenizer.transform(indexed)

    tokdata.show(8)

    // tf
    val hashingTF = new HashingTF()
      .setInputCol("tokens").setOutputCol("tf")//.setNumFeatures(20)
    val tfdata = hashingTF.transform(tokdata)

    tfdata.show(8)

    // idf
    val idf = new IDF().setInputCol("tf").setOutputCol("idf")
    val idfModel = idf.fit(tfdata)
    val idfdata = idfModel.transform(tfdata)

    idfdata.show(8)

    // assembler
    val assembler = new VectorAssembler()
      .setInputCols(Array("idf"))
      .setOutputCol("features")
    val assemdata = assembler.transform(idfdata)

    assemdata.show(8)

    // split
    val Array(trainingData, testData) = assemdata.randomSplit(Array(0.7, 0.3), 1000)

    // lr
    val lr = new LogisticRegression()
      .setLabelCol("label")
      .setFeaturesCol("features")

    // build the model
    val lrModel = lr.fit(trainingData)

    // predict
    val predict = lrModel.transform(testData)
    predict.show(10)

    // evaluate
    val evaluator = new BinaryClassificationEvaluator()
      .setRawPredictionCol("prediction")

    val accuracy = evaluator.evaluate(predict)
    println("Test Error = " + (1.0 - accuracy))

    import spark.implicits._

    // compute confusion matrix
    val predictionsAndLabels = predict.select("prediction", "label")
      .map(row => (row.getDouble(0), row.getDouble(1)))

    val metrics = new MulticlassMetrics(predictionsAndLabels.rdd)
    println("\nConfusion matrix:")
    println(metrics.confusionMatrix)

    spark.stop()
  }
}
// scalastyle:on println

