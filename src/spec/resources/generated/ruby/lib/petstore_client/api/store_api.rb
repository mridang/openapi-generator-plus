# frozen_string_literal: true

# rubocop:disable Lint/RedundantCopDisableDirective, Layout/LineLength
# rubocop:disable Layout/EmptyLinesAroundModuleBody, Layout/EmptyLineBetweenDefs, Layout/EmptyLines
# rubocop:disable Metrics/AbcSize, Metrics/ClassLength, Metrics/MethodLength, Naming/AccessorMethodName
# rubocop:disable Style/MethodCallWithoutArgsParentheses
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
      # @raise [ApiError] if fails to make API call
      def delete_order(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.delete_order"
        end

        delete_order_with_http_info(order_id).data
      end

      # @return [ApiResult]
      # @raise [ApiError] if fails to make API call
      def delete_order_with_http_info(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.delete_order"
        end

        path = '/store/order/{orderId}'
        path = path.sub('{orderId}', PetstoreClient::ValueSerializer.serialize_styled('orderId', order_id, :path, 'Integer', nil, 'simple', false).to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api_for_result(
          :DELETE, path, query_params, header_params, request_body,
          [],
          'application/json',
          nil,
          nil
        )
      end

      # Returns pet inventories by status

      # @return [Hash<String, Integer>]
      # @raise [ApiError] if fails to make API call
      def get_inventory()
        get_inventory_with_http_info().data
      end

      # @return [ApiResult]
      # @raise [ApiError] if fails to make API call
      def get_inventory_with_http_info()
        path = '/store/inventory'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api_for_result(
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
      # @raise [ApiError] if fails to make API call
      def get_order_by_id(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.get_order_by_id"
        end

        get_order_by_id_with_http_info(order_id).data
      end

      # @return [ApiResult]
      # @raise [ApiError] if fails to make API call
      def get_order_by_id_with_http_info(order_id)
        if order_id.nil?
          raise ArgumentError,
                "Missing the required parameter 'order_id' when calling StoreApi.get_order_by_id"
        end

        path = '/store/order/{orderId}'
        path = path.sub('{orderId}', PetstoreClient::ValueSerializer.serialize_styled('orderId', order_id, :path, 'Integer', nil, 'simple', false).to_s)
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = nil

        invoke_api_for_result(
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
      # @raise [ApiError] if fails to make API call
      def place_order(order = nil)
        place_order_with_http_info(order).data
      end

      # @return [ApiResult]
      # @raise [ApiError] if fails to make API call
      def place_order_with_http_info(order = nil)
        path = '/store/order'
        # @type var query_params: Hash[String, untyped]
        query_params = {}
        # @type var header_params: Hash[String, String]
        header_params = {}
        request_body = order # rubocop:disable Lint/SelfAssignment

        invoke_api_for_result(
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
# rubocop:enable Layout/EmptyLinesAroundModuleBody, Layout/EmptyLineBetweenDefs, Layout/EmptyLines
# rubocop:enable Metrics/AbcSize, Metrics/ClassLength, Metrics/MethodLength, Naming/AccessorMethodName
# rubocop:enable Style/MethodCallWithoutArgsParentheses
# rubocop:enable Style/DefWithParentheses
# rubocop:enable Style/StringConcatenation
