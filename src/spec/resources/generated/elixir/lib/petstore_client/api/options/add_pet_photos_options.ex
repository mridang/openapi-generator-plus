defmodule PetstoreClient.Api.Options.AddPetPhotosOptions do
  @moduledoc """
  Options for the `add_pet_photos` operation.
  """

  @type t :: %__MODULE__{
          files: term(),
          metadata: term()
        }

  defstruct [
    files: nil,
    metadata: nil
  ]
end
