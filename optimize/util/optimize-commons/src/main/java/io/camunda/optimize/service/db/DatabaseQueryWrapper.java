/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db;

import org.opensearch.client.opensearch._types.query_dsl.Query;

// TODO this should eventully be non-nullable fields to guarantee that we always have both queries
public record DatabaseQueryWrapper(
    co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery, Query osQuery) {}