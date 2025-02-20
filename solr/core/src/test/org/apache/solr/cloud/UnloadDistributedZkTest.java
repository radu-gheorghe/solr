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
package org.apache.solr.cloud;

import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.solr.SolrTestCaseJ4.SuppressSSL;

import org.junit.Test;

/**
 * This test simply does a bunch of basic things in solrcloud mode and asserts things
 * work as expected.
 * Implementation moved to AbstractUnloadDistributedZkTestBase as it is used by HDFS contrib module tests.
 */
@Slow
@SuppressSSL(bugUrl = "https://issues.apache.org/jira/browse/SOLR-5776")
public class UnloadDistributedZkTest extends AbstractUnloadDistributedZkTestBase {
  public UnloadDistributedZkTest() {
    super();
  }

  protected String getSolrXml() {
    return "solr.xml";
  }

  @Test
  @Override
  public void test() throws Exception {
    super.test();
  }

}
