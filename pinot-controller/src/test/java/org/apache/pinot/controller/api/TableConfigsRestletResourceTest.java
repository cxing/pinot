/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.controller.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pinot.controller.helix.ControllerTest;
import org.apache.pinot.core.realtime.impl.fakestream.FakeStreamConfigUtils;
import org.apache.pinot.spi.config.TableConfigs;
import org.apache.pinot.spi.config.table.TableConfig;
import org.apache.pinot.spi.config.table.TableType;
import org.apache.pinot.spi.config.table.TunerConfig;
import org.apache.pinot.spi.data.DateTimeFieldSpec;
import org.apache.pinot.spi.data.FieldSpec;
import org.apache.pinot.spi.data.MetricFieldSpec;
import org.apache.pinot.spi.data.Schema;
import org.apache.pinot.spi.stream.StreamConfig;
import org.apache.pinot.spi.utils.JsonUtils;
import org.apache.pinot.spi.utils.builder.TableConfigBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.collections.Lists;


/**
 * Tests for CRUD APIs of {@link TableConfigs}
 */
public class TableConfigsRestletResourceTest {
  private static final ControllerTest TEST_INSTANCE = ControllerTest.getInstance();

  private String _createTableConfigsUrl;

  @BeforeClass
  public void setUp()
      throws Exception {
    TEST_INSTANCE.setupSharedStateAndValidate();
    _createTableConfigsUrl = TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsCreate();
  }

  private Schema getSchema(String tableName) {
    return TEST_INSTANCE.createDummySchema(tableName);
  }

  private Schema getDimSchema(String tableName) {
    Schema schema = TEST_INSTANCE.createDummySchema(tableName);
    schema.setPrimaryKeyColumns(Lists.newArrayList(schema.getDimensionNames().get(0)));
    return schema;
  }

  private TableConfigBuilder getBaseTableConfigBuilder(String tableName, TableType tableType) {
    if (tableType == TableType.OFFLINE) {
      return new TableConfigBuilder(TableType.OFFLINE).setTableName(tableName).setTimeColumnName("timeColumn")
          .setRetentionTimeUnit("DAYS").setRetentionTimeValue("50");
    } else {
      StreamConfig streamConfig = FakeStreamConfigUtils.getDefaultLowLevelStreamConfigs();
      return new TableConfigBuilder(TableType.REALTIME).setTableName(tableName).setTimeColumnName("timeColumn")
          .setRetentionTimeUnit("DAYS").setLLC(true).setRetentionTimeValue("5")
          .setStreamConfigs(streamConfig.getStreamConfigsMap());
    }
  }

  private TableConfig getOfflineTableConfig(String tableName) {
    return getBaseTableConfigBuilder(tableName, TableType.OFFLINE).build();
  }

  private TableConfig getRealtimeTableConfig(String tableName) {
    return getBaseTableConfigBuilder(tableName, TableType.REALTIME).build();
  }

  private TableConfig getOfflineTunerTableConfig(String tableName) {
    return getBaseTableConfigBuilder(tableName, TableType.OFFLINE)
        .setTunerConfigList(Lists.newArrayList(new TunerConfig("realtimeAutoIndexTuner", null))).build();
  }

  private TableConfig getRealtimeTunerTableConfig(String tableName) {
    return getBaseTableConfigBuilder(tableName, TableType.REALTIME)
        .setTunerConfigList(Lists.newArrayList(new TunerConfig("realtimeAutoIndexTuner", null))).build();
  }

  private TableConfig getOfflineDimTableConfig(String tableName) {
    return getBaseTableConfigBuilder(tableName, TableType.OFFLINE).setIsDimTable(true).build();
  }

