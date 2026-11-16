package isep.sidis.common.http;

import isep.sidis.common.config.AisafeProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;

public final class HttpClientFactory {
    private HttpClientFactory() {
    }

    public static ClientHttpRequestFactory create(AisafeProperties props, ResourceLoader resourceLoader) {
        HttpClient.Builder builder = HttpClient.newBuilder()

                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.getHttp().getConnectTimeout());

        AisafeProperties.Tls tls = props.getTls();
        if (tls.getTrustStore() != null && !tls.getTrustStore().isBlank()) {
            builder.sslContext(sslContext(tls, resourceLoader.getResource(tls.getTrustStore())));
        }

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(builder.build());
        factory.setReadTimeout(props.getHttp().getReadTimeout());
        return factory;
    }

    private static SSLContext sslContext(AisafeProperties.Tls tls, Resource trustStore) {
        try (InputStream in = trustStore.getInputStream()) {
            KeyStore ks = KeyStore.getInstance(tls.getTrustStoreType());
            char[] password = tls.getTrustStorePassword() == null ? null : tls.getTrustStorePassword().toCharArray();
            ks.load(in, password);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Could not load trust store " + tls.getTrustStore(), e);
        }
    }
}
