defmodule PetstoreClient.ReadmeTest do
  use ExUnit.Case, async: true

  test "bhv mocking 009 Testing your code names the group interface and the client and shows a fake" do
    readme = File.read!("README.md")
    [_, rest] = String.split(readme, "### Testing your code", parts: 2)
    [section | _] = String.split(rest, "\n### ", parts: 2)

    assert section =~ "PetstoreClient.Api.PetApiBehaviour"
    assert section =~ "the client is a struct of the group structs"
    assert section =~ "@behaviour"
  end

  test "the README has an API information section" do
    assert File.read!("README.md") =~ "\n## API information\n"
  end

  # Every fence of a markdown file whose info string starts with the word elixir, as the
  # info string and the body; the bodies of other fences are skipped.
  defp elixir_fences(text) do
    ~r/^```(elixir(?: [^\n]*)?)\n(.*?)^```/ms
    |> Regex.scan(text)
    |> Enum.map(fn [_, info, body] -> {info, body} end)
  end

  test "bhv langelixir 094 ships a quickstart that builds a client, calls an operation and closes it" do
    quickstart = File.read!("examples/quickstart.ex")

    assert quickstart =~ "PetstoreClient.Client.with_token(host, token)"
    assert quickstart =~ "PetstoreClient.Api.StoreApi.get_defaults(client.store)"
    assert quickstart =~ "PetstoreClient.Client.close(client)"
  end

  test "bhv langelixir 095 reads the host and the token from the variables the README names" do
    quickstart = File.read!("examples/quickstart.ex")
    readme = File.read!("README.md")

    for name <- ["SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL", "SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"] do
      assert quickstart =~ "System.fetch_env!(\"#{name}\")"
      assert readme =~ name
    end

    refute quickstart =~ "your-token"
  end

  test "bhv langelixir 096 holds the statements of the README Usage fence in the quickstart, in order" do
    quickstart = File.read!("examples/quickstart.ex")
    [_, rest] = String.split(File.read!("README.md"), "\n## Usage\n", parts: 2)
    [section | _] = String.split(rest, "\n## ", parts: 2)
    [{_, body} | _] = elixir_fences(section)

    lines = body |> String.split("\n") |> Enum.map(&String.trim/1) |> Enum.reject(&(&1 == ""))

    Enum.reduce(lines, 0, fn line, position ->
      {found, length} =
        :binary.match(quickstart, line, scope: {position, byte_size(quickstart) - position})

      found + length
    end)
  end

  test "bhv langelixir 097 declares the kind of every elixir fence of README.md and SKILLS.md" do
    for name <- ["README.md", "SKILLS.md"], {info, _} <- elixir_fences(File.read!(name)) do
      assert info in ["elixir file", "elixir statements"], "#{name}: #{info}"
    end
  end

  test "bhv langelixir 098 leaves no placeholder line and no model built from nothing in an elixir fence" do
    for name <- ["README.md", "SKILLS.md"],
        {_, body} <- elixir_fences(File.read!(name)),
        line <- String.split(body, "\n") do
      trimmed = String.trim(line)

      refute trimmed in ["...", "# ..."], "#{name}: #{line}"

      refute String.starts_with?(trimmed, "<") and String.ends_with?(trimmed, ">"),
             "#{name}: #{line}"

      refute line =~ ~r/(?<!\()%[\w.]*Models\.\w+\{\}(?! =)/, "#{name}: #{line}"
    end
  end

  test "bhv langelixir 099 has Makefile targets that build and run the examples directory" do
    makefile = File.read!("Makefile")

    assert makefile =~ "\nexamples: install\n"
    assert makefile =~ "\nexamples-run: examples\n"
    assert makefile =~ "elixirc --warnings-as-errors"
  end
end
