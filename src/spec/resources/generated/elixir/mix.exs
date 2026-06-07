defmodule PetstoreClient.MixProject do
  use Mix.Project

  def project do
    [
      app: :petstore_client,
      version: "1.0.0",
      elixir: "~> 1.19",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      package: package(),
      docs: docs(),
      test_coverage: [tool: ExCoveralls],
      dialyzer: [
        plt_add_apps: [:mix, :ex_unit],
        plt_core_path: "_build/#{Mix.env()}",
        flags: [
          :error_handling,
          :extra_return,
          :missing_return,
          :underspecs,
          :unknown,
          :unmatched_returns
        ]
      ]
    ]
  end

  # Mix 1.19 moved `:preferred_cli_env` out of `def project` and into the
  # `def cli` callback; declaring it in `def project` is now a hard error.
  def cli do
    [
      preferred_envs: [
        coveralls: :test,
        "coveralls.detail": :test,
        "coveralls.post": :test,
        "coveralls.html": :test
      ]
    ]
  end

  defp package do
    [
      description: "A simplified Pet Store API for integration testing.",
      licenses: ["MIT"]
    ]
  end

  defp docs do
    [
      output: ".out/docs"
    ]
  end

  def application do
    [
      extra_applications: [:logger, :crypto]
    ]
  end

  defp deps do
    [
      {:req, "~> 0.5"},
      {:jason, "~> 1.4"},
      {:brotli, "~> 0.3", only: :prod, optional: true},
      {:mime, "~> 2.0"},
      {:excoveralls, "~> 0.18", only: :test},
      {:junit_formatter, "~> 3.4", only: :test},
      {:credo, "~> 1.7", only: [:dev, :test], runtime: false},
      {:dialyxir, "~> 1.4", only: [:dev, :test], runtime: false},
      {:ex_doc, "~> 0.34", only: :dev, runtime: false},
      {:testcontainers, "~> 1.12", only: :test},
      # `fs` is a transitive dep of `testcontainers` (used only by the
      # `mix testcontainers.run`/`.test` helper task that we don't call).
      # On boot Mix auto-starts every runtime dep; `fs`'s OTP app callback
      # spawns an `inotifywait` port driver and logs `backend port not
      # found: :inotifywait` when the binary isn't installed (the stock
      # `elixir:1.19` Docker image doesn't ship `inotify-tools`).
      # `runtime: false` keeps the dep compiled (so `testcontainers`
      # links cleanly) but skips the OTP start — no inotifywait, no
      # warning, no apt-install. `override: true` is required because
      # `testcontainers` declares `fs` itself.
      {:fs, "~> 8.6", only: :test, override: true, runtime: false}
    ]
  end
end
