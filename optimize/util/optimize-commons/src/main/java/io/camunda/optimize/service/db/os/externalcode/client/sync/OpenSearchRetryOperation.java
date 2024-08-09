/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.externalcode.client.sync;

import static io.camunda.optimize.service.exceptions.ExceptionHelper.withIOException;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;

@Slf4j
public abstract class OpenSearchRetryOperation extends OpenSearchSyncOperation {
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;

  public static final int DEFAULT_NUMBER_OF_EXECUTIONS = 5; // 5 attempts * 2s backoff = 10s maximum
  private final int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  protected OpenSearchRetryOperation(
      OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  protected <T> T executeWithRetries(CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  protected <T> T executeWithRetries(String operationName, CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  protected <T> T executeWithRetries(
      String operationName, CheckedSupplier<T> supplier, Predicate<T> retryPredicate) {
    return executeWithGivenRetries(
        DEFAULT_NUMBER_OF_EXECUTIONS, operationName, supplier, retryPredicate);
  }

  protected <T> T executeWithGivenRetries(
      int retries, String operationName, CheckedSupplier<T> operation, Predicate<T> predicate) {
    try {
      final RetryPolicy<T> retryPolicy =
          new RetryPolicy<T>()
              .handle(IOException.class, OpenSearchException.class)
              .withDelay(Duration.ofSeconds(delayIntervalInSeconds))
              .withMaxAttempts(retries)
              .onRetry(
                  e ->
                      log.info(
                          "Retrying #{} {} due to {}",
                          e.getAttemptCount(),
                          operationName,
                          e.getLastFailure()))
              .onAbort(e -> log.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e -> log.error("Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      if (predicate != null) {
        retryPolicy.handleResultIf(predicate);
      }
      return Failsafe.with(retryPolicy).get(operation);
    } catch (Exception e) {
      throw new OptimizeRuntimeException(
          "Couldn't execute operation "
              + operationName
              + " on opensearch for "
              + retries
              + " attempts with "
              + delayIntervalInSeconds
              + " seconds waiting.",
          e);
    }
  }

  protected GetTasksResponse task(String id) throws IOException {
    return openSearchClient.tasks().get(t -> t.taskId(id));
  }

  protected Map<String, Info> tasksWithActions(List<String> actions) throws IOException {
    return openSearchClient.tasks().list(l -> l.actions(actions)).tasks();
  }

  protected GetTasksResponse waitTaskCompletion(String taskId) {
    final String[] taskIdParts = taskId.split(":");
    final String nodeId = taskIdParts[0];
    final long id;
    try {
      id = Long.parseLong(taskIdParts[1]);
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException("Error while parsing task ID parts");
    }
    return executeWithGivenRetries(
        Integer.MAX_VALUE,
        "GetTaskInfo{" + nodeId + "},{" + id + "}",
        () -> {
          checkTaskErrorsOrFailures(nodeId, (int) id);
          return task(taskId);
        },
        this::needsToPollAgain);
  }

  private void checkTaskErrorsOrFailures(final String node, final Integer id) throws IOException {
    final GetTasksResponse tasks = withIOException(() -> task(node + ":" + id));

    if (tasks != null) {
      checkForErrors(tasks);
      checkForFailures(tasks);
    }
  }

  private void checkForErrors(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      throw new OptimizeRuntimeException(taskResponse.error().reason());
    }
  }

  private void checkForFailures(final GetTasksResponse taskResponse) {
    if (taskResponse.response().failures() != null) {
      throw new OptimizeRuntimeException(taskResponse.response().failures().get(0));
    }
  }

  private boolean needsToPollAgain(final GetTasksResponse taskResponse) {
    var r = taskResponse.response();
    var allTasksExecuted = r.total() == r.created() + r.updated() + r.deleted();
    return !(taskResponse.completed() && allTasksExecuted);
  }
}