/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupMemberAddRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupUpdateRequest;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupServiceTest {

  private GroupServices services;
  private GroupSearchClient client;
  private Authentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;

  @BeforeEach
  public void before() {
    authentication = Authentication.of(builder -> builder.user(1234L).token("auth_token"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(GroupSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new GroupServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
  }

  @Test
  public void shouldCreateGroup() {
    // given
    final var groupName = "testGroup";

    // when
    services.createGroup(groupName);

    // then
    final BrokerGroupCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    final GroupRecord record = request.getRequestWriter();
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isEqualTo(GroupIntent.CREATE);
    assertThat(request.getKey()).isEqualTo(-1L);
    assertThat(record).hasName(groupName);
  }

  @Test
  public void shouldEmptyQueryReturnGroups() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchGroups(any())).thenReturn(result);

    final GroupFilter filter = new GroupFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.groupSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleGroup() {
    // given
    final var entity = mock(GroupEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchGroups(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleGroupForGet() {
    // given
    final var entity = mock(GroupEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchGroups(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.findGroup(1L);

    // then
    assertThat(searchQueryResult).contains(entity);
  }

  @Test
  public void shouldThrowExceptionIfGroupNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchGroups(any())).thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    assertThat(services.findGroup(key)).isEmpty();
  }

  @Test
  public void shouldUpdateGroup() {
    // given
    final var groupKey = 100L;
    final var name = "UpdatedName";

    // when
    services.updateGroup(groupKey, name);

    // then
    final BrokerGroupUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.UPDATE);
    assertThat(request.getKey()).isEqualTo(groupKey);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasName(name);
    assertThat(record).hasGroupKey(groupKey);
  }

  @Test
  public void shouldDeleteGroup() {
    // given
    final var groupKey = 123L;

    // when
    services.deleteGroup(groupKey);

    // then
    final BrokerGroupDeleteRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.DELETE);
    assertThat(request.getKey()).isEqualTo(groupKey);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasGroupKey(groupKey);
  }

  @Test
  public void shouldAddMemberToGroup() {
    // given
    final var groupKey = 123L;
    final var memberKey = 456L;
    final var memberType = "user";

    // when
    services.assignMember(groupKey, memberKey, memberType);

    // then
    final BrokerGroupMemberAddRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.ADD_ENTITY);
    assertThat(request.getKey()).isEqualTo(groupKey);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasGroupKey(groupKey);
    assertThat(record).hasEntityKey(memberKey);
    assertThat(record).hasEntityType(EntityType.USER);
  }
}