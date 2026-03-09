require 'cgi'

module OpigenClient::Api
  # PetApi provides methods for the Pet API group.
  class PetApi < BaseApi
    def initialize(api_client = nil, config = OpigenClient::Configuration.default)
      super(api_client, config)
    end

    # Add a new pet to the store
    # @param pet [Pet] Create a new pet in the store
    # @return [Pet]
    def add_pet(pet)
      if pet.nil?
        fail ArgumentError, "Missing the required parameter 'pet' when calling PetApi.add_pet"
      end

      path = '/pet'
      # @type var query_params: Hash[String, untyped]
      query_params = {}
      # @type var header_params: Hash[String, String]
      header_params = {}
      body = pet

      invoke_api(:POST, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Pet')
    end

    # Deletes a pet
    # @param pet_id [Integer] Pet id to delete
    # @return [nil]
    def delete_pet(pet_id)
      if pet_id.nil?
        fail ArgumentError, "Missing the required parameter 'pet_id' when calling PetApi.delete_pet"
      end

      path = '/pet/{petId}'.sub('{' + 'petId' + '}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(pet_id)))
      # @type var query_params: Hash[String, untyped]
      query_params = {}
      # @type var header_params: Hash[String, String]
      header_params = {}
      body = nil

      invoke_api(:DELETE, path, query_params, header_params, body,
                 [],
                 'application/json',
                 nil)
    end

    # Finds Pets by status
    # @param [Hash] opts the optional parameters
    # @option opts [String] :status Status values that need to be considered for filter (default to 'available')
    # @return [Array<Pet>]
    def find_pets_by_status(opts = {})
      path = '/pet/findByStatus'
      # @type var query_params: Hash[String, untyped]
      query_params = {}
      query_params['status'] = OpigenClient::ObjectSerializer.to_query_value(opts[:status]) unless opts[:status].nil?
      # @type var header_params: Hash[String, String]
      header_params = {}
      body = nil

      invoke_api(:GET, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Array<Pet>')
    end

    # Find pet by ID
    # Returns a single pet
    # @param pet_id [Integer] ID of pet to return
    # @return [Pet]
    def get_pet_by_id(pet_id)
      if pet_id.nil?
        fail ArgumentError, "Missing the required parameter 'pet_id' when calling PetApi.get_pet_by_id"
      end

      path = '/pet/{petId}'.sub('{' + 'petId' + '}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(pet_id)))
      # @type var query_params: Hash[String, untyped]
      query_params = {}
      # @type var header_params: Hash[String, String]
      header_params = {}
      body = nil

      invoke_api(:GET, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Pet')
    end

    # Update an existing pet
    # @param pet_id [Integer] ID of pet to update
    # @param pet [Pet] Pet object that needs to be updated
    # @return [Pet]
    def update_pet(pet_id, pet)
      if pet_id.nil?
        fail ArgumentError, "Missing the required parameter 'pet_id' when calling PetApi.update_pet"
      end

      if pet.nil?
        fail ArgumentError, "Missing the required parameter 'pet' when calling PetApi.update_pet"
      end

      path = '/pet/{petId}'.sub('{' + 'petId' + '}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(pet_id)))
      # @type var query_params: Hash[String, untyped]
      query_params = {}
      # @type var header_params: Hash[String, String]
      header_params = {}
      body = pet

      invoke_api(:PUT, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Pet')
    end
  end
end
