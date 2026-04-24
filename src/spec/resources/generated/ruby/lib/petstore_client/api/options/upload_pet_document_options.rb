# frozen_string_literal: true

module PetstoreClient
  module Api
    module Options
      # Options for the upload_pet_document operation.
      class UploadPetDocumentOptions
        attr_accessor :file, :document_type, :notes

        def initialize(file: nil, document_type: nil, notes: nil)
          @file = file
          @document_type = document_type
          @notes = notes
        end
      end
    end
  end
end
