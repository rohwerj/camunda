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
package io.camunda.client.impl.search.request;

import static io.camunda.client.api.search.request.SearchRequestBuilders.processInstanceFilter;
import static io.camunda.client.api.search.request.SearchRequestBuilders.processInstanceSort;
import static io.camunda.client.api.search.request.SearchRequestBuilders.searchRequestPage;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.FinalSearchRequestStep;
import io.camunda.client.api.search.request.ProcessInstanceSearchRequest;
import io.camunda.client.api.search.request.SearchRequestPage;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.ProcessInstanceSearchQuery;
import io.camunda.client.protocol.rest.ProcessInstanceSearchQueryResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.hc.client5.http.config.RequestConfig;

public class ProcessInstanceSearchRequestImpl
    extends TypedSearchRequestPropertyProvider<ProcessInstanceSearchQuery>
    implements ProcessInstanceSearchRequest {

  private final ProcessInstanceSearchQuery request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public ProcessInstanceSearchRequestImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper) {
    request = new ProcessInstanceSearchQuery();
    this.jsonMapper = jsonMapper;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalSearchRequestStep<ProcessInstance> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<SearchResponse<ProcessInstance>> send() {
    final HttpCamundaFuture<SearchResponse<ProcessInstance>> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/process-instances/search",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        ProcessInstanceSearchQueryResult.class,
        SearchResponseMapper::toProcessInstanceSearchResponse,
        result);
    return result;
  }

  @Override
  public ProcessInstanceSearchRequest filter(final ProcessInstanceFilter value) {
    request.setFilter(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessInstanceSearchRequest filter(final Consumer<ProcessInstanceFilter> fn) {
    return filter(processInstanceFilter(fn));
  }

  @Override
  public ProcessInstanceSearchRequest sort(final ProcessInstanceSort value) {
    request.setSort(
        SearchRequestSortMapper.toProcessInstanceSearchQuerySortRequest(
            provideSearchRequestProperty(value)));
    return this;
  }

  @Override
  public ProcessInstanceSearchRequest sort(final Consumer<ProcessInstanceSort> fn) {
    return sort(processInstanceSort(fn));
  }

  @Override
  public ProcessInstanceSearchRequest page(final SearchRequestPage value) {
    request.setPage(provideSearchRequestProperty(value));
    return this;
  }

  @Override
  public ProcessInstanceSearchRequest page(final Consumer<SearchRequestPage> fn) {
    return page(searchRequestPage(fn));
  }

  @Override
  protected ProcessInstanceSearchQuery getSearchRequestProperty() {
    return request;
  }
}
