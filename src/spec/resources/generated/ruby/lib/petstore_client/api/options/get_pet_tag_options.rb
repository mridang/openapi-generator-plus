# frozen_string_literal: true

module PetstoreClient
  module Api
    module Options
      # Options for the get_pet_tag operation.
      class GetPetTagOptions
        attr_accessor :colors, :sizes, :filter

        def initialize(colors: nil, sizes: nil, filter: nil)
          @colors = colors
          @sizes = sizes
          @filter = filter
        end
      end
    end
  end
end
