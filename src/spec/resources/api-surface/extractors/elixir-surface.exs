# Prints the public API records of the Elixir SDK (see AbstractApiSurfaceSpec) from the
# documentation chunks and typespecs of the compiled application:
#
#   mix run --no-start /tmp/api-surface/elixir-surface.exs
#
# @@S  for every documented module (not `@moduledoc false`): the module, its struct fields,
#      every public function and macro that is not `@doc false` as `Module.name/arity`
#      followed by its `@spec` when it has one, its public types and its callbacks.
# @@T  every module a caller can see: the documented modules and any `@moduledoc false`
#      module that a printed signature names.
# @@E  every documented module.
# @@I  a documented module whose name has an `Internal` segment.
app = Mix.Project.config()[:app]
_ = Application.load(app)
{:ok, modules} = :application.get_key(app, :modules)
modules = Enum.sort(modules)

name = fn module -> inspect(module) end
flat = fn text ->
  text
  |> String.split()
  |> Enum.join(" ")
  |> String.replace(~r/([(\[{]) /, "\\1")
  |> String.replace(~r/ ([)\]}])/, "\\1")
end

docs =
  Map.new(modules, fn module ->
    {module, Code.fetch_docs(module)}
  end)

documented =
  Enum.filter(modules, fn module ->
    match?({:docs_v1, _, _, _, moduledoc, _, _} when moduledoc != :hidden, docs[module])
  end)

render_spec = fn fun, spec -> flat.(Macro.to_string(Code.Typespec.spec_to_quoted(fun, spec))) end

signatures =
  Enum.flat_map(documented, fn module ->
    {:docs_v1, _, _, _, _, _, entries} = docs[module]
    owner = name.(module)

    specs =
      case Code.Typespec.fetch_specs(module) do
        {:ok, list} -> Map.new(list)
        :error -> %{}
      end

    struct =
      if function_exported?(module, :__struct__, 0) do
        fields =
          module.__struct__()
          |> Map.from_struct()
          |> Map.keys()
          |> Enum.sort()
          |> Enum.map_join(", ", &inspect/1)

        ["defstruct #{owner} [#{fields}]"]
      else
        []
      end

    functions =
      for {{kind, fun, arity}, _, _, doc, _} <- entries,
          kind in [:function, :macro],
          doc != :hidden do
        keyword = if kind == :macro, do: "defmacro", else: "def"
        head = "#{keyword} #{owner}.#{fun}/#{arity}"

        case specs[{fun, arity}] do
          nil -> [head]
          list ->
            Enum.map(list, fn spec ->
              rendered = render_spec.(fun, spec)
              prefix = Atom.to_string(fun) <> "("
              args =
                if String.starts_with?(rendered, prefix),
                  do: String.replace_prefix(rendered, Atom.to_string(fun), ""),
                  else: rendered
              "#{head} #{args}"
            end)
        end
      end
      |> List.flatten()

    types =
      case Code.Typespec.fetch_types(module) do
        {:ok, list} ->
          for {kind, type} <- list, kind in [:type, :opaque] do
            {type_name, _, args} = type
            quoted = Code.Typespec.type_to_quoted(type)

            if kind == :opaque,
              do: "@opaque #{owner}.#{type_name}/#{length(args)}",
              else: "@type #{owner}.#{flat.(Macro.to_string(quoted))}"
          end

        :error ->
          []
      end

    callbacks =
      case Code.Typespec.fetch_callbacks(module) do
        {:ok, list} ->
          for {{fun, _arity}, cbs} <- list, cb <- cbs do
            "@callback #{owner}.#{render_spec.(fun, cb)}"
          end

        :error ->
          []
      end

    ["defmodule #{owner}"] ++ struct ++ functions ++ types ++ callbacks
  end)

hidden = modules -- documented

named =
  Enum.filter(hidden, fn module ->
    pattern = ~r/(?<![\w.])#{Regex.escape(name.(module))}(?![\w])/
    Enum.any?(signatures, &Regex.match?(pattern, &1))
  end)

records =
  Enum.map(signatures, &"@@S #{&1}") ++
    Enum.map(documented ++ named, &"@@T #{name.(&1)}") ++
    Enum.map(documented, &"@@E #{name.(&1)}") ++
    for(
      module <- documented,
      "Internal" in Module.split(module),
      do: "@@I #{name.(module)}"
    )

records |> Enum.uniq() |> Enum.sort() |> Enum.each(&IO.puts/1)
