# Integration tests for the Store API endpoints.

require 'spec_helper'

RSpec.describe OpigenClient::Api::StoreApi do
  let(:api) { OpigenClient::Api::StoreApi.new }

  describe '#get_inventory' do
    it 'returns inventory' do
      result = api.get_inventory

      expect(result).to be_a(Hash)
    end
  end

  describe '#place_order' do
    it 'places an order' do
      order = OpigenClient::Models::Order.new(
        id: 1,
        pet_id: 12345,
        quantity: 1,
        ship_date: Time.now.utc,
        status: 'placed',
        complete: false
      )

      result = api.place_order(order: order)

      expect(result).not_to be_nil
      expect(result.id).not_to be_nil
    end
  end

  describe '#get_order_by_id' do
    it 'returns an order by id' do
      result = api.get_order_by_id(1)

      expect(result).not_to be_nil
      expect(result.id).not_to be_nil
    end
  end

  describe '#delete_order' do
    it 'deletes an order' do
      expect { api.delete_order(1) }.not_to raise_error
    end
  end
end
