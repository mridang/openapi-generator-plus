# frozen_string_literal: true

require 'test_helper'

# Guards the claims the README makes about the SDK's own API.
describe 'README' do
  parallelize_me!

  def readme
    File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'README.md'))
  end

  it 'names the real proxy setter in the SOCKS section' do
    _(readme).must_include '`TransportOptions.builder.proxy(url)` accepts only `http://` and `https://` URLs.'
    _(readme).wont_include 'TransportOptions.proxy()'
  end

  it 'makes no claim about how the other SDKs treat a discriminator no-match' do
    _(readme).must_include 'raises'
    _(readme).wont_include 'This matches Python'
  end
end
