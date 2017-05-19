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

import java.io.IOException;
import java.net.URL;

import org.arquillian.cube.kubernetes.annotations.Named;
import org.arquillian.cube.kubernetes.annotations.PortForward;
import org.arquillian.cube.kubernetes.impl.requirement.RequiresKubernetes;
import org.arquillian.cube.requirement.ArquillianConditionalRunner;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.fabric8.kubernetes.api.model.v2_2.Service;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Pavol Loffay
 */
@RequiresKubernetes
@RunWith(ArquillianConditionalRunner.class)
public class AllInOneTest {
    private static final String SERVICE_NAME = "jaeger-all-in-one";

    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();

    @Named(SERVICE_NAME)
    @ArquillianResource
    private Service jaegerService;

    @Named(SERVICE_NAME)
    @PortForward
    @ArquillianResource
    private URL jaegerUiUrl;

    @Test
    public void testUiResponds() throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url(jaegerUiUrl)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());
        }
    }
}
