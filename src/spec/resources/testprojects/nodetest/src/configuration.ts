/**
 * Configuration for API clients.
 *
 * Holds settings that apply to all API requests such as the base URL,
 * default headers, TLS options, proxy, timeout, and retry policy.
 *
 * This class is immutable. Use {@link Configuration.builder} to create instances:
 *
 * ```typescript
 * const config = Configuration.builder()
 *   .baseUrl('https://api.example.com')
 *   .defaultHeader('Authorization', 'Bearer token')
 *   .verifySsl(false)
 *   .build();
 * ```
 */
export class Configuration {
  private static defaultInstance: Configuration | null = null;

  /** Base URL for all API requests. */
  public readonly baseUrl: string;

  /** Headers to include in every API request. */
  public readonly defaultHeaders: Readonly<Record<string, string>>;

  /** Enable debug logging of HTTP requests and responses. */
  public readonly debug: boolean;

  /** Enable SSL/TLS certificate verification. */
  public readonly verifySsl: boolean;

  /** Path to a CA certificate file for SSL/TLS verification. */
  public readonly sslCaCert: string | null;

  /** Path to a client certificate file for mutual TLS authentication. */
  public readonly certFile: string | null;

  /** Path to a client private key file for mutual TLS authentication. */
  public readonly keyFile: string | null;

  /** Proxy URL for all API requests. */
  public readonly proxy: string | null;

  /** Request timeout in seconds. null means no timeout. */
  public readonly timeout: number | null;

  /** Number of retry attempts for failed requests. null means no retries. */
  public readonly retries: number | null;

  constructor(
    options?: Partial<{
      baseUrl: string;
      defaultHeaders: Record<string, string>;
      debug: boolean;
      verifySsl: boolean;
      sslCaCert: string | null;
      certFile: string | null;
      keyFile: string | null;
      proxy: string | null;
      timeout: number | null;
      retries: number | null;
    }>
  ) {
    this.baseUrl = options?.baseUrl ?? '/api/v3';
    this.defaultHeaders = Object.freeze({ ...(options?.defaultHeaders ?? {}) });
    this.debug = options?.debug ?? false;
    this.verifySsl = options?.verifySsl ?? true;
    this.sslCaCert = options?.sslCaCert ?? null;
    this.certFile = options?.certFile ?? null;
    this.keyFile = options?.keyFile ?? null;
    this.proxy = options?.proxy ?? null;
    this.timeout = options?.timeout ?? null;
    this.retries = options?.retries ?? null;
  }

  /**
   * Create a new builder for constructing Configuration instances.
   */
  public static builder(): ConfigurationBuilder {
    return new ConfigurationBuilder();
  }

  /**
   * Return the default configuration instance, creating it lazily if needed.
   */
  public static getDefault(): Configuration {
    if (Configuration.defaultInstance === null) {
      Configuration.defaultInstance = new Configuration();
    }
    return Configuration.defaultInstance;
  }

  /**
   * Set the default configuration instance.
   */
  public static setDefault(configuration: Configuration | null): void {
    Configuration.defaultInstance = configuration;
  }
}

/**
 * Builder for creating immutable {@link Configuration} instances.
 */
export class ConfigurationBuilder {
  private _baseUrl: string = '/api/v3';
  private _defaultHeaders: Record<string, string> = {};
  private _debug: boolean = false;
  private _verifySsl: boolean = true;
  private _sslCaCert: string | null = null;
  private _certFile: string | null = null;
  private _keyFile: string | null = null;
  private _proxy: string | null = null;
  private _timeout: number | null = null;
  private _retries: number | null = null;

  /** Set the base URL for all API requests. */
  baseUrl(baseUrl: string): this {
    this._baseUrl = baseUrl;
    return this;
  }

  /** Add a default header to include in every API request. */
  defaultHeader(name: string, value: string): this {
    this._defaultHeaders[name] = value;
    return this;
  }

  /** Set all default headers to include in every API request. */
  defaultHeaders(headers: Record<string, string>): this {
    Object.assign(this._defaultHeaders, headers);
    return this;
  }

  /** Enable or disable debug logging. */
  debug(debug: boolean): this {
    this._debug = debug;
    return this;
  }

  /** Enable or disable SSL/TLS certificate verification. */
  verifySsl(verifySsl: boolean): this {
    this._verifySsl = verifySsl;
    return this;
  }

  /** Set the path to a CA certificate file for SSL/TLS verification. */
  sslCaCert(sslCaCert: string | null): this {
    this._sslCaCert = sslCaCert;
    return this;
  }

  /** Set the path to a client certificate file for mutual TLS. */
  certFile(certFile: string | null): this {
    this._certFile = certFile;
    return this;
  }

  /** Set the path to a client private key file for mutual TLS. */
  keyFile(keyFile: string | null): this {
    this._keyFile = keyFile;
    return this;
  }

  /** Set the proxy URL for all API requests. */
  proxy(proxy: string | null): this {
    this._proxy = proxy;
    return this;
  }

  /** Set the request timeout in seconds. */
  timeout(timeout: number | null): this {
    this._timeout = timeout;
    return this;
  }

  /** Set the number of retry attempts for failed requests. */
  retries(retries: number | null): this {
    this._retries = retries;
    return this;
  }

  /** Build and return an immutable Configuration instance. */
  build(): Configuration {
    return new Configuration({
      baseUrl: this._baseUrl,
      defaultHeaders: this._defaultHeaders,
      debug: this._debug,
      verifySsl: this._verifySsl,
      sslCaCert: this._sslCaCert,
      certFile: this._certFile,
      keyFile: this._keyFile,
      proxy: this._proxy,
      timeout: this._timeout,
      retries: this._retries
    });
  }
}
