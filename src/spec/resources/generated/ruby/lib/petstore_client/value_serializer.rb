# frozen_string_literal: true

require 'cgi'

module PetstoreClient
  # Serializes parameter values for HTTP requests based on their location.
  class ValueSerializer # rubocop:disable Metrics/ClassLength
    def self.serialize(value, location, _schema_type, collection_format: nil)
      return serialize_nil(location) if value.nil?
      return serialize_array(value, location, collection_format) if value.is_a?(Array)

      str_val = ObjectSerializer.stringify(value)

      return CGI.escape(str_val).gsub('+', '%20') if location == :path

      str_val
    end

    def self.serialize_nil(location)
      return nil if location == :query

      ''
    end

    def self.serialize_array(value, location, collection_format)
      if location == :query
        serialize_query_array(value, collection_format)
      elsif location == :header
        value.map { |v| ObjectSerializer.stringify(v) }.join(',')
      end
    end

    def self.serialize_query_array(value, collection_format) # rubocop:disable Metrics/AbcSize, Metrics/CyclomaticComplexity, Metrics/MethodLength
      case collection_format
      when :multi
        value.map { |v| ObjectSerializer.stringify(v) }
      when :ssv
        value.map { |v| ObjectSerializer.stringify(v) }.join(' ')
      when :tsv
        value.map { |v| ObjectSerializer.stringify(v) }.join("\t")
      when :pipes
        value.map { |v| ObjectSerializer.stringify(v) }.join('|')
      else
        value.map { |v| ObjectSerializer.stringify(v) }.join(',')
      end
    end

    # Serialize a deepObject-style query parameter.
    #
    # Produces a hash of flattened keys in the form +param_name[key]+ to
    # stringified values, suitable for inclusion in a query string.
    #
    # @param param_name [String] the parameter name (e.g. 'filter')
    # @param value [Hash, nil] the hash value to serialize
    # @return [Hash{String => String}] expanded keys to serialized values
    def self.serialize_deep_object(param_name, value)
      # @type var empty: Hash[String, String]
      empty = {}
      return empty if value.nil?

      # @type var acc: Hash[String, String]
      acc = {}
      value.each_with_object(acc) do |(key, val), result|
        result["#{param_name}[#{key}]"] = ObjectSerializer.stringify(val)
      end
    end

    # Serialize a parameter value according to OAS 3.0 style and explode rules.
    #
    # @param param_name [String] the parameter name
    # @param value [Object, nil] the value to serialize
    # @param location [Symbol] parameter location (:path, :query, :header, :cookie)
    # @param schema_type [String] the schema type (e.g. 'string', 'array')
    # @param collection_format [Symbol, nil] legacy collection format
    # @param style [String, nil] OAS 3.0 style (e.g. 'matrix', 'label', 'form',
    #   'simple', 'spaceDelimited', 'pipeDelimited')
    # @param explode [Boolean] whether to explode array values
    # @return [String, Array<String>, nil] the serialized value
    def self.serialize_styled(param_name, value, location, schema_type, collection_format, style, explode) # rubocop:disable Metrics/AbcSize, Metrics/CyclomaticComplexity, Metrics/MethodLength, Metrics/ParameterLists, Metrics/PerceivedComplexity
      return serialize(value, location, schema_type, collection_format: collection_format) if style.nil? || style.empty?

      case style
      when 'matrix'
        return nil if value.nil? && location == :query
        return '' if value.nil?

        if value.is_a?(Array)
          if explode
            value.map { |v| ";#{param_name}=#{ObjectSerializer.stringify(v)}" }.join
          else
            ";#{param_name}=#{value.map { |v| ObjectSerializer.stringify(v) }.join(',')}"
          end
        else
          ";#{param_name}=#{ObjectSerializer.stringify(value)}"
        end
      when 'label'
        return nil if value.nil? && location == :query
        return '' if value.nil?

        if value.is_a?(Array)
          if explode
            ".#{value.map { |v| ObjectSerializer.stringify(v) }.join('.')}"
          else
            ".#{value.map { |v| ObjectSerializer.stringify(v) }.join(',')}"
          end
        else
          ".#{ObjectSerializer.stringify(value)}"
        end
      when 'spaceDelimited'
        return nil if value.nil? && location == :query
        return '' if value.nil?

        if value.is_a?(Array)
          value.map { |v| ObjectSerializer.stringify(v) }.join(' ')
        else
          ObjectSerializer.stringify(value)
        end
      when 'pipeDelimited'
        return nil if value.nil? && location == :query
        return '' if value.nil?

        if value.is_a?(Array)
          value.map { |v| ObjectSerializer.stringify(v) }.join('|')
        else
          ObjectSerializer.stringify(value)
        end
      when 'form'
        return nil if value.nil? && location == :query
        return '' if value.nil?

        if value.is_a?(Array)
          if explode
            value.map { |v| ObjectSerializer.stringify(v) }
          else
            value.map { |v| ObjectSerializer.stringify(v) }.join(',')
          end
        else
          ObjectSerializer.stringify(value)
        end
      when 'simple'
        return nil if value.nil? && location == :query
        return '' if value.nil?

        if value.is_a?(Array)
          value.map { |v| ObjectSerializer.stringify(v) }.join(',')
        else
          ObjectSerializer.stringify(value)
        end
      else
        serialize(value, location, schema_type, collection_format: collection_format)
      end
    end

    private_class_method :serialize_nil, :serialize_array, :serialize_query_array
  end
end
