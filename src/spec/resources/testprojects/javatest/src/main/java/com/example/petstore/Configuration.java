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
   * Create a new default configuration.
   *
   * @return a new configuration with default settings
   */
  public static Configuration getDefault() {
    return new Configuration();
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
