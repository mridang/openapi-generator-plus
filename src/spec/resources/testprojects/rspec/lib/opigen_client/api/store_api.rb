# frozen_string_literal: true

# rubocop:disable Lint/RedundantCopDisableDirective, Metrics/MethodLength, Naming/AccessorMethodName

require 'cgi'

# :nodoc:
module OpigenClient
  module Api
    # StoreApi provides methods for the Store API group.
    class StoreApi < BaseApi
      def initialize(api_client = nil, config = OpigenClient::Configuration.default)
        super
      end

      # Delete purchase order by ID
      # @param order_id [Integer] ID of the order to delete
      # @return [nil]
      def delete_order(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.delete_order"
        end

        path = '/store/order/{orderId}'.sub('{orderId}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(order_id)))
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

      # Returns pet inventories by status
      # @return [Hash<String, Integer>]
      def get_inventory
        path = '/store/inventory'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        body = nil

        invoke_api(:GET, path, query_params, header_params, body,
                   ['application/json'],
                   'application/json',
                   'Hash<String, Integer>')
      end

      # Find purchase order by ID
      # @param order_id [Integer] ID of order to return
      # @return [Order]
      def get_order_by_id(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.get_order_by_id"
        end

        path = '/store/order/{orderId}'.sub('{orderId}', CGI.escape(OpigenClient::ObjectSerializer.to_path_value(order_id)))
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
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
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        body = opts[:order]

        invoke_api(:POST, path, query_params, header_params, body,
                   ['application/json'],
                   'application/json',
                   'Order')
      end
    end
  end
end
# rubocop:enable Lint/RedundantCopDisableDirective, Metrics/MethodLength, Naming/AccessorMethodName
