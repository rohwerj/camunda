/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.command;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.ZeebeFuture;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UpdateTenantCommandStep1;
import io.camunda.client.api.response.UpdateTenantResponse;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.http.HttpZeebeFuture;
import io.camunda.client.impl.response.UpdateTenantResponseImpl;
import io.camunda.client.protocol.rest.TenantUpdateRequest;
import io.camunda.client.protocol.rest.TenantUpdateResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class UpdateTenantCommandImpl implements UpdateTenantCommandStep1 {
  private final TenantUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long tenantKey;

  public UpdateTenantCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long tenantKey) {
    request = new TenantUpdateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    this.tenantKey = tenantKey;
  }

  @Override
  public UpdateTenantCommandStep1 name(final String name) {
    request.setName(name);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateTenantResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<UpdateTenantResponse> send() {
    ArgumentUtil.ensureNotNull("name", request.getName());
    final HttpZeebeFuture<UpdateTenantResponse> result = new HttpZeebeFuture<>();
    final UpdateTenantResponseImpl response = new UpdateTenantResponseImpl();

    httpClient.patch(
        "/tenants/" + tenantKey,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        TenantUpdateResponse.class,
        response::setResponse,
        result);
    return result;
  }
}