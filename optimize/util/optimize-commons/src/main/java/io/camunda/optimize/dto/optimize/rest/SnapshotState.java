/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

/** Represents the state that a snapshot can be in */
public enum SnapshotState {
  /** Snapshot process has started */
  IN_PROGRESS((byte) 0, false, false),
  /** Snapshot process completed successfully */
  SUCCESS((byte) 1, true, true),
  /** Snapshot failed */
  FAILED((byte) 2, true, false),
  /** Snapshot was partial successful */
  PARTIAL((byte) 3, true, true),
  /** Snapshot is incompatible with the current version of the cluster */
  INCOMPATIBLE((byte) 4, true, false);

  private final byte value;

  private final boolean completed;

  private final boolean restorable;

  SnapshotState(final byte value, final boolean completed, final boolean restorable) {
    this.value = value;
    this.completed = completed;
    this.restorable = restorable;
  }

  /**
   * Returns code that represents the snapshot state
   *
   * @return code for the state
   */
  public byte value() {
    return value;
  }

  /**
   * Returns true if snapshot completed (successfully or not)
   *
   * @return true if snapshot completed, false otherwise
   */
  public boolean completed() {
    return completed;
  }

  /**
   * Returns true if snapshot can be restored (at least partially)
   *
   * @return true if snapshot can be restored, false otherwise
   */
  public boolean restorable() {
    return restorable;
  }

  /**
   * Generate snapshot state from code
   *
   * @param value the state code
   * @return state
   */
  public static SnapshotState fromValue(final String value) {
    return switch (value) {
      case "0" -> IN_PROGRESS;
      case "1" -> SUCCESS;
      case "2" -> FAILED;
      case "3" -> PARTIAL;
      case "4" -> INCOMPATIBLE;
      default -> throw new IllegalArgumentException("No snapshot state for value [" + value + "]");
    };
  }
}