  @Test
  public void testValidateConfig()
      throws IOException {

    String validateConfigUrl = TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsValidate();

    String tableName = "testValidate";
    TableConfig offlineTableConfig = getOfflineTableConfig(tableName);
    TableConfig realtimeTableConfig = getRealtimeTableConfig(tableName);
    Schema schema = getSchema(tableName);
    TableConfigs tableConfigs;

    // invalid json
    try {
      tableConfigs = new TableConfigs(tableName, schema, offlineTableConfig, realtimeTableConfig);
      ControllerTest
          .sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString().replace("\"offline\"", "offline\""));
      Assert.fail("Creation of a TableConfigs with invalid json string should have failed");
    } catch (Exception e) {
      // expected
    }

    // null table configs
    try {
      tableConfigs = new TableConfigs(tableName, schema, null, null);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail(
          "Creation of an TableConfigs with null table offline tableConfig and realtime tableConfig should have "
              + "failed");
    } catch (Exception e) {
      // expected
    }

    // null schema
    try {
      tableConfigs = new TableConfigs(tableName, null, offlineTableConfig, null);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with null schema should have failed");
    } catch (Exception e) {
      // expected
    }

    // empty config name
    try {
      tableConfigs = new TableConfigs("", schema, offlineTableConfig, realtimeTableConfig);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with empty config name should have failed");
    } catch (Exception e) {
      // expected
    }

    // schema name doesn't match config name
    try {
      tableConfigs = new TableConfigs(tableName, getSchema("differentName"), offlineTableConfig, realtimeTableConfig);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with schema name different than tableName should have failed");
    } catch (Exception e) {
      // expected
    }

    // schema validation fails
    try {
      Schema schemaWithBlankSpace = getSchema(tableName);
      schemaWithBlankSpace.addField(new MetricFieldSpec("blank space", FieldSpec.DataType.LONG));
      tableConfigs = new TableConfigs(tableName, schemaWithBlankSpace, offlineTableConfig, realtimeTableConfig);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with blank space in column should have failed");
    } catch (Exception e) {
      // expected
    }

    // offline table name doesn't match config name
    try {
      tableConfigs = new TableConfigs(tableName, schema, getOfflineTableConfig("differentName"), null);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with offline table name different than tableName should have failed");
    } catch (Exception e) {
      // expected
    }

    // table name validation fails
    try {
      tableConfigs =
          new TableConfigs("blank space", getSchema("blank space"), getOfflineTableConfig("blank space"), null);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with blank space in table name should have failed");
    } catch (Exception e) {
      // expected
    }

    // table validation fails
    try {
      TableConfig invalidTableConfig = getOfflineTableConfig(tableName);
      invalidTableConfig.getIndexingConfig().setInvertedIndexColumns(Lists.newArrayList("nonExistent"));
      tableConfigs = new TableConfigs(tableName, schema, invalidTableConfig, null);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with invalid table config should have failed");
    } catch (Exception e) {
      // expected
    }

    // realtime table name doesn't match config name
    try {
      tableConfigs = new TableConfigs(tableName, schema, null, getRealtimeTableConfig("differentName"));
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with realtime table name different than tableName should have failed");
    } catch (Exception e) {
      // expected
    }

    // table name validation fails
    try {
      tableConfigs =
          new TableConfigs("blank space", getSchema("blank space"), null, getRealtimeTableConfig("blank space"));
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with blank space in table name should have failed");
    } catch (Exception e) {
      // expected
    }

    // table validation fails
    try {
      TableConfig invalidTableConfig = getRealtimeTableConfig(tableName);
      invalidTableConfig.getIndexingConfig().setInvertedIndexColumns(Lists.newArrayList("nonExistent"));
      tableConfigs = new TableConfigs(tableName, schema, null, invalidTableConfig);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Creation of an TableConfigs with invalid table config should have failed");
    } catch (Exception e) {
      // expected
    }

