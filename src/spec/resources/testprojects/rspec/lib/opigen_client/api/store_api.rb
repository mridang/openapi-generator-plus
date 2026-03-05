require 'cgi'

module OpigenClient::Api
  # StoreApi provides methods for the Store API group.
  class StoreApi < BaseApi
    def initialize(api_client = nil, config = OpigenClient::Configuration.default)
      super(api_client, config)
    end

    # Delete purchase order by ID
    # @param order_id [Integer] ID of the order to delete
    # @param [Hash] opts the optional parameters
    # @return [nil]
    def delete_order(order_id, opts = {})
      if order_id.nil?
        fail ArgumentError, "Missing the required parameter 'order_id' when calling StoreApi.delete_order"
      end

      path = '/store/order/{orderId}'.sub('{' + 'orderId' + '}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(order_id)))
      query_params = {}
      header_params = {}
      body = nil

      invoke_api(:DELETE, path, query_params, header_params, body,
                 [],
                 'application/json',
                 nil)
    end

    # Returns pet inventories by status
    # @param [Hash] opts the optional parameters
    # @return [Hash<String, Integer>]
    def get_inventory(opts = {})
      path = '/store/inventory'
      query_params = {}
      header_params = {}
      body = nil

      invoke_api(:GET, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Hash<String, Integer>')
    end

    # Find purchase order by ID
    # @param order_id [Integer] ID of order to return
    # @param [Hash] opts the optional parameters
    # @return [Order]
    def get_order_by_id(order_id, opts = {})
      if order_id.nil?
        fail ArgumentError, "Missing the required parameter 'order_id' when calling StoreApi.get_order_by_id"
      end

      path = '/store/order/{orderId}'.sub('{' + 'orderId' + '}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(order_id)))
      query_params = {}
      header_params = {}
      body = nil

      invoke_api(:GET, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Order')
    end

    # Place an order for a pet
    # @param [Hash] opts the optional parameters
    # @option opts [Order] :order
    # @return [Order]
    def place_order(opts = {})
      path = '/store/order'
      query_params = {}
      header_params = {}
      body = opts[:'order']

      invoke_api(:POST, path, query_params, header_params, body,
                 ['application/json'],
                 'application/json',
                 'Order')
    end
  end
end
