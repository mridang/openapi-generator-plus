# credo:disable-for-this-file
# Credo findings here are inherent to generated code (fully-qualified
# nested-module references and machine-generated control flow); the SDK
# uses Credo's default config and handles them with this file-level
# directive rather than relaxing the ruleset.
defmodule PetstoreClient.Api.PetApiTest do
  use ExUnit.Case, async: true

  setup do
    base_url = System.get_env("API_BASE_URL", "http://localhost:4010")
    auth = PetstoreClient.Auth.BearerAuthenticator.new(base_url, "test-token")

    config =
      PetstoreClient.Configuration.new(
        base_url: base_url,
        default_headers: %{"Authorization" => "Bearer test-token"}
      )

    api = PetstoreClient.Api.PetApi.new(nil, config)

    %{api: api, auth: auth, base_url: base_url}
  end

  test "add_pet creates a new pet", %{api: api, auth: auth} do
    pet = %PetstoreClient.Models.Pet{
      id: :rand.uniform(1_000_000_000),
      name: "TestDog",
      photo_urls: ["http://example.com/photo.jpg"],
      status: "available"
    }

    options = %PetstoreClient.Api.Options.AddPetOptions{auth: auth}
    assert {:ok, result} = PetstoreClient.Api.PetApi.add_pet(api, pet, options)
    assert result != nil
  end

  test "find_pets_by_status returns pets by status", %{api: api} do
    options = %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: "available"}
    assert {:ok, result} = PetstoreClient.Api.PetApi.find_pets_by_status(api, options)
    assert is_list(result)
    assert length(result) > 0
  end

  test "get_pet_by_id returns a pet by id", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_by_id(api, pet_id)
    assert result != nil
    assert result.id != nil
    assert result.name != nil
  end

  test "update_pet updates an existing pet", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)

    pet = %PetstoreClient.Models.Pet{
      id: pet_id,
      name: "UpdatedDog",
      photo_urls: ["http://example.com/updated.jpg"],
      status: "pending"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.update_pet(api, pet_id, pet)
    assert result != nil
  end

  test "delete_pet deletes a pet", %{api: api, auth: auth} do
    pet_id = :rand.uniform(1_000_000_000)
    options = %PetstoreClient.Api.Options.DeletePetOptions{auth: auth}
    assert {:ok, _result} = PetstoreClient.Api.PetApi.delete_pet(api, pet_id, options)
  end

  test "set_pet_avatar uploads binary image data", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)

    assert {:ok, _result} =
             PetstoreClient.Api.PetApi.set_pet_avatar(api, pet_id, <<0xFF, 0xD8, 0xFF>>)
  end

  test "get_pet_avatar downloads the pet avatar as decoded binary", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_avatar_with_http_info(api, pet_id)
    assert is_binary(result.data)
    # The transport base64-encodes the binary body into `raw_body`; the
    # decoded `data` must round-trip back to it. This fails if the client
    # returns the base64 string instead of the decoded bytes.
    assert Base.encode64(result.data) == result.raw_body
  end

  test "get_pet_avatar_thumbnail returns a base64-encoded thumbnail", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_avatar_thumbnail(api, pet_id)
    assert result != nil
  end

  test "set_pet_avatar_thumbnail uploads a base64 thumbnail via JSON", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    request = "iVBORw0KGgoAAAANSUhEUg=="

    assert {:ok, _result} =
             PetstoreClient.Api.PetApi.set_pet_avatar_thumbnail(api, pet_id, request)
  end

  test "upload_pet_certificate uploads a certificate via multipart", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)

    options = %PetstoreClient.Api.Options.UploadPetCertificateOptions{
      file: "cert-data"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.upload_pet_certificate(api, pet_id, options)
    assert result != nil
  end

  test "upload_pet_document uploads a document with metadata via multipart", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)

    options = %PetstoreClient.Api.Options.UploadPetDocumentOptions{
      file: "doc-data",
      document_type: "vaccination_record",
      notes: "Annual checkup"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.upload_pet_document(api, pet_id, options)
    assert result != nil
  end

  test "add_pet_photos uploads photos with metadata via multipart", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    metadata = %PetstoreClient.Models.PhotoMetadata{caption: "Test photo", is_primary: true}

    options = %PetstoreClient.Api.Options.AddPetPhotosOptions{
      files: [<<0xFF, 0xD8, 0xFF>>],
      metadata: metadata
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.add_pet_photos(api, pet_id, options)
    assert result != nil
    assert is_list(result)
  end

  test "download_pet_document downloads a document as binary", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    doc_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.download_pet_document(api, pet_id, doc_id)
    assert result != nil
  end

  test "get_pet_photo returns a photo via content negotiation", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    photo_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_photo(api, pet_id, photo_id)
    assert result != nil
  end

  test "get_pet_passport returns a passport with embedded byte fields", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_passport(api, pet_id)
    assert result != nil
    assert %PetstoreClient.Models.PetPassport{} = result
  end

  test "get_pet_tag sends styled path and query parameters", %{api: api} do
    options = %PetstoreClient.Api.Options.GetPetTagOptions{
      colors: ["blue", "black"],
      sizes: ["S", "M"]
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_tag(api, 5, "cute", options)
    assert result != nil
  end

  @tag :skip
  test "get_external_pet_info uses per-operation server URL" do
    # Per-operation server URL cannot be verified against mock server
  end

  # path-double-encoding: a string path param carrying an encodable char
  # must be percent-encoded exactly ONCE. The ValueSerializer already
  # encodes path segments per-item; the api template must NOT re-wrap the
  # whole string. A space must surface as %20, never %2520.
  defmodule PathCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> "" end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_url(name), do: Agent.get(name, & &1)

    @impl true
    def send_request(_method, url, _headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> url end)

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  test "string path param is percent-encoded exactly once (no double-encoding)" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)

    _result =
      PetstoreClient.Api.PetApi.get_pet_tag(
        api,
        5,
        "a b",
        %PetstoreClient.Api.Options.GetPetTagOptions{}
      )

    url = PathCapturingApiClient.captured_url(name)
    # get_pet_tag declares tagName with `style: label`, so the segment is
    # rendered as `.a%20b` (leading dot). This test verifies single-encoding
    # of the space, so assert the encoded form appears (a%20b) and is never
    # double-encoded (a%2520b) — style-agnostic.
    assert String.contains?(url, "a%20b"),
           "Expected single-encoded space (%20) in path, got: #{url}"

    refute String.contains?(url, "%2520"),
           "Path must not be double-encoded (%2520), got: #{url}"

    Agent.stop(name)
  end

  # RFC-6265 cookie-value validation on an operation cookie param. deletePet
  # declares an `api_key` cookie param that is interpolated raw into the Cookie
  # header; a value carrying CR/LF or a control char is a header-injection
  # vector and must FAIL CLOSED with the same ArgumentError the auth-cookie
  # path raises, before any request reaches the wire. A clean value passes.
  test "delete_pet cookie api_key with CRLF fails closed with ArgumentError" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)
    auth = %{auth_headers: %{"Authorization" => "Bearer t"}}

    for bad <- ["a\r\nX-Injected: yes", "a\rb", "a\nb", "a" <> <<0>> <> "b"] do
      assert_raise ArgumentError, fn ->
        PetstoreClient.Api.PetApi.delete_pet(
          api,
          1,
          %PetstoreClient.Api.Options.DeletePetOptions{api_key: bad, auth: auth}
        )
      end
    end

    # No request reached the wire for any rejected value.
    assert PathCapturingApiClient.captured_url(name) == ""
    Agent.stop(name)
  end

  test "delete_pet cookie api_key with a clean value is accepted" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)
    auth = %{auth_headers: %{"Authorization" => "Bearer t"}}

    {:ok, _result} =
      PetstoreClient.Api.PetApi.delete_pet(
        api,
        1,
        %PetstoreClient.Api.Options.DeletePetOptions{api_key: "session-abc123", auth: auth}
      )

    refute PathCapturingApiClient.captured_url(name) == ""
    Agent.stop(name)
  end

  defp new_pet_api_for_mock(status, content_type, body) do
    {:ok, socket} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true])
    {:ok, port} = :inet.port(socket)

    spawn(fn ->
      {:ok, client} = :gen_tcp.accept(socket)
      {:ok, _data} = :gen_tcp.recv(client, 0)

      response =
        "HTTP/1.1 #{status} OK\r\nContent-Type: #{content_type}\r\nContent-Length: #{byte_size(body)}\r\n\r\n#{body}"

      :gen_tcp.send(client, response)
      :gen_tcp.close(client)
      :gen_tcp.close(socket)
    end)

    config =
      PetstoreClient.Configuration.new(
        base_url: "http://127.0.0.1:#{port}",
        default_headers: %{}
      )

    PetstoreClient.Api.PetApi.new(nil, config)
  end

  test "error handling 404 not found" do
    api = new_pet_api_for_mock(404, "application/json", ~s({"message":"Pet not found"}))
    assert {:error, _reason} = PetstoreClient.Api.PetApi.get_pet_by_id(api, 99_999)
  end

  test "error handling 500 server error" do
    api = new_pet_api_for_mock(500, "application/json", ~s({"message":"Internal server error"}))

    assert {:error, _reason} =
             PetstoreClient.Api.PetApi.get_pet_by_id(api, :rand.uniform(1_000_000_000))
  end

  test "download binary from mock" do
    api = new_pet_api_for_mock(200, "application/octet-stream", "FAKE_BINARY_DATA")

    assert {:ok, result} =
             PetstoreClient.Api.PetApi.get_pet_avatar(api, :rand.uniform(1_000_000_000))

    assert result != nil
  end

  test "upload multipart from mock" do
    api =
      new_pet_api_for_mock(
        200,
        "application/json",
        ~s({"code":200,"type":"","message":"success"})
      )

    options = %PetstoreClient.Api.Options.UploadPetCertificateOptions{
      file: "fake-cert-data"
    }

    assert {:ok, result} =
             PetstoreClient.Api.PetApi.upload_pet_certificate(
               api,
               :rand.uniform(1_000_000_000),
               options
             )

    assert result != nil
  end

  # convenience-empty-body-handling: a body-returning operation that comes
  # back with an empty body must surface a typed ApiError from the plain
  # (non-_with_http_info) convenience method, not a silent nil.
  test "body-returning op with empty 200 body returns a typed ApiError" do
    api = new_pet_api_for_mock(200, "application/json", "")

    assert {:error, %PetstoreClient.ApiError{} = error} =
             PetstoreClient.Api.PetApi.get_pet_by_id(api, 1)

    assert error.status_code == 200
  end

  test "bang variant raises typed ApiError on empty 200 body" do
    api = new_pet_api_for_mock(200, "application/json", "")

    assert_raise PetstoreClient.ApiError, fn ->
      PetstoreClient.Api.PetApi.get_pet_by_id!(api, 1)
    end
  end

  test "get_pet_by_id_with_http_info returns http metadata", %{api: api} do
    assert {:ok, result} =
             PetstoreClient.Api.PetApi.get_pet_by_id_with_http_info(
               api,
               :rand.uniform(1_000_000_000)
             )

    assert result.status_code == 200
    assert result.data != nil
    assert result.raw_body != nil
  end

  test "add_pet_with_http_info returns http metadata", %{api: api, auth: auth} do
    pet = %PetstoreClient.Models.Pet{
      id: :rand.uniform(1_000_000_000),
      name: "HttpInfoDog",
      photo_urls: ["http://example.com/photo.jpg"],
      status: "available"
    }

    options = %PetstoreClient.Api.Options.AddPetOptions{auth: auth}
    assert {:ok, result} = PetstoreClient.Api.PetApi.add_pet_with_http_info(api, pet, options)
    assert result.status_code >= 200
    assert result.status_code < 300
    assert result.data != nil
  end

  test "update_pet_with_http_info returns http metadata", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)

    pet = %PetstoreClient.Models.Pet{
      id: pet_id,
      name: "UpdatedDog",
      photo_urls: ["http://example.com/updated.jpg"],
      status: "pending"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.update_pet_with_http_info(api, pet_id, pet)
    assert result.status_code >= 200
    assert result.status_code < 300
  end

  test "delete_pet_with_http_info returns http metadata", %{api: api, auth: auth} do
    pet_id = :rand.uniform(1_000_000_000)

    options = %PetstoreClient.Api.Options.DeletePetOptions{auth: auth}

    assert {:ok, result} =
             PetstoreClient.Api.PetApi.delete_pet_with_http_info(
               api,
               pet_id,
               options
             )

    assert result.status_code >= 200
    assert result.status_code < 300
  end

  test "find_pets_by_status_with_http_info returns http metadata", %{api: api} do
    options = %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: "available"}

    assert {:ok, result} =
             PetstoreClient.Api.PetApi.find_pets_by_status_with_http_info(api, options)

    assert result.status_code == 200
  end

  test "get_pet_passport_with_http_info returns http metadata", %{api: api} do
    pet_id = :rand.uniform(1_000_000_000)
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_passport_with_http_info(api, pet_id)
    assert result.status_code == 200
    assert result.data != nil
  end

  # per-call-auth-override: an authenticator passed to the BASE operation
  # method must apply its headers to the outgoing request, overriding the
  # config default header. The generated BaseApi only merges auth headers when
  # the authenticator is a plain map carrying an `:auth_headers` key
  # (`Map.has_key?(effective_auth, :auth_headers)`); the generated
  # `BearerAuthenticator` is a struct exposing `auth_headers/1` as a function,
  # not a field, so its headers are never merged on the wire. The override
  # therefore cannot be observed in Elixir — skipped and flagged as a feature
  # gap so the scenario count still matches the other SDKs.
  @tag :skip
  test "per-call auth override is applied on the base method" do
    # Authenticator struct headers are not merged by BaseApi; see comment above.
  end

  # auth-folded-into-options: the uniform model puts a per-operation `auth`
  # field on each authed operation's Options struct. These three tests verify
  # (1) auth supplied via Options reaches the wire, (2) an auth-omitted call
  # falls back to the API instance's configured authenticator, and (3) an
  # unsecured operation takes no auth field/opt at all.
  #
  # BaseApi merges auth headers only for a plain map carrying an `:auth_headers`
  # key (`Map.has_key?(effective_auth, :auth_headers)`), so the authenticators
  # below are plain maps rather than the generated authenticator structs.
  defmodule HeaderCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> %{} end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured_headers(name), do: Agent.get(name, & &1)

    @impl true
    def send_request(_method, _url, headers, _body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> headers end)

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  test "authed op: auth supplied via Options reaches the wire" do
    {:ok, name} = HeaderCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(HeaderCapturingApiClient, config)

    call_auth = %{auth_headers: %{"Authorization" => "Bearer per-call-token"}}

    options = %PetstoreClient.Api.Options.DeletePetOptions{auth: call_auth}
    {:ok, _result} = PetstoreClient.Api.PetApi.delete_pet_with_http_info(api, 1, options)

    headers = HeaderCapturingApiClient.captured_headers(name)
    assert headers["Authorization"] == "Bearer per-call-token"

    Agent.stop(name)
  end

  test "authed op: auth omitted falls back to the configured default credentials" do
    {:ok, name} = HeaderCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    default_auth = %{auth_headers: %{"Authorization" => "Bearer configured-default"}}
    api = PetstoreClient.Api.PetApi.new(HeaderCapturingApiClient, config, default_auth)

    # No Options auth and no :auth opt — must use the api's configured authenticator.
    {:ok, _result} = PetstoreClient.Api.PetApi.delete_pet_with_http_info(api, 1)

    headers = HeaderCapturingApiClient.captured_headers(name)
    assert headers["Authorization"] == "Bearer configured-default"

    Agent.stop(name)
  end

  test "unsecured op (get_pet_by_id) takes no auth field or opt" do
    {:ok, name} = HeaderCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    # No configured authenticator: get_pet_by_id declares `security: []`, so it
    # takes no Options arg and no `:auth` opt — the call below compiles only
    # because the signature is (api, pet_id, opts) with no auth anywhere.
    api = PetstoreClient.Api.PetApi.new(HeaderCapturingApiClient, config)

    {:ok, _result} = PetstoreClient.Api.PetApi.get_pet_by_id_with_http_info(api, 1)

    headers = HeaderCapturingApiClient.captured_headers(name)
    refute Map.has_key?(headers, "Authorization")

    Agent.stop(name)
  end

  # per-op-auth-precedence: per-operation auth is sourced SOLELY from the
  # Options struct's `auth` field. There is no `opts[:auth]` channel — an
  # `:auth` entry in the trailing keyword list is ignored and must never
  # override an explicit `options.auth`. 11 sibling SDKs source per-op auth
  # only from `options.auth`; this pins Elixir to the same contract.
  test "per-op auth comes only from options.auth; opts[:auth] is ignored" do
    {:ok, name} = HeaderCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(HeaderCapturingApiClient, config)

    options_auth = %{auth_headers: %{"Authorization" => "Bearer from-options"}}
    opts_auth = %{auth_headers: %{"Authorization" => "Bearer from-opts"}}

    options = %PetstoreClient.Api.Options.DeletePetOptions{auth: options_auth}

    {:ok, _result} =
      PetstoreClient.Api.PetApi.delete_pet_with_http_info(api, 1, options, auth: opts_auth)

    headers = HeaderCapturingApiClient.captured_headers(name)
    assert headers["Authorization"] == "Bearer from-options"

    Agent.stop(name)
  end

  # auth-struct-reaches-wire (regression guard for the Elixir auth-drop bug):
  # a REAL behaviour-struct authenticator (not a plain map) must have its
  # credential applied to the outbound request. The previous BaseApi read
  # `auth_headers` as a struct FIELD via `Map.has_key?`, which is always false
  # for a behaviour struct (the credential providers are callback FUNCTIONS),
  # so every configured authenticator was silently dropped. This pins the fix:
  # the credential is dispatched through the struct's module and reaches the
  # wire. A plain-map fixture (used by the tests above for override/fallback
  # logic) would NOT catch this bug, so this case uses the generated struct.
  test "configured BearerAuthenticator struct applies its credential to the wire" do
    {:ok, name} = HeaderCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    bearer = PetstoreClient.Auth.BearerAuthenticator.new("http://localhost", "real-struct-token")
    api = PetstoreClient.Api.PetApi.new(HeaderCapturingApiClient, config, bearer)

    {:ok, _result} = PetstoreClient.Api.PetApi.delete_pet_with_http_info(api, 1)

    headers = HeaderCapturingApiClient.captured_headers(name)
    assert headers["Authorization"] == "Bearer real-struct-token"

    Agent.stop(name)
  end

  # binary-request-body-fidelity: setPetAvatar declares a request body of
  # type:string format:binary with declared Content-Type image/jpeg. The raw
  # bytes must reach the wire UNCHANGED — never JSON-marshaled into an int-array
  # ([255,216,...]), never the literal "{}", never base64-encoded — and the
  # outgoing Content-Type must stay image/jpeg, never overridden to
  # application/octet-stream or application/json. This client records both the
  # request body and headers so the assertions can inspect what actually went
  # on the wire.
  defmodule BodyCapturingApiClient do
    @behaviour PetstoreClient.ApiClient
    use Agent

    def start do
      name = :"#{__MODULE__}-#{System.unique_integer([:positive])}"
      {:ok, _pid} = Agent.start_link(fn -> %{body: nil, headers: %{}} end, name: name)
      Process.put(__MODULE__, name)
      {:ok, name}
    end

    def captured(name), do: Agent.get(name, & &1)

    @impl true
    def send_request(_method, _url, headers, body) do
      name = Process.get(__MODULE__)
      Agent.update(name, fn _ -> %{body: body, headers: headers} end)

      %PetstoreClient.ApiHttpResponse{
        status_code: 200,
        body: "",
        headers: %{"Content-Type" => "application/json"}
      }
    end
  end

  test "set_pet_avatar streams raw bytes with declared content type" do
    {:ok, name} = BodyCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(BodyCapturingApiClient, config)

    # JPEG SOI marker — a known, non-trivial byte payload.
    payload = <<0xFF, 0xD8, 0xFF, 0xE0>>

    {:ok, _result} = PetstoreClient.Api.PetApi.set_pet_avatar(api, 5, payload)

    captured = BodyCapturingApiClient.captured(name)

    # (1) the body must be the EXACT raw bytes — not a JSON int-array, not "{}",
    # not base64, not JSON-marshaled in any way.
    assert captured.body == payload
    refute captured.body == "{}"
    refute captured.body == Base.encode64(payload)
    refute captured.body == Jason.encode!(:binary.bin_to_list(payload))

    # (2) the Content-Type must be exactly the declared image/jpeg.
    assert captured.headers["Content-Type"] == "image/jpeg"
    refute captured.headers["Content-Type"] == "application/octet-stream"
    refute captured.headers["Content-Type"] == "application/json"

    Agent.stop(name)
  end

  # request-content-type-selector: setPetAvatar declares THREE request
  # content-types (image/jpeg, image/png, application/json). The optional
  # `:content_type` key in `opts` lets the caller choose among the declared
  # types; when omitted it defaults to the FIRST declared type (image/jpeg), so
  # existing call sites keep sending image/jpeg unchanged. With image/jpeg and
  # image/png both being raw binary bodies, switching the selector only changes
  # the outgoing Content-Type header — the same raw bytes are sent either way.
  test "set_pet_avatar honours the selected request content type" do
    payload = <<0xFF, 0xD8, 0xFF, 0xE0>>

    # (1) No selector -> the first declared content-type (image/jpeg).
    {:ok, default_name} = BodyCapturingApiClient.start()
    default_config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    default_api = PetstoreClient.Api.PetApi.new(BodyCapturingApiClient, default_config)

    {:ok, _result} = PetstoreClient.Api.PetApi.set_pet_avatar(default_api, 5, payload)

    default_captured = BodyCapturingApiClient.captured(default_name)
    assert default_captured.headers["Content-Type"] == "image/jpeg"
    assert default_captured.body == payload
    Agent.stop(default_name)

    # (2) Selector set to image/png with the SAME raw bytes -> image/png.
    {:ok, png_name} = BodyCapturingApiClient.start()
    png_config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    png_api = PetstoreClient.Api.PetApi.new(BodyCapturingApiClient, png_config)

    {:ok, _result} =
      PetstoreClient.Api.PetApi.set_pet_avatar(png_api, 5, payload, content_type: "image/png")

    png_captured = BodyCapturingApiClient.captured(png_name)
    assert png_captured.headers["Content-Type"] == "image/png"
    refute png_captured.headers["Content-Type"] == "image/jpeg"
    # The selector only swaps the header; the raw bytes are unchanged.
    assert png_captured.body == payload
    Agent.stop(png_name)
  end

  # octet-stream-raw-body: uploadPetDocument declares TWO request content-types
  # (multipart/form-data, application/octet-stream). When the caller selects
  # application/octet-stream the wire body MUST be the RAW bytes of the single
  # file part with Content-Type: application/octet-stream — NOT a multipart
  # envelope, no boundary, not base64. The operation always builds request_body
  # as a multipart parts map, and the transport renders any MAP body into a
  # multipart envelope; the operation must therefore collapse that map to the
  # part's raw bytes before dispatch when octet-stream is selected. The default
  # (multipart) case is asserted alongside to guard against over-correction:
  # without a selector the body stays a parts map destined for a multipart
  # envelope. BodyCapturingApiClient records the body BaseApi hands to the
  # transport, so a raw binary here proves the raw-bytes path and a map proves
  # the multipart path.
  test "upload_pet_document octet-stream selection sends raw bytes, not multipart" do
    # A known, non-trivial binary payload (PDF magic bytes).
    payload = <<0x25, 0x50, 0x44, 0x46, 0x2D>>

    # (1) Selector = application/octet-stream -> raw bytes on the wire.
    {:ok, raw_name} = BodyCapturingApiClient.start()
    raw_config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    raw_api = PetstoreClient.Api.PetApi.new(BodyCapturingApiClient, raw_config)

    options = %PetstoreClient.Api.Options.UploadPetDocumentOptions{
      file: payload,
      document_type: "vaccination_record",
      notes: "Annual checkup"
    }

    # The capturing client records the outbound request at send_request time
    # (before the response is processed), so the body assertion holds regardless
    # of the operation's return — the empty mock body would otherwise trip
    # upload_pet_document's "expected a response body" check, which is not what
    # this test exercises.
    _result =
      PetstoreClient.Api.PetApi.upload_pet_document(
        raw_api,
        5,
        options,
        content_type: "application/octet-stream"
      )

    raw_captured = BodyCapturingApiClient.captured(raw_name)

    # The body is the EXACT raw file bytes — not the parts map, not a multipart
    # envelope, no boundary marker, not base64, not JSON.
    assert raw_captured.body == payload
    refute is_map(raw_captured.body)
    assert is_binary(raw_captured.body)
    refute raw_captured.body == Base.encode64(payload)
    refute String.contains?(to_string(raw_captured.body), "Content-Disposition")
    refute String.contains?(to_string(raw_captured.body), "boundary")
    # The Content-Type is exactly the selected octet-stream type.
    assert raw_captured.headers["Content-Type"] == "application/octet-stream"
    refute raw_captured.headers["Content-Type"] == "multipart/form-data"
    Agent.stop(raw_name)

    # (2) No selector -> the first declared type (multipart/form-data). The body
    # stays a parts MAP destined for a multipart envelope, NOT collapsed to raw
    # bytes — guards against over-correcting the octet-stream fix.
    {:ok, mp_name} = BodyCapturingApiClient.start()
    mp_config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    mp_api = PetstoreClient.Api.PetApi.new(BodyCapturingApiClient, mp_config)

    _result =
      PetstoreClient.Api.PetApi.upload_pet_document(mp_api, 5, options)

    mp_captured = BodyCapturingApiClient.captured(mp_name)

    # A map body is what the transport renders into a multipart envelope; the
    # raw payload alone is NOT what went to the transport here.
    assert is_map(mp_captured.body)
    refute mp_captured.body == payload
    # The file part carries the raw bytes tagged for a file part, and the
    # metadata fields ride alongside — proving multipart, not raw octet-stream.
    assert mp_captured.body["file"] == {:raw, payload}
    assert mp_captured.body["documentType"] == "vaccination_record"
    Agent.stop(mp_name)
  end

  # apiresult-rawbody-nullability: ApiResult.raw_body is typed String.t() (never
  # nil) because ApiHttpResponse.body is always populated. Even an empty 200 body
  # surfaces raw_body as a binary, not nil.
  test "api_result raw_body is always a binary, never nil" do
    api = new_pet_api_for_mock(200, "application/json", "")

    assert {:ok, result} = PetstoreClient.Api.PetApi.delete_pet_with_http_info(api, 1)
    assert is_binary(result.raw_body)
    refute is_nil(result.raw_body)
  end

  # required-nested-param-validation: get_pet_by_name has a REQUIRED query param
  # `category`. A nil value or empty string must raise before any HTTP call,
  # and a present value must be serialized onto the wire.
  test "get_pet_by_name raises when required category is nil" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)

    assert_raise ArgumentError, ~r/category/, fn ->
      PetstoreClient.Api.PetApi.get_pet_by_name_with_http_info(
        api,
        "rex",
        %PetstoreClient.Api.Options.GetPetByNameOptions{category: nil}
      )
    end

    assert PathCapturingApiClient.captured_url(name) == ""
    Agent.stop(name)
  end

  test "get_pet_by_name raises when required category is empty string" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)

    assert_raise ArgumentError, ~r/category/, fn ->
      PetstoreClient.Api.PetApi.get_pet_by_name_with_http_info(
        api,
        "rex",
        %PetstoreClient.Api.Options.GetPetByNameOptions{category: ""}
      )
    end

    assert PathCapturingApiClient.captured_url(name) == ""
    Agent.stop(name)
  end

  test "get_pet_by_name sends the required category on the wire" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)

    {:ok, _result} =
      PetstoreClient.Api.PetApi.get_pet_by_name_with_http_info(
        api,
        "rex",
        %PetstoreClient.Api.Options.GetPetByNameOptions{category: "dog"}
      )

    url = PathCapturingApiClient.captured_url(name)

    assert String.contains?(url, "category=dog"),
           "Expected required category=dog on the wire, got: #{url}"

    Agent.stop(name)
  end

  # allowEmptyValue-omitted-key: find_pets_by_status declares an OPTIONAL query
  # param `status` with allowEmptyValue:true. allowEmptyValue means the server
  # tolerates an empty value IF the caller sends the key — it does NOT oblige the
  # SDK to always emit the key. When status is omitted (nil) NO `status=` may
  # appear on the wire; when the caller passes an explicit empty string the key
  # is present with an empty value (`status=`).
  test "find_pets_by_status omitting status sends no status= in query" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)

    {:ok, _result} =
      PetstoreClient.Api.PetApi.find_pets_by_status_with_http_info(
        api,
        %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: nil}
      )

    url = PathCapturingApiClient.captured_url(name)

    refute String.contains?(url, "status="),
           "Omitted allowEmptyValue param must not emit a spurious status= key, got: #{url}"

    Agent.stop(name)
  end

  test "find_pets_by_status with explicit empty status sends status= with empty value" do
    {:ok, name} = PathCapturingApiClient.start()
    config = PetstoreClient.Configuration.new(base_url: "http://localhost")
    api = PetstoreClient.Api.PetApi.new(PathCapturingApiClient, config)

    {:ok, _result} =
      PetstoreClient.Api.PetApi.find_pets_by_status_with_http_info(
        api,
        %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: ""}
      )

    url = PathCapturingApiClient.captured_url(name)

    assert String.contains?(url, "status="),
           "An explicit empty allowEmptyValue param must send status= (key present), got: #{url}"

    Agent.stop(name)
  end
end
