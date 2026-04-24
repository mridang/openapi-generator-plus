# frozen_string_literal: true

require_relative '../../models/photo_metadata'

module PetstoreClient
  module Api
    module Options
      # Options for the add_pet_photos operation.
      class AddPetPhotosOptions
        attr_accessor :files, :metadata

        def initialize(files:, metadata:)
          @files = files
          @metadata = metadata
        end
      end
    end
  end
end
