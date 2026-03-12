# frozen_string_literal: true

require 'spec_helper'

def get_env_or_skip(name)
  value = ENV[name]
  unless value && !value.empty?
    skip "Skipping: #{name} not set"
  end
  value
end

describe PetstoreClient::DefaultApiClient do
  describe 'TLS verification disabled' do
    it 'makes HTTPS request with verify_ssl=false' do
      wiremock_url = get_env_or_skip('WIREMOCK_HTTPS_URL')

      config = PetstoreClient::Configuration.default
      config.base_url = wiremock_url
      config.verify_ssl = false

      client = PetstoreClient::DefaultApiClient.new(config)
      response = client.send_request(:GET, "#{wiremock_url}/api/test", {}, nil)

      _(response.status_code).must_equal(200)
      _(response.body).must_include('success')
    end
  end

  describe 'custom CA bundle' do
    it 'makes HTTPS request with custom CA cert' do
      wiremock_url = get_env_or_skip('WIREMOCK_HTTPS_URL')
      ca_cert_path = get_env_or_skip('CA_CERT_PATH')

      config = PetstoreClient::Configuration.default
      config.base_url = wiremock_url
      config.verify_ssl = true
      config.ssl_ca_cert = ca_cert_path

      client = PetstoreClient::DefaultApiClient.new(config)
      response = client.send_request(:GET, "#{wiremock_url}/api/test", {}, nil)

      _(response.status_code).must_equal(200)
      _(response.body).must_include('success')
    end
  end

  describe 'HTTP proxy' do
    it 'makes HTTP request through proxy' do
      wiremock_url = get_env_or_skip('WIREMOCK_HTTP_URL')
      proxy_url = get_env_or_skip('PROXY_URL')

      config = PetstoreClient::Configuration.default
      config.base_url = wiremock_url
      config.proxy = proxy_url

      client = PetstoreClient::DefaultApiClient.new(config)
      response = client.send_request(:GET, "#{wiremock_url}/api/test", {}, nil)

      _(response.status_code).must_equal(200)
      _(response.body).must_include('success')
    end
  end

  describe 'HTTP proxy with TLS' do
    it 'makes HTTPS request through proxy with verify_ssl=false' do
      wiremock_url = get_env_or_skip('WIREMOCK_HTTPS_URL')
      proxy_url = get_env_or_skip('PROXY_URL')

      config = PetstoreClient::Configuration.default
      config.base_url = wiremock_url
      config.proxy = proxy_url
      config.verify_ssl = false

      client = PetstoreClient::DefaultApiClient.new(config)
      response = client.send_request(:GET, "#{wiremock_url}/api/test", {}, nil)

      _(response.status_code).must_equal(200)
      _(response.body).must_include('success')
    end
  end
end
