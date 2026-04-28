defmodule PetstoreClient.Api.Options.UploadPetCertificateOptions do
  @moduledoc """
  Options for the `upload_pet_certificate` operation.
  """

  @type t :: %__MODULE__{
          file: term()
        }

  defstruct file: nil
end
