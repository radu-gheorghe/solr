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

package org.apache.solr.handler.admin.api;

import org.apache.solr.api.EndPoint;
import org.apache.solr.handler.SchemaHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.security.PermissionNameProvider;

import static org.apache.solr.client.solrj.SolrRequest.METHOD.GET;

/**
 * V2 API for getting the name of the unique-key field for an in-use schema.
 *
 * This API (GET /v2/collections/collectionName/schema/uniquekey) is analogous to the v1
 * /solr/collectionName/schema/uniquekey API.
 */
public class SchemaUniqueKeyAPI {
    private final SchemaHandler schemaHandler;

    public SchemaUniqueKeyAPI(SchemaHandler schemaHandler) {
        this.schemaHandler = schemaHandler;
    }

    @EndPoint(
            path = {"/schema/uniquekey"},
            method = GET,
            permission = PermissionNameProvider.Name.SCHEMA_READ_PERM)
    public void getSchemaUniqueKey(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        schemaHandler.handleRequestBody(req, rsp);
    }
}
