/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.kubernetes;

import com.uber.jaeger.Tracer;
import io.jaegertracing.kubernetes.deployment.BaseETest;
import java.io.IOException;
import java.util.UUID;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Ignore;

import static org.awaitility.Awaitility.await;

/**
 * @author Pavol Loffay
 */
public class ElasticearchETest extends BaseETest {

  /**
   * We need to initialize ES storage, before we proceed to tests for two reasons:
   * 1. sometimes first span is not stored
   * 2. jaeger-query returns 500 is ES storage is empty (without indices) https://github.com/jaegertracing/jaeger/issues/464
   */
  @Before
  public void before() {
    String serviceName = UUID.randomUUID().toString().replace("-", "");
    Tracer tracer = createJaegerTracer(serviceName);
    String operationName = UUID.randomUUID().toString().replace("-", "");
    tracer.buildSpan(operationName).startManual().finish();
    tracer.close();

    Request request = new Request.Builder()
        .url(queryUrl + "api/traces?service=" + serviceName)
        .get()
        .build();

    await().until(() -> {
      Response response = okHttpClient.newCall(request).execute();
      String body = response.body().string();
      return body.contains(operationName);
    });
  }

  @Ignore("It requires spark job")
  public void testDependencyLinks() throws IOException, InterruptedException {
  }
}
