/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.MappingDbModel;
import io.camunda.db.rdbms.write.domain.MappingDbModel.MappingDbModelBuilder;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class MappingFixtures extends CommonFixtures {

  private MappingFixtures() {}

  public static MappingDbModel createRandomized() {
    return createRandomized(b -> b);
  }

  public static MappingDbModel createRandomized(
      final Function<MappingDbModelBuilder, MappingDbModelBuilder> builderFunction) {
    final var id = nextKey();
    final var builder =
        new MappingDbModelBuilder()
            .mappingId(String.valueOf(id))
            .mappingKey(id)
            .claimName("claimName-" + UUID.randomUUID())
            .claimValue("claimValue-" + UUID.randomUUID())
            .name("name" + UUID.randomUUID());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomMappings(
      final RdbmsWriter rdbmsWriter,
      final Function<MappingDbModelBuilder, MappingDbModelBuilder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getMappingWriter().create(MappingFixtures.createRandomized(builderFunction));
    }
    rdbmsWriter.flush();
  }

  public static void createAndSaveMapping(
      final RdbmsWriter rdbmsWriter, final MappingDbModel mapping) {
    createAndSaveMappings(rdbmsWriter, List.of(mapping));
  }

  public static void createAndSaveMappings(
      final RdbmsWriter rdbmsWriter, final List<MappingDbModel> mappingList) {
    for (final MappingDbModel mapping : mappingList) {
      rdbmsWriter.getMappingWriter().create(mapping);
    }
    rdbmsWriter.flush();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }
}
