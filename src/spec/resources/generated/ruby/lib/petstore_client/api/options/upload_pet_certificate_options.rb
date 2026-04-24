# frozen_string_literal: true

module PetstoreClient
  module Api
    module Options
      # Options for the upload_pet_certificate operation.
      class UploadPetCertificateOptions
        attr_accessor :file

        def initialize(file: nil)
          @file = file
        end
      end
    end
  end
end
