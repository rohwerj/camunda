/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.CreateRoleRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateRoleRequest;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/roles")
public class RoleController {
  private final RoleServices roleServices;

  public RoleController(final RoleServices roleServices) {
    this.roleServices = roleServices;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createRole(
      @RequestBody final RoleCreateRequest createRoleRequest) {
    return RequestMapper.toRoleCreateRequest(createRoleRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createRole);
  }

  private CompletableFuture<ResponseEntity<Object>> createRole(
      final CreateRoleRequest createRoleRequest) {
    return RequestMapper.executeServiceMethod(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createRole(createRoleRequest.name()),
        ResponseMapper::toRoleCreateResponse);
  }

  @PatchMapping(
      path = "/{roleKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> updateRole(
      @PathVariable final long roleKey, @RequestBody final RoleUpdateRequest roleUpdateRequest) {
    return RequestMapper.toRoleUpdateRequest(roleUpdateRequest, roleKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateRole);
  }

  public CompletableFuture<ResponseEntity<Object>> updateRole(
      final UpdateRoleRequest updateRoleRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateRole(updateRoleRequest.roleKey(), updateRoleRequest.name()));
  }

  @DeleteMapping(
      path = "/{roleKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deleteRole(@PathVariable final long roleKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            roleServices.withAuthentication(RequestMapper.getAuthentication()).deleteRole(roleKey));
  }
}