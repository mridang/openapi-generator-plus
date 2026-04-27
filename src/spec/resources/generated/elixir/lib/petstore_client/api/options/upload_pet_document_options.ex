defmodule PetstoreClient.Api.Options.UploadPetDocumentOptions do
  @moduledoc """
  Options for the `upload_pet_document` operation.
  """

  @type t :: %__MODULE__{
          file: term(),
          document_type: term(),
          notes: term()
        }

  defstruct [
    file: nil,
    document_type: nil,
    notes: nil
  ]
end
