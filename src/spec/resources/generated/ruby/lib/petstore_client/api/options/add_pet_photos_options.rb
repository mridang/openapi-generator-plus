# frozen_string_literal: true

module PetstoreClient
  module Api
    module Options
      # Options for the add_pet_photos operation.
      class AddPetPhotosOptions
        attr_accessor :files, :metadata

        def initialize(files: nil, metadata: nil)
          @files = files
          @metadata = metadata
        end
      end
    end
  end
end
