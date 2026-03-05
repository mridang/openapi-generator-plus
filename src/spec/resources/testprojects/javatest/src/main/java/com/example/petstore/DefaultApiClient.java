package com.example.petstore;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/** Default implementation of {@link ApiClient} using Apache HttpClient 5. */
public class DefaultApiClient implements ApiClient {

  private final CloseableHttpClient httpClient;

  /** Create a client with default settings. */
  public DefaultApiClient() {
    this(HttpClients.createDefault());
  }

  /**
   * Create a client configured from the given {@link Configuration}.
   *
   * <p>Applies proxy, custom CA certificate, and TLS verification settings.
   *
   * @param config configuration to apply
   */
  public DefaultApiClient(Configuration config) {
    try {
      HttpClientBuilder builder = HttpClients.custom();

      if (config.getProxy() != null) {
        builder.setProxy(HttpHost.create(config.getProxy()));
      }

      SSLContext sslContext;
      if (!config.isVerifySsl()) {
        sslContext =
            SSLContextBuilder.create().loadTrustMaterial(TrustAllStrategy.INSTANCE).build();
      } else if (config.getSslCaCert() != null) {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert;
        try (FileInputStream fis = new FileInputStream(config.getSslCaCert())) {
          caCert = (X509Certificate) cf.generateCertificate(fis);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);
        sslContext = SSLContextBuilder.create().loadTrustMaterial(trustStore, null).build();
      } else {
        sslContext = SSLContexts.createDefault();
      }

      SSLConnectionSocketFactoryBuilder sslSocketBuilder =
          SSLConnectionSocketFactoryBuilder.create().setSslContext(sslContext);

      if (!config.isVerifySsl()) {
        sslSocketBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      builder.setConnectionManager(
          PoolingHttpClientConnectionManagerBuilder.create()
              .setSSLSocketFactory(sslSocketBuilder.build())
              .build());

      this.httpClient = builder.build();
    } catch (GeneralSecurityException | IOException | URISyntaxException e) {
      throw new RuntimeException("Failed to configure SSL/TLS", e);
    }
  }

  /**
   * Create a client with a pre-configured {@link CloseableHttpClient}.
   *
   * @param httpClient the HTTP client to use
   */
  public DefaultApiClient(CloseableHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public ApiResponse sendRequest(
      String method, String url, Map<String, String> headers, String body) throws ApiException {
    ClassicRequestBuilder builder = ClassicRequestBuilder.create(method).setUri(url);

    for (Map.Entry<String, String> header : headers.entrySet()) {
      builder.addHeader(header.getKey(), header.getValue());
    }

    if (body != null) {
      builder.setEntity(
          new StringEntity(body, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
    }

    try (CloseableHttpResponse response = httpClient.execute(builder.build())) {
      int statusCode = response.getCode();
      String responseBody =
          response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
      Map<String, String> responseHeaders = new HashMap<>();
      for (Header h : response.getHeaders()) {
        responseHeaders.put(h.getName(), h.getValue());
      }
      return new ApiResponse(statusCode, responseBody != null ? responseBody : "", responseHeaders);
    } catch (IOException | ParseException e) {
      throw new ApiException(e);
    }
  }
}
