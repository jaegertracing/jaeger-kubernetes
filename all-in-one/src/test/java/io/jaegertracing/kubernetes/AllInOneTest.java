package io.jaegertracing.kubernetes;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @author Pavol Loffay
 */
@RunWith(Arquillian.class)
public class AllInOneTest {
    private static final String SERVICE_NAME = "jaeger-all-in-one";
    private static final String TEMPLATE_NAME = "jaeger-all-in-one-template.yml";
    private static final String KUBERNETES_NAMESPACE = System.getenv("KUBERNETES_NAMESPACE");

    private static final int HTTP_CLIENT_TIMEOUT = 40;
    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(HTTP_CLIENT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(HTTP_CLIENT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(HTTP_CLIENT_TIMEOUT, TimeUnit.SECONDS)
            .build();

    @ArquillianResource
    private KubernetesClient client;

    @Before
    public void before() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        client.load(classLoader.getResource(TEMPLATE_NAME).openStream())
                .inNamespace(KUBERNETES_NAMESPACE)
                .createOrReplace();
    }

    @Test
    @RunAsClient
    public void testUiResponds() throws IOException, InterruptedException {
        waitForExternalIp(SERVICE_NAME);
        Service uiService = getService(SERVICE_NAME);
        String url = "http://" + getExternalIp(uiService);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            Assert.assertEquals(200, response.code());
        }
    }

    Service getService(String serviceName) {
        for (Service service: client.services().list().getItems()) {
            if (service.getMetadata().getName().equals(serviceName)) {
                return service;
            }
        }
        return null;
    }

    String getExternalIp(Service service) {
        return service.getStatus().getLoadBalancer().getIngress().get(0).getIp();
    }

    /**
     * Get an external IP address takes some time. It is a feature of cloud provider.
     * @param serviceName service name
     */
    void waitForExternalIp(String serviceName) {
        Awaitility.await()
                .atMost(5, TimeUnit.MINUTES)
                .pollDelay(1, TimeUnit.SECONDS)
                .until(() -> {
                    Service service = getService(serviceName);
                    return !service.getStatus().getLoadBalancer().getIngress().isEmpty();
                });
    }
}
