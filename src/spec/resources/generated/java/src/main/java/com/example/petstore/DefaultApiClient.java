package com.example.petstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Default implementation of {@link ApiClient} using {@link java.net.http.HttpClient}.
 *
 * <p>Applies transport-level settings from {@link TransportOptions}: TLS verification, custom CA
 * certificates, proxy routing, timeouts, redirect handling, {@code User-Agent} injection, {@code
 * X-Request-ID} injection, and transport-level default headers.
 *
 * <p>Header merge order (lowest to highest priority):
 *
 * <ol>
 *   <li>{@link TransportOptions#getDefaultHeaders()} — transport-level defaults
 *   <li>Caller-provided headers (from {@code BaseApi} — includes config defaults, auth, operation
 *       headers)
 *   <li>{@link TransportOptions#getUserAgent()} — injected if not already set
 *   <li>{@link TransportOptions#isInjectRequestId()} — injected if not already set
 * </ol>
 */
public final class DefaultApiClient implements ApiClient {

  private static final X509TrustManager TRUST_ALL_MANAGER =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };

  private final HttpClient httpClient;
  private final TransportOptions transportOptions;

  /**
   * Create a client with default transport settings.
   *
   * <p>Equivalent to {@code new DefaultApiClient(TransportOptions.builder().build())}.
   */
  public DefaultApiClient() {
    this(TransportOptions.builder().build());
  }

  /**
   * Create a client configured from the given {@link TransportOptions}.
   *
   * <p>Applies proxy, custom CA certificate, TLS verification, timeout, and redirect settings to
   * the underlying {@link HttpClient}.
   *
   * @param transportOptions transport configuration to apply
   */
  public DefaultApiClient(TransportOptions transportOptions) {
    this.transportOptions = transportOptions;
    try {
      HttpClient.Builder builder = HttpClient.newBuilder();

      if (transportOptions.getProxy() != null) {
        URI proxyUri = URI.create(transportOptions.getProxy());
        int port = proxyUri.getPort();
        if (port == -1) {
          port = "https".equals(proxyUri.getScheme()) ? 443 : 8080;
        }
        builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), port)));
      }

      if (!transportOptions.isVerifySsl()) {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {TRUST_ALL_MANAGER}, null);
        builder.sslContext(sslContext);
      } else if (transportOptions.getCaCertPath() != null) {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert;
        try (FileInputStream fis = new FileInputStream(transportOptions.getCaCertPath())) {
          caCert = (X509Certificate) cf.generateCertificate(fis);
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCert);
        TrustManagerFactory tmf =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        builder.sslContext(sslContext);
      }

      builder.followRedirects(
          transportOptions.isFollowRedirects()
              ? HttpClient.Redirect.NORMAL
              : HttpClient.Redirect.NEVER);

      if (transportOptions.getTimeout() != null) {
        builder.connectTimeout(Duration.ofMillis(transportOptions.getTimeout()));
      }

      this.httpClient = builder.build();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Failed to configure SSL/TLS", e);
    }
  }

  /**
   * Create a client with a pre-configured {@link HttpClient}.
   *
   * <p>Uses default {@link TransportOptions} for header injection settings.
   *
   * @param httpClient the HTTP client to use
   */
  public DefaultApiClient(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.transportOptions = TransportOptions.builder().build();
  }

  @Override
  @SuppressWarnings("unchecked")
  public ApiResponse sendRequest(
      String method, String url, Map<String, String> headers, @Nullable Object body)
      throws ApiException {

    Map<String, String> mergedHeaders = new HashMap<>(transportOptions.getDefaultHeaders());
    mergedHeaders.putAll(headers);

    if (transportOptions.getUserAgent() != null && !mergedHeaders.containsKey("User-Agent")) {
      mergedHeaders.put("User-Agent", transportOptions.getUserAgent());
    }
    if (transportOptions.isInjectRequestId() && !mergedHeaders.containsKey("X-Request-ID")) {
      mergedHeaders.put("X-Request-ID", UUID.randomUUID().toString());
    }

    HttpRequest.BodyPublisher bodyPublisher;
    if (body == null) {
      bodyPublisher = HttpRequest.BodyPublishers.noBody();
    } else if (body instanceof Map) {
      String boundary = UUID.randomUUID().toString();
      mergedHeaders.put("Content-Type", "multipart/form-data; boundary=" + boundary);
      bodyPublisher = buildMultipartBody((Map<String, Object>) body, boundary);
    } else if (body instanceof byte[] bytes) {
      bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
    } else if (body instanceof InputStream stream) {
      bodyPublisher = HttpRequest.BodyPublishers.ofInputStream(() -> stream);
    } else {
      bodyPublisher = HttpRequest.BodyPublishers.ofString(body.toString());
    }

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url)).method(method, bodyPublisher);

    if (transportOptions.getTimeout() != null) {
      builder.timeout(Duration.ofMillis(transportOptions.getTimeout()));
    }

    if (!mergedHeaders.containsKey("Accept-Encoding")) {
      builder.header("Accept-Encoding", getSupportedEncodings());
    }

    for (Map.Entry<String, String> header : mergedHeaders.entrySet()) {
      builder.header(header.getKey(), header.getValue());
    }

    try {
      HttpResponse<byte[]> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

      Map<String, String> responseHeaders = new HashMap<>();
      response
          .headers()
          .map()
          .forEach(
              (name, values) -> {
                if (!values.isEmpty()) {
                  responseHeaders.put(name, values.get(0));
                }
              });

      String contentEncoding = response.headers().firstValue("content-encoding").orElse("identity");
      byte[] bodyBytes = response.body() != null ? response.body() : new byte[0];
      String responseBody = decompressBody(bodyBytes, contentEncoding);

      return new ApiResponse(response.statusCode(), responseBody, responseHeaders);
    } catch (IOException e) {
      throw new ApiException(e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e.toString());
    }
  }

  /**
   * Decompress response body bytes based on the Content-Encoding header value.
   *
   * <p>Supports gzip and deflate natively. Brotli and zstd are supported when their respective
   * libraries ({@code org.brotli:dec} and {@code com.github.luben:zstd-jni}) are on the classpath.
   *
   * @param data the raw response bytes
   * @param encoding the Content-Encoding header value
   * @return the decompressed body as a UTF-8 string
   */
  private static String decompressBody(byte[] data, String encoding) throws IOException {
    if (data.length == 0) {
      return "";
    }
    byte[] decompressed =
        switch (encoding.toLowerCase(Locale.ROOT)) {
          case "gzip", "x-gzip" -> {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
              yield gis.readAllBytes();
            }
          }
          case "deflate" -> {
            try (InflaterInputStream iis =
                new InflaterInputStream(new ByteArrayInputStream(data))) {
              yield iis.readAllBytes();
            }
          }
          case "br" -> decompressBrotli(data);
          case "zstd" -> decompressZstd(data);
          default -> data;
        };
    return new String(decompressed, StandardCharsets.UTF_8);
  }

  private static byte[] decompressBrotli(byte[] data) throws IOException {
    try {
      Class<?> brotliClass = Class.forName("org.brotli.dec.BrotliInputStream");
      try (InputStream bis =
          (InputStream)
              brotliClass
                  .getConstructor(InputStream.class)
                  .newInstance(new ByteArrayInputStream(data))) {
        return bis.readAllBytes();
      }
    } catch (ClassNotFoundException e) {
      return data;
    } catch (ReflectiveOperationException e) {
      throw new IOException("Failed to decompress brotli response", e);
    }
  }

  private static byte[] decompressZstd(byte[] data) {
    try {
      Class<?> zstdClass = Class.forName("com.github.luben.zstd.Zstd");
      long originalSize =
          (long) zstdClass.getMethod("decompressedSize", byte[].class).invoke(null, data);
      int size = originalSize > 0 ? (int) originalSize : data.length * 4;
      return (byte[])
          zstdClass.getMethod("decompress", byte[].class, int.class).invoke(null, data, size);
    } catch (ClassNotFoundException e) {
      return data;
    } catch (ReflectiveOperationException e) {
      return data;
    }
  }

  @SuppressWarnings("EmptyCatch")
  private static String getSupportedEncodings() {
    StringBuilder sb = new StringBuilder("gzip, deflate");
    try {
      Class.forName("org.brotli.dec.BrotliInputStream");
      sb.append(", br");
    } catch (ClassNotFoundException ignored) {
    }
    try {
      Class.forName("com.github.luben.zstd.Zstd");
      sb.append(", zstd");
    } catch (ClassNotFoundException ignored) {
    }
    return sb.toString();
  }

  /**
   * Build a multipart/form-data request body from a map of form fields.
   *
   * <p>Each entry value may be a {@link File}, {@code byte[]}, {@link java.util.List}, or any other
   * object (model objects are JSON-serialized, primitives are converted to string text parts).
   *
   * @param formFields the form field names and values
   * @param boundary the multipart boundary string
   * @return a body publisher for the multipart content
   */
  private HttpRequest.BodyPublisher buildMultipartBody(
      Map<String, Object> formFields, String boundary) {
    var byteArrays = new java.util.ArrayList<byte[]>();

    for (Map.Entry<String, Object> entry : formFields.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof java.util.List<?> list) {
        for (Object item : list) {
          addMultipartField(byteArrays, boundary, fieldName, item);
        }
      } else {
        addMultipartField(byteArrays, boundary, fieldName, value);
      }
    }
    byteArrays.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

    int totalLength = 0;
    for (byte[] arr : byteArrays) {
      totalLength += arr.length;
    }
    byte[] result = new byte[totalLength];
    int offset = 0;
    for (byte[] arr : byteArrays) {
      System.arraycopy(arr, 0, result, offset, arr.length);
      offset += arr.length;
    }
    return HttpRequest.BodyPublishers.ofByteArray(result);
  }

  private void addMultipartField(
      List<byte[]> byteArrays, String boundary, String fieldName, Object value) {
    byte[] separator =
        ("--" + boundary + "\r\nContent-Disposition: form-data; name=")
            .getBytes(StandardCharsets.UTF_8);
    byteArrays.add(separator);

    if (value instanceof File file) {
      String fileName = file.getName();
      String mimeType;
      try {
        mimeType = Files.probeContentType(file.toPath());
      } catch (IOException e) {
        mimeType = "application/octet-stream";
      }
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }
      byteArrays.add(
          ("\""
                  + fieldName
                  + "\"; filename=\""
                  + fileName
                  + "\"\r\n"
                  + "Content-Type: "
                  + mimeType
                  + "\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      try {
        byteArrays.add(Files.readAllBytes(file.toPath()));
      } catch (IOException e) {
        throw new RuntimeException("Failed to read file: " + file, e);
      }
    } else if (value instanceof byte[] bytes) {
      byteArrays.add(
          ("\""
                  + fieldName
                  + "\"; filename=\""
                  + fieldName
                  + "\"\r\n"
                  + "Content-Type: application/octet-stream\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      byteArrays.add(bytes);
    } else if (value instanceof InputStream stream) {
      try {
        byteArrays.add(
            ("\""
                    + fieldName
                    + "\"; filename=\""
                    + fieldName
                    + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        byteArrays.add(stream.readAllBytes());
      } catch (IOException e) {
        throw new RuntimeException("Failed to read stream: " + fieldName, e);
      }
    } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      byteArrays.add(("\"" + fieldName + "\"\r\n\r\n" + value).getBytes(StandardCharsets.UTF_8));
    } else {
      try {
        String json = new ObjectMapper().writeValueAsString(value);
        byteArrays.add(
            ("\"" + fieldName + "\"\r\nContent-Type: application/json\r\n\r\n" + json)
                .getBytes(StandardCharsets.UTF_8));
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        byteArrays.add(("\"" + fieldName + "\"\r\n\r\n" + value).getBytes(StandardCharsets.UTF_8));
      }
    }
    byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
  }
}
