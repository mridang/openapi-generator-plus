defmodule PetstoreClient.MixProject do
  use Mix.Project

  def project do
    [
      app: :petstore_client,
      version: "1.0.0",
      elixir: "~> 1.18",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      package: package(),
      test_coverage: [tool: ExCoveralls],
      preferred_cli_env: [
        coveralls: :test,
        "coveralls.detail": :test,
        "coveralls.post": :test,
        "coveralls.html": :test
      ],
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

  defp package do
    [
      licenses: ["MIT"]
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
      {:brotli, "~> 0.3"},
      {:mime, "~> 2.0"},
      {:excoveralls, "~> 0.18", only: :test},
      {:junit_formatter, "~> 3.4", only: :test},
      {:credo, "~> 1.7", only: [:dev, :test], runtime: false},
      {:dialyxir, "~> 1.4", only: [:dev, :test], runtime: false},
      {:ex_doc, "~> 0.30", only: :dev, runtime: false},
      {:testcontainers, "~> 1.12", only: :test}
    ]
  end
end
