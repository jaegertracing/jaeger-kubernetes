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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.uber.jaeger.Tracer;
import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ConstSampler;
import com.uber.jaeger.senders.HttpSender;
import io.opentracing.Span;
import java.io.IOException;
import java.net.URL;
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
    Span span = tracer.buildSpan("foo").startManual();
    span.finish();

    tracer.close();

    Thread.sleep(5000);

    Request request = new Request.Builder()
        .url(queryUrl + "api/traces?service=service1")
        .get()
        .build();

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

    Thread.sleep(5000);

    Request request = new Request.Builder()
        .url(queryUrl + "api/dependencies?endTs=" + System.currentTimeMillis())
        .get()
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      assertEquals(200, response.code());
      String body = response.body().string();
      System.out.println(body);
      assertTrue(body.contains("service11"));
      assertTrue(body.contains("service22"));
    }
  }

  protected com.uber.jaeger.Tracer createTracer(String serviceName) {
    return new com.uber.jaeger.Tracer.Builder(serviceName,
        new RemoteReporter(new HttpSender(collectorUrl + "api/traces", 65000), 1, 100,
            new Metrics(new StatsFactoryImpl(new NullStatsReporter()))), new ConstSampler(true))
        .build();
  }
}
