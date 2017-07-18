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
package io.jaegertracing.kubernetes.deployment;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;
import io.opentracing.Span;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.arquillian.cube.kubernetes.annotations.Named;
import org.arquillian.cube.kubernetes.annotations.Port;
import org.arquillian.cube.kubernetes.annotations.PortForward;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Pavol Loffay
 */
@RunWith(ArquillianConditionalRunner.class)
public class BaseETest {

  private static final String QUERY_SERVICE_NAME = "jaeger-query";
  private static final String COLLECTOR_SERVICE_NAME = "jaeger-collector";

  private OkHttpClient okHttpClient = new OkHttpClient.Builder()
      .build();

  @Named(QUERY_SERVICE_NAME)
  @PortForward
  @ArquillianResource
  private URL queryUrl;

  @Port(14268)
  @Named(COLLECTOR_SERVICE_NAME)
  @PortForward
  @ArquillianResource
  private URL collectorUrl;

  @Test
  public void testUiResponds() throws IOException, InterruptedException {
    Request request = new Request.Builder()
        .url(queryUrl)
        .get()
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
    }
  }

  @Test
  public void testReportSpanToCollector() throws IOException, InterruptedException {
    Tracer tracer = createTracer("service1");
    tracer.buildSpan("foo").startManual().finish();
    tracer.close();

    Request request = new Request.Builder()
        .url(queryUrl + "api/traces?service=service1")
        .get()
        .build();

    await().atMost(5, TimeUnit.SECONDS).until(() -> {
      Response response = okHttpClient.newCall(request).execute();
      String body = response.body().string();
      return body.contains("foo");
    });

    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
      assertTrue(response.body().string().contains("foo"));
    }
  }

  @Test
  public void testDependencyLinks() throws IOException, InterruptedException {
    Tracer tracer1 = createTracer("service11");
    Span span1 = tracer1.buildSpan("foo").startManual();

    Tracer tracer2 = createTracer("service22");
    tracer2.buildSpan("foo").asChildOf(span1).startManual().finish();
    tracer2.close();

    span1.finish();
    tracer1.close();

    Request request = new Request.Builder()
        .url(queryUrl + "api/dependencies?endTs=" + System.currentTimeMillis())
        .get()
        .build();

    await().atMost(5, TimeUnit.SECONDS).until(() -> {
      Response response = okHttpClient.newCall(request).execute();
      String body = response.body().string();
      return body.contains("service11") && body.contains("service22");
    });

    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
      String body = response.body().string();
      assertTrue(body.contains("service11"));
      assertTrue(body.contains("service22"));
    }
  }

  @Test
  public void hitDependencyScreen() throws IOException {
    Request request = new Request.Builder()
            .url(queryUrl + "api/dependencies?endTs=0")
            .get()
            .build();
    Response response = okHttpClient.newCall(request).execute();
    assertEquals(200, response.code());
  }

  protected com.uber.jaeger.Tracer createTracer(String serviceName) {
    return new com.uber.jaeger.Tracer.Builder(serviceName,
        new RemoteReporter(new HttpSender(collectorUrl + "api/traces", 65000), 1, 100,
            new Metrics(new StatsFactoryImpl(new NullStatsReporter()))), new ConstSampler(true))
        .build();
  }
}
