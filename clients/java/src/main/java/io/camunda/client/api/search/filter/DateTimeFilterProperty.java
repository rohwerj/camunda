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
package io.camunda.client.api.search.filter;

import java.util.ArrayList;
import java.util.List;

public class DateTimeFilterProperty {

  private String eq;
  private String neq;
  private Boolean exists;
  private String gt;
  private String gte;
  private String lt;
  private String lte;
  private List<String> in = new ArrayList<>();

  public String getEq() {
    return eq;
  }

  public void setEq(String eq) {
    this.eq = eq;
  }

  public String getNeq() {
    return neq;
  }

  public void setNeq(String neq) {
    this.neq = neq;
  }

  public Boolean getExists() {
    return exists;
  }

  public void setExists(Boolean exists) {
    this.exists = exists;
  }

  public String getGt() {
    return gt;
  }

  public void setGt(String gt) {
    this.gt = gt;
  }

  public String getGte() {
    return gte;
  }

  public void setGte(String gte) {
    this.gte = gte;
  }

  public String getLt() {
    return lt;
  }

  public void setLt(String lt) {
    this.lt = lt;
  }

  public String getLte() {
    return lte;
  }

  public void setLte(String lte) {
    this.lte = lte;
  }

  public List<String> getIn() {
    return in;
  }

  public void setIn(List<String> in) {
    this.in = in;
  }
}
