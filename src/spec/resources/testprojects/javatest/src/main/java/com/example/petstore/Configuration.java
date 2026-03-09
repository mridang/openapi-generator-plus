package com.example.petstore;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Configuration for API clients.
 *
 * <p>Holds settings that apply to all API requests such as the base URL, default headers, TLS
 * options, proxy, timeout, and retry policy.
 */
public class Configuration {

  @Nullable private static volatile Configuration defaultInstance;

  /** Base URL for all API requests. */
  private String baseUrl = "/api/v3";

  /**
   * Headers to include in every API request. Use this for authentication (e.g. Authorization
   * header) and other custom headers.
   */
  private final Map<String, String> defaultHeaders = new HashMap<>();

  /** Enable debug logging of HTTP requests and responses. */
  private boolean debug = false;

  /** Enable SSL/TLS certificate verification. */
  private boolean verifySsl = true;

  /** Path to a CA certificate file for SSL/TLS verification. */
  @Nullable private String sslCaCert = null;

  /** Path to a client certificate file for mutual TLS authentication. */
  @Nullable private String certFile = null;

  /** Path to a client private key file for mutual TLS authentication. */
  @Nullable private String keyFile = null;

  /** Proxy URL for all API requests. */
  @Nullable private String proxy = null;

  /** Request timeout in seconds. null means no timeout. */
  @Nullable private Integer timeout = null;

  /** Number of retry attempts for failed requests. null means no retries. */
  @Nullable private Integer retries = null;

  /**
   * Return the default configuration instance, creating it lazily if needed.
   *
   * @return the default configuration
   */
  public static Configuration getDefault() {
    Configuration instance = defaultInstance;
    if (instance == null) {
      synchronized (Configuration.class) {
        instance = defaultInstance;
        if (instance == null) {
          instance = new Configuration();
          defaultInstance = instance;
        }
      }
    }
    return instance;
  }

  /**
   * Set the default configuration instance.
   *
   * @param configuration the configuration to use as default
   */
  public static void setDefault(Configuration configuration) {
    defaultInstance = configuration;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public Map<String, String> getDefaultHeaders() {
    return defaultHeaders;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public boolean isVerifySsl() {
    return verifySsl;
  }

  public void setVerifySsl(boolean verifySsl) {
    this.verifySsl = verifySsl;
  }

  @Nullable
  public String getSslCaCert() {
    return sslCaCert;
  }

  public void setSslCaCert(@Nullable String sslCaCert) {
    this.sslCaCert = sslCaCert;
  }

  @Nullable
  public String getCertFile() {
    return certFile;
  }

  public void setCertFile(@Nullable String certFile) {
    this.certFile = certFile;
  }

  @Nullable
  public String getKeyFile() {
    return keyFile;
  }

  public void setKeyFile(@Nullable String keyFile) {
    this.keyFile = keyFile;
  }

  @Nullable
  public String getProxy() {
    return proxy;
  }

  public void setProxy(@Nullable String proxy) {
    this.proxy = proxy;
  }

  @Nullable
  public Integer getTimeout() {
    return timeout;
  }

  public void setTimeout(@Nullable Integer timeout) {
    this.timeout = timeout;
  }

  @Nullable
  public Integer getRetries() {
    return retries;
  }

  public void setRetries(@Nullable Integer retries) {
    this.retries = retries;
  }
}
