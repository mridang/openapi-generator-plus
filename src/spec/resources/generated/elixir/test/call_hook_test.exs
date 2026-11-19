defmodule PetstoreClient.CallHookTest do
  use ExUnit.Case, async: true

  # A hook that reports its start and finish, tagged with its name, and can be
  # told to return nothing or to fail in `finish`.
  defmodule NamedHook do
    @behaviour PetstoreClient.CallHook

    @impl true
    def start(_info, headers), do: {headers, nil}

    @impl true
    def start(_info, headers, opts) do
      send(opts.pid, {:started, opts.name})

      if opts[:nothing] do
        nil
      else
        {Map.put(
           headers,
           "X-Order",
           Map.get(headers, "X-Order", "") <> Atom.to_string(opts.name)
         ), opts}
      end
    end

    @impl true
    def finish(opts, _outcome) do
      send(opts.pid, {:finished, opts.name})

      if opts[:finish_raises], do: raise("finish failed")
    end
  end

  defp info do
    %PetstoreClient.CallInfo{
      operation_id: "op",
      method: "GET",
      url_template: "/things",
      url: "https://api.example.com/things",
      server_address: "api.example.com"
    }
  end

  defp hook(name, extra \\ %{}), do: {NamedHook, Map.merge(%{pid: self(), name: name}, extra)}

  defp events(acc \\ []) do
    receive do
      {kind, name} -> events(acc ++ [{kind, name}])
    after
      0 -> acc
    end
  end

  test "bhv hooks 020 chain starts the hooks in order, finishes their observers in reverse order and skips a hook that returns nothing" do
    chained =
      PetstoreClient.CallHook.chain([hook(:a), hook(:skipped, %{nothing: true}), hook(:b)])

    {headers, observer} = PetstoreClient.CallHook.start_one(chained, info(), %{})

    assert headers == %{"X-Order" => "ab"}
    assert events() == [started: :a, started: :skipped, started: :b]

    :ok =
      PetstoreClient.CallHook.finish_one(chained, observer, %PetstoreClient.CallOutcome{
        status_code: 200,
        headers: %{}
      })

    assert events() == [finished: :b, finished: :a]
  end

  test "bhv langelixir 042 a chain finishes every observer even when one of them fails" do
    chained =
      PetstoreClient.CallHook.chain([hook(:a), hook(:b, %{finish_raises: true}), hook(:c)])

    {_headers, observer} = PetstoreClient.CallHook.start_one(chained, info(), %{})
    events()

    assert :ok =
             PetstoreClient.CallHook.finish_one(chained, observer, %PetstoreClient.CallOutcome{})

    assert events() == [finished: :c, finished: :b, finished: :a]
  end

  test "bhv langelixir 043 a chain of hooks that return nothing has no observer" do
    chained = PetstoreClient.CallHook.chain([hook(:a, %{nothing: true})])

    assert {%{}, nil} = PetstoreClient.CallHook.start_one(chained, info(), %{})
  end
end
