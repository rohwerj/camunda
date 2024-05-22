/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
create table groups (
                        id bigint generated by default as identity(start with 0) primary key,
                        group_name varchar_ignorecase(50) not null,
                        organization_id varchar_ignorecase(250) null
);

create table group_authorities (
                                   group_id bigint not null,
                                   authority varchar(50) not null,
                                   constraint fk_group_authorities_group foreign key(group_id) references groups(id)
);

create table group_members (
                               id bigint generated by default as identity(start with 0) primary key,
                               username varchar(50) not null,
                               group_id bigint not null,
                               constraint fk_group_members_group foreign key(group_id) references groups(id),
                               constraint uk_group_members_group_username unique(group_id , username)
);
