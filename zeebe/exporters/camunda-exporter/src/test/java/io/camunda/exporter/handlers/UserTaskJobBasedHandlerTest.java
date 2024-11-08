/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class UserTaskJobBasedHandlerTest {
  private static final Set<JobIntent> SUPPORTED_INTENTS =
      EnumSet.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.CANCELED,
          JobIntent.MIGRATED,
          JobIntent.RECURRED_AFTER_BACKOFF,
          JobIntent.FAILED);
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final UserTaskJobBasedHandler underTest = new UserTaskJobBasedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TaskEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    SUPPORTED_INTENTS.forEach(
        intent -> {
          final Record<JobRecordValue> jobRecord =
              factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));
          // when - then
          assertThat(underTest.handlesRecord(jobRecord)).isTrue();
        });
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    Arrays.stream(JobIntent.values())
        .filter(intent -> !SUPPORTED_INTENTS.contains(intent))
        .forEach(
            intent -> {
              final Record<JobRecordValue> jobRecord =
                  factory.generateRecord(ValueType.JOB, r -> r.withIntent(intent));
              // when - then
              assertThat(underTest.handlesRecord(jobRecord)).isFalse();
            });
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB, r -> r.withIntent(JobIntent.CREATED).withKey(expectedId));

    // when
    final var idList = underTest.generateIds(jobRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final TaskEntity inputEntity = new TaskEntity().setProcessInstanceId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    final Map<String, Object> updateFieldsMap = new HashMap<>();

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            inputEntity.getId(),
            inputEntity,
            updateFieldsMap,
            inputEntity.getProcessInstanceId());
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final var dateTime = OffsetDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    final long processInstanceKey = 123;
    final long jobKey = 456;
    final var assignee = "foo";
    final var formKey = "my-local-form-key";

    final var customerHeaders = new HashMap<String, String>();
    customerHeaders.put(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, assignee);
    customerHeaders.put(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, dateTime);
    customerHeaders.put(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, dateTime);
    customerHeaders.put(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user1\", \"user2\"]");
    customerHeaders.put(
        Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"group1\", \"group2\"]");
    customerHeaders.put(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, formKey);

    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(customerHeaders)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CREATED)
                    .withKey(jobKey)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId(String.valueOf(jobKey));
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getId()).isEqualTo(String.valueOf(jobKey));
    assertThat(taskEntity.getKey()).isEqualTo(jobRecord.getKey());
    assertThat(taskEntity.getTenantId()).isEqualTo(jobRecordValue.getTenantId());
    assertThat(taskEntity.getPartitionId()).isEqualTo(jobRecord.getPartitionId());
    assertThat(taskEntity.getPosition()).isEqualTo(jobRecord.getPosition());
    assertThat(taskEntity.getProcessInstanceId()).isEqualTo(String.valueOf(processInstanceKey));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(jobRecordValue.getElementId());
    assertThat(taskEntity.getBpmnProcessId()).isEqualTo(jobRecordValue.getBpmnProcessId());
    assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(String.valueOf(jobRecordValue.getProcessDefinitionKey()));
    assertThat(taskEntity.getProcessDefinitionVersion())
        .isEqualTo(jobRecordValue.getProcessDefinitionVersion());
    assertThat(taskEntity.getFormKey()).isEqualTo(formKey);
    assertThat(taskEntity.getExternalFormReference()).isNull();
    assertThat(taskEntity.getCustomHeaders()).isNull();
    assertThat(taskEntity.getPriority()).isNull();
    assertThat(taskEntity.getAction()).isNull();
    assertThat(taskEntity.getDueDate()).isEqualTo(dateTime);
    assertThat(taskEntity.getFollowUpDate()).isEqualTo(dateTime);
    assertThat(Arrays.stream(taskEntity.getCandidateGroups()).toList())
        .contains("group1", "group1");
    assertThat(Arrays.stream(taskEntity.getCandidateUsers()).toList()).contains("user1", "user2");
    assertThat(taskEntity.getAssignee()).isEqualTo(assignee);
    assertThat(taskEntity.getJoin()).isNotNull();
    assertThat(taskEntity.getJoin().getParent()).isEqualTo(jobRecordValue.getProcessInstanceKey());
    assertThat(taskEntity.getJoin().getName()).isEqualTo(TaskJoinRelationshipType.TASK.getType());
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
    assertThat(taskEntity.getCreationTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(jobRecord.getTimestamp())));
    assertThat(taskEntity.getImplementation()).isEqualTo(TaskImplementation.JOB_WORKER);
  }

  @Test
  void shouldUpdateEntityFromRecordForCanceledIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.CANCELED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CANCELED);
    assertThat(taskEntity.getCompletionTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(jobRecord.getTimestamp())));
  }

  @Test
  void shouldUpdateEntityFromRecordForCompletedIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.COMPLETED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.COMPLETED);
    assertThat(taskEntity.getCompletionTime())
        .isEqualTo(
            ExporterUtil.toZonedOffsetDateTime(Instant.ofEpochMilli(jobRecord.getTimestamp())));
  }

  @Test
  void shouldUpdateEntityFromRecordForMigratedIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.MIGRATED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void flushedEntityShouldContainMigratedData() {
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withProcessDefinitionKey(456L)
            .withElementId("elementId")
            .withBpmnProcessId("bpmnProcessId")
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.MIGRATED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.PROCESS_DEFINITION_ID, taskEntity.getProcessDefinitionId());
    expectedUpdates.put(TaskTemplate.BPMN_PROCESS_ID, taskEntity.getBpmnProcessId());
    expectedUpdates.put(TaskTemplate.FLOW_NODE_BPMN_ID, taskEntity.getFlowNodeBpmnId());
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    // then
    assertThat(taskEntity.getProcessDefinitionId())
        .isEqualTo(String.valueOf(jobRecordValue.getProcessDefinitionKey()));
    assertThat(taskEntity.getFlowNodeBpmnId()).isEqualTo(jobRecordValue.getElementId());
    assertThat(taskEntity.getBpmnProcessId()).isEqualTo(jobRecordValue.getBpmnProcessId());
    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void flushedEntityShouldContainCompletionUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.COMPLETED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.COMPLETED);
    expectedUpdates.put(TaskTemplate.COMPLETION_TIME, taskEntity.getCompletionTime());

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void shouldUpdateEntityFromRecordForFailedWithoutRetriesIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(0)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.FAILED);
  }

  @Test
  void flushedEntityShouldContainFailedWithoutRetriesUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(0)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.FAILED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void shouldUpdateEntityFromRecordForFailedWithRetryBackoffIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(100L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.FAILED);
  }

  @Test
  void flushedEntityShouldContainFailedWithRetryBackoffUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(100L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.FAILED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void shouldUpdateEntityFromRecordForFailedWithoutRetryBackoffIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(0L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void flushedEntityShouldContainFailedWithoutRetryBackoffUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder()
            .withRetries(1)
            .withRetryBackoff(0L)
            .withProcessInstanceKey(processInstanceKey)
            .build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.FAILED)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }

  @Test
  void shouldUpdateEntityFromRecordForRecurredAfterBackoffIntent() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.RECURRED_AFTER_BACKOFF)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    // then
    assertThat(taskEntity.getState()).isEqualTo(TaskState.CREATED);
  }

  @Test
  void flushedEntityShouldContainRecurredAfterBackoffUpdate() {
    // given
    final long processInstanceKey = 123;
    final JobRecordValue jobRecordValue =
        ImmutableJobRecordValue.builder().withProcessInstanceKey(processInstanceKey).build();

    final Record<JobRecordValue> jobRecord =
        factory.generateRecord(
            ValueType.JOB,
            r ->
                r.withIntent(JobIntent.RECURRED_AFTER_BACKOFF)
                    .withValue(jobRecordValue)
                    .withTimestamp(System.currentTimeMillis()));

    // when
    final TaskEntity taskEntity = new TaskEntity().setId("id");
    underTest.updateEntity(jobRecord, taskEntity);

    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(taskEntity, mockRequest);
    final Map<String, Object> expectedUpdates = new HashMap<>();
    expectedUpdates.put(TaskTemplate.STATE, TaskState.CREATED);

    verify(mockRequest, times(1))
        .upsertWithRouting(
            indexName,
            taskEntity.getId(),
            taskEntity,
            expectedUpdates,
            taskEntity.getProcessInstanceId());
  }
}