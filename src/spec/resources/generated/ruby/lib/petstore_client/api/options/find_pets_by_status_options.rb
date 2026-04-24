# frozen_string_literal: true

module PetstoreClient
  module Api
    module Options
      # Options for the find_pets_by_status operation.
      class FindPetsByStatusOptions
        attr_accessor :status, :filter

        def initialize(status: nil, filter: nil)
          @status = status
          @filter = filter
        end
      end
    end
  end
end
