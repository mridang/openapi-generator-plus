defmodule PetstoreClient.Api.Options.GetPetTagOptions do
  @moduledoc """
  Options for the `get_pet_tag` operation.
  """

  @type t :: %__MODULE__{
          colors: term(),
          sizes: term(),
          filter: term()
        }

  defstruct [
    colors: nil,
    sizes: nil,
    filter: nil
  ]
end
