# frozen_string_literal: true

# Integration tests for the Pet API endpoints.

require 'spec_helper'

describe OpigenClient::Api::PetApi do
  before do
    @api = OpigenClient::Api::PetApi.new
    @base_url = ENV['API_BASE_URL'] || 'http://localhost:4010'
    @auth = OpigenClient::Auth::BearerAuthenticator.new(@base_url, 'test-token')
  end

  describe '#add_pet' do
    it 'creates a new pet' do
      pet = OpigenClient::Models::Pet.new(
        id: 12345,
        name: 'TestDog',
        photo_urls: ['http://example.com/photo.jpg'],
        status: 'available'
      )

      result = @api.add_pet(@auth, pet)

      _(result).wont_be_nil
      _(result.name).wont_be_nil
    end
  end

  describe '#find_pets_by_status' do
    it 'returns pets by status' do
      result = @api.find_pets_by_status(status: 'available')

      _(result).must_be_kind_of(Array)
      _(result).wont_be_empty
      _(result.first).must_be_kind_of(OpigenClient::Models::Pet)
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
      pet = OpigenClient::Models::Pet.new(
        id: 1,
        name: 'UpdatedDog',
        photo_urls: ['http://example.com/updated.jpg'],
        status: 'pending'
      )

      result = @api.update_pet(1, pet)

      _(result).wont_be_nil
    end
  end

  describe '#delete_pet' do
    it 'deletes a pet' do
      @api.delete_pet(@auth, 1)
    end
  end
end
