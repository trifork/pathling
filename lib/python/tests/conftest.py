#  Copyright 2023 Commonwealth Scientific and Industrial Research
#  Organisation (CSIRO) ABN 41 687 119 230.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


import logging
import os
from tempfile import TemporaryDirectory

from pyspark.sql import SparkSession
from pytest import fixture

from pathling import PathlingContext
from pathling.etc import find_jar as find_pathling_jar

HERE = os.path.abspath(os.path.dirname(__file__))
LIB_API_DIR = os.path.normpath(os.path.join(HERE, "..", "..", "..", "library-api"))
TEST_DATA_DIR = os.path.join(LIB_API_DIR, "src", "test", "resources", "test-data")


@fixture(scope="module")
def test_data_dir():
    return TEST_DATA_DIR


@fixture(scope="module", autouse=True)
def temp_dir():
    temp_dir = TemporaryDirectory()
    yield temp_dir.name
    temp_dir.cleanup()


@fixture(scope="module", autouse=True)
def temp_warehouse_dir(temp_dir):
    temp_warehouse_dir = TemporaryDirectory(dir=temp_dir, prefix="warehouse")
    yield temp_warehouse_dir.name
    temp_warehouse_dir.cleanup()


# noinspection PyProtectedMember
@fixture(scope="module")
def pathling_ctx(request, temp_warehouse_dir):
    """
    Fixture for creating a Spark Session available for all tests in this
    testing session.
    """
    gateway_log = logging.getLogger("java_gateway")
    gateway_log.setLevel(logging.ERROR)

    # Get the shaded JAR for testing purposes.
    spark = (
        SparkSession.builder.appName("pathling-test")
        .master("local[1]")
        .config("spark.jars", find_pathling_jar(verbose=True))
        .config("spark.driver.memory", "4g")
        .config("spark.jars.packages", "io.delta:delta-core_2.12:2.2.0")
        .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
        .config(
            "spark.sql.catalog.spark_catalog",
            "org.apache.spark.sql.delta.catalog.DeltaCatalog",
        )
        .config("spark.sql.catalogImplementation", "hive")
        .config("spark.sql.warehouse.dir", temp_warehouse_dir)
        .getOrCreate()
    )
    spark.sql("CREATE DATABASE IF NOT EXISTS test")

    request.addfinalizer(lambda: spark.stop())

    jvm = spark._jvm
    encoders = (
        jvm.au.csiro.pathling.encoders.FhirEncoders.forR4()
        .withMaxNestingLevel(0)
        .withExtensionsEnabled(False)
        .withOpenTypes(jvm.java.util.Collections.emptySet())
        .getOrCreate()
    )
    terminology_service_factory = (
        jvm.au.csiro.pathling.terminology.mock.MockTerminologyServiceFactory()
    )
    pathling_context = jvm.au.csiro.pathling.library.PathlingContext.create(
        spark._jsparkSession, encoders, terminology_service_factory
    )
    return PathlingContext(spark, pathling_context)