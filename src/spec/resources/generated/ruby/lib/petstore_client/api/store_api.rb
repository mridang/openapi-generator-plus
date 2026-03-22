# frozen_string_literal: true

# rubocop:disable Lint/RedundantCopDisableDirective, Layout/LineLength
# rubocop:disable Metrics/AbcSize, Metrics/ClassLength, Metrics/MethodLength, Naming/AccessorMethodName
# rubocop:disable Style/DefWithParentheses
# rubocop:disable Style/StringConcatenation

require 'cgi'

# :nodoc:
module PetstoreClient
  module Api
    # StoreApi provides methods for the Store API group.
    # Access to Petstore orders
    class StoreApi < BaseApi
      def initialize(api_client = nil, config = PetstoreClient::Configuration.default)
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

        path = '/store/order/{orderId}'
        path = path.sub('{orderId}', PetstoreClient::ValueSerializer.serialize(order_id, :path, 'Integer').to_s)
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
          nil
        )
      end

      # Returns pet inventories by status
      # @return [Hash<String, Integer>]
      def get_inventory()
        path = '/store/inventory'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Hash<String, Integer>',
          nil
        )
      end

      # Find purchase order by ID
      # @param order_id [Integer] ID of order to return
      # @return [Order]
      def get_order_by_id(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.get_order_by_id"
        end

        path = '/store/order/{orderId}'
        path = path.sub('{orderId}', PetstoreClient::ValueSerializer.serialize(order_id, :path, 'Integer').to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api(
          :GET, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Order',
          nil
        )
      end

      # Place an order for a pet
      # @param order [Order]
      # @return [Order]
      def place_order(order = nil)
        path = '/store/order'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = order # rubocop:disable Lint/SelfAssignment

        invoke_api(
          :POST, path, query_params, header_params, request_body,
          ['application/json'],
          'application/json',
          'Order',
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
