/*
 * Copyright © Australian e-Health Research Centre, CSIRO. All rights reserved.
 */

package au.csiro.clinsight.query;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.SaveMode;

/**
 * This class knows how to persist a Dataset of resources within a specified database.
 *
 * @author John Grimes
 */
public class ResourceWriter {

  private final String warehouseUrl;
  private final String databaseName;

  public ResourceWriter(String warehouseUrl, String databaseName) {
    this.warehouseUrl = warehouseUrl;
    this.databaseName = databaseName;
  }

  public String getWarehouseUrl() {
    return warehouseUrl;
  }

  public String getDatabaseName() {
    return databaseName;
  }

  public void write(String resourceName, Dataset resources) {
    String tableUrl = warehouseUrl + "/" + databaseName + "/" + resourceName + ".parquet";
    resources.write().mode(SaveMode.Overwrite).parquet(tableUrl);
  }
}
