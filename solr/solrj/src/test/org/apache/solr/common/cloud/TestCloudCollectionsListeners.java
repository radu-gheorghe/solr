/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.common.cloud;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.cloud.SolrCloudTestCase;
import org.apache.solr.common.util.ExecutorUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestCloudCollectionsListeners extends SolrCloudTestCase {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final int CLUSTER_SIZE = 4;

  private static final ExecutorService executor =
      ExecutorUtil.newMDCAwareCachedThreadPool("backgroundWatchers");

  private static final int MAX_WAIT_TIMEOUT = 30;

  @AfterClass
  public static void shutdownBackgroundExecutors() {
    executor.shutdown();
  }

  @Before
  public void prepareCluster() throws Exception {
    configureCluster(CLUSTER_SIZE)
        .addConfig("config", getFile("solrj/solr/collection1/conf").toPath())
        .configure();

    int missingServers = CLUSTER_SIZE - cluster.getJettySolrRunners().size();
    for (int i = 0; i < missingServers; i++) {
      cluster.startJettySolrRunner();
    }
    cluster.waitForAllNodes(30);
  }

  @After
  public void afterTest() throws Exception {
    shutdownCluster();
  }

  @Test
  public void testSimpleCloudCollectionsListener() throws Exception {

    CloudSolrClient client = cluster.getSolrClient();

    Map<Integer, Set<String>> oldResults = new HashMap<>();
    Map<Integer, Set<String>> newResults = new HashMap<>();

    CloudCollectionsListener watcher1 =
        (oldCollections, newCollections) -> {
          log.info("New set of collections: {}, {}", oldCollections, newCollections);
          oldResults.put(1, oldCollections);
          newResults.put(1, newCollections);
        };
    client.getZkStateReader().registerCloudCollectionsListener(watcher1);
    CloudCollectionsListener watcher2 =
        (oldCollections, newCollections) -> {
          log.info("New set of collections: {}, {}", oldCollections, newCollections);
          oldResults.put(2, oldCollections);
          newResults.put(2, newCollections);
        };
    client.getZkStateReader().registerCloudCollectionsListener(watcher2);

    assertFalse(
        "CloudCollectionsListener not triggered after registration",
        oldResults.get(1).contains("testcollection1"));
    assertFalse(
        "CloudCollectionsListener not triggered after registration",
        oldResults.get(2).contains("testcollection1"));

    assertFalse(
        "CloudCollectionsListener not triggered after registration",
        newResults.get(1).contains("testcollection1"));
    assertFalse(
        "CloudCollectionsListener not triggered after registration",
        newResults.get(2).contains("testcollection1"));

    CollectionAdminRequest.createCollection("testcollection1", "config", 4, 1)
        .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
        .processAndWait(client, MAX_WAIT_TIMEOUT);
    client.waitForState(
        "testcollection1",
        MAX_WAIT_TIMEOUT,
        TimeUnit.SECONDS,
        (n, c) -> DocCollection.isFullyActive(n, c, 4, 1));

    assertFalse(
        "CloudCollectionsListener has new collection in old set of collections",
        oldResults.get(1).contains("testcollection1"));
    assertFalse(
        "CloudCollectionsListener has new collection in old set of collections",
        oldResults.get(2).contains("testcollection1"));

    assertTrue(
        "CloudCollectionsListener doesn't have new collection in new set of collections",
        newResults.get(1).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener doesn't have new collection in new set of collections",
        newResults.get(2).contains("testcollection1"));

    client.getZkStateReader().removeCloudCollectionsListener(watcher1);

    CollectionAdminRequest.createCollection("testcollection2", "config", 4, 1)
        .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
        .processAndWait(client, MAX_WAIT_TIMEOUT);
    cluster.waitForActiveCollection("testcollection2", 4, 4);

    assertFalse(
        "CloudCollectionsListener notified after removal",
        oldResults.get(1).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener does not contain old collection in list of old collections",
        oldResults.get(2).contains("testcollection1"));
    assertFalse(
        "CloudCollectionsListener contains new collection in old collection set",
        oldResults.get(1).contains("testcollection2"));
    assertFalse(
        "CloudCollectionsListener contains new collection in old collection set",
        oldResults.get(2).contains("testcollection2"));

    assertFalse(
        "CloudCollectionsListener notified after removal",
        newResults.get(1).contains("testcollection2"));
    assertTrue(
        "CloudCollectionsListener does not contain new collection in list of new collections",
        newResults.get(2).contains("testcollection2"));

    CollectionAdminRequest.deleteCollection("testcollection1")
        .processAndWait(client, MAX_WAIT_TIMEOUT);

    CollectionAdminRequest.deleteCollection("testcollection2")
        .processAndWait(client, MAX_WAIT_TIMEOUT);

    client.getZkStateReader().removeCloudCollectionsListener(watcher2);
  }

  @Test
  public void testCollectionDeletion() throws Exception {

    CloudSolrClient client = cluster.getSolrClient();

    CollectionAdminRequest.createCollection("testcollection1", "config", 4, 1)
        .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
        .processAndWait(client, MAX_WAIT_TIMEOUT);
    cluster.waitForActiveCollection("testcollection1", 4, 4);

    CollectionAdminRequest.createCollection("testcollection2", "config", 4, 1)
        .setPerReplicaState(SolrCloudTestCase.USE_PER_REPLICA_STATE)
        .processAndWait(client, MAX_WAIT_TIMEOUT);
    cluster.waitForActiveCollection("testcollection2", 4, 4);

    Map<Integer, Set<String>> oldResults = new HashMap<>();
    Map<Integer, Set<String>> newResults = new HashMap<>();

    CloudCollectionsListener watcher1 =
        (oldCollections, newCollections) -> {
          log.info("New set of collections: {}, {}", oldCollections, newCollections);
          oldResults.put(1, oldCollections);
          newResults.put(1, newCollections);
        };
    client.getZkStateReader().registerCloudCollectionsListener(watcher1);
    CloudCollectionsListener watcher2 =
        (oldCollections, newCollections) -> {
          log.info("New set of collections: {}, {}", oldCollections, newCollections);
          oldResults.put(2, oldCollections);
          newResults.put(2, newCollections);
        };
    client.getZkStateReader().registerCloudCollectionsListener(watcher2);

    assertEquals(
        "CloudCollectionsListener has old collection with size > 0 after registration",
        0,
        oldResults.get(1).size());
    assertEquals(
        "CloudCollectionsListener has old collection with size > 0 after registration",
        0,
        oldResults.get(2).size());

    assertTrue(
        "CloudCollectionsListener not notified of all collections after registration",
        newResults.get(1).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener not notified of all collections after registration",
        newResults.get(1).contains("testcollection2"));
    assertTrue(
        "CloudCollectionsListener not notified of all collections after registration",
        newResults.get(2).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener not notified of all collections after registration",
        newResults.get(2).contains("testcollection2"));

    CollectionAdminRequest.deleteCollection("testcollection1")
        .processAndWait(client, MAX_WAIT_TIMEOUT);

    assertEquals(
        "CloudCollectionsListener missing old collection after collection removal",
        2,
        oldResults.get(1).size());
    assertEquals(
        "CloudCollectionsListener missing old collection after collection removal",
        2,
        oldResults.get(2).size());

    assertFalse(
        "CloudCollectionsListener notifies with collection that no longer exists",
        newResults.get(1).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener doesn't notify of collection that exists",
        newResults.get(1).contains("testcollection2"));
    assertFalse(
        "CloudCollectionsListener notifies with collection that no longer exists",
        newResults.get(2).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener doesn't notify of collection that exists",
        newResults.get(2).contains("testcollection2"));

    client.getZkStateReader().removeCloudCollectionsListener(watcher2);

    CollectionAdminRequest.deleteCollection("testcollection2")
        .processAndWait(client, MAX_WAIT_TIMEOUT);

    assertEquals(
        "CloudCollectionsListener has incorrect number of old collections",
        1,
        oldResults.get(1).size());
    assertTrue(
        "CloudCollectionsListener has incorrect old collection after collection removal",
        oldResults.get(1).contains("testcollection2"));
    assertEquals("CloudCollectionsListener called after removal", 2, oldResults.get(2).size());

    assertFalse(
        "CloudCollectionsListener shows live collection after removal",
        newResults.get(1).contains("testcollection1"));
    assertFalse(
        "CloudCollectionsListener shows live collection after removal",
        newResults.get(1).contains("testcollection2"));
    assertFalse(
        "CloudCollectionsListener called after removal",
        newResults.get(2).contains("testcollection1"));
    assertTrue(
        "CloudCollectionsListener called after removal",
        newResults.get(2).contains("testcollection2"));

    client.getZkStateReader().removeCloudCollectionsListener(watcher1);
  }
}
