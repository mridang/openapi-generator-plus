require 'spec_helper'

RSpec.describe OpigenClient::Api::PetApi do
  let(:api) { OpigenClient::Api::PetApi.new }

  describe '#add_pet' do
    it 'creates a new pet' do
      pet = OpigenClient::Models::Pet.new(
        id: 12345,
        name: 'TestDog',
        photo_urls: ['http://example.com/photo.jpg'],
        status: 'available'
      )

      result = api.add_pet(pet)

      expect(result).not_to be_nil
      expect(result.name).not_to be_nil
    end
  end

  describe '#find_pets_by_status' do
    it 'returns pets by status' do
      result = api.find_pets_by_status(status: 'available')

      expect(result).to be_an(Array)
      expect(result).not_to be_empty
      expect(result.first).to be_a(OpigenClient::Models::Pet)
    end
  end

  describe '#get_pet_by_id' do
    it 'returns a pet by id' do
      result = api.get_pet_by_id(1)

      expect(result).not_to be_nil
      expect(result.id).not_to be_nil
      expect(result.name).not_to be_nil
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

      result = api.update_pet(1, pet)

      expect(result).not_to be_nil
    end
  end

  describe '#delete_pet' do
    it 'deletes a pet' do
      # Delete operation - Prism will return success
      expect { api.delete_pet(1) }.not_to raise_error
    end
  end
end
