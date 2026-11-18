# frozen_string_literal: true

require 'test_helper'

describe Petstore::Client::CallHook do
  parallelize_me!

  let(:info) do
    Petstore::Client::CallInfo.new(
      operation_id: 'getItem', http_method: 'GET', url_template: '/items/{id}',
      url: 'https://api.example.com/items/7', server_address: 'api.example.com'
    )
  end

  let(:outcome) { Petstore::Client::CallOutcome.new(status_code: 200, headers: {}, error: nil) }

  it 'bhv_hooks_020 chain starts the hooks in order and finishes their observers in reverse order' do
    log = [] # : Array[String]
    first = RecordingCallHook.new('first', log)
    second = RecordingCallHook.new('second', log)
    third = RecordingCallHook.new('third', log)

    observer = Petstore::Client::CallHook.chain(first, second, third).start(info, {})
    observer.finish(outcome)

    _(log).must_equal ['start first', 'start second', 'start third', 'finish third', 'finish second', 'finish first']
  end

  it 'bhv_hooks_020 chain skips a hook that returns nothing and gives the others the same headers' do
    log = [] # : Array[String]
    silent = Object.new
    silent.define_singleton_method(:start) do |_info, headers|
      headers['X-Silent'] = 'yes'
      nil
    end
    last = RecordingCallHook.new('last', log)
    headers = {} # : Hash[String, String]

    observer = Petstore::Client::CallHook.chain(silent, last).start(info, headers)
    observer.finish(outcome)

    _(log).must_equal ['start last', 'finish last']
    _(last.seen_headers.first).must_equal({ 'X-Silent' => 'yes' })
    _(last.outcomes).must_equal [outcome]
  end

  it 'finishes every observer of a chain although one of them fails' do
    failing = Object.new
    failing.define_singleton_method(:start) do |_info, _headers|
      observer = Object.new
      observer.define_singleton_method(:finish) { |_outcome| raise 'finish failed' }
      observer
    end
    after = RecordingCallHook.new('after')

    Petstore::Client::CallHook.chain(after, failing).start(info, {}).finish(outcome)

    _(after.outcomes).must_equal [outcome]
  end

  it 'carries what a call is described by in frozen values' do
    _(info.operation_id).must_equal 'getItem'
    _(info.http_method).must_equal 'GET'
    _(info.url_template).must_equal '/items/{id}'
    _(info.url).must_equal 'https://api.example.com/items/7'
    _(info.server_address).must_equal 'api.example.com'
    _(info).must_be :frozen?
    _(outcome.status_code).must_equal 200
    _(outcome.headers).must_equal({})
    _(outcome.error).must_be_nil
    _(outcome).must_be :frozen?
  end
end
