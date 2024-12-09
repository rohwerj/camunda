/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {XmlUtil.class})
class XmlUtilTest {

  public static final String PROCESS_DEFINITION_ID = "testProcess";
  public static final long PROCESS_DEFINITION_KEY = 1L;
  @Autowired XmlUtil xmlUtil;
  @MockBean ProcessDefinitionServices processDefinitionServices;
  @Mock ProcessDefinitionEntity processDefinition;
  @Mock UserTaskEntity userTaskEntity;
  @Mock FlowNodeInstanceEntity flowNodeInstanceEntity;
  private String bpmn;

  @BeforeEach
  void setupServices() throws Exception {
    bpmn =
        new String(
            getClass().getClassLoader().getResourceAsStream("./user-task.bpmn").readAllBytes(),
            StandardCharsets.UTF_8);
    when(processDefinition.bpmnXml()).thenReturn(bpmn);
    when(processDefinition.processDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
    when(processDefinition.processDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
    when(processDefinitionServices.getByKey(eq(PROCESS_DEFINITION_KEY)))
        .thenReturn(processDefinition);
    when(userTaskEntity.processDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
    when(flowNodeInstanceEntity.processDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);
    when(flowNodeInstanceEntity.type()).thenReturn(FlowNodeType.START_EVENT);
  }

  @Test
  void shouldReturnFlowNodeName() {
    // given
    when(flowNodeInstanceEntity.flowNodeId()).thenReturn("StartEvent_1");
    // when
    final var actual = xmlUtil.getFlowNodeName(flowNodeInstanceEntity);
    // then
    verify(processDefinitionServices).getByKey(eq(PROCESS_DEFINITION_KEY));
    assertThat(actual).isEqualTo("Start");
  }

  @Test
  void shouldReturnUserTaskName() {
    // given
    when(userTaskEntity.elementId()).thenReturn("taskB");
    // when
    final var actual = xmlUtil.getUserTaskName(userTaskEntity);
    // then
    verify(processDefinitionServices).getByKey(eq(PROCESS_DEFINITION_KEY));
    assertThat(actual).isEqualTo("Task B");
  }

  @Test
  void shouldReturnNameForStartEvent() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            bpmn,
            PROCESS_DEFINITION_ID,
            BpmnElementType.START_EVENT.getElementTypeName().get(),
            "StartEvent_1");
    // then
    assertThat(actual).isEqualTo("Start");
  }

  @Test
  void shouldReturnNameForUserTaskEvent() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            bpmn,
            PROCESS_DEFINITION_ID,
            BpmnElementType.USER_TASK.getElementTypeName().get(),
            "taskB");
    // then
    assertThat(actual).isEqualTo("Task B");
  }

  @Test
  void shouldReturnNameForEndEvent() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            bpmn,
            PROCESS_DEFINITION_ID,
            BpmnElementType.END_EVENT.getElementTypeName().get(),
            "Event_0692jdh");
    // then
    assertThat(actual).isEqualTo("End");
  }

  @Test
  void shouldReturnNodeIdWhenNameNotDefined() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            bpmn,
            PROCESS_DEFINITION_ID,
            BpmnElementType.USER_TASK.getElementTypeName().get(),
            "taskA");
    // then
    assertThat(actual).isEqualTo("taskA");
  }

  @Test
  void shouldReturnNodeIdWhenNodeMissing() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            bpmn,
            PROCESS_DEFINITION_ID,
            BpmnElementType.USER_TASK.getElementTypeName().get(),
            "taskC");
    // then
    assertThat(actual).isEqualTo("taskC");
  }

  @Test
  void shouldReturnNodeIdWhenProcessDefinitionIdMissing() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            bpmn, "not-found", BpmnElementType.USER_TASK.getElementTypeName().get(), "taskD");
    // then
    assertThat(actual).isEqualTo("taskD");
  }

  @Test
  void shouldReturnNodeIdWhenXmlInvalid() {
    // when
    final var actual =
        xmlUtil.extractFlowNodeName(
            "not-xml",
            PROCESS_DEFINITION_ID,
            BpmnElementType.USER_TASK.getElementTypeName().get(),
            "taskE");
    // then
    assertThat(actual).isEqualTo("taskE");
  }

  @Test
  void shouldSearchProcessDefinitionsAndResolveNames() {
    // given
    when(userTaskEntity.elementId()).thenReturn("taskB");
    when(processDefinitionServices.search(any()))
        .thenReturn(
            new SearchQueryResult.Builder<ProcessDefinitionEntity>()
                .items(List.of(processDefinition))
                .build());
    // when
    final var actual = xmlUtil.getUserTasksNames(List.of(userTaskEntity));
    // then
    verify(processDefinitionServices)
        .search(
            ProcessDefinitionQuery.of(
                q -> q.filter(f -> f.processDefinitionKeys(List.of(PROCESS_DEFINITION_KEY)))));
    assertThat(actual).hasSize(1);
    assertThat(actual.get(PROCESS_DEFINITION_KEY)).hasSize(1);
    assertThat(actual.get(PROCESS_DEFINITION_KEY).get("taskB")).isEqualTo("Task B");
  }
}
