defmodule PetstoreClient.Api.Options.FindPetsByStatusOptions do
  @moduledoc """
  Options for the `find_pets_by_status` operation.
  """

  @type t :: %__MODULE__{
          status: term(),
          filter: term()
        }

  defstruct [
    status: nil,
    filter: nil
  ]
end
