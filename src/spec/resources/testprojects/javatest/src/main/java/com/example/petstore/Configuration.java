package com.example.petstore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Configuration for API clients.
 *
 * <p>This class is immutable. Use {@link #builder()} to create instances:
 *
 * <pre>{@code
 * Configuration config = Configuration.builder()
 *     .baseUrl("https://api.example.com")
 *     .defaultHeader("Authorization", "Bearer token")
 *     .verifySsl(false)
 *     .build();
 * }</pre>
 */
public final class Configuration {

  @Nullable private static volatile Configuration defaultInstance;

  private final String baseUrl;
  private final Map<String, String> defaultHeaders;
  private final boolean debug;
  private final boolean verifySsl;
  @Nullable private final String sslCaCert;
  @Nullable private final String certFile;
  @Nullable private final String keyFile;
  @Nullable private final String proxy;
  @Nullable private final Integer timeout;
  @Nullable private final Integer retries;

  Configuration(
      String baseUrl,
      Map<String, String> defaultHeaders,
      boolean debug,
      boolean verifySsl,
      @Nullable String sslCaCert,
      @Nullable String certFile,
      @Nullable String keyFile,
      @Nullable String proxy,
      @Nullable Integer timeout,
      @Nullable Integer retries) {
    this.baseUrl = baseUrl;
    this.defaultHeaders = Collections.unmodifiableMap(new HashMap<>(defaultHeaders));
    this.debug = debug;
    this.verifySsl = verifySsl;
    this.sslCaCert = sslCaCert;
    this.certFile = certFile;
    this.keyFile = keyFile;
    this.proxy = proxy;
    this.timeout = timeout;
    this.retries = retries;
  }

  /**
   * Create a new builder for constructing Configuration instances.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Return the default configuration instance.
   *
   * @return the default configuration
   */
  public static Configuration getDefault() {
    if (defaultInstance == null) {
      defaultInstance = builder().build();
    }
    return defaultInstance;
  }

  /**
   * Set the default configuration instance.
   *
   * @param config the configuration to use as default
   */
  public static void setDefault(Configuration config) {
    defaultInstance = config;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public Map<String, String> getDefaultHeaders() {
    return defaultHeaders;
  }

  public boolean isDebug() {
    return debug;
  }

  public boolean isVerifySsl() {
    return verifySsl;
  }

  @Nullable
  public String getSslCaCert() {
    return sslCaCert;
  }

  @Nullable
  public String getCertFile() {
    return certFile;
  }

  @Nullable
  public String getKeyFile() {
    return keyFile;
  }

  @Nullable
  public String getProxy() {
    return proxy;
  }

  @Nullable
  public Integer getTimeout() {
    return timeout;
  }

  @Nullable
  public Integer getRetries() {
    return retries;
  }

  /** Builder for creating immutable {@link Configuration} instances. */
  public static final class Builder {

    private String baseUrl = "/api/v3";
    private final Map<String, String> defaultHeaders = new HashMap<>();
    private boolean debug = false;
    private boolean verifySsl = true;
    @Nullable private String sslCaCert = null;
    @Nullable private String certFile = null;
    @Nullable private String keyFile = null;
    @Nullable private String proxy = null;
    @Nullable private Integer timeout = null;
    @Nullable private Integer retries = null;

    Builder() {}

    /** Set the base URL for all API requests. */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /** Add a default header to include in every API request. */
    public Builder defaultHeader(String name, String value) {
      this.defaultHeaders.put(name, value);
      return this;
    }

    /** Set all default headers to include in every API request. */
    public Builder defaultHeaders(Map<String, String> headers) {
      this.defaultHeaders.putAll(headers);
      return this;
    }

    /** Enable or disable debug logging. */
    public Builder debug(boolean debug) {
      this.debug = debug;
      return this;
    }

    /** Enable or disable SSL/TLS certificate verification. */
    public Builder verifySsl(boolean verifySsl) {
      this.verifySsl = verifySsl;
      return this;
    }

    /** Set the path to a CA certificate file for SSL/TLS verification. */
    public Builder sslCaCert(@Nullable String sslCaCert) {
      this.sslCaCert = sslCaCert;
      return this;
    }

    /** Set the path to a client certificate file for mutual TLS. */
    public Builder certFile(@Nullable String certFile) {
      this.certFile = certFile;
      return this;
    }

    /** Set the path to a client private key file for mutual TLS. */
    public Builder keyFile(@Nullable String keyFile) {
      this.keyFile = keyFile;
      return this;
    }

    /** Set the proxy URL for all API requests. */
    public Builder proxy(@Nullable String proxy) {
      this.proxy = proxy;
      return this;
    }

    /** Set the request timeout in seconds. */
    public Builder timeout(@Nullable Integer timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Set the number of retry attempts for failed requests. */
    public Builder retries(@Nullable Integer retries) {
      this.retries = retries;
      return this;
    }

    /** Build and return an immutable Configuration instance. */
    public Configuration build() {
      return new Configuration(
          baseUrl,
          new HashMap<>(defaultHeaders),
          debug,
          verifySsl,
          sslCaCert,
          certFile,
          keyFile,
          proxy,
          timeout,
          retries);
    }
  }
}
