/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ResourceServices extends ApiServices<ResourceServices> {

  public ResourceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public ResourceServices withAuthentication(final Authentication authentication) {
    return new ResourceServices(brokerClient, searchClient, transformers, authentication);
  }

  public CompletableFuture<DeploymentRecord> deployResources(
      final DeployResourcesRequest deployResourcesRequest) {
    final var brokerRequest = new BrokerDeployResourceRequest();
    deployResourcesRequest.resources().forEach(brokerRequest::addResource);
    brokerRequest.setTenantId(deployResourcesRequest.tenantId());
    return sendBrokerRequest(brokerRequest);
  }

  public record DeployResourcesRequest(Map<String, byte[]> resources, String tenantId) {}
}