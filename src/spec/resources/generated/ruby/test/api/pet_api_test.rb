# frozen_string_literal: true

# rubocop:disable Metrics/BlockLength, Lint/MissingCopEnableDirective

# Integration tests for the Pet API endpoints.

require 'test_helper'
require 'set'
require 'stringio'
require 'socket'

describe PetstoreClient::Api::PetApi do
  parallelize_me!

  before do
    @api = PetstoreClient::Api::PetApi.new
    @base_url = ENV['API_BASE_URL'] || 'http://localhost:4010'
    @auth = PetstoreClient::Auth::BearerAuthenticator.new(@base_url, 'test-token')
  end

  describe '#add_pet' do
    it 'creates a new pet' do
      pet = PetstoreClient::Models::Pet.new(
        id: 12_345,
        name: 'TestDog',
        photo_urls: Set['http://example.com/photo.jpg'],
        status: 'available'
      )

      result = @api.add_pet(pet, auth: @auth)

      _(result).wont_be_nil
      _(result.name).wont_be_nil
    end
  end

  describe '#find_pets_by_status' do
    it 'returns pets by status' do
      result = @api.find_pets_by_status(PetstoreClient::Api::Options::FindPetsByStatusOptions.new(status: 'available'))

      _(result).must_be_kind_of(Array)
      _(result).wont_be_empty
      _(result.first).must_be_kind_of(PetstoreClient::Models::Pet)
    end
  end

  describe '#get_pet_by_id' do
    it 'returns a pet by id' do
      result = @api.get_pet_by_id(1)

      _(result).wont_be_nil
      _(result.id).wont_be_nil
      _(result.name).wont_be_nil
    end
  end

  describe '#update_pet' do
    it 'updates an existing pet' do
      pet = PetstoreClient::Models::Pet.new(
        id: 1,
        name: 'UpdatedDog',
        photo_urls: Set['http://example.com/updated.jpg'],
        status: 'pending'
      )

      result = @api.update_pet(1, pet)

      _(result).wont_be_nil
    end
  end

  describe '#delete_pet' do
    it 'deletes a pet' do
      @api.delete_pet(1, auth: @auth)
    end
  end

  describe '#set_pet_avatar' do
    it 'uploads binary image data' do
      @api.set_pet_avatar(1, StringIO.new("\xFF\xD8\xFF"))
    end
  end

  describe '#get_pet_avatar' do
    it 'downloads the pet avatar as binary' do
      result = @api.get_pet_avatar(1)

      _(result).wont_be_nil
    end
  end

  describe '#get_pet_avatar_thumbnail' do
    it 'returns a base64-encoded thumbnail' do
      result = @api.get_pet_avatar_thumbnail(1)

      _(result).wont_be_nil
    end
  end

  describe '#set_pet_avatar_thumbnail' do
    it 'uploads a base64 thumbnail via JSON' do
      request = 'iVBORw0KGgoAAAANSUhEUg=='

      @api.set_pet_avatar_thumbnail(1, request)
    end
  end

  describe '#upload_pet_certificate' do
    it 'uploads a certificate via multipart' do
      options = PetstoreClient::Api::Options::UploadPetCertificateOptions.new(
        file: StringIO.new('cert-data')
      )
      result = @api.upload_pet_certificate(1, options)

      _(result).wont_be_nil
    end
  end

  describe '#upload_pet_document' do
    it 'uploads a document with metadata via multipart' do
      result = @api.upload_pet_document(
        1,
        PetstoreClient::Api::Options::UploadPetDocumentOptions.new(
          file: StringIO.new('doc-data'),
          document_type: 'vaccination_record',
          notes: 'Annual checkup'
        )
      )

      _(result).wont_be_nil
    end
  end

  describe '#add_pet_photos' do
    it 'uploads photos with metadata via multipart' do
      metadata = PetstoreClient::Models::PhotoMetadata.new(caption: 'Test photo', is_primary: true)

      result = @api.add_pet_photos(
        1,
        PetstoreClient::Api::Options::AddPetPhotosOptions.new(
          files: [StringIO.new('photo1')],
          metadata: metadata
        )
      )

      _(result).wont_be_nil
      _(result).must_be_kind_of(Array)
    end
  end

  describe '#download_pet_document' do
    it 'downloads a document as binary' do
      result = @api.download_pet_document(1, 1)

      _(result).wont_be_nil
    end
  end

  describe '#get_pet_photo' do
    it 'returns a photo via content negotiation' do
      result = @api.get_pet_photo(1, 1)

      _(result).wont_be_nil
    end
  end

  describe '#get_pet_passport' do
    it 'returns a passport with embedded byte fields' do
      result = @api.get_pet_passport(1)

      _(result).wont_be_nil
      _(result).must_be_kind_of(PetstoreClient::Models::PetPassport)
    end
  end

  describe '#get_pet_tag' do
    it 'sends styled path and query parameters' do
      result = @api.get_pet_tag(
        5,
        'cute',
        PetstoreClient::Api::Options::GetPetTagOptions.new(colors: %w[blue black], sizes: %w[S M])
      )

      _(result).wont_be_nil
    end
  end

  describe '#get_external_pet_info' do
    it 'uses per-operation server URL' do
      skip 'Per-operation server URL cannot be verified against mock server'
    end
  end

  describe 'error handling' do
    def new_pet_api_for_mock(status, content_type, body) # rubocop:disable Metrics/AbcSize, Metrics/MethodLength
      server = TCPServer.new('127.0.0.1', 0)
      port = server.addr[1]
      thread = Thread.new do
        loop do
          client = server.accept rescue break # rubocop:disable Style/RescueModifier
          client.gets # read request line
          while (line = client.gets)
            break if line.strip.empty?
          end
          response = "HTTP/1.1 #{status} OK\r\nContent-Type: #{content_type}\r\n" \
                     "Content-Length: #{body.bytesize}\r\nConnection: close\r\n\r\n#{body}"
          client.print(response)
          client.close
        end
      end

      config = PetstoreClient::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: {})
      api = PetstoreClient::Api::PetApi.new(nil, config)
      [api, server, thread]
    end

    it 'raises error on 404 response' do
      api, server, thread = new_pet_api_for_mock(404, 'application/json', '{"message":"Pet not found"}')
      begin
        _(-> { api.get_pet_by_id(99_999) }).must_raise StandardError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'raises error on 500 response' do
      api, server, thread = new_pet_api_for_mock(500, 'application/json', '{"message":"Internal server error"}')
      begin
        _(-> { api.get_pet_by_id(1) }).must_raise StandardError
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'handles binary download from mock' do
      api, server, thread = new_pet_api_for_mock(200, 'application/octet-stream', 'FAKE_BINARY_DATA')
      begin
        result = api.get_pet_avatar(1)
        _(result).wont_be_nil
      ensure
        server.close
        thread.join(2)
      end
    end

    it 'handles multipart upload from mock' do
      api, server, thread = new_pet_api_for_mock(200, 'application/json', '{"code":200,"type":"","message":"success"}')
      begin
        options = PetstoreClient::Api::Options::UploadPetCertificateOptions.new(
          file: StringIO.new('fake-cert-data')
        )
        result = api.upload_pet_certificate(1, options)
        _(result).wont_be_nil
      ensure
        server.close
        thread.join(2)
      end
    end

    # convenience-empty-body-handling: a body-returning operation that
    # receives an empty 200 body must raise a typed ApiError from the
    # unwrapped convenience method, never return a silent nil.
    it 'raises ApiError when a body-returning op gets an empty body' do
      api, server, thread = new_pet_api_for_mock(200, 'application/json', '')
      begin
        err = assert_raises(PetstoreClient::ApiError) do
          api.get_pet_by_id(1)
        end
        _(err.status_code).must_equal 200
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  # path-double-encoding: a styled path-param value containing reserved
  # characters must be percent-encoded EXACTLY ONCE. Before the fix the api
  # template wrapped the already-encoded serializer output in a second
  # encode_path_segment pass, turning a space into %2520 instead of %20.
  describe 'path encoding' do
    def capture_request_line(status, content_type, body) # rubocop:disable Metrics/MethodLength,Metrics/AbcSize
      server = TCPServer.new('127.0.0.1', 0)
      port = server.addr[1]
      captured = Queue.new
      thread = Thread.new do
        client = server.accept rescue next # rubocop:disable Style/RescueModifier
        captured << client.gets.to_s
        while (line = client.gets)
          break if line.strip.empty?
        end
        response = "HTTP/1.1 #{status} OK\r\nContent-Type: #{content_type}\r\n" \
                   "Content-Length: #{body.bytesize}\r\nConnection: close\r\n\r\n#{body}"
        client.print(response)
        client.close
      end
      config = PetstoreClient::Configuration.new(base_url: "http://127.0.0.1:#{port}", default_headers: {})
      api = PetstoreClient::Api::PetApi.new(nil, config)
      [api, server, thread, captured]
    end

    it 'percent-encodes a path param value exactly once' do
      body = '{"id":1,"name":"x","photoUrls":[],"status":"available"}'
      api, server, thread, captured = capture_request_line(200, 'application/json', body)
      begin
        # 'a b' (label style) serialises to '.a%20b'. The wrong (double)
        # encoding would emit '.a%2520b'.
        api.get_pet_tag(
          5,
          'a b',
          PetstoreClient::Api::Options::GetPetTagOptions.new
        )
        request_line = captured.pop
        _(request_line).must_include '%20'
        _(request_line).wont_include '%2520'
      ensure
        server.close
        thread.join(2)
      end
    end
  end

  describe 'with_http_info methods' do
    describe '#get_pet_by_id_with_http_info' do
      it 'returns HTTP info along with the response' do
        result = @api.get_pet_by_id_with_http_info(1)

        _(result).wont_be_nil
        _(result.status_code).must_equal 200
        _(result.data).wont_be_nil
        _(result.raw_body).wont_be_nil
      end
    end

    describe '#add_pet_with_http_info' do
      it 'returns HTTP info on successful pet creation' do
        pet = PetstoreClient::Models::Pet.new(
          id: 99,
          name: 'HttpInfoDog',
          photo_urls: Set['http://example.com/photo.jpg'],
          status: 'available'
        )

        result = @api.add_pet_with_http_info(pet, auth: @auth)

        _(result).wont_be_nil
        _(result.status_code).must_be :>=, 200
        _(result.status_code).must_be :<, 300
        _(result.data).wont_be_nil
      end
    end
  end
end
