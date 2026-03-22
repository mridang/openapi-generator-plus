# frozen_string_literal: true

# rubocop:disable Lint/RedundantCopDisableDirective, Layout/LineLength
# rubocop:disable Metrics/AbcSize, Metrics/ClassLength, Metrics/MethodLength, Naming/AccessorMethodName
# rubocop:disable Style/DefWithParentheses
# rubocop:disable Style/StringConcatenation

require 'cgi'

# :nodoc:
module PetstoreClient
  module Api
    # PetApi provides methods for the Pet API group.
    # Everything about your Pets
    # @see https://example.com/docs/pets Find out more about pets
    class PetApi < BaseApi
      def initialize(api_client = nil, config = PetstoreClient::Configuration.default)
        super
      end

      # Add a new pet to the store
      # @param auth [Auth::Authenticator] authenticator for this operation
      # @param pet [Pet] Create a new pet in the store
      # @return [Pet]
      def add_pet(auth, pet)
        if pet.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet' when calling PetApi.add_pet"
        end

        path = '/pet'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = pet

        invoke_api(
          :POST, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Pet',
          auth
        )
      end

      # Add photos to the pet&#39;s gallery
      # Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
      # @param pet_id [Integer]
      # @param files [Array<File>]
      # @param metadata [PhotoMetadata]
      # @return [Array<Photo>]
      def add_pet_photos(pet_id, files:, metadata:)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.add_pet_photos"
        end

        if files.nil?
          raise ArgumentError,
                "Missing the required parameter 'files' when calling PetApi.add_pet_photos"
        end

        if metadata.nil?
          raise ArgumentError,
                "Missing the required parameter 'metadata' when calling PetApi.add_pet_photos"
        end

        path = '/pet/{petId}/photos'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        # @type var request_body: Hash[String, untyped]
        request_body = {}
        request_body['files'] = files
        request_body['metadata'] = metadata

        invoke_api(
          :POST, path, query_params, header_params, request_body,
          ['application/json'],
          'multipart/form-data',
          'Array<Photo>',
          nil
        )
      end

      # Deletes a pet
      # @param auth [Auth::Authenticator] authenticator for this operation
      # @param pet_id [Integer] Pet id to delete
      # @return [nil]
      def delete_pet(auth, pet_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.delete_pet"
        end

        path = '/pet/{petId}'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :DELETE, path, query_params, header_params, request_body,
          [],
          'application/json',
          nil,
          auth
        )
      end

      # Download a vet document
      # Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
      # @param pet_id [Integer]
      # @param document_id [Integer]
      # @return [File]
      def download_pet_document(pet_id, document_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.download_pet_document"
        end

        if document_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'document_id' when calling PetApi.download_pet_document"
        end

        path = '/pet/{petId}/documents/{documentId}'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        path = path.sub('{documentId}', PetstoreClient::ValueSerializer.serialize(document_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/octet-stream'],
          'application/json',
          'File',
          nil
        )
      end

      # Finds Pets by status
      # @param status [String] Status values that need to be considered for filter (optional) (deprecated)
      # @return [Array<Pet>]
      # @deprecated This operation is deprecated.
      # @see https://example.com/docs/filtering Find out more about filtering
      def find_pets_by_status(status: nil)
        path = '/pet/findByStatus'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        unless status.nil?
          query_params['status'] =
            PetstoreClient::ValueSerializer.serialize(status, :query, 'String')
        end
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Array<Pet>',
          nil
        )
      end

      # Get the pet&#39;s profile photo
      # Returns the raw image bytes of the pet&#39;s current avatar.
      # @param pet_id [Integer]
      # @return [File]
      def get_pet_avatar(pet_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.get_pet_avatar"
        end

        path = '/pet/{petId}/avatar'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['image/jpeg', 'image/png'],
          'application/json',
          'File',
          nil
        )
      end

      # Get the pet&#39;s avatar thumbnail as base64
      # Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
      # @param pet_id [Integer]
      # @return [String]
      def get_pet_avatar_thumbnail(pet_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.get_pet_avatar_thumbnail"
        end

        path = '/pet/{petId}/avatar/thumbnail'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'String',
          nil
        )
      end

      # Find pet by ID
      # Returns a single pet
      # @param pet_id [Integer] ID of pet to return
      # @return [Pet]
      # @deprecated This operation is deprecated.
      def get_pet_by_id(pet_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.get_pet_by_id"
        end

        path = '/pet/{petId}'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Pet',
          nil
        )
      end

      # Get the pet&#39;s passport
      # Returns a single JSON document combining the pet&#39;s profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
      # @param pet_id [Integer]
      # @return [PetPassport]
      def get_pet_passport(pet_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.get_pet_passport"
        end

        path = '/pet/{petId}/passport'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'PetPassport',
          nil
        )
      end

      # Get a photo or its metadata
      # Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
      # @param pet_id [Integer]
      # @param photo_id [Integer]
      # @return [File]
      def get_pet_photo(pet_id, photo_id)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.get_pet_photo"
        end

        if photo_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'photo_id' when calling PetApi.get_pet_photo"
        end

        path = '/pet/{petId}/photos/{photoId}'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        path = path.sub('{photoId}', PetstoreClient::ValueSerializer.serialize(photo_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['image/jpeg', 'image/png', 'application/json'],
          'application/json',
          'File',
          nil
        )
      end

      # Set the pet&#39;s profile photo
      # Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
      # @param pet_id [Integer]
      # @param body [File]
      # @return [nil]
      def set_pet_avatar(pet_id, body)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.set_pet_avatar"
        end

        if body.nil?
          raise ArgumentError,
                "Missing the required parameter 'body' when calling PetApi.set_pet_avatar"
        end

        path = '/pet/{petId}/avatar'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = body

        invoke_api(
          :PUT, path, query_params, header_params, request_body,
          [],
          'image/jpeg',
          nil,
          nil
        )
      end

      # Set the pet&#39;s avatar thumbnail as base64
      # Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
      # @param pet_id [Integer]
      # @param set_pet_avatar_thumbnail_request [SetPetAvatarThumbnailRequest]
      # @return [nil]
      def set_pet_avatar_thumbnail(pet_id, set_pet_avatar_thumbnail_request)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.set_pet_avatar_thumbnail"
        end

        if set_pet_avatar_thumbnail_request.nil?
          raise ArgumentError,
                "Missing the required parameter 'set_pet_avatar_thumbnail_request' when calling PetApi.set_pet_avatar_thumbnail"
        end

        path = '/pet/{petId}/avatar/thumbnail'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = set_pet_avatar_thumbnail_request

        invoke_api(
          :PUT, path, query_params, header_params, request_body,
          [],
          'application/json',
          nil,
          nil
        )
      end

      # Update an existing pet
      # @param pet_id [Integer] ID of pet to update
      # @param pet [Pet] Pet object that needs to be updated
      # @return [Pet]
      def update_pet(pet_id, pet)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.update_pet"
        end

        if pet.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet' when calling PetApi.update_pet"
        end

        path = '/pet/{petId}'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = pet

        invoke_api(
          :PUT, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Pet',
          nil
        )
      end

      # Upload the pet&#39;s adoption certificate
      # Attaches a single adoption certificate document. No metadata fields are required alongside the file.
      # @param pet_id [Integer]
      # @param file [File]
      # @return [ApiResponse]
      def upload_pet_certificate(pet_id, file:)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.upload_pet_certificate"
        end

        if file.nil?
          raise ArgumentError,
                "Missing the required parameter 'file' when calling PetApi.upload_pet_certificate"
        end

        path = '/pet/{petId}/certificate'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        # @type var request_body: Hash[String, untyped]
        request_body = {}
        request_body['file'] = file

        invoke_api(
          :POST, path, query_params, header_params, request_body,
          ['application/json'],
          'multipart/form-data',
          'ApiResponse',
          nil
        )
      end

      # Attach a vet document or health record
      # Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
      # @param pet_id [Integer]
      # @param file [File]
      # @param document_type [String] (optional)
      # @param notes [String] (optional)
      # @return [ApiResponse]
      def upload_pet_document(pet_id, file:, document_type: nil, notes: nil)
        if pet_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'pet_id' when calling PetApi.upload_pet_document"
        end

        if file.nil?
          raise ArgumentError,
                "Missing the required parameter 'file' when calling PetApi.upload_pet_document"
        end

        path = '/pet/{petId}/documents'
        path = path.sub('{petId}', PetstoreClient::ValueSerializer.serialize(pet_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        # @type var request_body: Hash[String, untyped]
        request_body = {}
        request_body['file'] = file
        request_body['documentType'] = document_type unless document_type.nil?
        request_body['notes'] = notes unless notes.nil?

        invoke_api(
          :POST, path, query_params, header_params, request_body,
          ['application/json'],
          'multipart/form-data',
          'ApiResponse',
          nil
        )
      end
    end
  end
end
# rubocop:enable Lint/RedundantCopDisableDirective, Layout/LineLength
# rubocop:enable Metrics/AbcSize, Metrics/ClassLength, Metrics/MethodLength, Naming/AccessorMethodName
# rubocop:enable Style/DefWithParentheses
# rubocop:enable Style/StringConcatenation