    // hybrid config consistency check fails
    try {
      Schema twoTimeColumns = getSchema(tableName);
      twoTimeColumns
          .addField(new DateTimeFieldSpec("time1", FieldSpec.DataType.LONG, "1:MILLISECONDS:EPOCH", "1:MILLISECONDS"));
      twoTimeColumns
          .addField(new DateTimeFieldSpec("time2", FieldSpec.DataType.LONG, "1:MILLISECONDS:EPOCH", "1:MILLISECONDS"));
      TableConfig offlineTableConfig1 = getOfflineTableConfig(tableName);
      offlineTableConfig1.getValidationConfig().setTimeColumnName("time1");
      TableConfig realtimeTableConfig1 = getRealtimeTableConfig(tableName);
      realtimeTableConfig1.getValidationConfig().setTimeColumnName("time2");
      tableConfigs = new TableConfigs(tableName, twoTimeColumns, offlineTableConfig1, realtimeTableConfig1);
      ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());
      Assert.fail(
          "Creation of an TableConfigs with inconsistencies across offline and realtime table config should have "
              + "failed");
    } catch (Exception e) {
      // expected
    }

    // successfully created with all 3 configs
    String tableName1 = "testValidate1";
    tableConfigs = new TableConfigs(tableName1, getSchema(tableName1), getOfflineTableConfig(tableName1),
        getRealtimeTableConfig(tableName1));
    ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());

    // successfully create with offline config
    String tableName2 = "testValidate2";
    tableConfigs = new TableConfigs(tableName2, getSchema(tableName2), getOfflineTableConfig(tableName2), null);
    ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());

    // successfully create with realtime config
    String tableName3 = "testValidate3";
    tableConfigs = new TableConfigs(tableName3, getSchema(tableName3), null, getRealtimeTableConfig(tableName3));
    ControllerTest.sendPostRequest(validateConfigUrl, tableConfigs.toPrettyJsonString());

    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName1));
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName2));
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName3));
  }

  /**
   * Tests for creation of TableConfigs
   */
  @Test
  public void testCreateConfig()
      throws IOException {
    String tableName = "testCreate";
    TableConfig offlineTableConfig = getOfflineTableConfig(tableName);
    TableConfig realtimeTableConfig = getRealtimeTableConfig(tableName);
    Schema schema = getSchema(tableName);
    TableConfigs tableConfigs = new TableConfigs(tableName, schema, offlineTableConfig, realtimeTableConfig);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    String response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    TableConfigs tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), offlineTableConfig.getTableName());
    Assert.assertEquals(tableConfigsResponse.getRealtime().getTableName(), realtimeTableConfig.getTableName());
    Assert.assertEquals(tableConfigsResponse.getSchema().getSchemaName(), schema.getSchemaName());

    // test POST of existing configs fails
    try {
      ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
      Assert.fail("Should fail for trying to add existing config");
    } catch (Exception e) {
      // expected
    }

    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));

    // replica check
    tableName = "testCreateReplicas";
    TableConfig replicaTestOfflineTableConfig = getOfflineTableConfig(tableName);
    TableConfig replicaTestRealtimeTableConfig = getRealtimeTableConfig(tableName);
    replicaTestOfflineTableConfig.getValidationConfig().setReplication("1");
    replicaTestRealtimeTableConfig.getValidationConfig().setReplicasPerPartition("1");
    tableConfigs = new TableConfigs(tableName, getSchema(tableName), replicaTestOfflineTableConfig,
        replicaTestRealtimeTableConfig);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getOffline().getValidationConfig().getReplicationNumber(),
        TEST_INSTANCE.MIN_NUM_REPLICAS);
    Assert.assertEquals(tableConfigsResponse.getRealtime().getValidationConfig().getReplicasPerPartitionNumber(),
        TEST_INSTANCE.MIN_NUM_REPLICAS);
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));

    // quota check
    tableName = "testCreateQuota";
    TableConfig offlineDimTableConfig = getOfflineDimTableConfig(tableName);
    Schema dimSchema = getDimSchema(tableName);
    tableConfigs = new TableConfigs(tableName, dimSchema, offlineDimTableConfig, null);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableName, tableConfigsResponse.getTableName());
    Assert.assertEquals(tableConfigsResponse.getOffline().getQuotaConfig().getStorage(),
        TEST_INSTANCE.getControllerConfig().getDimTableMaxSize());
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));

    // tuner config
    tableName = "testTunerConfig";
    TableConfig offlineTunerTableConfig = getOfflineTunerTableConfig(tableName);
    TableConfig realtimeTunerTableConfig = getRealtimeTunerTableConfig(tableName);
    tableConfigs = new TableConfigs(tableName, getSchema(tableName), offlineTunerTableConfig, realtimeTunerTableConfig);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableName, tableConfigsResponse.getTableName());
    Assert.assertTrue(tableConfigsResponse.getOffline().getIndexingConfig().getInvertedIndexColumns()
        .containsAll(schema.getDimensionNames()));
    Assert.assertTrue(tableConfigsResponse.getOffline().getIndexingConfig().getNoDictionaryColumns()
        .containsAll(schema.getMetricNames()));
    Assert.assertTrue(tableConfigsResponse.getRealtime().getIndexingConfig().getInvertedIndexColumns()
        .containsAll(schema.getDimensionNames()));
    Assert.assertTrue(tableConfigsResponse.getRealtime().getIndexingConfig().getNoDictionaryColumns()
        .containsAll(schema.getMetricNames()));
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));
  }

  @Test
  public void testListConfigs()
      throws IOException {
    // create with 1 config
    String tableName1 = "testList1";
    TableConfig offlineTableConfig = getOfflineTableConfig(tableName1);
    TableConfig realtimeTableConfig = getRealtimeTableConfig(tableName1);
    Schema schema = getSchema(tableName1);
    TableConfigs tableConfigs1 = new TableConfigs(tableName1, schema, offlineTableConfig, null);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs1.toPrettyJsonString());

    // list
    String getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    List<String> configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 1);
    TableConfigs tableConfigsResponse = JsonUtils.stringToObject(configs.get(0), TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableConfigs1.getTableName());
    Assert.assertEquals(tableConfigsResponse.getSchema(), tableConfigs1.getSchema());
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), tableConfigs1.getOffline().getTableName());
    Assert.assertNull(tableConfigsResponse.getRealtime());

    // update to 2
    tableConfigs1 = new TableConfigs(tableName1, schema, offlineTableConfig, realtimeTableConfig);
    ControllerTest
        .sendPutRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsUpdate(tableName1),
            tableConfigs1.toPrettyJsonString());

    // list
    getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 1);
    tableConfigsResponse = JsonUtils.stringToObject(configs.get(0), TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableConfigs1.getTableName());
    Assert.assertEquals(tableConfigsResponse.getSchema(), tableConfigs1.getSchema());
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), tableConfigs1.getOffline().getTableName());
    Assert.assertEquals(tableConfigsResponse.getRealtime().getTableName(), tableConfigs1.getRealtime().getTableName());

    // create new
    String tableName2 = "testList2";
    offlineTableConfig = getOfflineTableConfig(tableName2);
    schema = getSchema(tableName2);
    TableConfigs tableConfigs2 = new TableConfigs(tableName2, schema, offlineTableConfig, null);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs2.toPrettyJsonString());

    // list
    getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 2);
    Map<String, TableConfigs> tableNameToConfigs = new HashMap<>(2);
    for (String conf : configs) {
      TableConfigs response = JsonUtils.stringToObject(conf, TableConfigs.class);
      tableNameToConfigs.put(response.getTableName(), response);
    }
    Assert.assertEquals(tableNameToConfigs.get(tableName1).getTableName(), tableConfigs1.getTableName());
    Assert.assertEquals(tableNameToConfigs.get(tableName1).getSchema(), tableConfigs1.getSchema());
    Assert.assertEquals(tableNameToConfigs.get(tableName1).getOffline().getTableName(),
        tableConfigs1.getOffline().getTableName());
    Assert.assertEquals(tableNameToConfigs.get(tableName1).getRealtime().getTableName(),
        tableConfigs1.getRealtime().getTableName());

    Assert.assertEquals(tableNameToConfigs.get(tableName2).getTableName(), tableConfigs2.getTableName());
    Assert.assertEquals(tableNameToConfigs.get(tableName2).getSchema(), tableConfigs2.getSchema());
    Assert.assertEquals(tableNameToConfigs.get(tableName2).getOffline().getTableName(),
        tableConfigs2.getOffline().getTableName());
    Assert.assertNull(tableNameToConfigs.get(tableName2).getRealtime());

    // delete 1
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName2));

    // list 1
    getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 1);
    tableConfigsResponse = JsonUtils.stringToObject(configs.get(0), TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableConfigs1.getTableName());
    Assert.assertEquals(tableConfigsResponse.getSchema(), tableConfigs1.getSchema());
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), tableConfigs1.getOffline().getTableName());
    Assert.assertEquals(tableConfigsResponse.getRealtime().getTableName(), tableConfigs1.getRealtime().getTableName());

    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName1));
  }

  @Test
  public void testUpdateConfig()
      throws IOException {

    // create with 1
    String tableName = "testUpdate1";
    TableConfig offlineTableConfig = getOfflineTableConfig(tableName);
    TableConfig realtimeTableConfig = getRealtimeTableConfig(tableName);
    Schema schema = getSchema(tableName);
    TableConfigs tableConfigs = new TableConfigs(tableName, schema, offlineTableConfig, null);
    // PUT before POST should fail
    try {
      ControllerTest
          .sendPutRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsUpdate(tableName),
              tableConfigs.toPrettyJsonString());
      Assert.fail("Should fail for trying to PUT config before creating via POST");
    } catch (Exception e) {
      // expected
    }
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    String response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    TableConfigs tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), offlineTableConfig.getTableName());
    Assert.assertNull(tableConfigs.getRealtime());
    Assert.assertEquals(tableConfigsResponse.getSchema().getSchemaName(), schema.getSchemaName());

    // list
    String getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    List<String> configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 1);
    tableConfigsResponse = JsonUtils.stringToObject(configs.get(0), TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getSchema(), tableConfigs.getSchema());
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), tableConfigs.getOffline().getTableName());
    Assert.assertNull(tableConfigsResponse.getRealtime());

    // update to 2
    tableConfigs = new TableConfigs(tableName, tableConfigsResponse.getSchema(), tableConfigsResponse.getOffline(),
        realtimeTableConfig);
    ControllerTest
        .sendPutRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsUpdate(tableName),
            tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), offlineTableConfig.getTableName());
    Assert.assertEquals(tableConfigsResponse.getRealtime().getTableName(), realtimeTableConfig.getTableName());
    Assert.assertEquals(tableConfigsResponse.getSchema().getSchemaName(), schema.getSchemaName());

    // list
    getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 1);
    tableConfigsResponse = JsonUtils.stringToObject(configs.get(0), TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getSchema(), tableConfigs.getSchema());
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), tableConfigs.getOffline().getTableName());
    Assert.assertEquals(tableConfigsResponse.getRealtime().getTableName(), tableConfigs.getRealtime().getTableName());

    // update existing config
    schema.addField(new MetricFieldSpec("newMetric", FieldSpec.DataType.LONG));
    tableConfigs =
        new TableConfigs(tableName, schema, tableConfigsResponse.getOffline(), tableConfigsResponse.getRealtime());
    ControllerTest
        .sendPutRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsUpdate(tableName),
            tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);
    Assert.assertEquals(tableConfigsResponse.getOffline().getTableName(), offlineTableConfig.getTableName());
    Assert.assertEquals(tableConfigsResponse.getRealtime().getTableName(), realtimeTableConfig.getTableName());
    Assert.assertEquals(tableConfigsResponse.getSchema().getSchemaName(), schema.getSchemaName());
    Assert.assertTrue(tableConfigsResponse.getSchema().getMetricNames().contains("newMetric"));

    tableConfigsResponse.getOffline().getIndexingConfig().setInvertedIndexColumns(Lists.newArrayList("dimA"));
    tableConfigsResponse.getRealtime().getIndexingConfig().setInvertedIndexColumns(Lists.newArrayList("dimA"));
    tableConfigs =
        new TableConfigs(tableName, schema, tableConfigsResponse.getOffline(), tableConfigsResponse.getRealtime());
    ControllerTest
        .sendPutRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsUpdate(tableName),
            tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertTrue(tableConfigsResponse.getOffline().getIndexingConfig().getInvertedIndexColumns().contains("dimA"));
    Assert
        .assertTrue(tableConfigsResponse.getRealtime().getIndexingConfig().getInvertedIndexColumns().contains("dimA"));

    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));
  }

  @Test
  public void testDeleteConfig()
      throws Exception {
    // create with 1 config
    String tableName = "testDelete1";
    TableConfig offlineTableConfig = getOfflineTableConfig(tableName);
    Schema schema = getSchema(tableName);
    TableConfigs tableConfigs = new TableConfigs(tableName, schema, offlineTableConfig, null);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    String response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    TableConfigs tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);

    // delete & check
    ControllerTest
        .sendDeleteRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));
    String getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    List<String> configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 0);

    tableName = "testDelete2";
    offlineTableConfig = getOfflineTableConfig(tableName);
    TableConfig realtimeTableConfig = getRealtimeTableConfig(tableName);
    schema = getSchema(tableName);
    tableConfigs = new TableConfigs(tableName, schema, offlineTableConfig, realtimeTableConfig);
    ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigs.toPrettyJsonString());
    response = ControllerTest
        .sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsGet(tableName));
    tableConfigsResponse = JsonUtils.stringToObject(response, TableConfigs.class);
    Assert.assertEquals(tableConfigsResponse.getTableName(), tableName);

    // delete & check
    ControllerTest.sendDeleteRequest(
        TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));
    getResponse =
        ControllerTest.sendGetRequest(TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsList());
    configs = JsonUtils.stringToObject(getResponse, new TypeReference<List<String>>() {
    });
    Assert.assertEquals(configs.size(), 0);
  }

  @Test
  public void testUnrecognizedProperties()
      throws IOException {
    String tableName = "testUnrecognized1";
    TableConfig offlineTableConfig = getOfflineTableConfig(tableName);
    Schema schema = getSchema(tableName);
    TableConfigs tableConfigs = new TableConfigs(tableName, schema, offlineTableConfig, null);
    ObjectNode tableConfigsJson = JsonUtils.objectToJsonNode(tableConfigs).deepCopy();
    tableConfigsJson.put("illegalKey1", 1);

    // Validate
    TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsValidate();
    String response = ControllerTest.sendPostRequest(
        TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsValidate(),
        tableConfigsJson.toPrettyString());
    JsonNode responseJson = JsonUtils.stringToJsonNode(response);
    Assert.assertTrue(responseJson.has("unrecognizedProperties"));
    Assert.assertTrue(responseJson.get("unrecognizedProperties").has("/illegalKey1"));

    // Create
    response = ControllerTest.sendPostRequest(_createTableConfigsUrl, tableConfigsJson.toPrettyString());
    Assert.assertEquals(response, "{\"unrecognizedProperties\":{\"/illegalKey1\":1},\"status\":\"TableConfigs "
        + "testUnrecognized1 successfully added\"}");

    // Update
    response = ControllerTest.sendPutRequest(
        TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsUpdate(tableName),
        tableConfigsJson.toPrettyString());
    Assert.assertEquals(response,
        "{\"unrecognizedProperties\":{\"/illegalKey1\":1},\"status\":\"TableConfigs updated for testUnrecognized1\"}");
    // Delete
    ControllerTest.sendDeleteRequest(
        TEST_INSTANCE.getControllerRequestURLBuilder().forTableConfigsDelete(tableName));
  }

  @AfterClass
  public void tearDown() {
    TEST_INSTANCE.cleanup();
  }
}
