/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.clustering;

import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.NodeConfig;
import io.atomix.cluster.discovery.BootstrapDiscoveryConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.protocol.SwimMembershipProtocolConfig;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.MembershipCfg;
import io.camunda.zeebe.broker.system.configuration.NetworkCfg;
import io.camunda.zeebe.broker.system.configuration.SocketBindingCfg;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

// TODO: move this to BrokerClusterConfiguration in the dist module
public final class ClusterConfigFactory {
  public ClusterConfig mapConfiguration(final BrokerCfg config) {
    final var cluster = config.getCluster();
    final var name = cluster.getClusterName();
    final var discovery = discoveryConfig(cluster.getInitialContactPoints());
    final var membership = membershipConfig(cluster.getMembership());
    final var network = config.getNetwork();

    final var messaging = messagingConfig(cluster, network);
    final var member = memberConfig(network.getInternalApi(), cluster.getNodeId());

    return new ClusterConfig()
        .setClusterId(name)
        .setNodeConfig(member)
        .setDiscoveryConfig(discovery)
        .setMessagingConfig(messaging)
        .setProtocolConfig(membership);
  }

  private MemberConfig memberConfig(final SocketBindingCfg network, final int nodeId) {
    final var advertisedAddress =
        Address.from(network.getAdvertisedHost(), network.getAdvertisedPort());

    return new MemberConfig().setAddress(advertisedAddress).setId(String.valueOf(nodeId));
  }

  private SwimMembershipProtocolConfig membershipConfig(final MembershipCfg config) {
    return new SwimMembershipProtocolConfig()
        .setBroadcastDisputes(config.isBroadcastDisputes())
        .setBroadcastUpdates(config.isBroadcastUpdates())
        .setFailureTimeout(config.getFailureTimeout())
        .setGossipFanout(config.getGossipFanout())
        .setGossipInterval(config.getGossipInterval())
        .setNotifySuspect(config.isNotifySuspect())
        .setProbeInterval(config.getProbeInterval())
        .setProbeTimeout(config.getProbeTimeout())
        .setSuspectProbes(config.getSuspectProbes())
        .setSyncInterval(config.getSyncInterval());
  }

  private BootstrapDiscoveryConfig discoveryConfig(final Collection<String> contactPoints) {
    final var nodes =
        contactPoints.stream()
            .map(Address::from)
            .map(address -> new NodeConfig().setAddress(address))
            .collect(Collectors.toSet());

    return new BootstrapDiscoveryConfig().setNodes(nodes);
  }

  private MessagingConfig messagingConfig(final ClusterCfg cluster, final NetworkCfg network) {
    final var messaging =
        new MessagingConfig()
            .setCompressionAlgorithm(cluster.getMessageCompression())
            .setInterfaces(Collections.singletonList(network.getInternalApi().getHost()))
            .setPort(network.getInternalApi().getPort())
            .setSocketReceiveBuffer((int) network.getSocketReceiveBuffer().toBytes())
            .setSocketSendBuffer((int) network.getSocketSendBuffer().toBytes())
            .setHeartbeatTimeout(network.getHeartbeatTimeout())
            .setHeartbeatInterval(network.getHeartbeatInterval());

    if (network.getSecurity().isEnabled()) {
      final var security = network.getSecurity();

      messaging
          .setTlsEnabled(true)
          .configureTls(
              security.getKeyStore().getFilePath(),
              security.getKeyStore().getPassword(),
              security.getPrivateKeyPath(),
              security.getCertificateChainPath());
    }
    return messaging;
  }
}
