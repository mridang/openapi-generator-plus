# Canonical API surface

This file is the single public API that all twelve generated SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir) implement. It is derived from the goldens under `src/spec/resources/generated/<lang>/` by `etc/api-surface.py`, which extracts every SDK's public surface into one normalised form (identifiers lower-cased with separators removed, so `getPetByIdWithHttpInfo`, `get_pet_by_id_with_http_info` and `GetPetByIdWithHTTPInfo` are one element) and compares them. `python3 etc/api-surface.py check` prints every mismatch not covered by `etc/api-surface-allow.json` and exits non-zero while any remain; `--all` also prints the allowed ones, `--json` everything.

Languages differ only by idiom. These are folded before comparing and are never reported: naming case (camelCase, PascalCase, snake_case, kebab file names); the error suffix (`Exception` in java, kotlin, csharp, php, python, dart; `Error` elsewhere); a get/is accessor versus a property (`getStatusCode()` = `status_code` = `StatusCode { get; }`), and the getter-less spelling (`url(overrides)` in ruby/go/rust/swift/dart/elixir = `getUrl(overrides)`); fluent/with-er setters versus properties; C#/node/dart `Async` suffixes; elixir `!` variants; ruby `?` predicates and `x=` writers; receivers (`self`, `&self`, elixir's api struct, go's `context.Context`); java overload sets (a parameter some overload omits is optional); optionality where the language has no default arguments (go, rust); members a type inherits (resolved per language) and, in rust/elixir, members reached by composition; elixir's trailing keyword `opts`; and identity hooks (`toString`, `equals`, `hashCode`, `__repr__`, `fmt`, Codable `init(from:)`). Every other forced difference is an allow-list entry with the language feature that forces it.

Legend for the tables: each language cell is that SDK's actual spelling; `✗` marks a mismatch to fix, `~` an allowed idiom difference, `—` absent. The Decision column cites a numbered canonical decision (`Dnn`, listed below) or states the default (the majority shape).

## Mismatches per language

| Language | Surface mismatches | Initialism casing | Total |
| --- | --- | --- | --- |
| java | 34 | 0 | 34 |
| kotlin | 26 | 0 | 26 |
| csharp | 16 | 0 | 16 |
| php | 58 | 0 | 58 |
| python | 43 | 0 | 43 |
| ruby | 86 | 0 | 86 |
| node | 53 | 0 | 53 |
| dart | 93 | 0 | 93 |
| go | 53 | 78 | 131 |
| rust | 63 | 0 | 63 |
| swift | 43 | 69 | 112 |
| elixir | 50 | 0 | 50 |
| **all** | **618** | **147** | **765** |

Counts are per (element, aspect, language) as `etc/api-surface.py check` reports them; 470 further differences are allowed idioms (`etc/api-surface-allow.json`).

## Per-language change list

One fix agent per language can execute its list. Paths are under `src/main/resources/templates/<lang>/`; `(codegen: …)` means the name comes from the generator class under `src/main/java/io/github/mridang/codegen/generators/<lang>/`. Parameter lists use the normalised keys (`clientid` = `clientId`/`client_id`); apply them in the language's own case. After the change, regenerate the goldens and rerun `python3 etc/api-surface.py check` until the language's count is zero.

### java (34 changes)

- `api/api.mustache`
  - `StoreApi.placeOrder`: optionality (order) -> (order?)
  - `StoreApi.placeOrderWithHttpInfo`: optionality (order) -> (order?)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.buildAuthorizationUrl`: optionality (state) -> (state?)
  - `OAuth2AuthorizationCodeAuthenticator` constructor (host, clientid, clientsecret, authorizationurl, tokenurl, refreshurl, redirecturi, scopes) -> (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl)
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.buildAuthorizationUrl`: optionality (state) -> (state?)
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator` constructor (host, clientid, clientsecret, tokenurl, refreshurl, username, password, scopes, clientauthmethod) -> (host, clientid, clientsecret, tokenurl, username, password, scopes, refreshurl, clientauthmethod)
- `auth/oauth/oauth2_token_manager.mustache`
  - `OAuth2TokenManager.exchange`: optionality (tokenurl, params, extraheaders) -> (tokenurl, params, extraheaders?)
- `auth/oauth/openid_connect_authenticator.mustache`
  - `OpenIdConnectAuthenticator.buildAuthorizationUrl`: optionality (state) -> (state?)
- `errors/BadRequestException.mustache`
  - `BadRequestException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/ConflictException.mustache`
  - `ConflictException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/ForbiddenException.mustache`
  - `ForbiddenException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/InternalServerErrorException.mustache`
  - `InternalServerErrorException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/NotFoundException.mustache`
  - `NotFoundException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/OAuth2TokenException.mustache`
  - `OAuth2TokenException` constructor (message, cause) -> (message)
- `errors/UnauthorizedException.mustache`
  - `UnauthorizedException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/UnprocessableEntityException.mustache`
  - `UnprocessableEntityException` constructor defaults (message, responseheaders, responsebody, errorbody) -> (message?, responseheaders?, responsebody?, errorbody?)
- `models/model.mustache`
  - make `Metadata.setAdditionalProperty` non-public (or remove)
  - rename `ModelResult` to `Result` (BetterJavaCodegen)
  - add `actualInstance` to `PetFood`
  - make `PetTreatment.getInstances` non-public (or remove)
  - add `matchedInstances` to `PetTreatment`
  - make `StrictTag.setUnknownProperty` non-public (or remove)
  - make `TreatmentRecord.getInstances` non-public (or remove)
  - add `matchedInstances` to `TreatmentRecord`
  - rename model `ModelResult` to `Result` (BetterJavaCodegen.toModelName/getTypeDeclaration: drop "Result" from the Model-prefix set, keep "Error")
- `object_serializer.mustache`
  - make `ObjectSerializer.MAX_JSON_DEPTH` non-public (or remove)
  - `ObjectSerializer.deserialize`: parameters (jsonstring, type) -> (jsonstring, targettype)
  - `ObjectSerializer.toQueryValue`: optionality (value, collectionformat) -> (value, collectionformat?)
  - make `ObjectSerializer.formatTime` non-public (or remove)
  - make `ObjectSerializer.structuralEquals` non-public (or remove)
  - make `ObjectSerializer.structuralHashCode` non-public (or remove)
  - add `parseJson` to `ObjectSerializer`
- `server_configuration.mustache`
  - `ServerConfiguration` constructor defaults (urltemplate, description, variables) -> (urltemplate, description?, variables?)
- `server_variable.mustache`
  - `ServerVariable` constructor defaults (defaultvalue, description, enumvalues) -> (defaultvalue, description?, enumvalues?)

### kotlin (24 changes)

- `auth/oauth/oauth2_token_manager.mustache`
  - add `exchanged` to `OAuth2TokenManager`
  - make `OAuth2TokenManager.EXPIRY_SAFETY_MARGIN_MS` non-public (or remove)
  - make `OAuth2TokenManager.apiClient` non-public (or remove)
- `default_api_client.mustache`
  - `DefaultApiClient.sendRequest`: optionality (method, url, headers, body, noredirect) -> (method, url, headers, body, noredirect?)
- `errors/BadRequestException.mustache`
  - `BadRequestException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/ClientException.mustache`
  - `ClientException` constructor defaults (statuscode, message, responseheaders, responsebody, errorbody?) -> (statuscode, message, responseheaders, responsebody, errorbody)
- `errors/ConflictException.mustache`
  - `ConflictException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/ForbiddenException.mustache`
  - `ForbiddenException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/InternalServerErrorException.mustache`
  - `InternalServerErrorException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/NotFoundException.mustache`
  - `NotFoundException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/OAuth2ServerException.mustache`
  - make `OAuth2ServerException.buildMessage` non-public (or remove)
- `errors/OAuth2TokenException.mustache`
  - `OAuth2TokenException` constructor (message, cause) -> (message)
- `errors/ServerException.mustache`
  - `ServerException` constructor defaults (statuscode, message, responseheaders, responsebody, errorbody?) -> (statuscode, message, responseheaders, responsebody, errorbody)
- `errors/UnauthorizedException.mustache`
  - `UnauthorizedException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/UnprocessableEntityException.mustache`
  - `UnprocessableEntityException` constructor defaults (message, responseheaders, responsebody, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `models/model.mustache`
  - make `PetTreatment.instances` non-public (or remove)
  - add `matchedInstances` to `PetTreatment`
  - `RequiredNullable.note`: optional -> required
  - make `StrictTag.parseStrict` non-public (or remove)
  - make `TreatmentRecord.instances` non-public (or remove)
  - add `matchedInstances` to `TreatmentRecord`
- `object_serializer.mustache`
  - make `ObjectSerializer` public (object_serializer.mustache)
- `server_configuration.mustache`
  - `ServerConfiguration` constructor defaults (urltemplate, description, variables) -> (urltemplate, description?, variables?)
  - `ServerVariable` constructor defaults (defaultvalue, description, enumvalues) -> (defaultvalue, description?, enumvalues?)

### csharp (12 changes)

- `auth/oauth/oauth2_token_manager.mustache`
  - add `exchanged` to `OAuth2TokenManager`
- `default_api_client.mustache`
  - make `DefaultApiClient.SensitiveHeaderNames` non-public (or remove)
  - make `DefaultApiClient.ProxyAuthorizationHeader` non-public (or remove)
  - make `DefaultApiClient.ValidateMultipartFilename` non-public (or remove)
  - make `DefaultApiClient.BuildFilenameDirective` non-public (or remove)
  - make `DefaultApiClient.Rfc5987EncodeValue` non-public (or remove)
- `models/model.mustache`
  - `Metadata.AdditionalProperties`: optional -> required
  - add `actualInstance` to `PetFood`
  - make `StrictTag.UnknownProperties` non-public (or remove)
- `object_serializer.mustache`
  - make `ObjectSerializer` public (object_serializer.mustache)
- `server_configuration.mustache`
  - `ServerConfiguration` constructor defaults (urltemplate, description, variables) -> (urltemplate, description?, variables?)
  - `ServerVariable` constructor defaults (defaultvalue, description, enumvalues) -> (defaultvalue, description?, enumvalues?)

### php (56 changes)

- `abstract_api_client.mustache`
  - mark `AbstractApiClient` `@internal` (abstract_api_client.mustache)
- `api/api.mustache`
  - `PetApi.setPetAvatar`: parameters (petid, body, contenttype) -> (petid, body, requestcontenttype)
  - `PetApi.setPetAvatarWithHttpInfo`: parameters (petid, body, contenttype) -> (petid, body, requestcontenttype)
  - `PetApi.uploadPetDocument`: parameters (petid, options, contenttype) -> (petid, options, requestcontenttype)
  - `PetApi.uploadPetDocumentWithHttpInfo`: parameters (petid, options, contenttype) -> (petid, options, requestcontenttype)
- `api/server_type.mustache`
  - add `environment` to `GetStagingPetInfoServerStagingServer`
  - add `version` to `GetStagingPetInfoServerStagingServer`
- `api/server_variant.mustache`
  - add `region` to `GetMultiServerPetInfoServerRegional`
  - add `region` to `GetMultiServerPetInfoServerMirror`
  - add `channel` to `GetMultiServerPetInfoServerMirror`
- `client.mustache`
  - `Client.withToken`: parameters (host, accesstoken, transportoptions, apiclient) -> (host, accesstoken, transportoptions)
  - `Client.withAuthenticator`: parameters (authenticator, transportoptions, apiclient) -> (authenticator, transportoptions)
  - `Client` constructor (authenticator, transportoptions, apiclient) -> (authenticator, transportoptions)
- `models/model.mustache`
  - make `BinaryVault.getSealAsBytes` non-public (or remove)
  - make `BinaryVault.getShardsAsBytes` non-public (or remove)
  - make `BinaryVault.getLabelsAsBytes` non-public (or remove)
  - make `BinaryVault.setSealFromBytes` non-public (or remove)
  - make `BinaryVault.setShardsFromBytes` non-public (or remove)
  - make `BinaryVault.setLabelsFromBytes` non-public (or remove)
  - `Metadata.additionalProperties`: optional -> required
  - make `PetPassport.getThumbnailAsBytes` non-public (or remove)
  - make `PetPassport.getScansAsBytes` non-public (or remove)
  - make `PetPassport.getBiometricChipAsBytes` non-public (or remove)
  - make `PetPassport.setThumbnailFromBytes` non-public (or remove)
  - make `PetPassport.setScansFromBytes` non-public (or remove)
  - make `PetPassport.setBiometricChipFromBytes` non-public (or remove)
  - add `matchedInstances` to `PetTreatment`
  - make `PetTreatment.getActualInstances` non-public (or remove)
  - make `SetPetAvatarRequest.getDataAsBytes` non-public (or remove)
  - make `SetPetAvatarRequest.setDataFromBytes` non-public (or remove)
  - make `SetPetAvatarThumbnailRequest.getActualInstanceAsBytes` non-public (or remove)
  - make `SetPetAvatarThumbnailRequest.getActualInstanceAsByteList` non-public (or remove)
  - make `SetPetAvatarThumbnailRequest.fromBytes` non-public (or remove)
  - make `SetPetAvatarThumbnailRequest.fromByteList` non-public (or remove)
  - make `StrictTag.assertNoUnknownProperties` non-public (or remove)
  - add `matchedInstances` to `TreatmentRecord`
  - make `TreatmentRecord.getActualInstances` non-public (or remove)
- `object_serializer.mustache`
  - `ObjectSerializer.serialize`: parameters (data) -> (object)
  - `ObjectSerializer.deserialize`: parameters (data, class, httpheaders) -> (jsonstring, targettype)
  - `ObjectSerializer.resolveOneOf`: parameters (data, candidates) -> (json, candidates)
  - `ObjectSerializer.resolveAnyOf`: parameters (data, candidates) -> (json, candidates)
  - make `ObjectSerializer.parseRfc3339` non-public (or remove)
  - make `ObjectSerializer.encodeBase64Param` non-public (or remove)
  - make `ObjectSerializer.isPrimitiveValue` non-public (or remove)
  - make `ObjectSerializer.isStrictBase64` non-public (or remove)
  - make `ObjectSerializer.toPathDateValue` non-public (or remove)
  - make `ObjectSerializer.resolveAnyOfAll` non-public (or remove)
  - make `ObjectSerializer.qualifySchemaName` non-public (or remove)
  - make `ObjectSerializer.decodeBytes` non-public (or remove)
  - make `ObjectSerializer.encodeBytes` non-public (or remove)
  - make `ObjectSerializer.decodeByteOneOfScalar` non-public (or remove)
  - make `ObjectSerializer.decodeByteOneOfArray` non-public (or remove)
  - make `ObjectSerializer.formatProtobufDuration` non-public (or remove)
  - make `ObjectSerializer.parseProtobufDuration` non-public (or remove)
  - add `parseJson` to `ObjectSerializer`
- `transport_options.mustache`
  - make `TransportOptions.assertValidProxy` non-public (or remove)

### python (37 changes)

- `__init__.mustache`
  - `ApiException.from_response`: parameters (statuscode, headers, body) -> (statuscode, responseheaders, responsebody)
  - `ApiException` constructor (statuscode, message, responseheaders, responsebody, errorbody) -> (statuscode, message, responseheaders, responsebody, errorbody, cause)
- `api/api.mustache`
  - add `region` to `GetMultiServerPetInfoServerRegional`
  - add `region` to `GetMultiServerPetInfoServerMirror`
  - add `channel` to `GetMultiServerPetInfoServerMirror`
  - add `environment` to `GetStagingPetInfoServerStagingServer`
  - add `version` to `GetStagingPetInfoServerStagingServer`
- `api_client.mustache`
  - add `close` to `ApiClient`
  - `ApiClient.send_request`: optionality (method, url, headers, body?, noredirect?) -> (method, url, headers, body, noredirect?)
- `auth/api_key_authenticator.mustache`
  - make `ApiKeyAuthenticator.key_param_name` non-public (or remove)
  - make `ApiKeyAuthenticator.api_key` non-public (or remove)
  - make `ApiKeyAuthenticator.location` non-public (or remove)
- `auth/basic_authenticator.mustache`
  - make `BasicAuthenticator.username` non-public (or remove)
  - make `BasicAuthenticator.password` non-public (or remove)
- `auth/bearer_authenticator.mustache`
  - make `BearerAuthenticator.token` non-public (or remove)
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - make `OAuth2ClientCredentialsAuthenticator.client_id` non-public (or remove)
  - make `OAuth2ClientCredentialsAuthenticator.client_secret` non-public (or remove)
  - make `OAuth2ClientCredentialsAuthenticator.token_url` non-public (or remove)
  - make `OAuth2ClientCredentialsAuthenticator.scopes` non-public (or remove)
  - make `OAuth2ClientCredentialsAuthenticator.client_auth_method` non-public (or remove)
  - `OAuth2ClientCredentialsAuthenticator` constructor defaults (host, clientid, clientsecret, tokenurl, scopes?, clientauthmethod?) -> (host, clientid, clientsecret, tokenurl, scopes, clientauthmethod?)
- `auth/oauth/oauth2_password_authenticator.mustache`
  - make `OAuth2PasswordAuthenticator.client_id` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.client_secret` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.token_url` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.username` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.password` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.scopes` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.refresh_url` non-public (or remove)
  - make `OAuth2PasswordAuthenticator.client_auth_method` non-public (or remove)
  - `OAuth2PasswordAuthenticator` constructor defaults (host, clientid, clientsecret, tokenurl, username, password, scopes?, refreshurl?, clientauthmethod?) -> (host, clientid, clientsecret, tokenurl, username, password, scopes, refreshurl?, clientauthmethod?)
- `default_api_client.mustache`
  - `DefaultApiClient.send_request`: optionality (method, url, headers, body?, noredirect?) -> (method, url, headers, body, noredirect?)
  - `DefaultApiClient` constructor (transportoptions, poolmanager) -> (transportoptions)
- `errors/network_exception.mustache`
  - `NetworkException` constructor (message) -> (message, cause)
- `object_serializer.mustache`
  - `ObjectSerializer.serialize`: parameters (obj) -> (object)
  - add `resolveOneOf` to `ObjectSerializer`
  - add `resolveAnyOf` to `ObjectSerializer`
  - `ObjectSerializer.parse_json`: parameters (jsonstring) -> (data)

### ruby (80 changes)

- `api/api.mustache`
  - `PetApi.set_pet_avatar`: parameters (petid, body, contenttype) -> (petid, body, requestcontenttype)
  - `PetApi.set_pet_avatar_with_http_info`: parameters (petid, body, contenttype) -> (petid, body, requestcontenttype)
  - `PetApi.upload_pet_document`: parameters (petid, options, contenttype) -> (petid, options, requestcontenttype)
  - `PetApi.upload_pet_document_with_http_info`: parameters (petid, options, contenttype) -> (petid, options, requestcontenttype)
  - make `PetApi.config` non-public (or remove)
  - add `region` to `GetMultiServerPetInfoServerRegional`
  - add `region` to `GetMultiServerPetInfoServerMirror`
  - add `channel` to `GetMultiServerPetInfoServerMirror`
  - add `environment` to `GetStagingPetInfoServerStagingServer`
  - add `version` to `GetStagingPetInfoServerStagingServer`
  - make `StoreApi.config` non-public (or remove)
- `auth/http_aware_authenticator.mustache`
  - `HttpAwareAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.set_api_client`: parameters (client) -> (apiclient)
  - make `OAuth2AuthorizationCodeAuthenticator.token_manager` non-public (or remove)
  - `OAuth2AuthorizationCodeAuthenticator` constructor (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl, tokenmanager) -> (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl)
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - `OAuth2ClientCredentialsAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_token_manager.mustache`
  - make `OAuth2TokenManager.EXPIRY_SAFETY_MARGIN_S` non-public (or remove)
  - make `OAuth2TokenManager.mark_exchanged` non-public (or remove)
- `auth/oauth/openid_connect_authenticator.mustache`
  - make `OpenIdConnectAuthenticator.DEFAULT_DISCOVERY_MAX_AGE_SECONDS` non-public (or remove)
- `base_api.mustache`
  - make `BaseApi.NO_AUTH` non-public (or remove)
  - make `BaseApi.config` non-public (or remove)
- `client.mustache`
  - rename class `Petstore` to `Client` (client.mustache, sig, petstore_client.rb require)
- `configuration.mustache`
  - `Configuration` constructor defaults (baseurl, defaultheaders) -> (baseurl?, defaultheaders?)
  - `ConfigurationBuilder.base_url`: parameters (val) -> (baseurl)
- `default_api_client.mustache`
  - make `DefaultApiClient.EXTRA_SENSITIVE_HEADER_NAMES` non-public (or remove)
  - make `DefaultApiClient.supported_encodings` non-public (or remove)
- `errors/api_error.mustache`
  - `ApiError.from_response`: parameters (statuscode, headers, body) -> (statuscode, responseheaders, responsebody)
  - make `ApiError.detail_message` non-public (or remove)
  - make `ApiError.parse_error_body` non-public (or remove)
  - `ApiError` constructor (arg) -> (statuscode, message, responseheaders, responsebody, errorbody, cause)
- `errors/network_error.mustache`
  - `NetworkError` constructor (message) -> (message, cause)
- `models/model.mustache`
  - generate `<Model><Prop>Enum` constant classes for inline enums (models/model.mustache)
  - generate the named inline enum (models/model.mustache)
  - add `actualInstance` to `PetFood`
  - add `actualInstance` to `PetTreatment`
  - add `matchedInstances` to `PetTreatment`
  - add `actualInstance` to `SetPetAvatarThumbnailRequest`
  - make `StrictTag.UNEVALUATED_PROPERTIES_FALSE` non-public (or remove)
  - add `actualInstance` to `TreatmentRecord`
  - add `matchedInstances` to `TreatmentRecord`
- `object_serializer.mustache`
  - make `ObjectSerializer.MAX_JSON_DEPTH` non-public (or remove)
  - add `resolveOneOf` to `ObjectSerializer`
  - add `resolveAnyOf` to `ObjectSerializer`
  - make `ObjectSerializer.encode_base64_param` non-public (or remove)
  - `ObjectSerializer.parse_json`: parameters (jsonstring, symbolizenames) -> (data)
  - make `ObjectSerializer.DEFAULT_DATETIME_FORMAT` non-public (or remove)
  - make `ObjectSerializer.RFC3339_DATETIME_REGEX` non-public (or remove)
  - make `ObjectSerializer.URI_REGEX` non-public (or remove)
  - make `ObjectSerializer.PROTOBUF_DURATION_REGEX` non-public (or remove)
  - make `ObjectSerializer.UUID_REGEX` non-public (or remove)
  - make `ObjectSerializer.json_max_depth` non-public (or remove)
  - make `ObjectSerializer.encode_oneof_body` non-public (or remove)
  - make `ObjectSerializer.sanitize_for_serialization` non-public (or remove)
  - make `ObjectSerializer.sort_scalar_set` non-public (or remove)
  - make `ObjectSerializer.convert_element` non-public (or remove)
  - make `ObjectSerializer.model_type?` non-public (or remove)
  - make `ObjectSerializer.convert_to_type` non-public (or remove)
  - make `ObjectSerializer.deserialize_model` non-public (or remove)
  - make `ObjectSerializer.apply_format_on_deserialize` non-public (or remove)
  - make `ObjectSerializer.decode_byte` non-public (or remove)
  - make `ObjectSerializer.validate_uuid` non-public (or remove)
  - make `ObjectSerializer.validate_uri` non-public (or remove)
  - make `ObjectSerializer.duration_to_protobuf_json` non-public (or remove)
  - make `ObjectSerializer.duration_from_protobuf_json` non-public (or remove)
  - make `ObjectSerializer.apply_format_on_serialize` non-public (or remove)
  - make `ObjectSerializer.encode_byte` non-public (or remove)
- `servers.mustache`
  - add `all` to `Servers`
- `transport_options.mustache`
  - make `TransportOptions.validate_proxy!` non-public (or remove)
  - `TransportOptions` constructor defaults (verifyssl, cacertpath, proxy, timeout, followredirects, maxredirects, useragent, defaultheaders, injectrequestid) -> (verifyssl?, cacertpath?, proxy?, timeout?, followredirects?, maxredirects?, useragent?, defaultheaders?, injectrequestid?)
  - `TransportOptionsBuilder.verify_ssl`: parameters (val) -> (verifyssl)
  - `TransportOptionsBuilder.ca_cert_path`: parameters (val) -> (cacertpath)
  - `TransportOptionsBuilder.proxy`: parameters (val) -> (proxy)
  - `TransportOptionsBuilder.timeout`: parameters (val) -> (timeout)
  - `TransportOptionsBuilder.follow_redirects`: parameters (val) -> (followredirects)
  - `TransportOptionsBuilder.max_redirects`: parameters (val) -> (maxredirects)
  - `TransportOptionsBuilder.user_agent`: parameters (val) -> (useragent)
  - `TransportOptionsBuilder.inject_request_id`: parameters (val) -> (injectrequestid)
- `(codegen: BetterRubyCodegen.java)`
  - rename `Petstore` to `Client` (client.mustache)

### node (41 changes)

- `abstract_api_client.mustache`
  - stop exporting `AbstractApiClient` (abstract_api_client.mustache, index.mustache)
- `api/apis.mustache`
  - `PetApi.setPetAvatar`: parameters (petid, body, contenttype) -> (petid, body, requestcontenttype)
  - `PetApi.setPetAvatarWithHttpInfo`: parameters (petid, body, contenttype) -> (petid, body, requestcontenttype)
  - `PetApi.uploadPetDocument`: parameters (petid, options, contenttype) -> (petid, options, requestcontenttype)
  - `PetApi.uploadPetDocumentWithHttpInfo`: parameters (petid, options, contenttype) -> (petid, options, requestcontenttype)
- `api_client.mustache`
  - add `close` to `ApiClient`
  - `ApiClient.sendRequest`: parameters (method, url, headers, body, options) -> (method, url, headers, body, noredirect)
- `auth/http_aware_authenticator.mustache`
  - make `HttpAwareAuthenticator.isHttpAwareAuthenticator` non-public (or remove)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator` constructor (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl, tokenmanager) -> (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl)
- `auth/oauth/oauth2_token_manager.mustache`
  - make `OAuth2TokenManager.markExchanged` non-public (or remove)
- `authenticator.mustache`
  - make `Authenticator.isNoAuth` non-public (or remove)
- `default_api_client.mustache`
  - `DefaultApiClient.sendRequest`: parameters (method, url, headers, body, options) -> (method, url, headers, body, noredirect)
  - make `DefaultApiClient.buildDispatcher` non-public (or remove)
- `errors/api-error.mustache`
  - make `ApiError.registerStatusError` non-public (or remove)
- `errors/bad-request-error.mustache`
  - `BadRequestError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/client-error.mustache`
  - `ClientError` constructor defaults (statuscode, message, responseheaders?, responsebody?, errorbody?) -> (statuscode, message, responseheaders, responsebody, errorbody)
- `errors/conflict-error.mustache`
  - `ConflictError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/forbidden-error.mustache`
  - `ForbiddenError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/internal-server-error.mustache`
  - `InternalServerError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/not-found-error.mustache`
  - `NotFoundError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/server-error.mustache`
  - `ServerError` constructor defaults (statuscode, message, responseheaders?, responsebody?, errorbody?) -> (statuscode, message, responseheaders, responsebody, errorbody)
- `errors/unauthorized-error.mustache`
  - `UnauthorizedError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `errors/unprocessable-entity-error.mustache`
  - `UnprocessableEntityError` constructor defaults (message, responseheaders?, responsebody?, errorbody?) -> (message?, responseheaders?, responsebody?, errorbody?)
- `models/model.mustache`
  - add `additionalProperties` to `Metadata`
  - add `actualInstance` to `SetPetAvatarThumbnailRequest`
  - make `StrictTag.fromJsonStrict` non-public (or remove)
- `object_serializer.mustache`
  - `ObjectSerializer.serialize`: parameters (obj) -> (object)
  - `ObjectSerializer.deserialize`: parameters (json, cls) -> (jsonstring, targettype)
  - `ObjectSerializer.resolveOneOf`: optionality (json, candidates?) -> (json, candidates)
  - `ObjectSerializer.resolveAnyOf`: optionality (json, candidates?) -> (json, candidates)
  - `ObjectSerializer.parseJson`: parameters (json) -> (data)
  - make `ObjectSerializer.durationToProtoJson` non-public (or remove)
  - make `ObjectSerializer.durationFromProtoJson` non-public (or remove)
  - make `ObjectSerializer.decodeBase64` non-public (or remove)
  - make `ObjectSerializer.deserializeArray` non-public (or remove)
  - make `ObjectSerializer.deserializeMap` non-public (or remove)
  - make `ObjectSerializer.deserializeEnum` non-public (or remove)
  - make `ObjectSerializer.deserializeLeaf` non-public (or remove)
- `server_configuration.mustache`
  - `ServerConfiguration` constructor defaults (urltemplate, description, variables) -> (urltemplate, description?, variables?)
  - `ServerVariable` constructor defaults (defaultvalue, description, enumvalues) -> (defaultvalue, description?, enumvalues?)
- `value_serializer.mustache`
  - stop exporting `AllowReservedValue`

### dart (82 changes)

- `abstract_api_client.mustache`
  - stop exporting `AbstractApiClient` (abstract_api_client.mustache, barrel.mustache)
- `api/api.mustache`
  - make `PetApi.config` non-public (or remove)
  - make `PetApi.apiClient` non-public (or remove)
  - add `region` to `GetMultiServerPetInfoServerRegional`
  - add `region` to `GetMultiServerPetInfoServerMirror`
  - add `channel` to `GetMultiServerPetInfoServerMirror`
  - add `environment` to `GetStagingPetInfoServerStagingServer`
  - add `version` to `GetStagingPetInfoServerStagingServer`
  - make `StoreApi.config` non-public (or remove)
  - make `StoreApi.apiClient` non-public (or remove)
- `api_client.mustache`
  - add `close` to `ApiClient`
- `auth/http_aware_authenticator.mustache`
  - `HttpAwareAuthenticator.setApiClient`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2AuthorizationCodeAuthenticator` constructor defaults (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes?, refreshurl?) -> (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl?)
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - `OAuth2ClientCredentialsAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2ClientCredentialsAuthenticator` constructor defaults (host, clientid, clientsecret, tokenurl, scopes?, clientauthmethod?) -> (host, clientid, clientsecret, tokenurl, scopes, clientauthmethod?)
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2ImplicitAuthenticator` constructor defaults (host, clientid, authorizationurl, scopes?) -> (host, clientid, authorizationurl, scopes)
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2PasswordAuthenticator` constructor defaults (host, clientid, clientsecret, tokenurl, username, password, scopes?, refreshurl?, clientauthmethod?) -> (host, clientid, clientsecret, tokenurl, username, password, scopes, refreshurl?, clientauthmethod?)
- `auth/oauth/oauth2_token_manager.mustache`
  - add `exchanged` to `OAuth2TokenManager`
  - `OAuth2TokenManager.setApiClient`: parameters (client) -> (apiclient)
- `auth/oauth/openid_connect_authenticator.mustache`
  - `OpenIdConnectAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OpenIdConnectAuthenticator` constructor defaults (host, openidconnecturl, clientid, clientsecret, redirecturi, scopes?) -> (host, openidconnecturl, clientid, clientsecret, redirecturi, scopes)
- `authenticator.mustache`
  - make `Authenticator.isnoauth` non-public (or remove)
  - `Authenticator.isnoauth`: parameters () -> (auth)
- `base_api.mustache`
  - make `BaseApi.config` non-public (or remove)
  - make `BaseApi.apiClient` non-public (or remove)
  - make `BaseApi.invokeApi` non-public (or remove)
  - make `BaseApi.invokeApiForResult` non-public (or remove)
- `configuration.mustache`
  - `ConfigurationBuilder.baseUrl`: parameters (val) -> (baseurl)
  - `ConfigurationBuilder.server`: parameters (server, variables) -> (serverconfig, variables)
- `default_api_client_io.mustache`
  - make `DefaultApiClient.proxyAuthHeader` non-public (or remove)
  - make `DefaultApiClient.isNetworkError` non-public (or remove)
  - make `DefaultApiClient.decompressBytes` non-public (or remove)
- `errors/api_exception.mustache`
  - make `ApiException.underlyingError` non-public (or remove)
  - `ApiException` constructor (statuscode, message, responsebody, responseheaders, errorbody, underlyingerror) -> (statuscode, message, responseheaders, responsebody, errorbody, cause)
- `errors/network_exception.mustache`
  - `NetworkException` constructor (message, underlyingerror) -> (message, cause)
- `errors/network_timeout_exception.mustache`
  - `NetworkTimeoutException` constructor (message, underlyingerror) -> (message, cause)
- `errors/root_exception.mustache`
  - `OpenAPIException` constructor () -> (message, cause)
- `models/model.mustache`
  - add `actualInstance` to `PetFood`
  - make `PetFood.value` non-public (or remove)
  - add `actualInstance` to `PetTreatment`
  - add `matchedInstances` to `PetTreatment`
  - make `PetTreatment.value` non-public (or remove)
  - make `PetTreatment.values` non-public (or remove)
  - add `actualInstance` to `SetPetAvatarThumbnailRequest`
  - make `SetPetAvatarThumbnailRequest.value` non-public (or remove)
  - add `actualInstance` to `TreatmentRecord`
  - add `matchedInstances` to `TreatmentRecord`
  - make `TreatmentRecord.value` non-public (or remove)
  - make `TreatmentRecord.values` non-public (or remove)
- `object_serializer.mustache`
  - `object_serializer.deserialize`: parameters (data, fromjson) -> (jsonstring, targettype)
  - `object_serializer.toQueryValue`: optionality (value, collectionformat) -> (value, collectionformat?)
  - `object_serializer.resolveOneOf`: parameters (data, fromjsoncandidates) -> (json, candidates)
  - `object_serializer.resolveAnyOf`: parameters (data, fromjsoncandidates) -> (json, candidates)
  - make `object_serializer.decodeBase64` non-public (or remove)
  - `object_serializer.decodeBase64`: parameters (text) -> (value)
  - make `object_serializer.deserializeArray` non-public (or remove)
  - `object_serializer.deserializeArray`: parameters (json, deserializeelement) -> (json, cls)
  - make `object_serializer.deserializeMap` non-public (or remove)
  - `object_serializer.deserializeMap`: parameters (json, deserializevalue) -> (json, cls)
  - make `object_serializer.deserializeList` non-public (or remove)
  - make `object_serializer.wireList` non-public (or remove)
  - make `object_serializer.wireListOrNull` non-public (or remove)
  - make `object_serializer.deserializeMapFromJson` non-public (or remove)
  - make `object_serializer.deserializeArrayFromJson` non-public (or remove)
  - make `object_serializer.deserializeRaw` non-public (or remove)
  - make `object_serializer.parseDateTime` non-public (or remove)
  - make `object_serializer.parseUri` non-public (or remove)
  - make `object_serializer.formatDateTimeOffset` non-public (or remove)
- `servers.mustache`
  - add `all` to `servers`
  - make `servers.allServers` non-public (or remove)
- `transport_options.mustache`
  - `TransportOptionsBuilder.verifySsl`: parameters (val) -> (verifyssl)
  - `TransportOptionsBuilder.caCertPath`: parameters (val) -> (cacertpath)
  - `TransportOptionsBuilder.proxy`: parameters (val) -> (proxy)
  - `TransportOptionsBuilder.timeout`: parameters (val) -> (timeout)
  - `TransportOptionsBuilder.followRedirects`: parameters (val) -> (followredirects)
  - `TransportOptionsBuilder.maxRedirects`: parameters (val) -> (maxredirects)
  - `TransportOptionsBuilder.userAgent`: parameters (val) -> (useragent)
  - `TransportOptionsBuilder.injectRequestId`: parameters (val) -> (injectrequestid)
- `value_serializer.mustache`
  - stop exporting `AllowReservedValue`

### go (125 changes)

- `api/api.mustache`
  - rename `AddPetWithHTTPInfo` to `AddPetWithHttpInfo`
  - rename `AddPetPhotosWithHTTPInfo` to `AddPetPhotosWithHttpInfo`
  - rename `AddPetTreatmentWithHTTPInfo` to `AddPetTreatmentWithHttpInfo`
  - rename `DeletePetWithHTTPInfo` to `DeletePetWithHttpInfo`
  - rename `DownloadPetDocumentWithHTTPInfo` to `DownloadPetDocumentWithHttpInfo`
  - rename `FindPetsBornOnWithHTTPInfo` to `FindPetsBornOnWithHttpInfo`
  - rename `FindPetsByStatusWithHTTPInfo` to `FindPetsByStatusWithHttpInfo`
  - rename `GetExternalPetInfoWithHTTPInfo` to `GetExternalPetInfoWithHttpInfo`
  - rename `GetMultiServerPetInfoWithHTTPInfo` to `GetMultiServerPetInfoWithHttpInfo`
  - rename `GetPetAvatarWithHTTPInfo` to `GetPetAvatarWithHttpInfo`
  - rename `GetPetAvatarThumbnailWithHTTPInfo` to `GetPetAvatarThumbnailWithHttpInfo`
  - rename `GetPetByIdWithHTTPInfo` to `GetPetByIdWithHttpInfo`
  - rename `GetPetByNameWithHTTPInfo` to `GetPetByNameWithHttpInfo`
  - rename `GetPetPassportWithHTTPInfo` to `GetPetPassportWithHttpInfo`
  - rename `GetPetPhotoWithHTTPInfo` to `GetPetPhotoWithHttpInfo`
  - rename `GetPetSummaryWithHTTPInfo` to `GetPetSummaryWithHttpInfo`
  - rename `GetPetTagWithHTTPInfo` to `GetPetTagWithHttpInfo`
  - rename `GetStagingPetInfoWithHTTPInfo` to `GetStagingPetInfoWithHttpInfo`
  - rename `SetPetAvatarWithHTTPInfo` to `SetPetAvatarWithHttpInfo`
  - rename `SetPetAvatarThumbnailWithHTTPInfo` to `SetPetAvatarThumbnailWithHttpInfo`
  - rename `SetPetPreferencesWithHTTPInfo` to `SetPetPreferencesWithHttpInfo`
  - rename `UpdatePetWithHTTPInfo` to `UpdatePetWithHttpInfo`
  - rename `UploadPetCertificateWithHTTPInfo` to `UploadPetCertificateWithHttpInfo`
  - rename `UploadPetDocumentWithHTTPInfo` to `UploadPetDocumentWithHttpInfo`
  - rename `GetDefaultsWithHTTPInfo` to `GetDefaultsWithHttpInfo`
  - rename `GetDepartmentWithHTTPInfo` to `GetDepartmentWithHttpInfo`
  - rename `GetGroupedCategoriesWithHTTPInfo` to `GetGroupedCategoriesWithHttpInfo`
  - rename `GetInventoryWithHTTPInfo` to `GetInventoryWithHttpInfo`
  - rename `GetMatrixWithHTTPInfo` to `GetMatrixWithHttpInfo`
  - rename `GetSwatchWithHTTPInfo` to `GetSwatchWithHttpInfo`
  - rename `GetSwatchGroupsWithHTTPInfo` to `GetSwatchGroupsWithHttpInfo`
  - rename `GetTimestampGroupsWithHTTPInfo` to `GetTimestampGroupsWithHttpInfo`
  - rename `GetTreeWithHTTPInfo` to `GetTreeWithHttpInfo`
  - rename `CreateAccountWithHTTPInfo` to `CreateAccountWithHttpInfo`
  - rename `DeleteOrderWithHTTPInfo` to `DeleteOrderWithHttpInfo`
  - rename `GetBySwatchWithHTTPInfo` to `GetBySwatchWithHttpInfo`
  - rename `GetOrderByIdWithHTTPInfo` to `GetOrderByIdWithHttpInfo`
  - rename `GetOrderByTrackingIdWithHTTPInfo` to `GetOrderByTrackingIdWithHttpInfo`
  - rename `GetStockItemWithHTTPInfo` to `GetStockItemWithHttpInfo`
  - rename `PlaceOrderWithHTTPInfo` to `PlaceOrderWithHttpInfo`
  - rename `SearchStoreWithHTTPInfo` to `SearchStoreWithHttpInfo`
- `api/options.mustache`
  - `AddPetOptions.Auth`: required -> optional
  - `AddPetTreatmentOptions.Auth`: required -> optional
  - `DeletePetOptions.Auth`: required -> optional
  - `UploadPetCertificateOptions.File`: optional -> required
  - `UploadPetDocumentOptions.File`: optional -> required
- `api_error.mustache`
  - `ApiError.FromResponse`: parameters (statuscode, headers, body) -> (statuscode, responseheaders, responsebody)
  - `ApiError` constructor (statuscode, msg, responsebody, responseheaders, errorbody, cause) -> (statuscode, message, responseheaders, responsebody, errorbody, cause)
- `auth/http_aware_authenticator.mustache`
  - `HttpAwareAuthenticator.SetApiClient`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.SetApiClient`: parameters (client) -> (apiclient)
  - rename `BuildAuthorizationURL` to `BuildAuthorizationUrl`
  - rename `clientID` to `clientId`
  - rename `authorizationURL` to `authorizationUrl`
  - rename `tokenURL` to `tokenUrl`
  - rename `redirectURI` to `redirectUri`
  - rename `refreshURL` to `refreshUrl`
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - `OAuth2ClientCredentialsAuthenticator.SetApiClient`: parameters (client) -> (apiclient)
  - rename `clientID` to `clientId`
  - rename `tokenURL` to `tokenUrl`
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.SetApiClient`: parameters () -> (apiclient)
  - rename `BuildAuthorizationURL` to `BuildAuthorizationUrl`
  - rename `clientID` to `clientId`
  - rename `authorizationURL` to `authorizationUrl`
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator.SetApiClient`: parameters (client) -> (apiclient)
  - rename `clientID` to `clientId`
  - rename `tokenURL` to `tokenUrl`
  - rename `refreshURL` to `refreshUrl`
- `auth/oauth/oauth2_token_manager.mustache`
  - add `exchanged` to `OAuth2TokenManager`
  - `OAuth2TokenManager.SetApiClient`: parameters (client) -> (apiclient)
  - rename `tokenURL` to `tokenUrl`
  - rename `refreshURL` to `refreshUrl`
- `auth/oauth/openid_connect_authenticator.mustache`
  - `OpenIdConnectAuthenticator.SetApiClient`: parameters (client) -> (apiclient)
  - rename `BuildAuthorizationURL` to `BuildAuthorizationUrl`
  - rename `openIDConnectURL` to `openIdConnectUrl`
  - rename `clientID` to `clientId`
  - rename `redirectURI` to `redirectUri`
- `auth/scheme_authenticator.mustache`
  - rename `BuildAuthorizationURL` to `BuildAuthorizationUrl`
- `configuration.mustache`
  - `ConfigurationBuilder.BaseURL`: parameters (val) -> (baseurl)
  - `ConfigurationBuilder.Server`: parameters (server, variables) -> (serverconfig, variables)
  - rename `BaseURL` to `BaseUrl`
- `errors/network_error.mustache`
  - `NetworkError` constructor (msg, cause) -> (message, cause)
- `errors/network_timeout_error.mustache`
  - `NetworkTimeoutError` constructor (msg, cause) -> (message, cause)
- `iso8601.mustache`
  - rename `MarshalDurationProtoJSON` to `MarshalDurationProtoJson`
  - rename `UnmarshalDurationProtoJSON` to `UnmarshalDurationProtoJson`
- `models/model.mustache`
  - make `Defaults.AllDefaultsModeEnumValues` non-public (or remove)
  - rename `ModelError` to `Error` (BetterGoCodegen collision rename)
  - make `Order.AllOrderStatusEnumValues` non-public (or remove)
  - make `Pet.AllPetStatusEnumValues` non-public (or remove)
  - add `actualInstance` to `PetFood`
  - make `PetFood.Value` non-public (or remove)
  - make `PetFood.NewPetFoodFromDryFood` non-public (or remove)
  - make `PetFood.NewPetFoodFromWetFood` non-public (or remove)
  - add `actualInstance` to `PetTreatment`
  - add `matchedInstances` to `PetTreatment`
  - make `PetTreatment.Value` non-public (or remove)
  - make `PetTreatment.Medication` non-public (or remove)
  - make `PetTreatment.Surgery` non-public (or remove)
  - make `PetWithOwner.AllPetWithOwnerStatusEnumValues` non-public (or remove)
  - add `actualInstance` to `SetPetAvatarThumbnailRequest`
  - make `SetPetAvatarThumbnailRequest.Value` non-public (or remove)
  - make `SetPetAvatarThumbnailRequest.NewSetPetAvatarThumbnailRequestFromByteSliceSlice` non-public (or remove)
  - make `SetPetAvatarThumbnailRequest.NewSetPetAvatarThumbnailRequestFromByteSlice` non-public (or remove)
  - add `actualInstance` to `TreatmentRecord`
  - add `matchedInstances` to `TreatmentRecord`
  - make `TreatmentRecord.Value` non-public (or remove)
  - make `TreatmentRecord.Medication` non-public (or remove)
  - make `TreatmentRecord.Surgery` non-public (or remove)
  - rename model `ModelError` to `Error` (BetterGoCodegen.toModelName collision rename)
  - add `intoInner` to `DateTime`
- `object_serializer.mustache`
  - export `Serialize`/`Deserialize` package functions (object_serializer.mustache)
- `server_configuration.mustache`
  - rename `URLTemplate` to `UrlTemplate`
- `servers.mustache`
  - add `all` to `servers`
  - make `servers.AllServers` non-public (or remove)
- `transport_options.mustache`
  - `TransportOptionsBuilder.VerifySsl`: parameters (val) -> (verifyssl)
  - `TransportOptionsBuilder.CACertPath`: parameters (val) -> (cacertpath)
  - `TransportOptionsBuilder.Proxy`: parameters (val) -> (proxy)
  - `TransportOptionsBuilder.Timeout`: parameters (val) -> (timeout)
  - `TransportOptionsBuilder.FollowRedirects`: parameters (val) -> (followredirects)
  - `TransportOptionsBuilder.MaxRedirects`: parameters (val) -> (maxredirects)
  - `TransportOptionsBuilder.UserAgent`: parameters (val) -> (useragent)
  - `TransportOptionsBuilder.InjectRequestID`: parameters (val) -> (injectrequestid)
  - rename `ErrInvalidProxyURL` to `ErrInvalidProxyUrl`
  - rename `ErrInvalidCACertificate` to `ErrInvalidCaCertificate`
  - rename `CACertPath` to `CaCertPath`
  - rename `InjectRequestID` to `InjectRequestId`

### rust (56 changes)

- `api/options.mustache`
  - `UploadPetDocumentOptions.is_primary`: parameters (isprimary) -> ()
  - make `UploadPetDocumentOptions.is_primary` non-public (or remove)
- `auth/http_aware_authenticator.mustache`
  - `HttpAwareAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - `OAuth2ClientCredentialsAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_token_manager.mustache`
  - `OAuth2TokenManager.set_api_client`: parameters (client) -> (apiclient)
  - make `OAuth2TokenManager.mark_exchanged` non-public (or remove)
- `auth/oauth/openid_connect_authenticator.mustache`
  - `OpenIdConnectAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `base_api.mustache`
  - make `base_api.invoke_api` non-public (or remove)
  - `base_api.invoke_api`: parameters (params) -> (method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth, rawbody)
  - make `base_api.invoke_api_for_result` non-public (or remove)
  - `base_api.invoke_api_for_result`: parameters (params) -> (method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth, rawbody, deserialize)
  - make `base_api.invoke_api_for_empty_result` non-public (or remove)
- `configuration.mustache`
  - `ConfigurationBuilder.base_url`: parameters (val) -> (baseurl)
  - `ConfigurationBuilder.server`: parameters (server, variables) -> (serverconfig, variables)
- `date_time.mustache`
  - add `time` to `DateTime`
- `default_api_client.mustache`
  - make `DefaultApiClient.validate_multipart_filename` non-public (or remove)
  - make `DefaultApiClient.build_filename_directive` non-public (or remove)
  - make `DefaultApiClient.rfc5987_encode_value` non-public (or remove)
  - make `DefaultApiClient.is_closed` non-public (or remove)
  - make `DefaultApiClient.serialize_multipart_body` non-public (or remove)
  - make `DefaultApiClient.validate_multipart_field_name` non-public (or remove)
  - make `DefaultApiClient.escape_multipart_field_name` non-public (or remove)
  - make `DefaultApiClient.mime_for_filename` non-public (or remove)
  - make `DefaultApiClient.parse_charset` non-public (or remove)
  - make `DefaultApiClient.decode_text_body` non-public (or remove)
- `errors/api_error.mustache`
  - `ApiError.from_response`: parameters (statuscode, headers, body) -> (statuscode, responseheaders, responsebody)
  - make `ApiError.with_source` non-public (or remove)
  - `ApiError` constructor (statuscode, message, responsebody, responseheaders) -> (statuscode, message, responseheaders, responsebody, errorbody, cause)
- `models/model.mustache`
  - add `actualInstance` to `PetTreatment`
  - make `PetTreatment.medication` non-public (or remove)
  - make `PetTreatment.surgery` non-public (or remove)
  - add `actualInstance` to `TreatmentRecord`
  - make `TreatmentRecord.medication` non-public (or remove)
  - make `TreatmentRecord.surgery` non-public (or remove)
- `object_serializer.mustache`
  - `object_serializer.serialize`: parameters (value) -> (object)
  - `object_serializer.deserialize`: parameters (data) -> (jsonstring, targettype)
  - make `object_serializer.format_time` non-public (or remove)
  - add `resolveOneOf` to `object_serializer`
  - add `resolveAnyOf` to `object_serializer`
  - add `parseJson` to `object_serializer`
  - make `object_serializer.to_deep_object_map` non-public (or remove)
  - make `object_serializer.serialize_extras` non-public (or remove)
- `servers.mustache`
  - add `all` to `servers`
  - make `servers.all_servers` non-public (or remove)
- `transport_options.mustache`
  - `TransportOptionsBuilder.verify_ssl`: parameters (val) -> (verifyssl)
  - `TransportOptionsBuilder.ca_cert_path`: parameters (val) -> (cacertpath)
  - `TransportOptionsBuilder.proxy`: parameters (val) -> (proxy)
  - `TransportOptionsBuilder.timeout`: parameters (val) -> (timeout)
  - `TransportOptionsBuilder.follow_redirects`: parameters (val) -> (followredirects)
  - `TransportOptionsBuilder.max_redirects`: parameters (val) -> (maxredirects)
  - `TransportOptionsBuilder.user_agent`: parameters (val) -> (useragent)
  - `TransportOptionsBuilder.inject_request_id`: parameters (val) -> (injectrequestid)
  - make `TransportOptionsBuilder.user_agent_opt` non-public (or remove)

### swift (109 changes)

- `api/api.mustache`
  - rename `addPetWithHTTPInfo` to `addPetWithHttpInfo`
  - rename `addPetPhotosWithHTTPInfo` to `addPetPhotosWithHttpInfo`
  - rename `addPetTreatmentWithHTTPInfo` to `addPetTreatmentWithHttpInfo`
  - rename `deletePetWithHTTPInfo` to `deletePetWithHttpInfo`
  - rename `downloadPetDocumentWithHTTPInfo` to `downloadPetDocumentWithHttpInfo`
  - rename `findPetsBornOnWithHTTPInfo` to `findPetsBornOnWithHttpInfo`
  - rename `findPetsByStatusWithHTTPInfo` to `findPetsByStatusWithHttpInfo`
  - rename `getExternalPetInfoWithHTTPInfo` to `getExternalPetInfoWithHttpInfo`
  - rename `getMultiServerPetInfoWithHTTPInfo` to `getMultiServerPetInfoWithHttpInfo`
  - rename `getPetAvatarWithHTTPInfo` to `getPetAvatarWithHttpInfo`
  - rename `getPetAvatarThumbnailWithHTTPInfo` to `getPetAvatarThumbnailWithHttpInfo`
  - rename `getPetByIdWithHTTPInfo` to `getPetByIdWithHttpInfo`
  - rename `getPetByNameWithHTTPInfo` to `getPetByNameWithHttpInfo`
  - rename `getPetPassportWithHTTPInfo` to `getPetPassportWithHttpInfo`
  - rename `getPetPhotoWithHTTPInfo` to `getPetPhotoWithHttpInfo`
  - rename `getPetSummaryWithHTTPInfo` to `getPetSummaryWithHttpInfo`
  - rename `getPetTagWithHTTPInfo` to `getPetTagWithHttpInfo`
  - rename `getStagingPetInfoWithHTTPInfo` to `getStagingPetInfoWithHttpInfo`
  - rename `setPetAvatarWithHTTPInfo` to `setPetAvatarWithHttpInfo`
  - rename `setPetAvatarThumbnailWithHTTPInfo` to `setPetAvatarThumbnailWithHttpInfo`
  - rename `setPetPreferencesWithHTTPInfo` to `setPetPreferencesWithHttpInfo`
  - rename `updatePetWithHTTPInfo` to `updatePetWithHttpInfo`
  - rename `uploadPetCertificateWithHTTPInfo` to `uploadPetCertificateWithHttpInfo`
  - rename `uploadPetDocumentWithHTTPInfo` to `uploadPetDocumentWithHttpInfo`
  - rename `getDefaultsWithHTTPInfo` to `getDefaultsWithHttpInfo`
  - rename `getDepartmentWithHTTPInfo` to `getDepartmentWithHttpInfo`
  - rename `getGroupedCategoriesWithHTTPInfo` to `getGroupedCategoriesWithHttpInfo`
  - rename `getInventoryWithHTTPInfo` to `getInventoryWithHttpInfo`
  - rename `getMatrixWithHTTPInfo` to `getMatrixWithHttpInfo`
  - rename `getSwatchWithHTTPInfo` to `getSwatchWithHttpInfo`
  - rename `getSwatchGroupsWithHTTPInfo` to `getSwatchGroupsWithHttpInfo`
  - rename `getTimestampGroupsWithHTTPInfo` to `getTimestampGroupsWithHttpInfo`
  - rename `getTreeWithHTTPInfo` to `getTreeWithHttpInfo`
  - rename `createAccountWithHTTPInfo` to `createAccountWithHttpInfo`
  - rename `deleteOrderWithHTTPInfo` to `deleteOrderWithHttpInfo`
  - rename `getBySwatchWithHTTPInfo` to `getBySwatchWithHttpInfo`
  - rename `getOrderByIdWithHTTPInfo` to `getOrderByIdWithHttpInfo`
  - rename `getOrderByTrackingIdWithHTTPInfo` to `getOrderByTrackingIdWithHttpInfo`
  - rename `getStockItemWithHTTPInfo` to `getStockItemWithHttpInfo`
  - rename `placeOrderWithHTTPInfo` to `placeOrderWithHttpInfo`
  - rename `searchStoreWithHTTPInfo` to `searchStoreWithHttpInfo`
- `auth/http_aware_authenticator.mustache`
  - `HttpAwareAuthenticator.setApiClient`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - make `OAuth2AuthorizationCodeAuthenticator.authHeadersOrThrow` non-public (or remove)
  - `OAuth2AuthorizationCodeAuthenticator` constructor defaults (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes?, refreshurl?) -> (host, clientid, clientsecret, authorizationurl, tokenurl, redirecturi, scopes, refreshurl?)
  - rename `buildAuthorizationURL` to `buildAuthorizationUrl`
  - rename `clientID` to `clientId`
  - rename `authorizationURL` to `authorizationUrl`
  - rename `tokenURL` to `tokenUrl`
  - rename `redirectURI` to `redirectUri`
  - rename `refreshURL` to `refreshUrl`
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - `OAuth2ClientCredentialsAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2ClientCredentialsAuthenticator` constructor defaults (host, clientid, clientsecret, tokenurl, scopes?, clientauthmethod?) -> (host, clientid, clientsecret, tokenurl, scopes, clientauthmethod?)
  - rename `clientID` to `clientId`
  - rename `tokenURL` to `tokenUrl`
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2ImplicitAuthenticator` constructor defaults (host, clientid, authorizationurl, scopes?) -> (host, clientid, authorizationurl, scopes)
  - rename `buildAuthorizationURL` to `buildAuthorizationUrl`
  - rename `clientID` to `clientId`
  - rename `authorizationURL` to `authorizationUrl`
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OAuth2PasswordAuthenticator` constructor defaults (host, clientid, clientsecret, tokenurl, username, password, scopes?, refreshurl?, clientauthmethod?) -> (host, clientid, clientsecret, tokenurl, username, password, scopes, refreshurl?, clientauthmethod?)
  - rename `clientID` to `clientId`
  - rename `tokenURL` to `tokenUrl`
  - rename `refreshURL` to `refreshUrl`
- `auth/oauth/oauth2_token_manager.mustache`
  - `OAuth2TokenManager.setApiClient`: parameters (client) -> (apiclient)
  - rename `tokenURL` to `tokenUrl`
  - rename `refreshURL` to `refreshUrl`
- `auth/oauth/openid_connect_authenticator.mustache`
  - `OpenIdConnectAuthenticator.setApiClient`: parameters (client) -> (apiclient)
  - `OpenIdConnectAuthenticator` constructor defaults (host, openidconnecturl, clientid, clientsecret, redirecturi, scopes?) -> (host, openidconnecturl, clientid, clientsecret, redirecturi, scopes)
  - rename `buildAuthorizationURL` to `buildAuthorizationUrl`
  - rename `openIDConnectURL` to `openIdConnectUrl`
  - rename `clientID` to `clientId`
  - rename `redirectURI` to `redirectUri`
- `auth/scheme_authenticator.mustache`
  - rename `buildAuthorizationURL` to `buildAuthorizationUrl`
- `configuration.mustache`
  - `ConfigurationBuilder.baseURL`: parameters (val) -> (baseurl)
  - `ConfigurationBuilder.server`: parameters (server, variables) -> (serverconfig, variables)
  - rename `baseURL` to `baseUrl`
- `errors/api_error.mustache`
  - `ApiError.fromResponse`: parameters (statuscode, headers, body) -> (statuscode, responseheaders, responsebody)
- `errors/configuration_error.mustache`
  - `ConfigurationError` cases invalidargument,invalidcacertificate,invalidproxyurl,invalidservervariable,invalidstate,proxyunsupported,tlsoptionsunsupported -> invalidargument,invalidcacertificate,invalidproxy,invalidservervariable,invalidstate,invalidtimeout
- `errors/oauth2_token_error.mustache`
  - `OAuth2TokenError`: enum -> type
- `iso8601_duration.mustache`
  - `ISO8601Duration.formatProtobufDuration`: parameters (interval) -> (duration)
  - add `validatePartialTime` to `ISO8601Duration`
- `models/model.mustache`
  - add `actualInstance` to `PetFood`
  - make `PetFood.value` non-public (or remove)
  - `PetFood.value`: parameters (type) -> ()
  - add `actualInstance` to `PetTreatment`
  - add `matchedInstances` to `PetTreatment`
  - make `PetTreatment.value` non-public (or remove)
  - `PetTreatment.value`: parameters (type) -> ()
  - make `PetTreatment.values` non-public (or remove)
  - add `actualInstance` to `SetPetAvatarThumbnailRequest`
  - make `SetPetAvatarThumbnailRequest.value` non-public (or remove)
  - `SetPetAvatarThumbnailRequest.value`: parameters (type) -> ()
  - add `actualInstance` to `TreatmentRecord`
  - add `matchedInstances` to `TreatmentRecord`
  - make `TreatmentRecord.value` non-public (or remove)
  - `TreatmentRecord.value`: parameters (type) -> ()
  - make `TreatmentRecord.values` non-public (or remove)
- `object_serializer.mustache`
  - make `ObjectSerializer` public (object_serializer.mustache)
- `transport_options.mustache`
  - `TransportOptionsBuilder.verifySsl`: parameters (val) -> (verifyssl)
  - `TransportOptionsBuilder.caCertPath`: parameters (val) -> (cacertpath)
  - `TransportOptionsBuilder.proxy`: parameters (val) -> (proxy)
  - `TransportOptionsBuilder.timeout`: parameters (val) -> (timeout)
  - `TransportOptionsBuilder.followRedirects`: parameters (val) -> (followredirects)
  - `TransportOptionsBuilder.maxRedirects`: parameters (val) -> (maxredirects)
  - `TransportOptionsBuilder.userAgent`: parameters (val) -> (useragent)
  - `TransportOptionsBuilder.injectRequestID`: parameters (val) -> (injectrequestid)
  - rename `injectRequestID` to `injectRequestId`

### elixir (52 changes)

- `api/options.mustache`
  - `AddPetPhotosOptions.files`: optional -> required
  - `AddPetPhotosOptions.metadata`: optional -> required
  - `GetPetByNameOptions.category`: optional -> required
  - `GetPetByNameOptions.x_correlation_id`: optional -> required
  - `SetPetPreferencesOptions.nickname`: optional -> required
  - `UploadPetCertificateOptions.file`: optional -> required
  - `UploadPetDocumentOptions.file`: optional -> required
- `api_client.mustache`
  - add `sendRequest` to `ApiClient`
- `auth/http_aware_authenticator.mustache`
  - `HttpAwareAuthenticator.set_api_client`: parameters (arg1) -> (apiclient)
- `auth/oauth/oauth2_auth_code_authenticator.mustache`
  - `OAuth2AuthorizationCodeAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_client_credentials_authenticator.mustache`
  - `OAuth2ClientCredentialsAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_implicit_authenticator.mustache`
  - `OAuth2ImplicitAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_password_authenticator.mustache`
  - `OAuth2PasswordAuthenticator.set_api_client`: parameters (client) -> (apiclient)
- `auth/oauth/oauth2_token_manager.mustache`
  - `OAuth2TokenManager.set_api_client`: parameters (client) -> (apiclient)
- `base_api.mustache`
  - make `BaseApi.invoke_api` non-public (or remove)
  - `BaseApi.invoke_api`: parameters (method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth) -> (method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth, rawbody)
  - make `BaseApi.invoke_api_for_result` non-public (or remove)
  - `BaseApi.invoke_api_for_result`: parameters (method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth) -> (method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth, rawbody, deserialize)
  - make `BaseApi.build_collection_param` non-public (or remove)
- `configuration.mustache`
  - make `Configuration.from_server` non-public (or remove)
- `default_api_client.mustache`
  - make `DefaultApiClient.decode_text_body` non-public (or remove)
  - `DefaultApiClient.decode_text_body`: parameters (body, contenttype) -> (bytes, contenttype)
  - make `DefaultApiClient.classify_transport_error` non-public (or remove)
  - make `DefaultApiClient.request_options` non-public (or remove)
  - make `DefaultApiClient.build_content_disposition` non-public (or remove)
- `errors/api_error.mustache`
  - `ApiError.from_response`: parameters (statuscode, headers, body) -> (statuscode, responseheaders, responsebody)
- `models/model.mustache`
  - generate `<Model><Prop>Enum` modules for inline enums (models/model.mustache)
  - `DryFood.food_type`: optional -> required
  - `Metadata.additional_properties`: optional -> required
  - generate the named inline enum (models/model.mustache)
  - add `actualInstance` to `PetFood`
  - add `actualInstance` to `PetTreatment`
  - add `matchedInstances` to `PetTreatment`
  - add `actualInstance` to `SetPetAvatarThumbnailRequest`
  - make `SetPetAvatarThumbnailRequest.serialize` non-public (or remove)
  - make `StrictTag.from_map_strict` non-public (or remove)
  - add `actualInstance` to `TreatmentRecord`
  - add `matchedInstances` to `TreatmentRecord`
  - `WetFood.food_type`: optional -> required
- `object_serializer.mustache`
  - `ObjectSerializer.stringify`: parameters (dt) -> (value)
  - add `resolveOneOf` to `ObjectSerializer`
  - add `resolveAnyOf` to `ObjectSerializer`
  - make `ObjectSerializer.format_protobuf_duration` non-public (or remove)
  - `ObjectSerializer.format_protobuf_duration`: parameters (duration) -> (interval)
  - make `ObjectSerializer.parse_protobuf_duration` non-public (or remove)
  - `ObjectSerializer.parse_protobuf_duration`: parameters (text) -> (value)
  - make `ObjectSerializer.json_max_depth` non-public (or remove)
  - make `ObjectSerializer.sanitize_for_serialization` non-public (or remove)
  - `ObjectSerializer.sanitize_for_serialization`: parameters (value) -> (object, visited)
  - make `ObjectSerializer.convert_to_type` non-public (or remove)
  - `ObjectSerializer.convert_to_type`: parameters (data, type) -> (data, returntype)
  - make `ObjectSerializer.find_and_cast_into_type` non-public (or remove)

## Canonical decisions

Chosen by (1) the majority, (2) the documented rules in AGENTS.md, (3) least surprise for callers, in that order. A decision that departs from the majority says why.

- **D01** `error` (type): Model named `Error` (majority). go renames `ModelError` to `Error` (no clash in go). java and swift keep `ModelError` (forced: java.lang.Error / Swift.Error).
- **D02** `modelerror` (type): See `Error`: `ModelError` survives only where forced (java, swift).
- **D03** `result` (type): Model named `Result` (majority). java renames `ModelResult` to `Result` (java has no implicit Result). rust and swift keep `ModelResult` (forced: prelude std Result / Swift.Result).
- **D04** `modelresult` (type): See `Result`: `ModelResult` survives only where forced (rust, swift).
- **D05** `client` (type): Entry point is a class named `Client` in every SDK. ruby renames `Petstore::Client::Petstore` to `Petstore::Client::Client` (a class may share its namespace's name).
- **D06** `petstore` (type): ruby-only name of the entry class; renamed to `Client` (see `Client`).
- **D07** `valueserializer` (type): Internal helper: not part of the public surface. Hide it wherever the language can (node: drop from index exports; dart: stop exporting from the barrel; rust: `pub(crate)`); python/ruby/php/elixir keep it but mark it internal (`_`-free name kept, `@internal`/`:nodoc:`/`@moduledoc false`). java/kotlin/csharp/go/swift already hide it: no change.
- **D08** `headerselector` (type): Internal helper, same rule as `ValueSerializer`: hide in node/dart/rust, mark internal in python/ruby/php/elixir.
- **D09** `valueserializer.*` (*): Internal helper member: no public contract; the per-language differences are not harmonised. Follows from hiding `ValueSerializer`.
- **D10** `headerselector.*` (*): Internal helper member; follows from hiding `HeaderSelector`.
- **D11** `allowreservedvalue` (type): Internal helper of `ValueSerializer`; hide with it.
- **D12** `valueserializerallowreservedvalue` (type): Internal helper of `ValueSerializer`; hide with it.
- **D13** `objectserializer` (type): `ObjectSerializer` is public (majority, 8 of 12): callers use `serialize`/`deserialize` to round-trip models and decode typed error bodies. kotlin, csharp and swift make it public; go exports `Serialize`/`Deserialize` as package functions.
- **D14** `objectserializer.*` (member): Public surface of `ObjectSerializer` is `serialize(object)`, `deserialize(jsonString, targetType)`, `parseJson(data)`, `stringify(value)`, the `to*Value` parameter encoders and `resolveOneOf`/`resolveAnyOf` where composed schemas need them; every other helper becomes non-public (private/internal/`@internal`/`@doc false`).
- **D15** `objectserializer.*` (params): Parameter names follow java's `ObjectSerializer`: `serialize(object)`, `deserialize(jsonString, targetType)`, `parseJson(data)`.
- **D16** `defaultapiclient.*` (member): `DefaultApiClient` exposes only the `ApiClient` contract (`sendRequest`, `close`) and its constructor; every other member becomes non-public.
- **D17** `abstractapiclient` (type): No public abstract base: fold `AbstractApiClient` into `DefaultApiClient` or make it internal (php `@internal`, node not exported, dart not exported).
- **D18** `abstractapiclient.*` (*): Follows from making `AbstractApiClient` internal.
- **D19** `baseapi.*` (member): `BaseApi` is a base class for the generated api classes; its plumbing (`invokeApi`, `apiClient`, `config`, `NO_AUTH`) is not public. Make the extra members protected/private.
- **D20** `baseapi` (type): go keeps `baseApi` unexported (no inheritance; embedded); elixir has no base module. Not harmonised further.
- **D21** `petapi.config` (*): Api classes do not expose `config`/`apiClient`/`authenticator`; make them private.
- **D22** `storeapi.config` (*): Api classes do not expose `config`/`apiClient`/`authenticator`; make them private.
- **D23** `petapi.apiclient` (*): Api classes do not expose `apiClient`; make it private.
- **D24** `storeapi.apiclient` (*): Api classes do not expose `apiClient`; make it private.
- **D25** `apierror.fromresponse` (params): `fromResponse(statusCode, responseHeaders, responseBody)`: the factory's parameter names match the constructor and the properties they fill (the 6/6 tie is broken by least surprise).
- **D26** `apierror.<ctor>` (ctor): `ApiError(statusCode, message, responseHeaders, responseBody, errorBody, cause)`: headers before body everywhere (dart, go, rust, swift reorder); the message parameter is `message` (go renames `msg`); dart renames `underlyingError` to `cause`; python and rust add `cause`/`errorBody`; ruby replaces the polymorphic `initialize(arg)` with keyword arguments of the same names.
- **D27** `networkerror.<ctor>` (ctor): `NetworkError(message, cause)`: python and ruby add the `cause` argument, dart renames `underlyingError` to `cause`, go renames `msg` to `message`.
- **D28** `networktimeouterror.<ctor>` (ctor): `NetworkTimeoutError(message, cause)`, as `NetworkError`.
- **D29** `openapierror.<ctor>` (ctor): Root `OpenAPIError(message, cause)`; dart gives its root a `(message, cause)` constructor.
- **D30** `oauth2tokenerror.<ctor>` (ctor): `OAuth2TokenError(message)` (majority); java and kotlin drop the extra `cause` overload (csharp's innerException is the .NET-mandated chaining ctor and stays).
- **D31** `oauth2tokenerror` (kind): `OAuth2TokenError` is a class carrying a message in every SDK; swift turns its enum into a class (swift's typed-case style is kept for ConfigurationError only, per AGENTS.md).
- **D32** `oauth2authorizationcodeauthenticator.<ctor>` (ctor): `OAuth2AuthorizationCodeAuthenticator(host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl?)`: `refreshUrl` is the LAST, optional argument (java moves it from position 6 and keeps a 7-arg overload); ruby and node drop the injectable `tokenManager` from the public constructor (test seam only).
- **D33** `oauth2passwordauthenticator.<ctor>` (ctor): `OAuth2PasswordAuthenticator(host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl?, clientAuthMethod?)`: java moves `refreshUrl` after `scopes`.
- **D34** `*authenticator.<ctor>` (ctor-optional): Required: every credential and endpoint; optional: `scopes` defaults to empty only where the majority defaults it, `refreshUrl`/`clientAuthMethod` always optional. dart/swift stop defaulting `scopes`; python stops defaulting secrets (`client_secret`, `password`, `token`, `api_key` are required).
- **D35** `*authenticator.setapiclient` (params): `setApiClient(apiClient)` (6/6 tie broken toward the parameter's type name, which the property and the docs already use).
- **D36** `httpawareauthenticator.setapiclient` (params): `setApiClient(apiClient)`.
- **D37** `oauth2tokenmanager.setapiclient` (params): `setApiClient(apiClient)`.
- **D38** `oauth2tokenmanager.exchanged` (member): `isExchanged()` (property `exchanged` in property languages) is public on `OAuth2TokenManager` (majority); kotlin, csharp, dart, go add it.
- **D39** `oauth2tokenmanager.markexchanged` (member): `markExchanged` is internal (minority); ruby, node, rust, elixir make it non-public.
- **D40** `*.tokenmanager` (member): The token manager is an implementation detail; ruby/elixir stop exposing it (elixir: covered by struct transparency only if it stays a field).
- **D41** `*.actualinstance` (member): Composed schemas expose `actualInstance` (oneOf and anyOf: the matched value) and anyOf also `matchedInstances` (all matches). Every SDK spells these two names; enum-backed languages (dart/swift/go `value`/`values`) rename theirs; rust keeps its enum (forced) and adds no accessor.
- **D42** `*.matchedinstances` (member): See `actualInstance`: anyOf exposes `matchedInstances`; java/kotlin rename `getInstances`/`instances`, php renames `getActualInstances`, dart/swift rename `values`.
- **D43** `*.instances` (member): Renamed to `matchedInstances` (see `actualInstance`).
- **D44** `*.actualinstances` (member): Renamed to `matchedInstances` (see `actualInstance`).
- **D45** `*.value` (member): Composed-schema accessor is `actualInstance` (see above); dart/go/swift rename `value`.
- **D46** `*.values` (member): Composed-schema accessor is `matchedInstances` (see above); dart/swift rename `values`.
- **D47** `*asbytes` (member): php drops the `get*AsBytes`/`set*FromBytes` helpers and types byte properties as decoded binary strings, like the other eleven SDKs type them as bytes.
- **D48** `*frombytes` (member): See `*AsBytes`: php drops these helpers.
- **D49** `*.frombytelist` (member): See `*AsBytes`: php drops these helpers.
- **D50** `*.actualinstanceasbytelist` (member): See `*AsBytes`: php drops these helpers.
- **D51** `*.setadditionalproperty` (member): Additional properties are a plain `additionalProperties` map in every SDK; java drops `setAdditionalProperty`.
- **D52** `stricttag.*` (member): Strict (unevaluatedProperties: false) decoding is internal to the serializer; the per-language `parseStrict`/`fromJsonStrict`/`assertNoUnknownProperties`/`UNEVALUATED_PROPERTIES_FALSE` helpers become non-public.
- **D53** `petapi.setpetavatar*` (params): The request-content-type argument is `requestContentType` (majority); php, ruby and node rename `contentType`.
- **D54** `petapi.uploadpetdocument*` (params): The request-content-type argument is `requestContentType` (majority); php, ruby and node rename `contentType`.
- **D55** `configurationbuilder.*` (params): A builder setter's parameter is named after the property it sets (`baseUrl(baseUrl)`), not `val`; `server(serverConfig, variables)`.
- **D56** `transportoptionsbuilder.*` (params): A builder setter's parameter is named after the property it sets (`verifySsl(verifySsl)`), not `val`.
- **D57** `client.withtoken` (params): `Client.withToken(host, accessToken, transportOptions?)`; php drops the trailing `apiClient` injection parameter (tests inject through the constructor of `DefaultApiClient`).
- **D58** `client.withauthenticator` (params): `Client.withAuthenticator(authenticator, transportOptions?)`; php drops `apiClient`.
- **D59** `client.<ctor>` (ctor): `Client(authenticator, transportOptions?)`; php drops the `apiClient` injection parameter; swift drops its convenience `init(host:accessToken:transportOptions:)` because `Client.withToken` is the token form in every SDK.
- **D60** `apiclient.close` (member): `ApiClient.close()` is part of the transport contract (majority); python, node and dart add it to the interface (csharp: IDisposable.Dispose, forced).
- **D61** `configuration.<ctor>` (ctor-optional): Configuration is built by the builder; where a constructor is public, both arguments are optional. ruby makes `base_url`/`default_headers` optional.
- **D62** `transportoptions.<ctor>` (ctor-optional): Every TransportOptions argument is optional (they all have defaults); ruby adds the defaults.
- **D63** `serverconfiguration.<ctor>` (ctor-optional): `ServerConfiguration(urlTemplate, description?, variables?)`; kotlin, csharp and node default the last two; java adds the 1-arg overload.
- **D64** `servervariable.<ctor>` (ctor-optional): `ServerVariable(defaultValue, description?, enumValues?)`; kotlin, csharp and node default the last two; java adds the overload.
- **D65** `serverconfiguration.geturl` (member): `getUrl(overrides?)` (url in getter-less languages is the idiom spelling, folded by the extractor); every SDK accepts the optional overrides map.
- **D66** `servers.all` (member): `Servers.ALL` (all/`ALL`/`all`) is the list of every server; dart, go and rust rename `allServers` to `all`; ruby adds `ALL` as a public constant (it is computed but not exposed).
- **D67** `servers.allservers` (member): Renamed to `all` (see `Servers.ALL`).
- **D68** `getmultiserverpetinfoserver*.*` (member): Per-operation server variants expose their variables as public read-only properties (majority); php, python, ruby and dart make them public.
- **D69** `getstagingpetinfoserver*.*` (member): Per-operation server variants expose their variables as public read-only properties (majority); php, python, ruby and dart make them public.
- **D70** `*options.*` (prop-optional): An Options field is required exactly when the operation parameter is required: go types required options fields as values (not pointers) and optional ones as pointers; elixir adds `@enforce_keys` for required fields.
- **D71** `*.<ctor>` (ctor-optional): Constructor defaults follow the majority shown in the canonical column.
- **D72** `defaultsmodeenum` (type): Inline property enums are named types (`<Model><Prop>Enum`) in every SDK; ruby and elixir generate them (ruby as a constants class, elixir as an atom module) instead of inlining the allowed values.
- **D73** `*statusenum` (type): As `DefaultsModeEnum`: ruby and elixir generate the named inline enum.
- **D74** `authenticator.isnoauth` (*): The NO_AUTH check is identity comparison against the sentinel; node `isNoAuth()` and dart `noAuth` become internal.
- **D75** `httpawareauthenticator.ishttpawareauthenticator` (member): node's type guard becomes internal (callers use `instanceof`).
- **D76** `configurationerror` (cases): swift's ConfigurationError cases match rust's: rename `invalidProxyUrl` to `invalidProxy`, and fold `proxyUnsupported`/`tlsOptionsUnsupported` into `invalidProxy`/`invalidCaCertificate`, adding `invalidTimeout`.
- **D77** `metadata.additionalproperties` (*): `additionalProperties` is a non-null map (empty when absent) on every model with additionalProperties; csharp, php and elixir stop typing it nullable; node adds the field.
- **D78** `requirednullable.note` (prop-optional): A required-nullable property is required (must be present) and nullable; kotlin drops its default.
- **D79** `dryfood.foodtype` (prop-optional): Discriminator properties are required; elixir adds them to `@enforce_keys`.
- **D80** `wetfood.foodtype` (prop-optional): Discriminator properties are required; elixir adds them to `@enforce_keys`.
- **D81** `*.<ctor>` (ctor): Constructor parameters follow the canonical order in the canonical column.
- **D82** `*` (casing): Initialisms are word-cased in every camel/Pascal-case SDK (`WithHttpInfo`, `clientId`, `baseUrl`, `ApiClient`). go and swift already word-case most (`PetApi`, `GetPetById`) and capitalise only some (`WithHTTPInfo`, `clientID`); they converge on word-casing rather than renaming every type to full Go/Swift initialism style.

## API classes and operations

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **BaseApi** | BaseApi | BaseApi | BaseApi | BaseApi | BaseApi | BaseApi | BaseApi | BaseApi | BaseApi | base_api | base_api | BaseApi | BaseApi |  |
| BaseApi ctor | new(apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | — | (apiClient?, config?, authenticator?) | (api_client?, config?, authenticator?) | (api_client?, config?, authenticator?) | — | (apiClient?, config?, authenticator?) | — | (api_client, config, authenticator) | — | — |  |
| BaseApi.noAuth | noAuth | — | — | — | — | — | NO_AUTH ✗ | — | — | — | — | — | — | D19 |
| BaseApi.config | config | — | — | — | — | — | config ✗ | — | config ✗ | — | — | — | — | D19 |
| BaseApi.apiClient | apiClient | — | — | — | — | — | — | — | apiClient ✗ | — | — | — | — | D19 |
| BaseApi.invokeApi | invokeApi | — | — | — | — | — | — | — | invokeApi(method, path, queryParams?, headerParams?, body?, accepts?, contentType?, returnType?, auth?, rawBody?) ✗ | — | invoke_api(params) ✗ | — | invoke_api(method, path, query_params, header_params, body, accepts, content_type, return_type, auth?) ✗ | D19 Parameters `(method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth, rawbody)` (majority) in rust, elixir. |
| BaseApi.invokeApiForResult | invokeApiForResult | — | — | — | — | — | — | — | invokeApiForResult(method, path, queryParams?, headerParams?, body?, accepts?, contentType?, returnType?, auth?, rawBody?, deserialize?) ✗ | — | invoke_api_for_result(params) ✗ | — | invoke_api_for_result(method, path, query_params, header_params, body, accepts, content_type, return_type, auth?) ✗ | D19 Parameters `(method, path, queryparams, headerparams, body, accepts, contenttype, returntype, auth, rawbody, deserialize)` (majority) in rust, elixir. |
| BaseApi.errMissingRequiredParameter | errMissingRequiredParameter | — | — | — | — | — | — | — | — | ErrMissingRequiredParameter ~ | — | — | — | idiom (allow-list) |
| BaseApi.errInvalidPathParameter | errInvalidPathParameter | — | — | — | — | — | — | — | — | ErrInvalidPathParameter ~ | — | — | — | idiom (allow-list) |
| BaseApi.errInvalidCookie | errInvalidCookie | — | — | — | — | — | — | — | — | ErrInvalidCookie ~ | — | — | — | idiom (allow-list) |
| BaseApi.invokeApiForEmptyResult | invokeApiForEmptyResult | — | — | — | — | — | — | — | — | — | invoke_api_for_empty_result(params) ✗ | — | — | D19 |
| BaseApi.buildCollectionParam | buildCollectionParam | — | — | — | — | — | — | — | — | — | — | — | build_collection_param(param, format) ✗ | D19 |
| **PetApi** | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi | PetApi |  |
| PetApi ctor | new(apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | — | (api_client?, config?, authenticator?) | (api_client?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient, config, authenticator) | (api_client, config, authenticator) | (apiClient?, config?, authenticator?) | (api_client?, config?, authenticator?) |  |
| PetApi.addPet | addPet(pet, options?) | addPet(pet, options?) | addPet(pet, options?) | AddPetAsync(pet, options?) | addPet(pet, options?) | add_pet(pet, options?) | add_pet(pet, options?) | addPet(pet, options?) | addPet(pet, options) ~ | AddPet(pet, options) | add_pet(pet, options) | addPet(pet, options?) | add_pet(pet, options?, opts?) | idiom (allow-list) |
| PetApi.addPetWithHttpInfo | addPetWithHttpInfo(pet, options?) | addPetWithHttpInfo(pet, options?) | addPetWithHttpInfo(pet, options?) | AddPetWithHttpInfoAsync(pet, options?) | addPetWithHttpInfo(pet, options?) | add_pet_with_http_info(pet, options?) | add_pet_with_http_info(pet, options?) | addPetWithHttpInfo(pet, options?) | addPetWithHttpInfo(pet, options) ~ | AddPetWithHTTPInfo(pet, options) ✗ | add_pet_with_http_info(pet, options) | addPetWithHTTPInfo(pet, options?) ✗ | add_pet_with_http_info(pet, options?, opts?) | D82 |
| PetApi.addPetPhotos | addPetPhotos(petId, options) | addPetPhotos(petId, options) | addPetPhotos(petId, options) | AddPetPhotosAsync(petId, options) | addPetPhotos(petId, options) | add_pet_photos(pet_id, options) | add_pet_photos(pet_id, options) | addPetPhotos(petId, options) | addPetPhotos(petId, options) | AddPetPhotos(petId, options) | add_pet_photos(pet_id, options) | addPetPhotos(petId, options) | add_pet_photos(pet_id, options, opts?) |  |
| PetApi.addPetPhotosWithHttpInfo | addPetPhotosWithHttpInfo(petId, options) | addPetPhotosWithHttpInfo(petId, options) | addPetPhotosWithHttpInfo(petId, options) | AddPetPhotosWithHttpInfoAsync(petId, options) | addPetPhotosWithHttpInfo(petId, options) | add_pet_photos_with_http_info(pet_id, options) | add_pet_photos_with_http_info(pet_id, options) | addPetPhotosWithHttpInfo(petId, options) | addPetPhotosWithHttpInfo(petId, options) | AddPetPhotosWithHTTPInfo(petId, options) ✗ | add_pet_photos_with_http_info(pet_id, options) | addPetPhotosWithHTTPInfo(petId, options) ✗ | add_pet_photos_with_http_info(pet_id, options, opts?) | D82 |
| PetApi.addPetTreatment | addPetTreatment(petId, petTreatment, options?) | addPetTreatment(petId, petTreatment, options?) | addPetTreatment(petId, petTreatment, options?) | AddPetTreatmentAsync(petId, petTreatment, options?) | addPetTreatment(petId, petTreatment, options?) | add_pet_treatment(pet_id, pet_treatment, options?) | add_pet_treatment(pet_id, pet_treatment, options?) | addPetTreatment(petId, petTreatment, options?) | addPetTreatment(petId, petTreatment, options) ~ | AddPetTreatment(petId, petTreatment, options) | add_pet_treatment(pet_id, pet_treatment, options) | addPetTreatment(petId, petTreatment, options?) | add_pet_treatment(pet_id, pet_treatment, options?, opts?) | idiom (allow-list) |
| PetApi.addPetTreatmentWithHttpInfo | addPetTreatmentWithHttpInfo(petId, petTreatment, options?) | addPetTreatmentWithHttpInfo(petId, petTreatment, options?) | addPetTreatmentWithHttpInfo(petId, petTreatment, options?) | AddPetTreatmentWithHttpInfoAsync(petId, petTreatment, options?) | addPetTreatmentWithHttpInfo(petId, petTreatment, options?) | add_pet_treatment_with_http_info(pet_id, pet_treatment, options?) | add_pet_treatment_with_http_info(pet_id, pet_treatment, options?) | addPetTreatmentWithHttpInfo(petId, petTreatment, options?) | addPetTreatmentWithHttpInfo(petId, petTreatment, options) ~ | AddPetTreatmentWithHTTPInfo(petId, petTreatment, options) ✗ | add_pet_treatment_with_http_info(pet_id, pet_treatment, options) | addPetTreatmentWithHTTPInfo(petId, petTreatment, options?) ✗ | add_pet_treatment_with_http_info(pet_id, pet_treatment, options?, opts?) | D82 |
| PetApi.deletePet | deletePet(petId, options?) | deletePet(petId, options?) | deletePet(petId, options?) | DeletePetAsync(petId, options?) | deletePet(petId, options?) | delete_pet(pet_id, options?) | delete_pet(pet_id, options?) | deletePet(petId, options?) | deletePet(petId, options) ~ | DeletePet(petId, options) | delete_pet(pet_id, options) | deletePet(petId, options?) | delete_pet(pet_id, options?, opts?) | idiom (allow-list) |
| PetApi.deletePetWithHttpInfo | deletePetWithHttpInfo(petId, options?) | deletePetWithHttpInfo(petId, options?) | deletePetWithHttpInfo(petId, options?) | DeletePetWithHttpInfoAsync(petId, options?) | deletePetWithHttpInfo(petId, options?) | delete_pet_with_http_info(pet_id, options?) | delete_pet_with_http_info(pet_id, options?) | deletePetWithHttpInfo(petId, options?) | deletePetWithHttpInfo(petId, options) ~ | DeletePetWithHTTPInfo(petId, options) ✗ | delete_pet_with_http_info(pet_id, options) | deletePetWithHTTPInfo(petId, options?) ✗ | delete_pet_with_http_info(pet_id, options?, opts?) | D82 |
| PetApi.downloadPetDocument | downloadPetDocument(petId, documentId) | downloadPetDocument(petId, documentId) | downloadPetDocument(petId, documentId) | DownloadPetDocumentAsync(petId, documentId) | downloadPetDocument(petId, documentId) | download_pet_document(pet_id, document_id) | download_pet_document(pet_id, document_id) | downloadPetDocument(petId, documentId) | downloadPetDocument(petId, documentId) | DownloadPetDocument(petId, documentId) | download_pet_document(pet_id, document_id) | downloadPetDocument(petId, documentId) | download_pet_document(pet_id, document_id, opts?) |  |
| PetApi.downloadPetDocumentWithHttpInfo | downloadPetDocumentWithHttpInfo(petId, documentId) | downloadPetDocumentWithHttpInfo(petId, documentId) | downloadPetDocumentWithHttpInfo(petId, documentId) | DownloadPetDocumentWithHttpInfoAsync(petId, documentId) | downloadPetDocumentWithHttpInfo(petId, documentId) | download_pet_document_with_http_info(pet_id, document_id) | download_pet_document_with_http_info(pet_id, document_id) | downloadPetDocumentWithHttpInfo(petId, documentId) | downloadPetDocumentWithHttpInfo(petId, documentId) | DownloadPetDocumentWithHTTPInfo(petId, documentId) ✗ | download_pet_document_with_http_info(pet_id, document_id) | downloadPetDocumentWithHTTPInfo(petId, documentId) ✗ | download_pet_document_with_http_info(pet_id, document_id, opts?) | D82 |
| PetApi.findPetsBornOn | findPetsBornOn(bornOn) | findPetsBornOn(bornOn) | findPetsBornOn(bornOn) | FindPetsBornOnAsync(bornOn) | findPetsBornOn(bornOn) | find_pets_born_on(born_on) | find_pets_born_on(born_on) | findPetsBornOn(bornOn) | findPetsBornOn(bornOn) | FindPetsBornOn(bornOn) | find_pets_born_on(born_on) | findPetsBornOn(bornOn) | find_pets_born_on(born_on, opts?) |  |
| PetApi.findPetsBornOnWithHttpInfo | findPetsBornOnWithHttpInfo(bornOn) | findPetsBornOnWithHttpInfo(bornOn) | findPetsBornOnWithHttpInfo(bornOn) | FindPetsBornOnWithHttpInfoAsync(bornOn) | findPetsBornOnWithHttpInfo(bornOn) | find_pets_born_on_with_http_info(born_on) | find_pets_born_on_with_http_info(born_on) | findPetsBornOnWithHttpInfo(bornOn) | findPetsBornOnWithHttpInfo(bornOn) | FindPetsBornOnWithHTTPInfo(bornOn) ✗ | find_pets_born_on_with_http_info(born_on) | findPetsBornOnWithHTTPInfo(bornOn) ✗ | find_pets_born_on_with_http_info(born_on, opts?) | D82 |
| PetApi.findPetsByStatus | findPetsByStatus(options?) | findPetsByStatus(options?) | findPetsByStatus(options?) | FindPetsByStatusAsync(options?) | findPetsByStatus(options?) | find_pets_by_status(options?) | find_pets_by_status(options?) | findPetsByStatus(options?) | findPetsByStatus(options) ~ | FindPetsByStatus(options) | find_pets_by_status(options) | findPetsByStatus(options?) | find_pets_by_status(options?, opts?) | idiom (allow-list) |
| PetApi.findPetsByStatusWithHttpInfo | findPetsByStatusWithHttpInfo(options?) | findPetsByStatusWithHttpInfo(options?) | findPetsByStatusWithHttpInfo(options?) | FindPetsByStatusWithHttpInfoAsync(options?) | findPetsByStatusWithHttpInfo(options?) | find_pets_by_status_with_http_info(options?) | find_pets_by_status_with_http_info(options?) | findPetsByStatusWithHttpInfo(options?) | findPetsByStatusWithHttpInfo(options) ~ | FindPetsByStatusWithHTTPInfo(options) ✗ | find_pets_by_status_with_http_info(options) | findPetsByStatusWithHTTPInfo(options?) ✗ | find_pets_by_status_with_http_info(options?, opts?) | D82 |
| PetApi.getExternalPetInfo | getExternalPetInfo(petId, server?) | getExternalPetInfo(petId, server?) | getExternalPetInfo(petId, server?) | GetExternalPetInfoAsync(petId, server?) | getExternalPetInfo(petId, server?) | get_external_pet_info(pet_id, server?) | get_external_pet_info(pet_id, server?) | getExternalPetInfo(petId, server?) | getExternalPetInfo(petId, server) ~ | GetExternalPetInfo(petId, server) | get_external_pet_info(pet_id, server) | getExternalPetInfo(petId, server?) | get_external_pet_info(pet_id, opts?) | idiom (allow-list) |
| PetApi.getExternalPetInfoWithHttpInfo | getExternalPetInfoWithHttpInfo(petId, server?) | getExternalPetInfoWithHttpInfo(petId, server?) | getExternalPetInfoWithHttpInfo(petId, server?) | GetExternalPetInfoWithHttpInfoAsync(petId, server?) | getExternalPetInfoWithHttpInfo(petId, server?) | get_external_pet_info_with_http_info(pet_id, server?) | get_external_pet_info_with_http_info(pet_id, server?) | getExternalPetInfoWithHttpInfo(petId, server?) | getExternalPetInfoWithHttpInfo(petId, server) ~ | GetExternalPetInfoWithHTTPInfo(petId, server) ✗ | get_external_pet_info_with_http_info(pet_id, server) | getExternalPetInfoWithHTTPInfo(petId, server?) ✗ | get_external_pet_info_with_http_info(pet_id, opts?) | D82 |
| PetApi.getMultiServerPetInfo | getMultiServerPetInfo(petId, server?) | getMultiServerPetInfo(petId, server?) | getMultiServerPetInfo(petId, server?) | GetMultiServerPetInfoAsync(petId, server?) | getMultiServerPetInfo(petId, server?) | get_multi_server_pet_info(pet_id, server?) | get_multi_server_pet_info(pet_id, server?) | getMultiServerPetInfo(petId, server?) | getMultiServerPetInfo(petId, server) ~ | GetMultiServerPetInfo(petId, server) | get_multi_server_pet_info(pet_id, server) | getMultiServerPetInfo(petId, server?) | get_multi_server_pet_info(pet_id, opts?) | idiom (allow-list) |
| PetApi.getMultiServerPetInfoWithHttpInfo | getMultiServerPetInfoWithHttpInfo(petId, server?) | getMultiServerPetInfoWithHttpInfo(petId, server?) | getMultiServerPetInfoWithHttpInfo(petId, server?) | GetMultiServerPetInfoWithHttpInfoAsync(petId, server?) | getMultiServerPetInfoWithHttpInfo(petId, server?) | get_multi_server_pet_info_with_http_info(pet_id, server?) | get_multi_server_pet_info_with_http_info(pet_id, server?) | getMultiServerPetInfoWithHttpInfo(petId, server?) | getMultiServerPetInfoWithHttpInfo(petId, server) ~ | GetMultiServerPetInfoWithHTTPInfo(petId, server) ✗ | get_multi_server_pet_info_with_http_info(pet_id, server) | getMultiServerPetInfoWithHTTPInfo(petId, server?) ✗ | get_multi_server_pet_info_with_http_info(pet_id, opts?) | D82 |
| PetApi.getPetAvatar | getPetAvatar(petId) | getPetAvatar(petId) | getPetAvatar(petId) | GetPetAvatarAsync(petId) | getPetAvatar(petId) | get_pet_avatar(pet_id) | get_pet_avatar(pet_id) | getPetAvatar(petId) | getPetAvatar(petId) | GetPetAvatar(petId) | get_pet_avatar(pet_id) | getPetAvatar(petId) | get_pet_avatar(pet_id, opts?) |  |
| PetApi.getPetAvatarWithHttpInfo | getPetAvatarWithHttpInfo(petId) | getPetAvatarWithHttpInfo(petId) | getPetAvatarWithHttpInfo(petId) | GetPetAvatarWithHttpInfoAsync(petId) | getPetAvatarWithHttpInfo(petId) | get_pet_avatar_with_http_info(pet_id) | get_pet_avatar_with_http_info(pet_id) | getPetAvatarWithHttpInfo(petId) | getPetAvatarWithHttpInfo(petId) | GetPetAvatarWithHTTPInfo(petId) ✗ | get_pet_avatar_with_http_info(pet_id) | getPetAvatarWithHTTPInfo(petId) ✗ | get_pet_avatar_with_http_info(pet_id, opts?) | D82 |
| PetApi.getPetAvatarThumbnail | getPetAvatarThumbnail(petId) | getPetAvatarThumbnail(petId) | getPetAvatarThumbnail(petId) | GetPetAvatarThumbnailAsync(petId) | getPetAvatarThumbnail(petId) | get_pet_avatar_thumbnail(pet_id) | get_pet_avatar_thumbnail(pet_id) | getPetAvatarThumbnail(petId) | getPetAvatarThumbnail(petId) | GetPetAvatarThumbnail(petId) | get_pet_avatar_thumbnail(pet_id) | getPetAvatarThumbnail(petId) | get_pet_avatar_thumbnail(pet_id, opts?) |  |
| PetApi.getPetAvatarThumbnailWithHttpInfo | getPetAvatarThumbnailWithHttpInfo(petId) | getPetAvatarThumbnailWithHttpInfo(petId) | getPetAvatarThumbnailWithHttpInfo(petId) | GetPetAvatarThumbnailWithHttpInfoAsync(petId) | getPetAvatarThumbnailWithHttpInfo(petId) | get_pet_avatar_thumbnail_with_http_info(pet_id) | get_pet_avatar_thumbnail_with_http_info(pet_id) | getPetAvatarThumbnailWithHttpInfo(petId) | getPetAvatarThumbnailWithHttpInfo(petId) | GetPetAvatarThumbnailWithHTTPInfo(petId) ✗ | get_pet_avatar_thumbnail_with_http_info(pet_id) | getPetAvatarThumbnailWithHTTPInfo(petId) ✗ | get_pet_avatar_thumbnail_with_http_info(pet_id, opts?) | D82 |
| PetApi.getPetById | getPetById(petId, server?) | getPetById(petId, server?) | getPetById(petId, server?) | GetPetByIdAsync(petId, server?) | getPetById(petId, server?) | get_pet_by_id(pet_id, server?) | get_pet_by_id(pet_id, server?) | getPetById(petId, server?) | getPetById(petId, server) ~ | GetPetById(petId, server) | get_pet_by_id(pet_id, server) | getPetById(petId, server?) | get_pet_by_id(pet_id, opts?) | idiom (allow-list) |
| PetApi.getPetByIdWithHttpInfo | getPetByIdWithHttpInfo(petId, server?) | getPetByIdWithHttpInfo(petId, server?) | getPetByIdWithHttpInfo(petId, server?) | GetPetByIdWithHttpInfoAsync(petId, server?) | getPetByIdWithHttpInfo(petId, server?) | get_pet_by_id_with_http_info(pet_id, server?) | get_pet_by_id_with_http_info(pet_id, server?) | getPetByIdWithHttpInfo(petId, server?) | getPetByIdWithHttpInfo(petId, server) ~ | GetPetByIdWithHTTPInfo(petId, server) ✗ | get_pet_by_id_with_http_info(pet_id, server) | getPetByIdWithHTTPInfo(petId, server?) ✗ | get_pet_by_id_with_http_info(pet_id, opts?) | D82 |
| PetApi.getPetByName | getPetByName(name, options) | getPetByName(name, options) | getPetByName(name, options) | GetPetByNameAsync(name, options) | getPetByName(name, options) | get_pet_by_name(name, options) | get_pet_by_name(name, options) | getPetByName(name, options) | getPetByName(name, options) | GetPetByName(name, options) | get_pet_by_name(name, options) | getPetByName(name, options) | get_pet_by_name(name, options, opts?) |  |
| PetApi.getPetByNameWithHttpInfo | getPetByNameWithHttpInfo(name, options) | getPetByNameWithHttpInfo(name, options) | getPetByNameWithHttpInfo(name, options) | GetPetByNameWithHttpInfoAsync(name, options) | getPetByNameWithHttpInfo(name, options) | get_pet_by_name_with_http_info(name, options) | get_pet_by_name_with_http_info(name, options) | getPetByNameWithHttpInfo(name, options) | getPetByNameWithHttpInfo(name, options) | GetPetByNameWithHTTPInfo(name, options) ✗ | get_pet_by_name_with_http_info(name, options) | getPetByNameWithHTTPInfo(name, options) ✗ | get_pet_by_name_with_http_info(name, options, opts?) | D82 |
| PetApi.getPetPassport | getPetPassport(petId) | getPetPassport(petId) | getPetPassport(petId) | GetPetPassportAsync(petId) | getPetPassport(petId) | get_pet_passport(pet_id) | get_pet_passport(pet_id) | getPetPassport(petId) | getPetPassport(petId) | GetPetPassport(petId) | get_pet_passport(pet_id) | getPetPassport(petId) | get_pet_passport(pet_id, opts?) |  |
| PetApi.getPetPassportWithHttpInfo | getPetPassportWithHttpInfo(petId) | getPetPassportWithHttpInfo(petId) | getPetPassportWithHttpInfo(petId) | GetPetPassportWithHttpInfoAsync(petId) | getPetPassportWithHttpInfo(petId) | get_pet_passport_with_http_info(pet_id) | get_pet_passport_with_http_info(pet_id) | getPetPassportWithHttpInfo(petId) | getPetPassportWithHttpInfo(petId) | GetPetPassportWithHTTPInfo(petId) ✗ | get_pet_passport_with_http_info(pet_id) | getPetPassportWithHTTPInfo(petId) ✗ | get_pet_passport_with_http_info(pet_id, opts?) | D82 |
| PetApi.getPetPhoto | getPetPhoto(petId, photoId) | getPetPhoto(petId, photoId) | getPetPhoto(petId, photoId) | GetPetPhotoAsync(petId, photoId) | getPetPhoto(petId, photoId) | get_pet_photo(pet_id, photo_id) | get_pet_photo(pet_id, photo_id) | getPetPhoto(petId, photoId) | getPetPhoto(petId, photoId) | GetPetPhoto(petId, photoId) | get_pet_photo(pet_id, photo_id) | getPetPhoto(petId, photoId) | get_pet_photo(pet_id, photo_id, opts?) |  |
| PetApi.getPetPhotoWithHttpInfo | getPetPhotoWithHttpInfo(petId, photoId) | getPetPhotoWithHttpInfo(petId, photoId) | getPetPhotoWithHttpInfo(petId, photoId) | GetPetPhotoWithHttpInfoAsync(petId, photoId) | getPetPhotoWithHttpInfo(petId, photoId) | get_pet_photo_with_http_info(pet_id, photo_id) | get_pet_photo_with_http_info(pet_id, photo_id) | getPetPhotoWithHttpInfo(petId, photoId) | getPetPhotoWithHttpInfo(petId, photoId) | GetPetPhotoWithHTTPInfo(petId, photoId) ✗ | get_pet_photo_with_http_info(pet_id, photo_id) | getPetPhotoWithHTTPInfo(petId, photoId) ✗ | get_pet_photo_with_http_info(pet_id, photo_id, opts?) | D82 |
| PetApi.getPetSummary | getPetSummary(petId) | getPetSummary(petId) | getPetSummary(petId) | GetPetSummaryAsync(petId) | getPetSummary(petId) | get_pet_summary(pet_id) | get_pet_summary(pet_id) | getPetSummary(petId) | getPetSummary(petId) | GetPetSummary(petId) | get_pet_summary(pet_id) | getPetSummary(petId) | get_pet_summary(pet_id, opts?) |  |
| PetApi.getPetSummaryWithHttpInfo | getPetSummaryWithHttpInfo(petId) | getPetSummaryWithHttpInfo(petId) | getPetSummaryWithHttpInfo(petId) | GetPetSummaryWithHttpInfoAsync(petId) | getPetSummaryWithHttpInfo(petId) | get_pet_summary_with_http_info(pet_id) | get_pet_summary_with_http_info(pet_id) | getPetSummaryWithHttpInfo(petId) | getPetSummaryWithHttpInfo(petId) | GetPetSummaryWithHTTPInfo(petId) ✗ | get_pet_summary_with_http_info(pet_id) | getPetSummaryWithHTTPInfo(petId) ✗ | get_pet_summary_with_http_info(pet_id, opts?) | D82 |
| PetApi.getPetTag | getPetTag(petId, tagName, options?) | getPetTag(petId, tagName, options?) | getPetTag(petId, tagName, options?) | GetPetTagAsync(petId, tagName, options?) | getPetTag(petId, tagName, options?) | get_pet_tag(pet_id, tag_name, options?) | get_pet_tag(pet_id, tag_name, options?) | getPetTag(petId, tagName, options?) | getPetTag(petId, tagName, options) ~ | GetPetTag(petId, tagName, options) | get_pet_tag(pet_id, tag_name, options) | getPetTag(petId, tagName, options?) | get_pet_tag(pet_id, tag_name, options?, opts?) | idiom (allow-list) |
| PetApi.getPetTagWithHttpInfo | getPetTagWithHttpInfo(petId, tagName, options?) | getPetTagWithHttpInfo(petId, tagName, options?) | getPetTagWithHttpInfo(petId, tagName, options?) | GetPetTagWithHttpInfoAsync(petId, tagName, options?) | getPetTagWithHttpInfo(petId, tagName, options?) | get_pet_tag_with_http_info(pet_id, tag_name, options?) | get_pet_tag_with_http_info(pet_id, tag_name, options?) | getPetTagWithHttpInfo(petId, tagName, options?) | getPetTagWithHttpInfo(petId, tagName, options) ~ | GetPetTagWithHTTPInfo(petId, tagName, options) ✗ | get_pet_tag_with_http_info(pet_id, tag_name, options) | getPetTagWithHTTPInfo(petId, tagName, options?) ✗ | get_pet_tag_with_http_info(pet_id, tag_name, options?, opts?) | D82 |
| PetApi.getStagingPetInfo | getStagingPetInfo(petId, server?) | getStagingPetInfo(petId, server?) | getStagingPetInfo(petId, server?) | GetStagingPetInfoAsync(petId, server?) | getStagingPetInfo(petId, server?) | get_staging_pet_info(pet_id, server?) | get_staging_pet_info(pet_id, server?) | getStagingPetInfo(petId, server?) | getStagingPetInfo(petId, server) ~ | GetStagingPetInfo(petId, server) | get_staging_pet_info(pet_id, server) | getStagingPetInfo(petId, server?) | get_staging_pet_info(pet_id, opts?) | idiom (allow-list) |
| PetApi.getStagingPetInfoWithHttpInfo | getStagingPetInfoWithHttpInfo(petId, server?) | getStagingPetInfoWithHttpInfo(petId, server?) | getStagingPetInfoWithHttpInfo(petId, server?) | GetStagingPetInfoWithHttpInfoAsync(petId, server?) | getStagingPetInfoWithHttpInfo(petId, server?) | get_staging_pet_info_with_http_info(pet_id, server?) | get_staging_pet_info_with_http_info(pet_id, server?) | getStagingPetInfoWithHttpInfo(petId, server?) | getStagingPetInfoWithHttpInfo(petId, server) ~ | GetStagingPetInfoWithHTTPInfo(petId, server) ✗ | get_staging_pet_info_with_http_info(pet_id, server) | getStagingPetInfoWithHTTPInfo(petId, server?) ✗ | get_staging_pet_info_with_http_info(pet_id, opts?) | D82 |
| PetApi.setPetAvatar | setPetAvatar(petId, body, requestContentType?) | setPetAvatar(petId, body, requestContentType?) | setPetAvatar(petId, body, requestContentType?) | SetPetAvatarAsync(petId, body, requestContentType?) | setPetAvatar(petId, body, contentType?) ✗ | set_pet_avatar(pet_id, body, request_content_type?) | set_pet_avatar(pet_id, body, content_type?) ✗ | setPetAvatar(petId, body, contentType?) ✗ | setPetAvatar(petId, body, requestContentType?) | SetPetAvatar(petId, body, requestContentType) | set_pet_avatar(pet_id, body) ~ | setPetAvatar(petId, body, requestContentType?) | set_pet_avatar(pet_id, body, opts?) | D53 |
| PetApi.setPetAvatarWithHttpInfo | setPetAvatarWithHttpInfo(petId, body, requestContentType?) | setPetAvatarWithHttpInfo(petId, body, requestContentType?) | setPetAvatarWithHttpInfo(petId, body, requestContentType?) | SetPetAvatarWithHttpInfoAsync(petId, body, requestContentType?) | setPetAvatarWithHttpInfo(petId, body, contentType?) ✗ | set_pet_avatar_with_http_info(pet_id, body, request_content_type?) | set_pet_avatar_with_http_info(pet_id, body, content_type?) ✗ | setPetAvatarWithHttpInfo(petId, body, contentType?) ✗ | setPetAvatarWithHttpInfo(petId, body, requestContentType?) | SetPetAvatarWithHTTPInfo(petId, body, requestContentType) ✗ | set_pet_avatar_with_http_info(pet_id, body) ~ | setPetAvatarWithHTTPInfo(petId, body, requestContentType?) ✗ | set_pet_avatar_with_http_info(pet_id, body, opts?) | D53 D82 |
| PetApi.setPetAvatarThumbnail | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | SetPetAvatarThumbnailAsync(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | set_pet_avatar_thumbnail(pet_id, set_pet_avatar_thumbnail_request) | set_pet_avatar_thumbnail(pet_id, set_pet_avatar_thumbnail_request) | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | SetPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | set_pet_avatar_thumbnail(pet_id, set_pet_avatar_thumbnail_request) | setPetAvatarThumbnail(petId, setPetAvatarThumbnailRequest) | set_pet_avatar_thumbnail(pet_id, set_pet_avatar_thumbnail_request, opts?) |  |
| PetApi.setPetAvatarThumbnailWithHttpInfo | setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest) | SetPetAvatarThumbnailWithHttpInfoAsync(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest) | set_pet_avatar_thumbnail_with_http_info(pet_id, set_pet_avatar_thumbnail_request) | set_pet_avatar_thumbnail_with_http_info(pet_id, set_pet_avatar_thumbnail_request) | setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest) | setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest) | SetPetAvatarThumbnailWithHTTPInfo(petId, setPetAvatarThumbnailRequest) ✗ | set_pet_avatar_thumbnail_with_http_info(pet_id, set_pet_avatar_thumbnail_request) | setPetAvatarThumbnailWithHTTPInfo(petId, setPetAvatarThumbnailRequest) ✗ | set_pet_avatar_thumbnail_with_http_info(pet_id, set_pet_avatar_thumbnail_request, opts?) | D82 |
| PetApi.setPetPreferences | setPetPreferences(petId, options) | setPetPreferences(petId, options) | setPetPreferences(petId, options) | SetPetPreferencesAsync(petId, options) | setPetPreferences(petId, options) | set_pet_preferences(pet_id, options) | set_pet_preferences(pet_id, options) | setPetPreferences(petId, options) | setPetPreferences(petId, options) | SetPetPreferences(petId, options) | set_pet_preferences(pet_id, options) | setPetPreferences(petId, options) | set_pet_preferences(pet_id, options, opts?) |  |
| PetApi.setPetPreferencesWithHttpInfo | setPetPreferencesWithHttpInfo(petId, options) | setPetPreferencesWithHttpInfo(petId, options) | setPetPreferencesWithHttpInfo(petId, options) | SetPetPreferencesWithHttpInfoAsync(petId, options) | setPetPreferencesWithHttpInfo(petId, options) | set_pet_preferences_with_http_info(pet_id, options) | set_pet_preferences_with_http_info(pet_id, options) | setPetPreferencesWithHttpInfo(petId, options) | setPetPreferencesWithHttpInfo(petId, options) | SetPetPreferencesWithHTTPInfo(petId, options) ✗ | set_pet_preferences_with_http_info(pet_id, options) | setPetPreferencesWithHTTPInfo(petId, options) ✗ | set_pet_preferences_with_http_info(pet_id, options, opts?) | D82 |
| PetApi.updatePet | updatePet(petId, pet) | updatePet(petId, pet) | updatePet(petId, pet) | UpdatePetAsync(petId, pet) | updatePet(petId, pet) | update_pet(pet_id, pet) | update_pet(pet_id, pet) | updatePet(petId, pet) | updatePet(petId, pet) | UpdatePet(petId, pet) | update_pet(pet_id, pet) | updatePet(petId, pet) | update_pet(pet_id, pet, opts?) |  |
| PetApi.updatePetWithHttpInfo | updatePetWithHttpInfo(petId, pet) | updatePetWithHttpInfo(petId, pet) | updatePetWithHttpInfo(petId, pet) | UpdatePetWithHttpInfoAsync(petId, pet) | updatePetWithHttpInfo(petId, pet) | update_pet_with_http_info(pet_id, pet) | update_pet_with_http_info(pet_id, pet) | updatePetWithHttpInfo(petId, pet) | updatePetWithHttpInfo(petId, pet) | UpdatePetWithHTTPInfo(petId, pet) ✗ | update_pet_with_http_info(pet_id, pet) | updatePetWithHTTPInfo(petId, pet) ✗ | update_pet_with_http_info(pet_id, pet, opts?) | D82 |
| PetApi.uploadPetCertificate | uploadPetCertificate(petId, options) | uploadPetCertificate(petId, options) | uploadPetCertificate(petId, options) | UploadPetCertificateAsync(petId, options) | uploadPetCertificate(petId, options) | upload_pet_certificate(pet_id, options) | upload_pet_certificate(pet_id, options) | uploadPetCertificate(petId, options) | uploadPetCertificate(petId, options) | UploadPetCertificate(petId, options) | upload_pet_certificate(pet_id, options) | uploadPetCertificate(petId, options) | upload_pet_certificate(pet_id, options, opts?) |  |
| PetApi.uploadPetCertificateWithHttpInfo | uploadPetCertificateWithHttpInfo(petId, options) | uploadPetCertificateWithHttpInfo(petId, options) | uploadPetCertificateWithHttpInfo(petId, options) | UploadPetCertificateWithHttpInfoAsync(petId, options) | uploadPetCertificateWithHttpInfo(petId, options) | upload_pet_certificate_with_http_info(pet_id, options) | upload_pet_certificate_with_http_info(pet_id, options) | uploadPetCertificateWithHttpInfo(petId, options) | uploadPetCertificateWithHttpInfo(petId, options) | UploadPetCertificateWithHTTPInfo(petId, options) ✗ | upload_pet_certificate_with_http_info(pet_id, options) | uploadPetCertificateWithHTTPInfo(petId, options) ✗ | upload_pet_certificate_with_http_info(pet_id, options, opts?) | D82 |
| PetApi.uploadPetDocument | uploadPetDocument(petId, options, requestContentType?) | uploadPetDocument(petId, options, requestContentType?) | uploadPetDocument(petId, options, requestContentType?) | UploadPetDocumentAsync(petId, options, requestContentType?) | uploadPetDocument(petId, options, contentType?) ✗ | upload_pet_document(pet_id, options, request_content_type?) | upload_pet_document(pet_id, options, content_type?) ✗ | uploadPetDocument(petId, options, contentType?) ✗ | uploadPetDocument(petId, options, requestContentType?) | UploadPetDocument(petId, options, requestContentType) | upload_pet_document(pet_id, options) ~ | uploadPetDocument(petId, options, requestContentType?) | upload_pet_document(pet_id, options, opts?) | D54 |
| PetApi.uploadPetDocumentWithHttpInfo | uploadPetDocumentWithHttpInfo(petId, options, requestContentType?) | uploadPetDocumentWithHttpInfo(petId, options, requestContentType?) | uploadPetDocumentWithHttpInfo(petId, options, requestContentType?) | UploadPetDocumentWithHttpInfoAsync(petId, options, requestContentType?) | uploadPetDocumentWithHttpInfo(petId, options, contentType?) ✗ | upload_pet_document_with_http_info(pet_id, options, request_content_type?) | upload_pet_document_with_http_info(pet_id, options, content_type?) ✗ | uploadPetDocumentWithHttpInfo(petId, options, contentType?) ✗ | uploadPetDocumentWithHttpInfo(petId, options, requestContentType?) | UploadPetDocumentWithHTTPInfo(petId, options, requestContentType) ✗ | upload_pet_document_with_http_info(pet_id, options) ~ | uploadPetDocumentWithHTTPInfo(petId, options, requestContentType?) ✗ | upload_pet_document_with_http_info(pet_id, options, opts?) | D54 D82 |
| PetApi.config | config | — | — | — | — | — | config ✗ | — | config ✗ | — | — | — | config ~ | D21 |
| PetApi.apiClient | apiClient | — | — | — | — | — | — | — | apiClient ✗ | — | — | — | api_client ~ | D23 |
| PetApi.setPetAvatarWithContentType | setPetAvatarWithContentType | — | — | — | — | — | — | — | — | — | set_pet_avatar_with_content_type(pet_id, body, request_content_type) ~ | — | — | idiom (allow-list) |
| PetApi.uploadPetDocumentWithContentType | uploadPetDocumentWithContentType | — | — | — | — | — | — | — | — | — | upload_pet_document_with_content_type(pet_id, options, request_content_type) ~ | — | — | idiom (allow-list) |
| PetApi.authenticator | authenticator | — | — | — | — | — | — | — | — | — | — | — | authenticator ~ | idiom (allow-list) |
| **GetExternalPetInfoServer** | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer | GetExternalPetInfoServer |  |
| GetExternalPetInfoServer.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetExternalPetInfoServerServer0** | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 | GetExternalPetInfoServerServer0 |  |
| GetExternalPetInfoServerServer0 ctor | new() | () | — | — | — | () | — | () | () | — | — | () | — |  |
| GetExternalPetInfoServerServer0.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetMultiServerPetInfoServer** | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer | GetMultiServerPetInfoServer |  |
| GetMultiServerPetInfoServer.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetMultiServerPetInfoServerPrimary** | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary | GetMultiServerPetInfoServerPrimary |  |
| GetMultiServerPetInfoServerPrimary ctor | new() | () | — | — | — | () | — | () | () | — | — | () | — |  |
| GetMultiServerPetInfoServerPrimary.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetMultiServerPetInfoServerRegional** | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional | GetMultiServerPetInfoServerRegional |  |
| GetMultiServerPetInfoServerRegional ctor | new(region) | (region) | (region) | (region) | (region) | (region) | (region) | (region) | (region) | — | — | (region) | (region?) |  |
| GetMultiServerPetInfoServerRegional.region | region | region | region | Region | — ✗ | — ✗ | — ✗ | region | — ✗ | Region | region | region | region | D68 |
| GetMultiServerPetInfoServerRegional.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetMultiServerPetInfoServerMirror** | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror | GetMultiServerPetInfoServerMirror |  |
| GetMultiServerPetInfoServerMirror ctor | new(region, channel) | (region, channel) | (region, channel) | (region, channel) | (region, channel) | (region, channel) | (region, channel) | (region, channel) | (region, channel) | — | — | (region, channel) | (region?, channel?) |  |
| GetMultiServerPetInfoServerMirror.region | region | region | region | Region | — ✗ | — ✗ | — ✗ | region | — ✗ | Region | region | region | region | D68 |
| GetMultiServerPetInfoServerMirror.channel | channel | channel | channel | Channel | — ✗ | — ✗ | — ✗ | channel | — ✗ | Channel | channel | channel | channel | D68 |
| GetMultiServerPetInfoServerMirror.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetPetByIdServer** | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer | GetPetByIdServer |  |
| GetPetByIdServer.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetPetByIdServerCdnBackedReadEndpointForPetDetails** | GetPetByIdServerCdnBackedReadEndpointForPetDetails | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails | GetPetByIdServerCDNBackedReadEndpointForPetDetails | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails | GetPetByIdServerCDNBackedReadEndpointForPetDetails ~ | GetPetByIdServerCDNBackedReadEndpointForPetDetails | idiom (allow-list) |
| GetPetByIdServerCdnBackedReadEndpointForPetDetails ctor | new() | () | — | — | — | () | — | () | () | — | — | () | — |  |
| GetPetByIdServerCdnBackedReadEndpointForPetDetails.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetStagingPetInfoServer** | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer | GetStagingPetInfoServer |  |
| GetStagingPetInfoServer.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **GetStagingPetInfoServerStagingServer** | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer | GetStagingPetInfoServerStagingServer |  |
| GetStagingPetInfoServerStagingServer ctor | new(environment, version) | (environment, version) | (environment, version) | (environment, version) | (environment, version) | (environment, version) | (environment, version) | (environment, version) | (environment, version) | — | — | (environment, version) | (environment?, version?) |  |
| GetStagingPetInfoServerStagingServer.environment | environment | environment | environment | Environment | — ✗ | — ✗ | — ✗ | environment | — ✗ | Environment | environment | environment | environment | D69 |
| GetStagingPetInfoServerStagingServer.version | version | version | version | Version | — ✗ | — ✗ | — ✗ | version | — ✗ | Version | version | version | version | D69 |
| GetStagingPetInfoServerStagingServer.getUrl | getUrl | getUrl | getUrl | Url | getUrl | get_url | url | getUrl | getUrl | GetUrl | get_url | getUrl | url |  |
| **StoreApi** | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi | StoreApi |  |
| StoreApi ctor | new(apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | — | (api_client?, config?, authenticator?) | (api_client?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient?, config?, authenticator?) | (apiClient, config, authenticator) | (api_client, config, authenticator) | (apiClient?, config?, authenticator?) | (api_client?, config?, authenticator?) |  |
| StoreApi.getDefaults | getDefaults | getDefaults | getDefaults | GetDefaultsAsync | getDefaults | get_defaults | get_defaults | getDefaults | getDefaults | GetDefaults | get_defaults | getDefaults | — |  |
| StoreApi.getDefaultsWithHttpInfo | getDefaultsWithHttpInfo | getDefaultsWithHttpInfo | getDefaultsWithHttpInfo | GetDefaultsWithHttpInfoAsync | getDefaultsWithHttpInfo | get_defaults_with_http_info | get_defaults_with_http_info | getDefaultsWithHttpInfo | getDefaultsWithHttpInfo | GetDefaultsWithHTTPInfo | get_defaults_with_http_info | getDefaultsWithHTTPInfo | — |  |
| StoreApi.getDepartment | getDepartment | getDepartment | getDepartment | GetDepartmentAsync | getDepartment | get_department | get_department | getDepartment | getDepartment | GetDepartment | get_department | getDepartment | — |  |
| StoreApi.getDepartmentWithHttpInfo | getDepartmentWithHttpInfo | getDepartmentWithHttpInfo | getDepartmentWithHttpInfo | GetDepartmentWithHttpInfoAsync | getDepartmentWithHttpInfo | get_department_with_http_info | get_department_with_http_info | getDepartmentWithHttpInfo | getDepartmentWithHttpInfo | GetDepartmentWithHTTPInfo | get_department_with_http_info | getDepartmentWithHTTPInfo | — |  |
| StoreApi.getGroupedCategories | getGroupedCategories | getGroupedCategories | getGroupedCategories | GetGroupedCategoriesAsync | getGroupedCategories | get_grouped_categories | get_grouped_categories | getGroupedCategories | getGroupedCategories | GetGroupedCategories | get_grouped_categories | getGroupedCategories | — |  |
| StoreApi.getGroupedCategoriesWithHttpInfo | getGroupedCategoriesWithHttpInfo | getGroupedCategoriesWithHttpInfo | getGroupedCategoriesWithHttpInfo | GetGroupedCategoriesWithHttpInfoAsync | getGroupedCategoriesWithHttpInfo | get_grouped_categories_with_http_info | get_grouped_categories_with_http_info | getGroupedCategoriesWithHttpInfo | getGroupedCategoriesWithHttpInfo | GetGroupedCategoriesWithHTTPInfo | get_grouped_categories_with_http_info | getGroupedCategoriesWithHTTPInfo | — |  |
| StoreApi.getInventory | getInventory | getInventory | getInventory | GetInventoryAsync | getInventory | get_inventory | get_inventory | getInventory | getInventory | GetInventory | get_inventory | getInventory | — |  |
| StoreApi.getInventoryWithHttpInfo | getInventoryWithHttpInfo | getInventoryWithHttpInfo | getInventoryWithHttpInfo | GetInventoryWithHttpInfoAsync | getInventoryWithHttpInfo | get_inventory_with_http_info | get_inventory_with_http_info | getInventoryWithHttpInfo | getInventoryWithHttpInfo | GetInventoryWithHTTPInfo | get_inventory_with_http_info | getInventoryWithHTTPInfo | — |  |
| StoreApi.getMatrix | getMatrix | getMatrix | getMatrix | GetMatrixAsync | getMatrix | get_matrix | get_matrix | getMatrix | getMatrix | GetMatrix | get_matrix | getMatrix | — |  |
| StoreApi.getMatrixWithHttpInfo | getMatrixWithHttpInfo | getMatrixWithHttpInfo | getMatrixWithHttpInfo | GetMatrixWithHttpInfoAsync | getMatrixWithHttpInfo | get_matrix_with_http_info | get_matrix_with_http_info | getMatrixWithHttpInfo | getMatrixWithHttpInfo | GetMatrixWithHTTPInfo | get_matrix_with_http_info | getMatrixWithHTTPInfo | — |  |
| StoreApi.getSwatch | getSwatch | getSwatch | getSwatch | GetSwatchAsync | getSwatch | get_swatch | get_swatch | getSwatch | getSwatch | GetSwatch | get_swatch | getSwatch | — |  |
| StoreApi.getSwatchWithHttpInfo | getSwatchWithHttpInfo | getSwatchWithHttpInfo | getSwatchWithHttpInfo | GetSwatchWithHttpInfoAsync | getSwatchWithHttpInfo | get_swatch_with_http_info | get_swatch_with_http_info | getSwatchWithHttpInfo | getSwatchWithHttpInfo | GetSwatchWithHTTPInfo | get_swatch_with_http_info | getSwatchWithHTTPInfo | — |  |
| StoreApi.getSwatchGroups | getSwatchGroups | getSwatchGroups | getSwatchGroups | GetSwatchGroupsAsync | getSwatchGroups | get_swatch_groups | get_swatch_groups | getSwatchGroups | getSwatchGroups | GetSwatchGroups | get_swatch_groups | getSwatchGroups | — |  |
| StoreApi.getSwatchGroupsWithHttpInfo | getSwatchGroupsWithHttpInfo | getSwatchGroupsWithHttpInfo | getSwatchGroupsWithHttpInfo | GetSwatchGroupsWithHttpInfoAsync | getSwatchGroupsWithHttpInfo | get_swatch_groups_with_http_info | get_swatch_groups_with_http_info | getSwatchGroupsWithHttpInfo | getSwatchGroupsWithHttpInfo | GetSwatchGroupsWithHTTPInfo | get_swatch_groups_with_http_info | getSwatchGroupsWithHTTPInfo | — |  |
| StoreApi.getTimestampGroups | getTimestampGroups | getTimestampGroups | getTimestampGroups | GetTimestampGroupsAsync | getTimestampGroups | get_timestamp_groups | get_timestamp_groups | getTimestampGroups | getTimestampGroups | GetTimestampGroups | get_timestamp_groups | getTimestampGroups | — |  |
| StoreApi.getTimestampGroupsWithHttpInfo | getTimestampGroupsWithHttpInfo | getTimestampGroupsWithHttpInfo | getTimestampGroupsWithHttpInfo | GetTimestampGroupsWithHttpInfoAsync | getTimestampGroupsWithHttpInfo | get_timestamp_groups_with_http_info | get_timestamp_groups_with_http_info | getTimestampGroupsWithHttpInfo | getTimestampGroupsWithHttpInfo | GetTimestampGroupsWithHTTPInfo | get_timestamp_groups_with_http_info | getTimestampGroupsWithHTTPInfo | — |  |
| StoreApi.getTree | getTree | getTree | getTree | GetTreeAsync | getTree | get_tree | get_tree | getTree | getTree | GetTree | get_tree | getTree | — |  |
| StoreApi.getTreeWithHttpInfo | getTreeWithHttpInfo | getTreeWithHttpInfo | getTreeWithHttpInfo | GetTreeWithHttpInfoAsync | getTreeWithHttpInfo | get_tree_with_http_info | get_tree_with_http_info | getTreeWithHttpInfo | getTreeWithHttpInfo | GetTreeWithHTTPInfo | get_tree_with_http_info | getTreeWithHTTPInfo | — |  |
| StoreApi.createAccount | createAccount(account) | createAccount(account) | createAccount(account) | CreateAccountAsync(account) | createAccount(account) | create_account(account) | create_account(account) | createAccount(account) | createAccount(account) | CreateAccount(account) | create_account(account) | createAccount(account) | create_account(account, opts?) |  |
| StoreApi.createAccountWithHttpInfo | createAccountWithHttpInfo(account) | createAccountWithHttpInfo(account) | createAccountWithHttpInfo(account) | CreateAccountWithHttpInfoAsync(account) | createAccountWithHttpInfo(account) | create_account_with_http_info(account) | create_account_with_http_info(account) | createAccountWithHttpInfo(account) | createAccountWithHttpInfo(account) | CreateAccountWithHTTPInfo(account) ✗ | create_account_with_http_info(account) | createAccountWithHTTPInfo(account) ✗ | create_account_with_http_info(account, opts?) | D82 |
| StoreApi.deleteOrder | deleteOrder(orderId) | deleteOrder(orderId) | deleteOrder(orderId) | DeleteOrderAsync(orderId) | deleteOrder(orderId) | delete_order(order_id) | delete_order(order_id) | deleteOrder(orderId) | deleteOrder(orderId) | DeleteOrder(orderId) | delete_order(order_id) | deleteOrder(orderId) | delete_order(order_id, opts?) |  |
| StoreApi.deleteOrderWithHttpInfo | deleteOrderWithHttpInfo(orderId) | deleteOrderWithHttpInfo(orderId) | deleteOrderWithHttpInfo(orderId) | DeleteOrderWithHttpInfoAsync(orderId) | deleteOrderWithHttpInfo(orderId) | delete_order_with_http_info(order_id) | delete_order_with_http_info(order_id) | deleteOrderWithHttpInfo(orderId) | deleteOrderWithHttpInfo(orderId) | DeleteOrderWithHTTPInfo(orderId) ✗ | delete_order_with_http_info(order_id) | deleteOrderWithHTTPInfo(orderId) ✗ | delete_order_with_http_info(order_id, opts?) | D82 |
| StoreApi.getBySwatch | getBySwatch(pathSwatch, options?) | getBySwatch(pathSwatch, options?) | getBySwatch(pathSwatch, options?) | GetBySwatchAsync(pathSwatch, options?) | getBySwatch(pathSwatch, options?) | get_by_swatch(path_swatch, options?) | get_by_swatch(path_swatch, options?) | getBySwatch(pathSwatch, options?) | getBySwatch(pathSwatch, options) ~ | GetBySwatch(pathSwatch, options) | get_by_swatch(path_swatch, options) | getBySwatch(pathSwatch, options?) | get_by_swatch(path_swatch, options?, opts?) | idiom (allow-list) |
| StoreApi.getBySwatchWithHttpInfo | getBySwatchWithHttpInfo(pathSwatch, options?) | getBySwatchWithHttpInfo(pathSwatch, options?) | getBySwatchWithHttpInfo(pathSwatch, options?) | GetBySwatchWithHttpInfoAsync(pathSwatch, options?) | getBySwatchWithHttpInfo(pathSwatch, options?) | get_by_swatch_with_http_info(path_swatch, options?) | get_by_swatch_with_http_info(path_swatch, options?) | getBySwatchWithHttpInfo(pathSwatch, options?) | getBySwatchWithHttpInfo(pathSwatch, options) ~ | GetBySwatchWithHTTPInfo(pathSwatch, options) ✗ | get_by_swatch_with_http_info(path_swatch, options) | getBySwatchWithHTTPInfo(pathSwatch, options?) ✗ | get_by_swatch_with_http_info(path_swatch, options?, opts?) | D82 |
| StoreApi.getOrderById | getOrderById(orderId) | getOrderById(orderId) | getOrderById(orderId) | GetOrderByIdAsync(orderId) | getOrderById(orderId) | get_order_by_id(order_id) | get_order_by_id(order_id) | getOrderById(orderId) | getOrderById(orderId) | GetOrderById(orderId) | get_order_by_id(order_id) | getOrderById(orderId) | get_order_by_id(order_id, opts?) |  |
| StoreApi.getOrderByIdWithHttpInfo | getOrderByIdWithHttpInfo(orderId) | getOrderByIdWithHttpInfo(orderId) | getOrderByIdWithHttpInfo(orderId) | GetOrderByIdWithHttpInfoAsync(orderId) | getOrderByIdWithHttpInfo(orderId) | get_order_by_id_with_http_info(order_id) | get_order_by_id_with_http_info(order_id) | getOrderByIdWithHttpInfo(orderId) | getOrderByIdWithHttpInfo(orderId) | GetOrderByIdWithHTTPInfo(orderId) ✗ | get_order_by_id_with_http_info(order_id) | getOrderByIdWithHTTPInfo(orderId) ✗ | get_order_by_id_with_http_info(order_id, opts?) | D82 |
| StoreApi.getOrderByTrackingId | getOrderByTrackingId(trackingId) | getOrderByTrackingId(trackingId) | getOrderByTrackingId(trackingId) | GetOrderByTrackingIdAsync(trackingId) | getOrderByTrackingId(trackingId) | get_order_by_tracking_id(tracking_id) | get_order_by_tracking_id(tracking_id) | getOrderByTrackingId(trackingId) | getOrderByTrackingId(trackingId) | GetOrderByTrackingId(trackingId) | get_order_by_tracking_id(tracking_id) | getOrderByTrackingId(trackingId) | get_order_by_tracking_id(tracking_id, opts?) |  |
| StoreApi.getOrderByTrackingIdWithHttpInfo | getOrderByTrackingIdWithHttpInfo(trackingId) | getOrderByTrackingIdWithHttpInfo(trackingId) | getOrderByTrackingIdWithHttpInfo(trackingId) | GetOrderByTrackingIdWithHttpInfoAsync(trackingId) | getOrderByTrackingIdWithHttpInfo(trackingId) | get_order_by_tracking_id_with_http_info(tracking_id) | get_order_by_tracking_id_with_http_info(tracking_id) | getOrderByTrackingIdWithHttpInfo(trackingId) | getOrderByTrackingIdWithHttpInfo(trackingId) | GetOrderByTrackingIdWithHTTPInfo(trackingId) ✗ | get_order_by_tracking_id_with_http_info(tracking_id) | getOrderByTrackingIdWithHTTPInfo(trackingId) ✗ | get_order_by_tracking_id_with_http_info(tracking_id, opts?) | D82 |
| StoreApi.getStockItem | getStockItem(options?) | getStockItem(options?) | getStockItem(options?) | GetStockItemAsync(options?) | getStockItem(options?) | get_stock_item(options?) | get_stock_item(options?) | getStockItem(options?) | getStockItem(options) ~ | GetStockItem(options) | get_stock_item(options) | getStockItem(options?) | get_stock_item(options?, opts?) | idiom (allow-list) |
| StoreApi.getStockItemWithHttpInfo | getStockItemWithHttpInfo(options?) | getStockItemWithHttpInfo(options?) | getStockItemWithHttpInfo(options?) | GetStockItemWithHttpInfoAsync(options?) | getStockItemWithHttpInfo(options?) | get_stock_item_with_http_info(options?) | get_stock_item_with_http_info(options?) | getStockItemWithHttpInfo(options?) | getStockItemWithHttpInfo(options) ~ | GetStockItemWithHTTPInfo(options) ✗ | get_stock_item_with_http_info(options) | getStockItemWithHTTPInfo(options?) ✗ | get_stock_item_with_http_info(options?, opts?) | D82 |
| StoreApi.placeOrder | placeOrder(order?) | placeOrder(order) ✗ | placeOrder(order?) | PlaceOrderAsync(order?) | placeOrder(order?) | place_order(order?) | place_order(order?) | placeOrder(order?) | placeOrder(order) ~ | PlaceOrder(order) | place_order(order) | placeOrder(order?) | place_order(order?, opts?) | Optionality `(order?)` (majority) in java. |
| StoreApi.placeOrderWithHttpInfo | placeOrderWithHttpInfo(order?) | placeOrderWithHttpInfo(order) ✗ | placeOrderWithHttpInfo(order?) | PlaceOrderWithHttpInfoAsync(order?) | placeOrderWithHttpInfo(order?) | place_order_with_http_info(order?) | place_order_with_http_info(order?) | placeOrderWithHttpInfo(order?) | placeOrderWithHttpInfo(order) ~ | PlaceOrderWithHTTPInfo(order) ✗ | place_order_with_http_info(order) | placeOrderWithHTTPInfo(order?) ✗ | place_order_with_http_info(order?, opts?) | Optionality `(order?)` (majority) in java. D82 |
| StoreApi.searchStore | searchStore(options?) | searchStore(options?) | searchStore(options?) | SearchStoreAsync(options?) | searchStore(options?) | search_store(options?) | search_store(options?) | searchStore(options?) | searchStore(options) ~ | SearchStore(options) | search_store(options) | searchStore(options?) | search_store(options?, opts?) | idiom (allow-list) |
| StoreApi.searchStoreWithHttpInfo | searchStoreWithHttpInfo(options?) | searchStoreWithHttpInfo(options?) | searchStoreWithHttpInfo(options?) | SearchStoreWithHttpInfoAsync(options?) | searchStoreWithHttpInfo(options?) | search_store_with_http_info(options?) | search_store_with_http_info(options?) | searchStoreWithHttpInfo(options?) | searchStoreWithHttpInfo(options) ~ | SearchStoreWithHTTPInfo(options) ✗ | search_store_with_http_info(options) | searchStoreWithHTTPInfo(options?) ✗ | search_store_with_http_info(options?, opts?) | D82 |
| StoreApi.config | config | — | — | — | — | — | config ✗ | — | config ✗ | — | — | — | config ~ | D22 |
| StoreApi.apiClient | apiClient | — | — | — | — | — | — | — | apiClient ✗ | — | — | — | api_client ~ | D24 |
| StoreApi.authenticator | authenticator | — | — | — | — | — | — | — | — | — | — | — | authenticator ~ | idiom (allow-list) |
| StoreApi.getDefaults | getDefaults(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_defaults(opts?) |  |
| StoreApi.getDefaultsWithHttpInfo | getDefaultsWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_defaults_with_http_info(opts?) | D82 |
| StoreApi.getDepartment | getDepartment(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_department(opts?) |  |
| StoreApi.getDepartmentWithHttpInfo | getDepartmentWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_department_with_http_info(opts?) | D82 |
| StoreApi.getGroupedCategories | getGroupedCategories(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_grouped_categories(opts?) |  |
| StoreApi.getGroupedCategoriesWithHttpInfo | getGroupedCategoriesWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_grouped_categories_with_http_info(opts?) | D82 |
| StoreApi.getInventory | getInventory(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_inventory(opts?) |  |
| StoreApi.getInventoryWithHttpInfo | getInventoryWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_inventory_with_http_info(opts?) | D82 |
| StoreApi.getMatrix | getMatrix(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_matrix(opts?) |  |
| StoreApi.getMatrixWithHttpInfo | getMatrixWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_matrix_with_http_info(opts?) | D82 |
| StoreApi.getSwatch | getSwatch(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_swatch(opts?) |  |
| StoreApi.getSwatchWithHttpInfo | getSwatchWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_swatch_with_http_info(opts?) | D82 |
| StoreApi.getSwatchGroups | getSwatchGroups(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_swatch_groups(opts?) |  |
| StoreApi.getSwatchGroupsWithHttpInfo | getSwatchGroupsWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_swatch_groups_with_http_info(opts?) | D82 |
| StoreApi.getTimestampGroups | getTimestampGroups(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_timestamp_groups(opts?) |  |
| StoreApi.getTimestampGroupsWithHttpInfo | getTimestampGroupsWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_timestamp_groups_with_http_info(opts?) | D82 |
| StoreApi.getTree | getTree(opts?) | — | — | — | — | — | — | — | — | — | — | — | get_tree(opts?) |  |
| StoreApi.getTreeWithHttpInfo | getTreeWithHttpInfo(opts?) | — | — | — | — | — | — | — | — | — ✗ | — | — ✗ | get_tree_with_http_info(opts?) | D82 |

## Options (request) types

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **AddPetOptions** | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions | AddPetOptions |  |
| AddPetOptions.auth | auth | auth | auth | Auth | auth | auth | auth | auth | auth | Auth ✗ | auth | auth | auth | D70 |
| **AddPetPhotosOptions** | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions | AddPetPhotosOptions |  |
| AddPetPhotosOptions.files | files | files | files | Files | files | files | files | files | files | Files | files | files | files ✗ | D70 |
| AddPetPhotosOptions.metadata | metadata | metadata | metadata | Metadata | metadata | metadata | metadata | metadata | metadata | Metadata | metadata | metadata | metadata ✗ | D70 |
| **AddPetTreatmentOptions** | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions | AddPetTreatmentOptions |  |
| AddPetTreatmentOptions.auth | auth | auth | auth | Auth | auth | auth | auth | auth | auth | Auth ✗ | auth | auth | auth | D70 |
| **DeletePetOptions** | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions | DeletePetOptions |  |
| DeletePetOptions.auth | auth | auth | auth | Auth | auth | auth | auth | auth | auth | Auth ✗ | auth | auth | auth | D70 |
| DeletePetOptions (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **FindPetsByStatusOptions** | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions | FindPetsByStatusOptions |  |
| FindPetsByStatusOptions (5 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **GetBySwatchOptions** | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions | GetBySwatchOptions |  |
| GetBySwatchOptions (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **GetPetByNameOptions** | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions | GetPetByNameOptions |  |
| GetPetByNameOptions.category | category | category | category | Category | category | category | category | category | category | Category | category | category | category ✗ | D70 |
| GetPetByNameOptions.xCorrelationId | xCorrelationId | xCorrelationId | xCorrelationId | XCorrelationId | xCorrelationId | x_correlation_id | x_correlation_id | xCorrelationId | xCorrelationId | XCorrelationId | x_correlation_id | xCorrelationId | x_correlation_id ✗ | D70 |
| **GetPetTagOptions** | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions | GetPetTagOptions |  |
| GetPetTagOptions (10 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **GetStockItemOptions** | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions | GetStockItemOptions |  |
| GetStockItemOptions (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **SearchStoreOptions** | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions | SearchStoreOptions |  |
| SearchStoreOptions (4 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **SetPetPreferencesOptions** | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions | SetPetPreferencesOptions |  |
| SetPetPreferencesOptions.nickname | nickname | nickname | nickname | Nickname | nickname | nickname | nickname | nickname | nickname | Nickname | nickname | nickname | nickname ✗ | D70 |
| SetPetPreferencesOptions (4 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **UploadPetCertificateOptions** | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions | UploadPetCertificateOptions |  |
| UploadPetCertificateOptions.file | file | file | _file | File | file | file | file | file | file | File ✗ | file | file | file ✗ | D70 |
| **UploadPetDocumentOptions** | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions | UploadPetDocumentOptions |  |
| UploadPetDocumentOptions.file | file | file | _file | File | file | file | file | file | file | File ✗ | file | file | file ✗ | D70 |
| UploadPetDocumentOptions.isPrimary | isPrimary | isPrimary | — | — | — | — | — | — | — | — | is_primary(is_primary) ✗ | — | — | Parameters `()` (majority) in rust. |
| UploadPetDocumentOptions.isPrimary | isPrimary | — | isPrimary | IsPrimary | isPrimary | is_primary | is_primary | isPrimary | isPrimary | IsPrimary | is_primary ✗ | isPrimary | is_primary | Not part of the canonical surface: make it non-public in rust. |
| UploadPetDocumentOptions (5 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |

## Models

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Account** | Account | Account | Account | Account | Account | Account | Account | Account | Account | Account | Account | Account | Account |  |
| Account.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Account.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Account (3 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **ApiResponse** | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse | ApiResponse |  |
| ApiResponse.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| ApiResponse.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| ApiResponse (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **BinaryVault** | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault | BinaryVault |  |
| BinaryVault.getSealAsBytes | getSealAsBytes | — | — | — | getSealAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| BinaryVault.getShardsAsBytes | getShardsAsBytes | — | — | — | getShardsAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| BinaryVault.getLabelsAsBytes | getLabelsAsBytes | — | — | — | getLabelsAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| BinaryVault.setSealFromBytes | setSealFromBytes | — | — | — | setSealFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| BinaryVault.setShardsFromBytes | setShardsFromBytes | — | — | — | setShardsFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| BinaryVault.setLabelsFromBytes | setLabelsFromBytes | — | — | — | setLabelsFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| BinaryVault.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| BinaryVault.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| BinaryVault (3 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Category** | Category | Category | Category | Category | Category | Category | Category | Category | Category | Category | Category | Category | Category |  |
| Category.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Category.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Category (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Defaults** | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults | Defaults |  |
| Defaults.retries | retries | retries | retries | Retries | retries ~ | retries | retries | retries | retries | Retries | retries | retries | retries | idiom (allow-list) |
| Defaults.mode | mode | mode | mode | Mode | mode ~ | mode | mode | mode | mode | Mode | mode | mode | mode | idiom (allow-list) |
| Defaults.greeting | greeting | greeting | greeting | Greeting | greeting ~ | greeting | greeting | greeting | greeting | Greeting | greeting | greeting | greeting | idiom (allow-list) |
| Defaults.ratio | ratio | ratio | ratio | Ratio | ratio ~ | ratio | ratio | ratio | ratio | Ratio | ratio | ratio | ratio | idiom (allow-list) |
| Defaults.scale | scale | scale | scale | Scale | scale ~ | scale | scale | scale | scale | Scale | scale | scale | scale | idiom (allow-list) |
| Defaults.weight | weight | weight | weight | Weight | weight ~ | weight | weight | weight | weight | Weight | weight | weight | weight | idiom (allow-list) |
| Defaults.ceiling | ceiling | ceiling | ceiling | Ceiling | ceiling ~ | ceiling | ceiling | ceiling | ceiling | Ceiling | ceiling | ceiling | ceiling | idiom (allow-list) |
| Defaults.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Defaults.allDefaultsModeEnumValues | allDefaultsModeEnumValues | — | — | — | — | — | — | — | — | AllDefaultsModeEnumValues ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| Defaults.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Defaults (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Department** | Department | Department | Department | Department | Department | Department | Department | Department | Department | Department | Department | Department | Department |  |
| Department.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Department.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Department (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **DryFood** | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood | DryFood |  |
| DryFood.foodType | foodType | foodType | foodType | FoodType | foodType | food_type | food_type | foodType | foodType | FoodType | food_type | foodType | food_type ✗ | D79 |
| DryFood.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| DryFood.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| DryFood (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **EdgeCases** | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases | EdgeCases |  |
| EdgeCases.freeFormAny | freeFormAny | freeFormAny | freeFormAny | FreeFormAny | freeFormAny ~ | free_form_any | free_form_any | freeFormAny | freeFormAny | FreeFormAny | free_form_any | freeFormAny | free_form_any | idiom (allow-list) |
| EdgeCases.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| EdgeCases.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| EdgeCases (17 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Employee** | Employee | Employee | Employee | Employee | Employee | Employee | Employee | Employee | Employee | Employee | Employee | Employee | Employee |  |
| Employee.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Employee.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Employee (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Medication** | Medication | Medication | Medication | Medication | Medication | Medication | Medication | Medication | Medication | Medication | Medication | Medication | Medication |  |
| Medication.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Medication.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Medication (3 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Metadata** | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata | Metadata |  |
| Metadata.additionalProperties | additionalProperties | getAdditionalProperties | additionalProperties | AdditionalProperties ✗ | additionalProperties ✗ | additional_properties | ADDITIONAL_PROPERTIES | — ✗ | additionalProperties | AdditionalProperties | additional_properties | additionalProperties | additional_properties ✗ | D77 |
| Metadata.setAdditionalProperty | setAdditionalProperty | setAdditionalProperty(key, value) ✗ | — | — | — | — | — | — | — | — | — | — | — | D51 |
| Metadata.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Metadata.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Metadata (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **ModelError** | Error | ModelError ~ | — | — | — | — | — | — | — | ModelError ✗ | — | ModelError ~ | — | D02 |
| ModelError.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| ModelError (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **ModelResult** | Result | ModelResult ✗ | — | — | — | — | — | — | — | — | ModelResult ~ | ModelResult ~ | — | D04 |
| ModelResult (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Order** | Order | Order | Order | Order | Order | Order | Order | Order | Order | Order | Order | Order | Order |  |
| Order.status | status | status | status | Status | status ~ | status | status | status | status | Status | status | status | status | idiom (allow-list) |
| Order.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Order.allOrderStatusEnumValues | allOrderStatusEnumValues | — | — | — | — | — | — | — | — | AllOrderStatusEnumValues ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| Order.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Order (6 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Pet** | Pet | Pet | Pet | Pet | Pet | Pet | Pet | Pet | Pet | Pet | Pet | Pet | Pet |  |
| Pet.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Pet.allPetStatusEnumValues | allPetStatusEnumValues | — | — | — | — | — | — | — | — | AllPetStatusEnumValues ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| Pet.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Pet (13 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **PetCriteria** | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria | PetCriteria |  |
| PetCriteria.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PetCriteria.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| PetCriteria (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **PetFood** | PetFood | PetFood | PetFood | PetFood | PetFood | PetFood | PetFood | PetFood | PetFood | PetFood | PetFood ~ | PetFood | PetFood | idiom (allow-list) |
| PetFood.actualInstance | actualInstance | — | actualInstance ✗ | — | getActualInstance ✗ | actual_instance ✗ | — | getActualInstance ✗ | — | — | — | — | — | D41 |
| PetFood.build | build | — | — | — | build(data) ~ | — | build(data) ~ | — | — | — | — | — | build(data) ~ | idiom (allow-list) |
| PetFood.oneOf0 | oneOf0 | — | — | — | — | one_of_0 ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| PetFood.oneOf1 | oneOf1 | — | — | — | — | one_of_1 ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| PetFood.getDiscriminatorValue | getDiscriminatorValue | — | — | — | — | get_discriminator_value(obj) ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| PetFood.openapiOneOf | openapiOneOf | — | — | — | — | — | openapi_one_of ~ | — | — | — | — | — | openapi_one_of ~ | idiom (allow-list) |
| PetFood.serializeOneof | serializeOneof | — | — | — | — | — | serialize_oneof(value) ~ | — | — | — | — | — | — | idiom (allow-list) |
| PetFood.openapiDiscriminatorName | openapiDiscriminatorName | — | — | — | — | — | openapi_discriminator_name ~ | — | — | — | — | — | openapi_discriminator_name ~ | idiom (allow-list) |
| PetFood.openapiDiscriminatorMapping | openapiDiscriminatorMapping | — | — | — | — | — | openapi_discriminator_mapping ~ | — | — | — | — | — | openapi_discriminator_mapping ~ | idiom (allow-list) |
| PetFood.oneOfSchemas | oneOfSchemas | — | — | — | — | — | — | ONE_OF_SCHEMAS ~ | — | — | — | — | — | idiom (allow-list) |
| PetFood.discriminatorProperty | discriminatorProperty | — | — | — | — | — | — | DISCRIMINATOR_PROPERTY ~ | — | — | — | — | — | idiom (allow-list) |
| PetFood.discriminatorMapping | discriminatorMapping | — | — | — | — | — | — | DISCRIMINATOR_MAPPING ~ | — | — | — | — | — | idiom (allow-list) |
| PetFood.value | value | — | — | — | — | — | — | — | value ✗ | Value ✗ | — | value(type?) ✗ | — | D45 Parameters `()` (majority) in swift. |
| PetFood.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PetFood.newPetFoodFromDryFood | newPetFoodFromDryFood | — | — | — | — | — | — | — | — | NewPetFoodFromDryFood(v) ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| PetFood.newPetFoodFromWetFood | newPetFoodFromWetFood | — | — | — | — | — | — | — | — | NewPetFoodFromWetFood(v) ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| **PetPassport** | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport | PetPassport |  |
| PetPassport.getThumbnailAsBytes | getThumbnailAsBytes | — | — | — | getThumbnailAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| PetPassport.getScansAsBytes | getScansAsBytes | — | — | — | getScansAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| PetPassport.getBiometricChipAsBytes | getBiometricChipAsBytes | — | — | — | getBiometricChipAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| PetPassport.setThumbnailFromBytes | setThumbnailFromBytes | — | — | — | setThumbnailFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| PetPassport.setScansFromBytes | setScansFromBytes | — | — | — | setScansFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| PetPassport.setBiometricChipFromBytes | setBiometricChipFromBytes | — | — | — | setBiometricChipFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| PetPassport.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PetPassport.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| PetPassport (5 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **PetTreatment** | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment | PetTreatment |  |
| PetTreatment.actualInstance | actualInstance | getActualInstance | actualInstance | ActualInstance | getActualInstance | actual_instance | — ✗ | getActualInstance | — ✗ | — ✗ | — ✗ | — ✗ | — ✗ | D41 |
| PetTreatment.getInstances | getInstances | getInstances ✗ | instances ✗ | — | — | — | — | — | — | — | — | — | — | D43 |
| PetTreatment.matchedInstances | matchedInstances | — | — | MatchedInstances ✗ | — | matched_instances ✗ | — | getMatchedInstances ✗ | — | — | — | — | — | D42 |
| PetTreatment.getActualInstances | getActualInstances | — | — | — | getActualInstances ✗ | — | — | — | — | — | — | — | — | D44 |
| PetTreatment.build | build | — | — | — | build(data) ~ | — | build(data) ~ | — | — | — | — | — | build(data) ~ | idiom (allow-list) |
| PetTreatment.anyofMergedDump | anyofMergedDump | — | — | — | — | anyof_merged_dump ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| PetTreatment.openapiAnyOf | openapiAnyOf | — | — | — | — | — | openapi_any_of ~ | — | — | — | — | — | openapi_any_of ~ | idiom (allow-list) |
| PetTreatment.anyOfSchemas | anyOfSchemas | — | — | — | — | — | — | ANY_OF_SCHEMAS ~ | — | — | — | — | — | idiom (allow-list) |
| PetTreatment.value | value | — | — | — | — | — | — | — | value ✗ | Value ✗ | — | value(type?) ✗ | — | D45 Parameters `()` (majority) in swift. |
| PetTreatment.values | values | — | — | — | — | — | — | — | values ✗ | — | — | values ✗ | — | D46 |
| PetTreatment.ofValues | ofValues | — | — | — | — | — | — | — | ofValues(values) ~ | — | — | — | — | idiom (allow-list) |
| PetTreatment.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PetTreatment.medication | medication | — | — | — | — | — | — | — | — | Medication ✗ | medication ✗ | — | — | Not part of the canonical surface: make it non-public in rust, go. |
| PetTreatment.surgery | surgery | — | — | — | — | — | — | — | — | Surgery ✗ | surgery ✗ | — | — | Not part of the canonical surface: make it non-public in rust, go. |
| PetTreatment.matchesAny | matchesAny | — | — | — | — | — | — | — | — | — | matches_any ~ | — | — | idiom (allow-list) |
| **PetWithOwner** | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner | PetWithOwner |  |
| PetWithOwner.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PetWithOwner.allPetWithOwnerStatusEnumValues | allPetWithOwnerStatusEnumValues | — | — | — | — | — | — | — | — | AllPetWithOwnerStatusEnumValues ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| PetWithOwner.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| PetWithOwner (14 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Photo** | Photo | Photo | Photo | Photo | Photo | Photo | Photo | Photo | Photo | Photo | Photo | Photo | Photo |  |
| Photo.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Photo.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Photo (4 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **PhotoMetadata** | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata | PhotoMetadata |  |
| PhotoMetadata.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PhotoMetadata.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| PhotoMetadata (4 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **PhotoMetadataLocation** | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation | PhotoMetadataLocation |  |
| PhotoMetadataLocation.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| PhotoMetadataLocation.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| PhotoMetadataLocation (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **RequiredNullable** | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable | RequiredNullable |  |
| RequiredNullable.note | note | note | note ✗ | Note | note | note | note | note | note | Note | note | note | note | D78 |
| RequiredNullable.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| RequiredNullable.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| **SetPetAvatarRequest** | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest | SetPetAvatarRequest |  |
| SetPetAvatarRequest.getDataAsBytes | getDataAsBytes | — | — | — | getDataAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| SetPetAvatarRequest.setDataFromBytes | setDataFromBytes | — | — | — | setDataFromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| SetPetAvatarRequest.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| SetPetAvatarRequest.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| SetPetAvatarRequest (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **SetPetAvatarThumbnailRequest** | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest ~ | SetPetAvatarThumbnailRequest | SetPetAvatarThumbnailRequest | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.actualInstance | actualInstance | getActualInstance ✗ | actualInstance ✗ | ActualInstance ✗ | getActualInstance ✗ | actual_instance ✗ | — | — | — | — | — | — | — | D41 |
| SetPetAvatarThumbnailRequest.getActualInstanceAsBytes | getActualInstanceAsBytes | — | — | — | getActualInstanceAsBytes ✗ | — | — | — | — | — | — | — | — | D47 |
| SetPetAvatarThumbnailRequest.getActualInstanceAsByteList | getActualInstanceAsByteList | — | — | — | getActualInstanceAsByteList ✗ | — | — | — | — | — | — | — | — | D50 |
| SetPetAvatarThumbnailRequest.fromBytes | fromBytes | — | — | — | fromBytes(raw) ✗ | — | — | — | — | — | — | — | — | D48 |
| SetPetAvatarThumbnailRequest.fromByteList | fromByteList | — | — | — | fromByteList(raw) ✗ | — | — | — | — | — | — | — | — | D49 |
| SetPetAvatarThumbnailRequest.build | build | — | — | — | build(data) ~ | — | build(data) ~ | — | — | — | — | — | build(data) ~ | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.oneOf0 | oneOf0 | — | — | — | — | one_of_0 ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.oneOf1 | oneOf1 | — | — | — | — | one_of_1 ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.openapiOneOf | openapiOneOf | — | — | — | — | — | openapi_one_of ~ | — | — | — | — | — | openapi_one_of ~ | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.serializeOneof | serializeOneof | — | — | — | — | — | serialize_oneof(value) ~ | — | — | — | — | — | — | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.value | value | — | — | — | — | — | — | — | value ✗ | Value ✗ | — | value(type?) ✗ | — | D45 Parameters `()` (majority) in swift. |
| SetPetAvatarThumbnailRequest.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| SetPetAvatarThumbnailRequest.newSetPetAvatarThumbnailRequestFromByteSliceSlice | newSetPetAvatarThumbnailRequestFromByteSliceSlice | — | — | — | — | — | — | — | — | NewSetPetAvatarThumbnailRequestFromByteSliceSlice(v) ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| SetPetAvatarThumbnailRequest.newSetPetAvatarThumbnailRequestFromByteSlice | newSetPetAvatarThumbnailRequestFromByteSlice | — | — | — | — | — | — | — | — | NewSetPetAvatarThumbnailRequestFromByteSlice(v) ✗ | — | — | — | Not part of the canonical surface: make it non-public in go. |
| SetPetAvatarThumbnailRequest.serialize | serialize | — | — | — | — | — | — | — | — | — | — | — | serialize(value) ✗ | Not part of the canonical surface: make it non-public in elixir. |
| **StockItem** | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem | StockItem |  |
| StockItem.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| StockItem.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| StockItem (3 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **StrictTag** | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag | StrictTag |  |
| StrictTag.setUnknownProperty | setUnknownProperty | setUnknownProperty(key, value) ✗ | — | — | — | — | — | — | — | — | — | — | — | D52 |
| StrictTag.parseStrict | parseStrict | — | parseStrict(raw) ✗ | — | — | — | — | — | — | — | — | — | — | D52 |
| StrictTag.unknownProperties | unknownProperties | — | — | UnknownProperties ✗ | — | — | — | — | — | — | — | — | — | D52 |
| StrictTag.assertNoUnknownProperties | assertNoUnknownProperties | — | — | — | assertNoUnknownProperties(data) ✗ | — | — | — | — | — | — | — | — | D52 |
| StrictTag.unevaluatedPropertiesFalse | unevaluatedPropertiesFalse | — | — | — | — | — | UNEVALUATED_PROPERTIES_FALSE ✗ | — | — | — | — | — | — | D52 |
| StrictTag.fromJsonStrict | fromJsonStrict | — | — | — | — | — | — | fromJsonStrict(raw) ✗ | — | — | — | — | — | D52 |
| StrictTag.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| StrictTag.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| StrictTag.fromMapStrict | fromMapStrict | — | — | — | — | — | — | — | — | — | — | — | from_map_strict(data) ✗ | D52 |
| StrictTag (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Surgery** | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery | Surgery |  |
| Surgery.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Surgery.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Surgery (3 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Tag** | Tag | Tag | Tag | Tag | Tag | Tag | Tag | Tag | Tag | Tag | Tag | Tag | Tag |  |
| Tag.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Tag.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Tag (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **TreatmentRecord** | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord | TreatmentRecord |  |
| TreatmentRecord.actualInstance | actualInstance | getActualInstance | actualInstance | ActualInstance | getActualInstance | actual_instance | — ✗ | getActualInstance | — ✗ | — ✗ | — ✗ | — ✗ | — ✗ | D41 |
| TreatmentRecord.getInstances | getInstances | getInstances ✗ | instances ✗ | — | — | — | — | — | — | — | — | — | — | D43 |
| TreatmentRecord.matchedInstances | matchedInstances | — | — | MatchedInstances ✗ | — | matched_instances ✗ | — | getMatchedInstances ✗ | — | — | — | — | — | D42 |
| TreatmentRecord.getActualInstances | getActualInstances | — | — | — | getActualInstances ✗ | — | — | — | — | — | — | — | — | D44 |
| TreatmentRecord.build | build | — | — | — | build(data) ~ | — | build(data) ~ | — | — | — | — | — | build(data) ~ | idiom (allow-list) |
| TreatmentRecord.anyofMergedDump | anyofMergedDump | — | — | — | — | anyof_merged_dump ~ | — | — | — | — | — | — | — | idiom (allow-list) |
| TreatmentRecord.openapiAnyOf | openapiAnyOf | — | — | — | — | — | openapi_any_of ~ | — | — | — | — | — | openapi_any_of ~ | idiom (allow-list) |
| TreatmentRecord.anyOfSchemas | anyOfSchemas | — | — | — | — | — | — | ANY_OF_SCHEMAS ~ | — | — | — | — | — | idiom (allow-list) |
| TreatmentRecord.value | value | — | — | — | — | — | — | — | value ✗ | Value ✗ | — | value(type?) ✗ | — | D45 Parameters `()` (majority) in swift. |
| TreatmentRecord.values | values | — | — | — | — | — | — | — | values ✗ | — | — | values ✗ | — | D46 |
| TreatmentRecord.ofValues | ofValues | — | — | — | — | — | — | — | ofValues(values) ~ | — | — | — | — | idiom (allow-list) |
| TreatmentRecord.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| TreatmentRecord.medication | medication | — | — | — | — | — | — | — | — | Medication ✗ | medication ✗ | — | — | Not part of the canonical surface: make it non-public in rust, go. |
| TreatmentRecord.surgery | surgery | — | — | — | — | — | — | — | — | Surgery ✗ | surgery ✗ | — | — | Not part of the canonical surface: make it non-public in rust, go. |
| TreatmentRecord.matchesAny | matchesAny | — | — | — | — | — | — | — | — | — | matches_any ~ | — | — | idiom (allow-list) |
| **TreeNode** | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode | TreeNode |  |
| TreeNode.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| TreeNode.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| TreeNode (2 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **WetFood** | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood | WetFood |  |
| WetFood.foodType | foodType | foodType | foodType | FoodType | foodType | food_type | food_type | foodType | foodType | FoodType | food_type | foodType | food_type ✗ | D80 |
| WetFood.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| WetFood.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| WetFood (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Error** | Error | — ~ | Error | Error | Error | Error | Error | Error | Error | — ✗ | Error | — ~ | Error | D01 |
| Error.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Error (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |
| **Result** | Result | — ✗ | Result | Result | Result | Result | Result | Result | Result | Result | — ~ | — ~ | Result | D03 |
| Result.fromJson | fromJson | — | — | — | — | — | — | — | fromJson(json) ~ | — | — | — | — | idiom (allow-list) |
| Result.equal | equal | — | — | — | — | — | — | — | — | Equal(other) ~ | — | — | — | idiom (allow-list) |
| Result (1 other properties) | identical |  |  |  |  |  |  |  |  |  |  |  |  |  |

## Enums

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **GetMultiServerPetInfoServerRegionalRegion** | GetMultiServerPetInfoServerRegionalRegion {ap, eu, us} | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion | GetMultiServerPetInfoServerRegionalRegion |  |
| **GetMultiServerPetInfoServerMirrorRegion** | GetMultiServerPetInfoServerMirrorRegion {eu, us} | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion | GetMultiServerPetInfoServerMirrorRegion |  |
| **GetMultiServerPetInfoServerChannel** | GetMultiServerPetInfoServerChannel {ab, its} | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel | GetMultiServerPetInfoServerChannel |  |
| **GetStagingPetInfoServerEnvironment** | GetStagingPetInfoServerEnvironment {sandbox, staging} | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment | GetStagingPetInfoServerEnvironment |  |
| **GetStagingPetInfoServerVersion** | GetStagingPetInfoServerVersion {v2, v3} | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion | GetStagingPetInfoServerVersion |  |
| **ApiKeyLocation** | ApiKeyLocation {cookie, header, query} | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation | ApiKeyLocation |  |
| **ClientAuthMethod** | ClientAuthMethod {basic, body} | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod | ClientAuthMethod |  |
| **Availability** | Availability {ab, available, eq, oclock, onhold, sold, x} | Availability | Availability | Availability | Availability | Availability | Availability | Availability | Availability | Availability | Availability | Availability | Availability |  |
| **Color** | Color {blue, green, red} | Color | Color | Color | Color | Color | Color | Color | Color | Color | Color | Color | Color |  |
| **DefaultsModeEnum** | DefaultsModeEnum {high, low, medium} | DefaultsModeEnum | DefaultsModeEnum | DefaultsModeEnum | DefaultsModeEnum | DefaultsModeEnum | — ✗ | DefaultsModeEnum | DefaultsModeEnum | DefaultsModeEnum | DefaultsModeEnum | DefaultsModeEnum | — ✗ | D72 |
| **OrderStatusEnum** | OrderStatusEnum {approved, delivered, placed} | OrderStatusEnum | OrderStatusEnum | OrderStatusEnum | OrderStatusEnum | OrderStatusEnum | — ✗ | OrderStatusEnum | OrderStatusEnum | OrderStatusEnum | OrderStatusEnum | OrderStatusEnum | — ✗ | D73 |
| **PetStatusEnum** | PetStatusEnum {available, pending, sold} | PetStatusEnum | PetStatusEnum | PetStatusEnum | PetStatusEnum | PetStatusEnum | — ✗ | PetStatusEnum | PetStatusEnum | PetStatusEnum | PetStatusEnum | PetStatusEnum | — ✗ | D73 |
| **PetWithOwnerStatusEnum** | PetWithOwnerStatusEnum {available, pending, sold} | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | — ✗ | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | PetWithOwnerStatusEnum | — ✗ | D73 |
| **Priority** | Priority {number1, number2, number3} | Priority | Priority | Priority | Priority | Priority | Priority | Priority | Priority | Priority | Priority | Priority | Priority |  |
| **Separator** | Separator {ab, ab2, space1, value00400040} | Separator | Separator | Separator | Separator | Separator | Separator | Separator | Separator | Separator | Separator | Separator | Separator |  |
| **Swatch** | Swatch {blue, green, red} | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch | Swatch |  |
| **ConfigurationError** | ConfigurationError {invalidargument, invalidcacertificate, invalidproxy, invalidservervariable, invalidstate, invalidtimeout} | — | — | — | — | — | — | — | — | — | ConfigurationError ~ | ConfigurationError ✗ | — | D76 |
| **OAuth2AuthorizationCodeError** | OAuth2AuthorizationCodeError {codenotexchanged, emptycode} | — | — | — | — | — | — | — | — | — | OAuth2AuthorizationCodeError ~ | OAuth2AuthorizationCodeError ~ | — | idiom (allow-list) |

## Authenticators and constructor arguments

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **AdminBasicAuthenticator** | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator | AdminBasicAuthenticator |  |
| AdminBasicAuthenticator ctor | new(host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | — | (host, username, password) | — | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) |  |
| AdminBasicAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| AdminBasicAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **ApiKeyAuthenticator** | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator | ApiKeyAuthenticator |  |
| ApiKeyAuthenticator ctor | new(host, keyParamName, apiKey, location) | (host, keyParamName, apiKey, location) | (host, keyParamName, apiKey, location) | (host, keyParamName, apiKey, location) | (host, keyParamName, apiKey, location) | (host, key_param_name, api_key, location) | (host, key_param_name, api_key, location) | (host, keyParamName, apiKey, location) | (host, keyParamName, apiKey, location) | (host, keyParamName, apiKey, location) | (host, key_param_name, api_key, location) | (host, keyParamName, apiKey, location) | (host, key_param_name, api_key, location) |  |
| ApiKeyAuthenticator.host | host | getHost | getHost | GetHost | getHost | host | host | getHost | host | Host | host | host | host |  |
| ApiKeyAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| ApiKeyAuthenticator.getQueryParams | getQueryParams | getQueryParams | getQueryParams | GetQueryParams | getQueryParams | get_query_params | query_params | getQueryParams | queryParams | QueryParams | query_params | queryParams | query_params |  |
| ApiKeyAuthenticator.getCookieParams | getCookieParams | getCookieParams | getCookieParams | GetCookieParams | getCookieParams | get_cookie_params | cookie_params | getCookieParams | cookieParams | CookieParams | cookie_params | cookieParams | cookie_params |  |
| ApiKeyAuthenticator.keyParamName | keyParamName | — | — | — | — | key_param_name ✗ | — | — | — | — | — | — | key_param_name ~ | Not part of the canonical surface: make it non-public in python. |
| ApiKeyAuthenticator.apiKey | apiKey | — | — | — | — | api_key ✗ | — | — | — | — | — | — | api_key ~ | Not part of the canonical surface: make it non-public in python. |
| ApiKeyAuthenticator.location | location | — | — | — | — | location ✗ | — | — | — | — | — | — | location ~ | Not part of the canonical surface: make it non-public in python. |
| ApiKeyAuthenticator.errInvalidApiKey | errInvalidApiKey | — | — | — | — | — | — | — | — | ErrInvalidApiKey ~ | — | — | — | idiom (allow-list) |
| **ApiKeyHeaderAuthenticator** | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator | ApiKeyHeaderAuthenticator |  |
| ApiKeyHeaderAuthenticator ctor | new(host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, api_key) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, apiKey) | (host, api_key) |  |
| ApiKeyHeaderAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| ApiKeyHeaderAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **ApiKeyQueryAuthenticator** | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator | ApiKeyQueryAuthenticator |  |
| ApiKeyQueryAuthenticator ctor | new(host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, api_key) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, apiKey) | (host, api_key) |  |
| ApiKeyQueryAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| ApiKeyQueryAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **Authenticator** | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator | Authenticator |  |
| Authenticator.getHost | getHost | getHost | getHost | GetHost | getHost | get_host | host | getHost | host | Host | host | host | host |  |
| Authenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| Authenticator.getQueryParams | getQueryParams | getQueryParams | getQueryParams | GetQueryParams | getQueryParams | get_query_params | query_params | getQueryParams | queryParams | QueryParams | query_params | queryParams | query_params |  |
| Authenticator.getCookieParams | getCookieParams | getCookieParams | getCookieParams | GetCookieParams | getCookieParams | get_cookie_params | cookie_params | getCookieParams | cookieParams | CookieParams | cookie_params | cookieParams | cookie_params |  |
| Authenticator.isNoAuth | isNoAuth | — | — | — | — | — | — | isNoAuth(auth) ✗ | — ✗ | — | — | — | — | D74 |
| Authenticator.noAuth | noAuth | — | — | — | — | — | — | — | noAuth | — | — | — | — |  |
| Authenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| Authenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **BaseAuthenticator** | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | BaseAuthenticator | — ~ | BaseAuthenticator | BaseAuthenticator | idiom (allow-list) |
| BaseAuthenticator ctor | — | — | — | — | — | — | — | — | — | — | — | () | — |  |
| BaseAuthenticator.getHost | getHost | getHost | getHost | GetHost | getHost | get_host | host | getHost | host | — ~ | — | host | — ~ | idiom (allow-list) |
| BaseAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeadersAsync | — ~ | — | authHeaders | — ~ | idiom (allow-list) |
| BaseAuthenticator.getQueryParams | getQueryParams | getQueryParams | getQueryParams | GetQueryParams | getQueryParams | get_query_params | query_params | getQueryParams | queryParams | QueryParams | — | queryParams | — ~ | idiom (allow-list) |
| BaseAuthenticator.getCookieParams | getCookieParams | getCookieParams | getCookieParams | GetCookieParams | getCookieParams | get_cookie_params | cookie_params | getCookieParams | cookieParams | CookieParams | — | cookieParams | — ~ | idiom (allow-list) |
| BaseAuthenticator.customMirror | customMirror | — | — | — | — | — | — | — | — | — | — | customMirror ~ | — | idiom (allow-list) |
| BaseAuthenticator.using | using | — | — | — | — | — | — | — | — | — | — | — | __using__(opts) ~ | idiom (allow-list) |
| **BasicAuthenticator** | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator | BasicAuthenticator |  |
| BasicAuthenticator ctor | new(host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) |  |
| BasicAuthenticator.host | host | getHost | getHost | GetHost | getHost | host | host | getHost | host | Host | host | host | host |  |
| BasicAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| BasicAuthenticator.username | username | — | — | — | — | username ✗ | — | — | — | — | — | — | username ~ | Not part of the canonical surface: make it non-public in python. |
| BasicAuthenticator.password | password | — | — | — | — | password ✗ | — | — | — | — | — | — | password ~ | Not part of the canonical surface: make it non-public in python. |
| BasicAuthenticator.errBasicAuthUsernameControlChar | errBasicAuthUsernameControlChar | — | — | — | — | — | — | — | — | ErrBasicAuthUsernameControlChar ~ | — | — | — | idiom (allow-list) |
| BasicAuthenticator.errBasicAuthUsernameColon | errBasicAuthUsernameColon | — | — | — | — | — | — | — | — | ErrBasicAuthUsernameColon ~ | — | — | — | idiom (allow-list) |
| BasicAuthenticator.errBasicAuthPasswordControlChar | errBasicAuthPasswordControlChar | — | — | — | — | — | — | — | — | ErrBasicAuthPasswordControlChar ~ | — | — | — | idiom (allow-list) |
| **BearerAuthenticator** | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator | BearerAuthenticator |  |
| BearerAuthenticator ctor | new(host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) |  |
| BearerAuthenticator.host | host | getHost | getHost | GetHost | getHost | host | host | getHost | host | Host | host | host | host |  |
| BearerAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| BearerAuthenticator.token | token | — | — | — | — | token ✗ | — | — | — | — | — | — | token ~ | Not part of the canonical surface: make it non-public in python. |
| BearerAuthenticator.errInvalidBearerToken | errInvalidBearerToken | — | — | — | — | — | — | — | — | ErrInvalidBearerToken ~ | — | — | — | idiom (allow-list) |
| **HttpAwareAuthenticator** | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator | HttpAwareAuthenticator |  |
| HttpAwareAuthenticator.getHost | getHost | getHost | getHost | GetHost | getHost | get_host | — ~ | getHost | host | Host | — | host | — | idiom (allow-list) |
| HttpAwareAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | — ~ | getAuthHeaders | authHeaders | AuthHeaders | — | authHeaders | — | idiom (allow-list) |
| HttpAwareAuthenticator.getQueryParams | getQueryParams | getQueryParams | getQueryParams | GetQueryParams | getQueryParams | get_query_params | — ~ | getQueryParams | queryParams | QueryParams | — | queryParams | — | idiom (allow-list) |
| HttpAwareAuthenticator.getCookieParams | getCookieParams | getCookieParams | getCookieParams | GetCookieParams | getCookieParams | get_cookie_params | — ~ | getCookieParams | cookieParams | CookieParams | — | cookieParams | — | idiom (allow-list) |
| HttpAwareAuthenticator.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | setApiClient(apiClient) | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(client) ✗ | setApiClient(apiClient) | setApiClient(client) ✗ | SetApiClient(client) ✗ | set_api_client(client) ✗ | setApiClient(client) ✗ | set_api_client(arg1) ✗ | D35 |
| HttpAwareAuthenticator.isHttpAwareAuthenticator | isHttpAwareAuthenticator | — | — | — | — | — | — | isHttpAwareAuthenticator(auth) ✗ | — | — | — | — | — | D75 |
| **InternalApiKeyAuthenticator** | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator | InternalApiKeyAuthenticator |  |
| InternalApiKeyAuthenticator ctor | new(host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, api_key) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, apiKey) | (host, api_key) |  |
| InternalApiKeyAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| InternalApiKeyAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **PetStoreBasicAuthenticator** | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator | PetStoreBasicAuthenticator |  |
| PetStoreBasicAuthenticator ctor | new(host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | — | (host, username, password) | — | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) | (host, username, password) |  |
| PetStoreBasicAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| PetStoreBasicAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **PetStoreBearerAuthenticator** | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator | PetStoreBearerAuthenticator |  |
| PetStoreBearerAuthenticator ctor | new(host, token) | (host, token) | (host, token) | (host, token) | — | (host, token) | — | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) |  |
| PetStoreBearerAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| PetStoreBearerAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **ServiceTokenAuthenticator** | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator | ServiceTokenAuthenticator |  |
| ServiceTokenAuthenticator ctor | new(host, token) | (host, token) | (host, token) | (host, token) | — | (host, token) | — | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) | (host, token) |  |
| ServiceTokenAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| ServiceTokenAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **SessionCookieAuthenticator** | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator | SessionCookieAuthenticator |  |
| SessionCookieAuthenticator ctor | new(host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, api_key) | (host, apiKey) | (host, apiKey) | (host, apiKey) | (host, api_key) | (host, apiKey) | (host, api_key) |  |
| SessionCookieAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| SessionCookieAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **BrowserAuthImplicitAuthenticator** | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator | BrowserAuthImplicitAuthenticator |  |
| BrowserAuthImplicitAuthenticator ctor | new(host, clientId) | (host, clientId) | (host, clientId) | (host, clientId) | (host, clientId) | (host, client_id) | (host, client_id) | (host, clientId) | (host, clientId) | (host, clientId) | (host, client_id) | (host, clientId) | (host, client_id) |  |
| BrowserAuthImplicitAuthenticator.buildAuthorizationUrl | buildAuthorizationUrl(state) | buildAuthorizationUrl(state) | buildAuthorizationUrl(state?) | BuildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | build_authorization_url(state?) | build_authorization_url(state?) | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | BuildAuthorizationURL(state) ✗ | — | buildAuthorizationURL(state?) ✗ | — | D82 |
| BrowserAuthImplicitAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| BrowserAuthImplicitAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **LegacyAuthPasswordAuthenticator** | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator | LegacyAuthPasswordAuthenticator |  |
| LegacyAuthPasswordAuthenticator ctor | new(host, clientId, clientSecret, username, password) | (host, clientId, clientSecret, username, password) | (host, clientId, clientSecret, username, password) | (host, clientId, clientSecret, username, password) | (host, clientId, clientSecret, username, password) | (host, client_id, client_secret, username, password) | (host, client_id, client_secret, username, password) | (host, clientId, clientSecret, username, password) | (host, clientId, clientSecret, username, password) | (host, clientId, clientSecret, username, password) | (host, client_id, client_secret, username, password) | (host, clientId, clientSecret, username, password) | (host, client_id, client_secret, username, password) |  |
| LegacyAuthPasswordAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| LegacyAuthPasswordAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **MachineAuthClientCredentialsAuthenticator** | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator | MachineAuthClientCredentialsAuthenticator |  |
| MachineAuthClientCredentialsAuthenticator ctor | new(host, clientId, clientSecret) | (host, clientId, clientSecret) | (host, clientId, clientSecret) | (host, clientId, clientSecret) | (host, clientId, clientSecret) | (host, client_id, client_secret) | (host, client_id, client_secret) | (host, clientId, clientSecret) | (host, clientId, clientSecret) | (host, clientId, clientSecret) | (host, client_id, client_secret) | (host, clientId, clientSecret) | (host, client_id, client_secret) |  |
| MachineAuthClientCredentialsAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| MachineAuthClientCredentialsAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **OAuth2AuthorizationCodeAuthenticator** | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator | OAuth2AuthorizationCodeAuthenticator |  |
| OAuth2AuthorizationCodeAuthenticator ctor | new(host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl) | (host, clientId, clientSecret, authorizationUrl, tokenUrl, refreshUrl?, redirectUri, scopes) ✗ | (host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl?) | (host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl?) | (host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl?) | (host, client_id, client_secret, authorization_url, token_url, redirect_uri, scopes, refresh_url?) | (host, client_id, client_secret, authorization_url, token_url, redirect_uri, scopes, refresh_url?, token_manager?) ✗ | (host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl?, tokenManager?) ✗ | (host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes?, refreshUrl?) ✗ | (host, clientID, clientSecret, authorizationURL, tokenURL, redirectURI, scopes, refreshURL) | (host, client_id, client_secret, authorization_url, token_url, redirect_uri, scopes, refresh_url) | (host, clientID, clientSecret, authorizationURL, tokenURL, redirectURI, scopes?, refreshURL?) ✗ | (host, client_id, client_secret, authorization_url, token_url, redirect_uri, scopes, opts?) | D32 D34 |
| OAuth2AuthorizationCodeAuthenticator.getHost | getHost | getHost | getHost | GetHost | getHost | get_host | host | getHost | host | Host | host | host | host |  |
| OAuth2AuthorizationCodeAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| OAuth2AuthorizationCodeAuthenticator.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | setApiClient(apiClient) | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(client) ✗ | setApiClient(apiClient) | setApiClient(client) ✗ | SetApiClient(client) ✗ | set_api_client(client) ✗ | setApiClient(client) ✗ | set_api_client(client) ✗ | D35 |
| OAuth2AuthorizationCodeAuthenticator.buildAuthorizationUrl | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state) ✗ | buildAuthorizationUrl(state?) | BuildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | build_authorization_url(state?) | build_authorization_url(state?) | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | BuildAuthorizationURL(state) ✗ | build_authorization_url(state) | buildAuthorizationURL(state?) ✗ | build_authorization_url(state?) | Optionality `(state?)` (majority) in java. D82 |
| OAuth2AuthorizationCodeAuthenticator.exchangeCode | exchangeCode(code) | exchangeCode(code) | exchangeCode(code) | ExchangeCodeAsync(code) | exchangeCode(code) | exchange_code(code) | exchange_code(code) | exchangeCode(code) | exchangeCode(code) | ExchangeCode(code) | exchange_code(code) | exchangeCode(code) | exchange_code(code) |  |
| OAuth2AuthorizationCodeAuthenticator.tokenManager | tokenManager | — | — | — | — | — | token_manager ✗ | — | — | — | — | — | token_manager ~ | D40 |
| OAuth2AuthorizationCodeAuthenticator.errAuthCodeNotExchanged | errAuthCodeNotExchanged | — | — | — | — | — | — | — | — | ErrAuthCodeNotExchanged ~ | — | — | — | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.errAuthCodeEmpty | errAuthCodeEmpty | — | — | — | — | — | — | — | — | ErrAuthCodeEmpty ~ | — | — | — | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.authHeadersOrError | authHeadersOrError | — | — | — | — | — | — | — | — | AuthHeadersOrError ~ | — | — | — | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.authHeadersOrThrow | authHeadersOrThrow | — | — | — | — | — | — | — | — | — | — | authHeadersOrThrow ✗ | — | Not part of the canonical surface: make it non-public in swift. |
| OAuth2AuthorizationCodeAuthenticator.clientId | clientId | — | — | — | — | — | — | — | — | — | — | — | client_id ~ | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.clientSecret | clientSecret | — | — | — | — | — | — | — | — | — | — | — | client_secret ~ | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.authorizationUrl | authorizationUrl | — | — | — | — | — | — | — | — | — | — | — | authorization_url ~ | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.tokenUrl | tokenUrl | — | — | — | — | — | — | — | — | — | — | — | token_url ~ | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.refreshUrl | refreshUrl | — | — | — | — | — | — | — | — | — | — | — | refresh_url ~ | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.redirectUri | redirectUri | — | — | — | — | — | — | — | — | — | — | — | redirect_uri ~ | idiom (allow-list) |
| OAuth2AuthorizationCodeAuthenticator.scopes | scopes | — | — | — | — | — | — | — | — | — | — | — | scopes ~ | idiom (allow-list) |
| **OAuth2ClientCredentialsAuthenticator** | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator | OAuth2ClientCredentialsAuthenticator |  |
| OAuth2ClientCredentialsAuthenticator ctor | new(host, clientId, clientSecret, tokenUrl, scopes, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, scopes, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, scopes, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, scopes, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, scopes, clientAuthMethod?) | (host, client_id, client_secret, token_url, scopes?, client_auth_method?) ✗ | (host, client_id, client_secret, token_url, scopes, client_auth_method?) | (host, clientId, clientSecret, tokenUrl, scopes, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, scopes?, clientAuthMethod?) ✗ | (host, clientID, clientSecret, tokenURL, scopes) | (host, client_id, client_secret, token_url, scopes) | (host, clientID, clientSecret, tokenURL, scopes?, clientAuthMethod?) ✗ | (host, client_id, client_secret, token_url, scopes, opts?) | D34 |
| OAuth2ClientCredentialsAuthenticator.host | host | getHost | getHost | GetHost | getHost | host | host | getHost | host | Host | host | host | host |  |
| OAuth2ClientCredentialsAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| OAuth2ClientCredentialsAuthenticator.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | setApiClient(apiClient) | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(client) ✗ | setApiClient(apiClient) | setApiClient(client) ✗ | SetApiClient(client) ✗ | set_api_client(client) ✗ | setApiClient(client) ✗ | set_api_client(client) ✗ | D35 |
| OAuth2ClientCredentialsAuthenticator.clientId | clientId | — | — | — | — | client_id ✗ | — | — | — | — | — | — | client_id ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2ClientCredentialsAuthenticator.clientSecret | clientSecret | — | — | — | — | client_secret ✗ | — | — | — | — | — | — | client_secret ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2ClientCredentialsAuthenticator.tokenUrl | tokenUrl | — | — | — | — | token_url ✗ | — | — | — | — | — | — | token_url ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2ClientCredentialsAuthenticator.scopes | scopes | — | — | — | — | scopes ✗ | — | — | — | — | — | — | scopes ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2ClientCredentialsAuthenticator.clientAuthMethod | clientAuthMethod | — | — | — | — | client_auth_method ✗ | — | — | — | — | — | — | client_auth_method ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2ClientCredentialsAuthenticator.authHeadersOrError | authHeadersOrError | — | — | — | — | — | — | — | — | AuthHeadersOrError ~ | — | — | — | idiom (allow-list) |
| OAuth2ClientCredentialsAuthenticator.withClientAuthMethod | withClientAuthMethod | — | — | — | — | — | — | — | — | WithClientAuthMethod(method) ~ | with_client_auth_method(method) ~ | — | — | idiom (allow-list) |
| OAuth2ClientCredentialsAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| OAuth2ClientCredentialsAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| OAuth2ClientCredentialsAuthenticator.tokenManager | tokenManager | — | — | — | — | — | — | — | — | — | — | — | token_manager ~ | idiom (allow-list) |
| **OAuth2ImplicitAuthenticator** | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator | OAuth2ImplicitAuthenticator |  |
| OAuth2ImplicitAuthenticator ctor | new(host, clientId, authorizationUrl, scopes) | (host, clientId, authorizationUrl, scopes) | (host, clientId, authorizationUrl, scopes) | (host, clientId, authorizationUrl, scopes) | (host, clientId, authorizationUrl, scopes) | (host, client_id, authorization_url, scopes) | (host, client_id, authorization_url, scopes) | (host, clientId, authorizationUrl, scopes) | (host, clientId, authorizationUrl, scopes?) ✗ | (host, clientID, authorizationURL, scopes) | (host, client_id, authorization_url, scopes) | (host, clientID, authorizationURL, scopes?) ✗ | (host, client_id, authorization_url, scopes) | D34 |
| OAuth2ImplicitAuthenticator.getHost | getHost | getHost | getHost | GetHost | getHost | get_host | host | getHost | host | Host | host | host | host |  |
| OAuth2ImplicitAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| OAuth2ImplicitAuthenticator.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | setApiClient(apiClient) | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(_client) ✗ | setApiClient(_apiClient) | setApiClient(client) ✗ | SetApiClient(_) ✗ | set_api_client(_client) ✗ | setApiClient(client) ✗ | set_api_client(client) ✗ | D35 |
| OAuth2ImplicitAuthenticator.buildAuthorizationUrl | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state) ✗ | buildAuthorizationUrl(state?) | BuildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | build_authorization_url(state?) | build_authorization_url(state?) | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | BuildAuthorizationURL(state) ✗ | build_authorization_url(state) | buildAuthorizationURL(state?) ✗ | build_authorization_url(state?) | Optionality `(state?)` (majority) in java. D82 |
| OAuth2ImplicitAuthenticator.setAccessToken | setAccessToken(token) | setAccessToken(token) | setAccessToken(token) | SetAccessToken(token) | setAccessToken(token) | set_access_token(token) | set_access_token(token) | setAccessToken(token) | setAccessToken(token) | SetAccessToken(token) | set_access_token(token) | setAccessToken(token) | — |  |
| OAuth2ImplicitAuthenticator.errInvalidAccessToken | errInvalidAccessToken | — | — | — | — | — | — | — | — | ErrInvalidAccessToken ~ | — | — | — | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.errAccessTokenNotSet | errAccessTokenNotSet | — | — | — | — | — | — | — | — | ErrAccessTokenNotSet ~ | — | — | — | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.authHeadersOrError | authHeadersOrError | — | — | — | — | — | — | — | — | AuthHeadersOrError ~ | — | — | — | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.clientId | clientId | — | — | — | — | — | — | — | — | — | — | — | client_id ~ | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.authorizationUrl | authorizationUrl | — | — | — | — | — | — | — | — | — | — | — | authorization_url ~ | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.scopes | scopes | — | — | — | — | — | — | — | — | — | — | — | scopes ~ | idiom (allow-list) |
| OAuth2ImplicitAuthenticator.accessToken | accessToken | — | — | — | — | — | — | — | — | — | — | — | access_token ~ | idiom (allow-list) |
| **OAuth2PasswordAuthenticator** | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator | OAuth2PasswordAuthenticator |  |
| OAuth2PasswordAuthenticator ctor | new(host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl, clientAuthMethod) | (host, clientId, clientSecret, tokenUrl, refreshUrl?, username, password, scopes, clientAuthMethod?) ✗ | (host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl?, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl?, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl?, clientAuthMethod?) | (host, client_id, client_secret, token_url, username, password, scopes?, refresh_url?, client_auth_method?) ✗ | (host, client_id, client_secret, token_url, username, password, scopes, refresh_url?, client_auth_method?) | (host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl?, clientAuthMethod?) | (host, clientId, clientSecret, tokenUrl, username, password, scopes?, refreshUrl?, clientAuthMethod?) ✗ | (host, clientID, clientSecret, tokenURL, username, password, scopes, refreshURL) ~ | (host, client_id, client_secret, token_url, username, password, scopes, refresh_url) ~ | (host, clientID, clientSecret, tokenURL, username, password, scopes?, refreshURL?, clientAuthMethod?) ✗ | (host, client_id, client_secret, token_url, username, password, scopes, opts?) | D33 D34 |
| OAuth2PasswordAuthenticator.host | host | getHost | getHost | GetHost | getHost | host | host | getHost | host | Host | host | host | host |  |
| OAuth2PasswordAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| OAuth2PasswordAuthenticator.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | setApiClient(apiClient) | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(client) ✗ | setApiClient(apiClient) | setApiClient(client) ✗ | SetApiClient(client) ✗ | set_api_client(client) ✗ | setApiClient(client) ✗ | set_api_client(client) ✗ | D35 |
| OAuth2PasswordAuthenticator.clientId | clientId | — | — | — | — | client_id ✗ | — | — | — | — | — | — | client_id ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.clientSecret | clientSecret | — | — | — | — | client_secret ✗ | — | — | — | — | — | — | client_secret ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.tokenUrl | tokenUrl | — | — | — | — | token_url ✗ | — | — | — | — | — | — | token_url ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.username | username | — | — | — | — | username ✗ | — | — | — | — | — | — | username ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.password | password | — | — | — | — | password ✗ | — | — | — | — | — | — | password ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.scopes | scopes | — | — | — | — | scopes ✗ | — | — | — | — | — | — | scopes ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.refreshUrl | refreshUrl | — | — | — | — | refresh_url ✗ | — | — | — | — | — | — | refresh_url ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.clientAuthMethod | clientAuthMethod | — | — | — | — | client_auth_method ✗ | — | — | — | — | — | — | client_auth_method ~ | Not part of the canonical surface: make it non-public in python. |
| OAuth2PasswordAuthenticator.authHeadersOrError | authHeadersOrError | — | — | — | — | — | — | — | — | AuthHeadersOrError ~ | — | — | — | idiom (allow-list) |
| OAuth2PasswordAuthenticator.withClientAuthMethod | withClientAuthMethod | — | — | — | — | — | — | — | — | WithClientAuthMethod(method) ~ | with_client_auth_method(method) ~ | — | — | idiom (allow-list) |
| OAuth2PasswordAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| OAuth2PasswordAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| OAuth2PasswordAuthenticator.tokenManager | tokenManager | — | — | — | — | — | — | — | — | — | — | — | token_manager ~ | idiom (allow-list) |
| **OpenIdConnectAuthenticator** | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator | OpenIdConnectAuthenticator |  |
| OpenIdConnectAuthenticator ctor | new(host, openidConnectUrl, clientId, clientSecret, redirectUri, scopes) | (host, openIdConnectUrl, clientId, clientSecret, redirectUri, scopes) | (host, openIdConnectUrl, clientId, clientSecret, redirectUri, scopes) | (host, openIdConnectUrl, clientId, clientSecret, redirectUri, scopes) | (host, openIdConnectUrl, clientId, clientSecret, redirectUri, scopes) | (host, openid_connect_url, client_id, client_secret, redirect_uri, scopes) | (host, openid_connect_url, client_id, client_secret, redirect_uri, scopes) | (host, openIdConnectUrl, clientId, clientSecret, redirectUri, scopes) | (host, openIdConnectUrl, clientId, clientSecret, redirectUri, scopes?) ✗ | (host, openIDConnectURL, clientID, clientSecret, redirectURI, scopes) | (host, openid_connect_url, client_id, client_secret, redirect_uri, scopes) | (host, openIDConnectURL, clientID, clientSecret, redirectURI, scopes?) ✗ | (host, openid_connect_url, client_id, client_secret, redirect_uri, scopes) | D34 |
| OpenIdConnectAuthenticator.getHost | getHost | getHost | getHost | GetHost | getHost | get_host | host | getHost | host | Host | host | host | host |  |
| OpenIdConnectAuthenticator.getAuthHeaders | getAuthHeaders | getAuthHeaders | getAuthHeaders | GetAuthHeaders | getAuthHeaders | get_auth_headers | auth_headers | getAuthHeaders | authHeaders | AuthHeaders | auth_headers | authHeaders | auth_headers |  |
| OpenIdConnectAuthenticator.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | setApiClient(apiClient) | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(api_client) | setApiClient(apiClient) | setApiClient(client) ✗ | SetApiClient(client) ✗ | set_api_client(client) ✗ | setApiClient(client) ✗ | — | D35 |
| OpenIdConnectAuthenticator.buildAuthorizationUrl | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state) ✗ | buildAuthorizationUrl(state?) | BuildAuthorizationUrlAsync(state?) | buildAuthorizationUrl(state?) | build_authorization_url(state?) | build_authorization_url(state?) | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | BuildAuthorizationURL(state) ✗ | build_authorization_url(state) | buildAuthorizationURL(state?) ✗ | build_authorization_url(state?) | Optionality `(state?)` (majority) in java. D82 |
| OpenIdConnectAuthenticator.exchangeCode | exchangeCode(code) | exchangeCode(code) | exchangeCode(code) | ExchangeCodeAsync(code) | exchangeCode(code) | exchange_code(code) | exchange_code(code) | exchangeCode(code) | exchangeCode(code) | ExchangeCode(code) | exchange_code(code) | exchangeCode(code) | exchange_code(code) |  |
| OpenIdConnectAuthenticator.defaultDiscoveryMaxAgeSeconds | defaultDiscoveryMaxAgeSeconds | — | — | — | — | — | DEFAULT_DISCOVERY_MAX_AGE_SECONDS ✗ | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in ruby. |
| OpenIdConnectAuthenticator.authHeadersOrError | authHeadersOrError | — | — | — | — | — | — | — | — | AuthHeadersOrError ~ | — | — | — | idiom (allow-list) |
| OpenIdConnectAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| OpenIdConnectAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| OpenIdConnectAuthenticator.openidConnectUrl | openidConnectUrl | — | — | — | — | — | — | — | — | — | — | — | openid_connect_url ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.clientId | clientId | — | — | — | — | — | — | — | — | — | — | — | client_id ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.clientSecret | clientSecret | — | — | — | — | — | — | — | — | — | — | — | client_secret ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.redirectUri | redirectUri | — | — | — | — | — | — | — | — | — | — | — | redirect_uri ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.scopes | scopes | — | — | — | — | — | — | — | — | — | — | — | scopes ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.apiClient | apiClient | — | — | — | — | — | — | — | — | — | — | — | api_client ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.delegate | delegate | — | — | — | — | — | — | — | — | — | — | — | delegate ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.discoveryExpiry | discoveryExpiry | — | — | — | — | — | — | — | — | — | — | — | discovery_expiry ~ | idiom (allow-list) |
| OpenIdConnectAuthenticator.discoveryCache | discoveryCache | — | — | — | — | — | — | — | — | — | — | — | discovery_cache ~ | idiom (allow-list) |
| **SsoAuthAuthenticator** | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator | SsoAuthAuthenticator |  |
| SsoAuthAuthenticator ctor | new(host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, client_id, client_secret, redirect_uri) | (host, client_id, client_secret, redirect_uri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, client_id, client_secret, redirect_uri) | (host, clientId, clientSecret, redirectUri) | (host, client_id, client_secret, redirect_uri) |  |
| SsoAuthAuthenticator.buildAuthorizationUrl | buildAuthorizationUrl(state) | buildAuthorizationUrl(state) | buildAuthorizationUrl(state?) | BuildAuthorizationUrlAsync(state?) | buildAuthorizationUrl(state?) | build_authorization_url(state?) | build_authorization_url(state?) | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | BuildAuthorizationURL(state) ✗ | — | buildAuthorizationURL(state?) ✗ | — | D82 |
| SsoAuthAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| SsoAuthAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |
| **UserAuthAuthorizationCodeAuthenticator** | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator | UserAuthAuthorizationCodeAuthenticator |  |
| UserAuthAuthorizationCodeAuthenticator ctor | new(host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, client_id, client_secret, redirect_uri) | (host, client_id, client_secret, redirect_uri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, clientId, clientSecret, redirectUri) | (host, client_id, client_secret, redirect_uri) | (host, clientId, clientSecret, redirectUri) | (host, client_id, client_secret, redirect_uri) |  |
| UserAuthAuthorizationCodeAuthenticator.buildAuthorizationUrl | buildAuthorizationUrl(state) | buildAuthorizationUrl(state) | buildAuthorizationUrl(state?) | BuildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | build_authorization_url(state?) | build_authorization_url(state?) | buildAuthorizationUrl(state?) | buildAuthorizationUrl(state?) | BuildAuthorizationURL(state) ✗ | — | buildAuthorizationURL(state?) ✗ | — | D82 |
| UserAuthAuthorizationCodeAuthenticator.tryAuthHeaders | tryAuthHeaders | — | — | — | — | — | — | — | — | — | try_auth_headers ~ | — | — | idiom (allow-list) |
| UserAuthAuthorizationCodeAuthenticator.asHttpAwareMut | asHttpAwareMut | — | — | — | — | — | — | — | — | — | as_http_aware_mut ~ | — | — | idiom (allow-list) |

## OAuth2TokenManager

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **OAuth2TokenManager** | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager | OAuth2TokenManager |  |
| OAuth2TokenManager ctor | new() | () | — | — | — | () | () | — | — | () | () | () | — |  |
| OAuth2TokenManager.exchanged | exchanged | isExchanged | — ✗ | — ✗ | isExchanged | exchanged | exchanged? | isExchanged | — ✗ | — ✗ | is_exchanged | exchanged | exchanged? | D38 |
| OAuth2TokenManager.invalidateAccessToken | invalidateAccessToken | invalidateAccessToken | invalidateAccessToken | InvalidateAccessToken | invalidateAccessToken | invalidate_access_token | invalidate_access_token | invalidateAccessToken | invalidateAccessToken | InvalidateAccessToken | invalidate_access_token | invalidateAccessToken | invalidate_access_token |  |
| OAuth2TokenManager.refreshToken | refreshToken | getRefreshToken | getRefreshToken | RefreshToken | getRefreshToken | refresh_token | refresh_token | getRefreshToken | refreshToken | RefreshToken | refresh_token | refreshToken | refresh_token |  |
| OAuth2TokenManager.setApiClient | setApiClient(apiClient) | setApiClient(apiClient) | — | SetApiClient(apiClient) | setApiClient(apiClient) | set_api_client(api_client) | set_api_client(api_client) | setApiClient(apiClient) | setApiClient(client) ✗ | SetApiClient(client) ✗ | set_api_client(client) ✗ | setApiClient(client) ✗ | set_api_client(client) ✗ | D37 |
| OAuth2TokenManager.getAccessToken | getAccessToken(tokenUrl, params, extraHeaders?, refreshUrl?) | getAccessToken(tokenUrl, params, extraHeaders?, refreshUrl?) | getAccessToken(tokenUrl, params, extraHeaders?, refreshUrl?) | GetAccessTokenAsync(tokenUrl, parameters, extraHeaders?, refreshUrl?) ~ | getAccessToken(tokenUrl, params, extraHeaders?, refreshUrl?) | get_access_token(token_url, params, extra_headers?, refresh_url?) | get_access_token(token_url, params, extra_headers?, refresh_url?) | getAccessToken(tokenUrl, params, extraHeaders?, refreshUrl?) | getAccessToken(tokenUrl, params, extraHeaders?, refreshUrl?) | GetAccessToken(tokenURL, params) ~ | get_access_token(token_url, params) ~ | getAccessToken(tokenURL, params, extraHeaders?, refreshURL?) | get_access_token(token_url, params, extra_headers?, opts?) | idiom (allow-list) |
| OAuth2TokenManager.exchange | exchange(tokenUrl, params, extraHeaders?) | exchange(tokenUrl, params, extraHeaders) ✗ | exchange(tokenUrl, params, extraHeaders?) | ExchangeAsync(tokenUrl, parameters, extraHeaders?) ~ | exchange(tokenUrl, params, extraHeaders?) | exchange(token_url, params, extra_headers?) | exchange(token_url, params, extra_headers?) | exchange(tokenUrl, params, extraHeaders?) | exchange(tokenUrl, params, extraHeaders?) | Exchange(tokenURL, params) ~ | exchange(token_url, params, extra_headers) | exchange(tokenURL, params, extraHeaders?) | exchange(token_url, params, extra_headers?) | Optionality `(tokenurl, params, extraheaders?)` (majority) in java. |
| OAuth2TokenManager.setAccessToken | setAccessToken(token) | setAccessToken(token) | setAccessToken(token) | SetAccessToken(token) | setAccessToken(token) | set_access_token(token) | set_access_token(token) | setAccessToken(token) | setAccessToken(token) | SetAccessToken(token) | set_access_token(token) | setAccessToken(token) | set_access_token(token) |  |
| OAuth2TokenManager.expirySafetyMarginMs | expirySafetyMarginMs | — | EXPIRY_SAFETY_MARGIN_MS ✗ | — | — | — | — | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in kotlin. |
| OAuth2TokenManager.apiClient | apiClient | — | apiClient ✗ | — | — | — | — | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in kotlin. |
| OAuth2TokenManager.expirySafetyMarginS | expirySafetyMarginS | — | — | — | — | — | EXPIRY_SAFETY_MARGIN_S ✗ | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in ruby. |
| OAuth2TokenManager.markExchanged | markExchanged | — | — | — | — | — | mark_exchanged ✗ | markExchanged ✗ | — | — | mark_exchanged ✗ | — | mark_exchanged ~ | D39 |
| OAuth2TokenManager.errApiClientNotInjected | errApiClientNotInjected | — | — | — | — | — | — | — | — | ErrApiClientNotInjected ~ | — | — | — | idiom (allow-list) |
| OAuth2TokenManager.getAccessTokenWithHeaders | getAccessTokenWithHeaders | — | — | — | — | — | — | — | — | GetAccessTokenWithHeaders(tokenURL, params, extraHeaders) ~ | get_access_token_with_headers(token_url, params, extra_headers) ~ | — | — | idiom (allow-list) |
| OAuth2TokenManager.getAccessTokenWithRefresh | getAccessTokenWithRefresh | — | — | — | — | — | — | — | — | GetAccessTokenWithRefresh(tokenURL, refreshURL, params, extraHeaders) ~ | — | — | — | idiom (allow-list) |
| OAuth2TokenManager.getAccessTokenWithRefreshUrl | getAccessTokenWithRefreshUrl | — | — | — | — | — | — | — | — | — | get_access_token_with_refresh_url(token_url, refresh_url, params, extra_headers) ~ | — | — | idiom (allow-list) |
| OAuth2TokenManager.startLink | startLink | — | — | — | — | — | — | — | — | — | — | — | start_link(opts?) ~ | idiom (allow-list) |

## TransportOptions

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **TransportOptions** | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions | TransportOptions |  |
| TransportOptions ctor | new(verifySsl, caCertPath, proxy, timeout, followRedirects, maxRedirects, userAgent, defaultHeaders, injectRequestId) | — | — | — | (verifySsl?, caCertPath?, proxy?, timeout?, followRedirects?, maxRedirects?, userAgent?, defaultHeaders?, injectRequestId?) | (verify_ssl?, ca_cert_path?, proxy?, timeout?, follow_redirects?, max_redirects?, user_agent?, default_headers?, inject_request_id?) | (verify_ssl, ca_cert_path, proxy, timeout, follow_redirects, max_redirects, user_agent, default_headers, inject_request_id) ✗ | (options) ~ | — | — | — | — | (opts?) | D62 |
| TransportOptions.verifySsl | verifySsl | isVerifySsl | verifySsl | VerifySsl | verifySsl | verify_ssl | verify_ssl | verifySsl | verifySsl | VerifySsl | verify_ssl | verifySsl | verify_ssl |  |
| TransportOptions.caCertPath | caCertPath | getCaCertPath | caCertPath | CaCertPath | caCertPath | ca_cert_path | ca_cert_path | caCertPath | caCertPath | CACertPath ✗ | ca_cert_path | caCertPath | ca_cert_path | D82 |
| TransportOptions.proxy | proxy | getProxy | proxy | Proxy | proxy | proxy | proxy | proxy | proxy | Proxy | proxy | proxy | proxy |  |
| TransportOptions.timeout | timeout | getTimeout | timeout | Timeout | timeout | timeout | timeout | timeout | timeout | Timeout | timeout | timeout | timeout |  |
| TransportOptions.followRedirects | followRedirects | isFollowRedirects | followRedirects | FollowRedirects | followRedirects | follow_redirects | follow_redirects | followRedirects | followRedirects | FollowRedirects | follow_redirects | followRedirects | follow_redirects |  |
| TransportOptions.maxRedirects | maxRedirects | getMaxRedirects | maxRedirects | MaxRedirects | maxRedirects | max_redirects | max_redirects | maxRedirects | maxRedirects | MaxRedirects | max_redirects | maxRedirects | max_redirects |  |
| TransportOptions.userAgent | userAgent | getUserAgent | userAgent | UserAgent | userAgent | user_agent | user_agent | userAgent | userAgent | UserAgent | user_agent | userAgent | user_agent |  |
| TransportOptions.defaultHeaders | defaultHeaders | getDefaultHeaders | defaultHeaders | DefaultHeaders | defaultHeaders | default_headers | default_headers | defaultHeaders | defaultHeaders | DefaultHeaders | default_headers | defaultHeaders | default_headers |  |
| TransportOptions.injectRequestId | injectRequestId | isInjectRequestId | injectRequestId | InjectRequestId | injectRequestId | inject_request_id | inject_request_id | injectRequestId | injectRequestId | InjectRequestID ✗ | inject_request_id | injectRequestID ✗ | inject_request_id | D82 |
| TransportOptions.builder | builder() | builder | builder | Builder | builder | builder | builder | builder | builder | — ~ | builder | builder | — ~ | idiom (allow-list) |
| TransportOptions.assertValidProxy | assertValidProxy | — | — | — | assertValidProxy(proxy) ✗ | — | — | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in php. |
| TransportOptions.validateProxy | validateProxy | — | — | — | — | — | validate_proxy!(val) ✗ | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in ruby. |
| TransportOptions.errInvalidProxyUrl | errInvalidProxyUrl | — | — | — | — | — | — | — | — | ErrInvalidProxyURL ✗ | — | — | — | D82 |
| TransportOptions.errInvalidCaCertificate | errInvalidCaCertificate | — | — | — | — | — | — | — | — | ErrInvalidCACertificate ✗ | — | — | — | D82 |
| **TransportOptionsBuilder** | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | TransportOptionsBuilder | — ~ | idiom (allow-list) |
| TransportOptionsBuilder ctor | new() | — | — | — | — | () | () | — | — | () | () | () | — |  |
| TransportOptionsBuilder.build | build | build | build | Build | build | build | build | build | build | Build | build | build | — |  |
| TransportOptionsBuilder.verifySsl | verifySsl(verifySsl) | verifySsl(verifySsl) | verifySsl(verifySsl) | VerifySsl(verifySsl) | verifySsl(verifySsl) | verify_ssl(verify_ssl) | verify_ssl(val) ✗ | verifySsl(verifySsl) | verifySsl(val) ✗ | VerifySsl(val) ✗ | verify_ssl(val) ✗ | verifySsl(val) ✗ | — | D56 |
| TransportOptionsBuilder.caCertPath | caCertPath(caCertPath) | caCertPath(caCertPath) | caCertPath(caCertPath) | CaCertPath(caCertPath) | caCertPath(caCertPath) | ca_cert_path(ca_cert_path) | ca_cert_path(val) ✗ | caCertPath(caCertPath) | caCertPath(val) ✗ | CACertPath(val) ✗ | ca_cert_path(val) ✗ | caCertPath(val) ✗ | — | D56 D82 |
| TransportOptionsBuilder.proxy | proxy(proxy) | proxy(proxy) | proxy(proxy) | Proxy(proxy) | proxy(proxy) | proxy(proxy) | proxy(val) ✗ | proxy(proxy) | proxy(val) ✗ | Proxy(val) ✗ | proxy(val) ✗ | proxy(val) ✗ | — | D56 |
| TransportOptionsBuilder.timeout | timeout(timeout) | timeout(timeout) | timeout(timeout) | Timeout(timeout) | timeout(timeout) | timeout(timeout) | timeout(val) ✗ | timeout(timeout) | timeout(val) ✗ | Timeout(val) ✗ | timeout(val) ✗ | timeout(val) ✗ | — | D56 |
| TransportOptionsBuilder.followRedirects | followRedirects(followRedirects) | followRedirects(followRedirects) | followRedirects(followRedirects) | FollowRedirects(followRedirects) | followRedirects(followRedirects) | follow_redirects(follow_redirects) | follow_redirects(val) ✗ | followRedirects(followRedirects) | followRedirects(val) ✗ | FollowRedirects(val) ✗ | follow_redirects(val) ✗ | followRedirects(val) ✗ | — | D56 |
| TransportOptionsBuilder.maxRedirects | maxRedirects(maxRedirects) | maxRedirects(maxRedirects) | maxRedirects(maxRedirects) | MaxRedirects(maxRedirects) | maxRedirects(maxRedirects) | max_redirects(max_redirects) | max_redirects(val) ✗ | maxRedirects(maxRedirects) | maxRedirects(val) ✗ | MaxRedirects(val) ✗ | max_redirects(val) ✗ | maxRedirects(val) ✗ | — | D56 |
| TransportOptionsBuilder.userAgent | userAgent(userAgent) | userAgent(userAgent) | userAgent(userAgent) | UserAgent(userAgent) | userAgent(userAgent) | user_agent(user_agent) | user_agent(val) ✗ | userAgent(userAgent) | userAgent(val) ✗ | UserAgent(val) ✗ | user_agent(val) ✗ | userAgent(val) ✗ | — | D56 |
| TransportOptionsBuilder.defaultHeader | defaultHeader(name, value) | defaultHeader(name, value) | defaultHeader(name, value) | DefaultHeader(name, value) | defaultHeader(name, value) | default_header(name, value) | default_header(name, value) | defaultHeader(name, value) | defaultHeader(name, value) | DefaultHeader(name, value) | default_header(name, value) | defaultHeader(name, value) | — |  |
| TransportOptionsBuilder.defaultHeaders | defaultHeaders(headers) | defaultHeaders(headers) | defaultHeaders(headers) | DefaultHeaders(headers) | defaultHeaders(headers) | default_headers(headers) | default_headers(headers) | defaultHeaders(headers) | defaultHeaders(headers) | DefaultHeaders(headers) | default_headers(headers) | defaultHeaders(headers) | — |  |
| TransportOptionsBuilder.injectRequestId | injectRequestId(injectRequestId) | injectRequestId(injectRequestId) | injectRequestId(injectRequestId) | InjectRequestId(injectRequestId) | injectRequestId(injectRequestId) | inject_request_id(inject_request_id) | inject_request_id(val) ✗ | injectRequestId(injectRequestId) | injectRequestId(val) ✗ | InjectRequestID(val) ✗ | inject_request_id(val) ✗ | injectRequestID(val) ✗ | — | D56 D82 |
| TransportOptionsBuilder.userAgentOpt | userAgentOpt | — | — | — | — | — | — | — | — | — | user_agent_opt(val) ✗ | — | — | Not part of the canonical surface: make it non-public in rust. |

## Server configuration

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **ServerConfiguration** | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration | ServerConfiguration |  |
| ServerConfiguration ctor | new(urlTemplate, description?, variables?) | (urlTemplate, description, variables) ✗ | (urlTemplate, description, variables) ✗ | (urlTemplate, description, variables) ✗ | (urlTemplate, description?, variables?) | (url_template, description?, variables?) | (url_template, description?, variables?) | (urlTemplate, description, variables) ✗ | (urlTemplate, description?, variables?) | (urlTemplate, description, variables) | (url_template, description, variables) | (urlTemplate, description?, variables?) | (url_template, description?, variables?) | D63 |
| ServerConfiguration.urlTemplate | urlTemplate | getUrlTemplate | urlTemplate | UrlTemplate | urlTemplate | url_template | url_template | urlTemplate | urlTemplate | URLTemplate ✗ | url_template | urlTemplate | url_template | D82 |
| ServerConfiguration.variables | variables | getVariables | variables | Variables | variables | variables | variables | variables | variables | Variables | variables | variables | variables |  |
| ServerConfiguration.getUrl | getUrl(overrides?) | getUrl(overrides?) | getUrl(overrides?) | GetUrl(overrides?) | getUrl(overrides?) | get_url(overrides?) | — | getUrl(overrides?) | — | — | — | — | — |  |
| ServerConfiguration.url | url(overrides?) | — | — | — | — | — | url(overrides?) | — | url(overrides?) | URL(overrides) | url(overrides) | url(overrides?) | url(overrides?) |  |
| ServerConfiguration.errInvalidServerVariable | errInvalidServerVariable | — | — | — | — | — | — | — | — | ErrInvalidServerVariable ~ | — | — | — | idiom (allow-list) |
| **ServerVariable** | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable | ServerVariable |  |
| ServerVariable ctor | new(defaultValue, description?, enumValues?) | (defaultValue, description, enumValues) ✗ | (defaultValue, description, enumValues) ✗ | (DefaultValue, Description, EnumValues) ✗ | (defaultValue, description?, enumValues?) | (default_value, description?, enum_values?) | (default_value, description?, enum_values?) | (defaultValue, description, enumValues) ✗ | (defaultValue, description?, enumValues?) | (defaultValue, description, enumValues) | (default_value, description, enum_values) | (defaultValue, description?, enumValues?) | (default_value, description?, enum_values?) | D64 |
| ServerVariable.defaultValue | defaultValue | getDefaultValue | defaultValue | DefaultValue | defaultValue | default_value | default_value | defaultValue | defaultValue | DefaultValue | default_value | defaultValue | default_value |  |
| ServerVariable.enumValues | enumValues | getEnumValues | enumValues | EnumValues | enumValues | enum_values | enum_values | enumValues | enumValues | EnumValues | enum_values | enumValues | enum_values |  |
| **Servers** | Servers | Servers | Servers | Servers | Servers | servers | Servers | Servers | servers | servers | servers | Servers | Servers |  |
| Servers.server0 | server0 | SERVER_0 | SERVER_0 | Server0 | server0 | SERVER_0 | SERVER_0 | SERVER_0 | server0 | Server0 | server_0 | server0 | server_0 |  |
| Servers.server1 | server1 | SERVER_1 | SERVER_1 | Server1 | server1 | SERVER_1 | SERVER_1 | SERVER_1 | server1 | Server1 | server_1 | server1 | server_1 |  |
| Servers.server2 | server2 | SERVER_2 | SERVER_2 | Server2 | server2 | SERVER_2 | SERVER_2 | SERVER_2 | server2 | Server2 | server_2 | server2 | server_2 |  |
| Servers.all | all | ALL | ALL | All | all | ALL | — ✗ | ALL | — ✗ | — ✗ | — ✗ | all | all | D66 |
| Servers.allServers | allServers | — | — | — | — | — | — | — | allServers ✗ | AllServers ✗ | all_servers ✗ | — | — | D67 |

## Client, Configuration and transport contract

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **ApiClient** | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient | ApiClient |  |
| ApiClient.close | close | close | close | — ~ | close | — ✗ | close | — ✗ | — ✗ | Close | close | close | close | D60 |
| ApiClient.sendRequest | sendRequest(method, url, headers, body, noRedirect?) | sendRequest(method, url, headers, body, noRedirect?) | sendRequest(method, url, headers, body, noRedirect?) | SendRequestAsync(method, url, headers, body, noRedirect?) | sendRequest(method, url, headers, body, noRedirect?) | send_request(method, url, headers, body?, no_redirect?) ✗ | send_request(method, url, headers, body, no_redirect?) | sendRequest(method, url, headers, body, options?) ✗ | sendRequest(method, url, headers, body, noRedirect?) | SendRequest(method, url, headers, body) ~ | send_request(method, url, headers, body) ~ | sendRequest(method, url, headers, body, noRedirect?) | — ✗ | Add `sendrequest` in elixir. Parameters `(method, url, headers, body, noredirect)` (majority) in node. Optionality `(method, url, headers, body, noredirect?)` (majority) in python. |
| ApiClient.sendRequestWithOptions | sendRequestWithOptions | — | — | — | — | — | — | — | — | SendRequestWithOptions(method, url, headers, body, opts) ~ | send_request_with_options(method, url, headers, body, _options) ~ | — | — | idiom (allow-list) |
| **ApiHttpResponse** | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse | ApiHttpResponse |  |
| ApiHttpResponse ctor | new(statusCode, body, headers) | (statusCode, body, headers) | (statusCode, body, headers) | (statusCode, body, headers) | (statusCode, body, headers) | (status_code, body, headers) | (status_code, body, headers) | — | (statusCode, body, headers) | — | (status_code, body, headers) | (statusCode, body, headers) | (status_code?, body?, headers?) |  |
| ApiHttpResponse.statusCode | statusCode | statusCode | statusCode | StatusCode | statusCode | status_code | status_code | statusCode | statusCode | StatusCode | status_code | statusCode | status_code |  |
| ApiHttpResponse.body | body | body | body | Body | body | body | body | body | body | Body | body | body | body |  |
| ApiHttpResponse.headers | headers | headers | headers | Headers | headers | headers | headers | headers | headers | Headers | headers | headers | headers |  |
| **ApiResult** | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult | ApiResult |  |
| ApiResult ctor | new(statusCode, data, rawBody, headers) | (statusCode, data, rawBody, headers) | (statusCode, data, rawBody, headers) | (statusCode, data, rawBody, headers) | (statusCode, data, rawBody, headers) | (status_code, data, raw_body, headers) | (status_code, data, raw_body, headers) | — | (statusCode, data, rawBody, headers) | (statusCode, data, rawBody, headers) | (status_code, data, raw_body, headers) | (statusCode, data, rawBody, headers) | (status_code?, data?, raw_body?, headers?) |  |
| ApiResult.statusCode | statusCode | statusCode | statusCode | StatusCode | statusCode | status_code | status_code | statusCode | statusCode | StatusCode | status_code | statusCode | status_code |  |
| ApiResult.data | data | data | data | Data | data | data | data | data | data | Data | data | data | data |  |
| ApiResult.rawBody | rawBody | rawBody | rawBody | RawBody | rawBody | raw_body | raw_body | rawBody | rawBody | RawBody | raw_body | rawBody | raw_body |  |
| ApiResult.headers | headers | headers | headers | Headers | headers | headers | headers | headers | headers | Headers | headers | headers | headers |  |
| ApiResult.intoData | intoData | — | — | — | — | — | — | — | — | — | into_data ~ | — | — | idiom (allow-list) |
| **Client** | Client | Client | Client | Client | Client | Client | — ✗ | Client | Client | Client | Client | Client | Client | D05 |
| Client ctor | new(authenticator, transportOptions) | (authenticator, transportOptions?) | (authenticator, transportOptions?) | (authenticator, transportOptions?) | (authenticator, transportOptions?, apiClient?) ✗ | (authenticator, transport_options?) | — | (authenticator, transportOptions?) | (authenticator, transportOptions?) | (authenticator, transportOptions) | (authenticator, transport_options) | (host?, accessToken?, transportOptions?, authenticator?) | (authenticator, transport_options?) | D59 |
| Client.pet | pet | pet | pet | Pet | pet | pet | — | pet | pet | Pet | pet | pet | pet |  |
| Client.store | store | store | store | Store | store | store | — | store | store | Store | store | store | store |  |
| Client.withToken | withToken(host, accessToken, transportOptions?) | withToken(host, accessToken, transportOptions?) | withToken(host, accessToken, transportOptions?) | WithToken(host, accessToken, transportOptions?) | withToken(host, accessToken, transportOptions?, apiClient?) ✗ | with_token(host, access_token, transport_options?) | — | withToken(host, accessToken, transportOptions?) | withToken(host, accessToken, transportOptions?) | WithToken(host, accessToken, transportOptions) | with_token(host, access_token, transport_options) | withToken(host, accessToken, transportOptions?) | with_token(host, access_token, transport_options?) | D57 |
| Client.withAuthenticator | withAuthenticator(authenticator, transportOptions?) | withAuthenticator(authenticator, transportOptions?) | withAuthenticator(authenticator, transportOptions?) | WithAuthenticator(authenticator, transportOptions?) | withAuthenticator(authenticator, transportOptions?, apiClient?) ✗ | with_authenticator(authenticator, transport_options?) | — | withAuthenticator(authenticator, transportOptions?) | withAuthenticator(authenticator, transportOptions?) | WithAuthenticator(authenticator, transportOptions) | with_authenticator(authenticator, transport_options) | withAuthenticator(authenticator, transportOptions?) | with_authenticator(authenticator, transport_options?) | D58 |
| Client.dispose | dispose | — | — | Dispose ~ | — | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **Configuration** | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration | Configuration |  |
| Configuration ctor | new(baseUrl, defaultHeaders) | — | — | (baseUrl?, defaultHeaders?) | (baseUrl?, defaultHeaders?) | (base_url?, default_headers?) | (base_url, default_headers) ✗ | (options?) ~ | — | — | — | — | (opts?) | D61 |
| Configuration.baseUrl | baseUrl | getBaseUrl | baseUrl | BaseUrl | baseUrl | base_url | base_url | baseUrl | baseUrl | BaseURL ✗ | base_url | baseURL ✗ | base_url | D82 |
| Configuration.defaultHeaders | defaultHeaders | getDefaultHeaders | defaultHeaders | DefaultHeaders | defaultHeaders | default_headers | default_headers | defaultHeaders | defaultHeaders | DefaultHeaders | default_headers | defaultHeaders | default_headers |  |
| Configuration.builder | builder() | builder | builder | Builder | builder | builder | builder | builder | builder | — ~ | builder | builder | — ~ | idiom (allow-list) |
| Configuration.defaultConfiguration | defaultConfiguration() | defaultConfiguration | defaultConfiguration | DefaultConfiguration | defaultConfiguration | default_configuration | default_configuration | defaultConfiguration | defaultConfiguration | DefaultConfiguration | — ~ | — ~ | — ~ | idiom (allow-list) |
| Configuration.default | default | — | — | — | — | — | — | — | — | — | — | `default` ~ | default ~ | idiom (allow-list) |
| Configuration.fromServer | fromServer | — | — | — | — | — | — | — | — | — | — | — | from_server(server_config, variables?) ✗ | Not part of the canonical surface: make it non-public in elixir. |
| **ConfigurationBuilder** | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | ConfigurationBuilder | — ~ | idiom (allow-list) |
| ConfigurationBuilder ctor | new() | — | — | — | — | () | () | — | — | () | () | () | — |  |
| ConfigurationBuilder.build | build | build | build | Build | build | build | build | build | build | Build | build | build | — |  |
| ConfigurationBuilder.baseUrl | baseUrl(baseUrl) | baseUrl(baseUrl) | baseUrl(baseUrl) | BaseUrl(baseUrl) | baseUrl(baseUrl) | base_url(base_url) | base_url(val) ✗ | baseUrl(baseUrl) | baseUrl(val) ✗ | BaseURL(val) ✗ | base_url(val) ✗ | baseURL(val) ✗ | — | D55 D82 |
| ConfigurationBuilder.defaultHeader | defaultHeader(name, value) | defaultHeader(name, value) | defaultHeader(name, value) | DefaultHeader(name, value) | defaultHeader(name, value) | default_header(name, value) | default_header(name, value) | defaultHeader(name, value) | defaultHeader(name, value) | DefaultHeader(name, value) | default_header(name, value) | defaultHeader(name, value) | — |  |
| ConfigurationBuilder.defaultHeaders | defaultHeaders(headers) | defaultHeaders(headers) | defaultHeaders(headers) | DefaultHeaders(headers) | defaultHeaders(headers) | default_headers(headers) | default_headers(headers) | defaultHeaders(headers) | defaultHeaders(headers) | DefaultHeaders(headers) | default_headers(headers) | defaultHeaders(headers) | — |  |
| ConfigurationBuilder.server | server(serverConfig, variables?) | server(serverConfig, variables?) | server(serverConfig, variables?) | Server(serverConfig, variables?) | server(serverConfig, variables?) | server(server_config, variables?) | server(server_config, variables?) | server(serverConfig, variables?) | server(server, variables?) ✗ | Server(server, variables) ✗ | server(server, variables) ✗ | server(server, variables?) ✗ | — | D55 |
| **DefaultApiClient** | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient | DefaultApiClient |  |
| DefaultApiClient ctor | new(transportOptions) | (transportOptions?) | (transportOptions?) | (transportOptions?) | (transportOptions?) | (transport_options?, pool_manager?) ✗ | (transport_options?) | (transportOptions?) | (transportOptions?) | (transportOptions) | (transport_options) | (transportOptions?) | (transport_options?) | D81 |
| DefaultApiClient.close | close | close | close | Dispose | close | close | close | close | close | Close | close | close | close |  |
| DefaultApiClient.sendRequest | sendRequest(method, url, headers, body, noRedirect?) | sendRequest(method, url, headers, body, noRedirect?) | sendRequest(method, url, headers, body, noRedirect) ✗ | SendRequestAsync(method, url, headers, body, noRedirect?) | sendRequest(method, url, headers, body, noRedirect?) | send_request(method, url, headers, body?, no_redirect?) ✗ | send_request(method, url, headers, body, no_redirect?) | sendRequest(method, url, headers, body, options?) ✗ | sendRequest(method, url, headers, body, noRedirect?) | SendRequest(method, url, headers, body) ~ | send_request(method, url, headers, body) ~ | sendRequest(method, url, headers, body, noRedirect?) | send_request(method, url, headers, body, request_opts?) ~ | Parameters `(method, url, headers, body, noredirect)` (majority) in node. Optionality `(method, url, headers, body, noredirect?)` (majority) in kotlin, python. |
| DefaultApiClient.sensitiveHeaderNames | sensitiveHeaderNames | — | — | SensitiveHeaderNames ✗ | — | — | — | — | — | — | — | — | — | D16 |
| DefaultApiClient.proxyAuthorizationHeader | proxyAuthorizationHeader | — | — | ProxyAuthorizationHeader ✗ | — | — | — | — | — | — | — | — | — | D16 |
| DefaultApiClient.validateMultipartFilename | validateMultipartFilename | — | — | ValidateMultipartFilename(filename) ✗ | — | — | — | — | — | — | validate_multipart_filename(filename) ✗ | — | — | D16 |
| DefaultApiClient.buildFilenameDirective | buildFilenameDirective | — | — | BuildFilenameDirective(filename) ✗ | — | — | — | — | — | — | build_filename_directive(filename) ✗ | — | — | D16 |
| DefaultApiClient.rfc5987EncodeValue | rfc5987EncodeValue | — | — | Rfc5987EncodeValue(s) ✗ | — | — | — | — | — | — | rfc5987_encode_value(s) ✗ | — | — | D16 |
| DefaultApiClient.extraSensitiveHeaderNames | extraSensitiveHeaderNames | — | — | — | — | — | EXTRA_SENSITIVE_HEADER_NAMES ✗ | — | — | — | — | — | — | D16 |
| DefaultApiClient.supportedEncodings | supportedEncodings | — | — | — | — | — | supported_encodings ✗ | — | — | — | — | — | — | D16 |
| DefaultApiClient.buildDispatcher | buildDispatcher | — | — | — | — | — | — | buildDispatcher(transportOptions) ✗ | — | — | — | — | — | D16 |
| DefaultApiClient.proxyAuthHeader | proxyAuthHeader | — | — | — | — | — | — | — | proxyAuthHeader ✗ | — | — | — | proxy_auth_header ~ | D16 |
| DefaultApiClient.isNetworkError | isNetworkError | — | — | — | — | — | — | — | isNetworkError(error) ✗ | — | — | — | — | D16 |
| DefaultApiClient.decompressBytes | decompressBytes | — | — | — | — | — | — | — | decompressBytes(bytes, contentEncoding) ✗ | — | — | — | — | D16 |
| DefaultApiClient.errClientClosed | errClientClosed | — | — | — | — | — | — | — | — | ErrClientClosed ~ | — | — | — | idiom (allow-list) |
| DefaultApiClient.errInvalidRequest | errInvalidRequest | — | — | — | — | — | — | — | — | ErrInvalidRequest ~ | — | — | — | idiom (allow-list) |
| DefaultApiClient.sendRequestWithOptions | sendRequestWithOptions | — | — | — | — | — | — | — | — | SendRequestWithOptions(method, url, headers, body, opts) ~ | send_request_with_options(method, url, headers, body, options) ~ | — | — | idiom (allow-list) |
| DefaultApiClient.isClosed | isClosed | — | — | — | — | — | — | — | — | — | is_closed ✗ | — | closed ~ | D16 |
| DefaultApiClient.serializeMultipartBody | serializeMultipartBody | — | — | — | — | — | — | — | — | — | serialize_multipart_body(fields, boundary) ✗ | — | — | D16 |
| DefaultApiClient.validateMultipartFieldName | validateMultipartFieldName | — | — | — | — | — | — | — | — | — | validate_multipart_field_name(name) ✗ | — | — | D16 |
| DefaultApiClient.escapeMultipartFieldName | escapeMultipartFieldName | — | — | — | — | — | — | — | — | — | escape_multipart_field_name(name) ✗ | — | — | D16 |
| DefaultApiClient.mimeForFilename | mimeForFilename | — | — | — | — | — | — | — | — | — | mime_for_filename(filename) ✗ | — | — | D16 |
| DefaultApiClient.parseCharset | parseCharset | — | — | — | — | — | — | — | — | — | parse_charset(content_type) ✗ | — | — | D16 |
| DefaultApiClient.decodeTextBody | decodeTextBody | — | — | — | — | — | — | — | — | — | decode_text_body(bytes, content_type) ✗ | — | decode_text_body(body, content_type) ✗ | D16 Parameters `(bytes, contenttype)` (majority) in elixir. |
| DefaultApiClient.transportOptions | transportOptions | — | — | — | — | — | — | — | — | — | — | — | transport_options ~ | idiom (allow-list) |
| DefaultApiClient.baseReq | baseReq | — | — | — | — | — | — | — | — | — | — | — | base_req ~ | idiom (allow-list) |
| DefaultApiClient.classifyTransportError | classifyTransportError | — | — | — | — | — | — | — | — | — | — | — | classify_transport_error(e) ✗ | D16 |
| DefaultApiClient.requestOptions | requestOptions | — | — | — | — | — | — | — | — | — | — | — | request_options(opts) ✗ | D16 |
| DefaultApiClient.buildContentDisposition | buildContentDisposition | — | — | — | — | — | — | — | — | — | — | — | build_content_disposition(name, filename) ✗ | D16 |
| **AbstractApiClient** | (internal) | — | — | — | AbstractApiClient ✗ | — | — | AbstractApiClient ✗ | AbstractApiClient ✗ | — | — | — | — | D17 |
| AbstractApiClient ctor | new(transportOptions) | — | — | — | (transportOptions?) | — | — | (transportOptions?) | (_transportOptions, _httpClient, _proxyAuthHeader) ✗ | — | — | — | — | D18 |
| AbstractApiClient.close | close | — | — | — | close | — | — | close | close | — | — | — | — |  |
| AbstractApiClient.sendRequest | sendRequest(method, url, headers, body, noRedirect?) | — | — | — | sendRequest(method, url, headers, body, noRedirect?) | — | — | sendRequest(method, url, headers, body, options?) ✗ | sendRequest(method, url, headers, body, noRedirect?) | — | — | — | — | D18 |
| AbstractApiClient.validateMultipartFilename | validateMultipartFilename(filename) | — | — | — | validateMultipartFilename(filename) | — | — | — ✗ | validateMultipartFilename(filename) | — | — | — | — | D18 |
| AbstractApiClient.buildFilenameDirective | buildFilenameDirective(filename) | — | — | — | buildFilenameDirective(filename) | — | — | — ✗ | buildFilenameDirective(filename) | — | — | — | — | D18 |
| AbstractApiClient.ensureDecoded | ensureDecoded | — | — | — | — | — | — | ensureDecoded(buf, contentEncoding, statusCode?) ✗ | — | — | — | — | — | D18 |
| AbstractApiClient.decodeBody | decodeBody | — | — | — | — | — | — | decodeBody(buf, contentType?) ✗ | — | — | — | — | — | D18 |
| AbstractApiClient.mimeTypeForFilename | mimeTypeForFilename | — | — | — | — | — | — | mimeTypeForFilename(filename) ✗ | — | — | — | — | — | D18 |
| AbstractApiClient.buildContentDisposition | buildContentDisposition | — | — | — | — | — | — | buildContentDisposition(name, filename) ✗ | — | — | — | — | — | D18 |
| AbstractApiClient.proxyAuthHeader | proxyAuthHeader | — | — | — | — | — | — | — | proxyAuthHeader ✗ | — | — | — | — | D18 |
| AbstractApiClient.decompressBytes | decompressBytes | — | — | — | — | — | — | — | decompressBytes(bytes, contentEncoding) ✗ | — | — | — | — | D18 |
| AbstractApiClient.isNetworkError | isNetworkError | — | — | — | — | — | — | — | isNetworkError(error) ✗ | — | — | — | — | D18 |
| AbstractApiClient.isTimeoutMessage | isTimeoutMessage | — | — | — | — | — | — | — | isTimeoutMessage(message) ✗ | — | — | — | — | D18 |
| AbstractApiClient.buildProxyAuthHeader | buildProxyAuthHeader | — | — | — | — | — | — | — | buildProxyAuthHeader(proxy) ✗ | — | — | — | — | D18 |
| AbstractApiClient.validateMultipartFieldName | validateMultipartFieldName | — | — | — | — | — | — | — | validateMultipartFieldName(name) ✗ | — | — | — | — | D18 |
| AbstractApiClient.escapeMultipartFieldName | escapeMultipartFieldName | — | — | — | — | — | — | — | escapeMultipartFieldName(name) ✗ | — | — | — | — | D18 |
| AbstractApiClient.rfc5987EncodeValue | rfc5987EncodeValue | — | — | — | — | — | — | — | rfc5987EncodeValue(s) ✗ | — | — | — | — | D18 |
| **Petstore** | Petstore | — | — | — | — | — | Petstore ✗ | — | — | — | — | — | — | D06 |
| Petstore ctor | — | — | — | — | — | — | (authenticator, transport_options?) | — | — | — | — | — | — |  |
| Petstore.pet | pet | — | — | — | — | — | pet | — | — | — | — | — | — |  |
| Petstore.store | store | — | — | — | — | — | store | — | — | — | — | — | — |  |
| Petstore.withToken | withToken(host, accessToken, transportOptions?) | — | — | — | — | — | with_token(host, access_token, transport_options?) | — | — | — | — | — | — |  |
| Petstore.withAuthenticator | withAuthenticator(authenticator, transportOptions?) | — | — | — | — | — | with_authenticator(authenticator, transport_options?) | — | — | — | — | — | — |  |

## Errors and exceptions

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **ApiException** | ApiException | ApiException | ApiException | ApiException | ApiException | ApiException | ApiError | ApiError | ApiException | ApiError | ApiError | ApiError | ApiError |  |
| ApiException ctor | new(statusCode, message, responseHeaders, responseBody, errorBody, cause) | (statusCode?, message, responseHeaders?, responseBody?, errorBody?, cause?) | (statusCode?, message, responseHeaders?, responseBody?, errorBody?, cause?) | (statusCode?, message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (statusCode?, message?, responseHeaders?, responseBody?, errorBody?, previous?) ~ | (status_code?, message?, response_headers?, response_body?, error_body?) ✗ | (arg?) ✗ | (statusCode, message, responseHeaders?, responseBody?, errorBody?, options?) ~ | (statusCode, message, responseBody?, responseHeaders?, errorBody?, underlyingError?) ✗ | (statusCode, msg, responseBody, responseHeaders, errorBody, cause) ✗ | (status_code, message, response_body, response_headers) ✗ | (statusCode?, message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) ~ | (message?, status_code?, response_headers?, response_body?, error_body?, cause?) | D26 |
| ApiException.statusCode | statusCode | getStatusCode | statusCode | StatusCode | getStatusCode | status_code | status_code | statusCode | statusCode | StatusCode | status_code | statusCode | status_code |  |
| ApiException.responseHeaders | responseHeaders | getResponseHeaders | responseHeaders | ResponseHeaders | getResponseHeaders | response_headers | response_headers | responseHeaders | responseHeaders | ResponseHeaders | response_headers | responseHeaders | response_headers |  |
| ApiException.responseBody | responseBody | getResponseBody | responseBody | ResponseBody | getResponseBody | response_body | response_body | responseBody | responseBody | ResponseBody | response_body | responseBody | response_body |  |
| ApiException.errorBody | errorBody | getErrorBody | errorBody | ErrorBody | getErrorBody | error_body | error_body | errorBody | errorBody | ErrorBody | error_body | errorBody | error_body |  |
| ApiException.fromResponse | fromResponse(statusCode, responseHeaders, responseBody) | fromResponse(statusCode, responseHeaders, responseBody) | fromResponse(statusCode, responseHeaders, responseBody) | FromResponse(statusCode, responseHeaders, responseBody) | fromResponse(statusCode, responseHeaders, responseBody) | from_response(status_code, headers, body) ✗ | from_response(status_code, headers, body) ✗ | fromResponse(statusCode, responseHeaders, responseBody) | fromResponse(statusCode, responseHeaders, responseBody) | FromResponse(statusCode, headers, body) ✗ | from_response(status_code, headers, body) ✗ | fromResponse(statusCode, headers, body) ✗ | from_response(status_code, headers, body) ✗ | D25 |
| ApiException.getTypedErrorBody | getTypedErrorBody(clazz) | getTypedErrorBody(clazz) | — ~ | — ~ | getTypedErrorBody(class) ~ | get_typed_error_body(clazz) | — ~ | getTypedErrorBody(clazz) | getTypedErrorBody(fromJson) ~ | GetTypedErrorBody(target) ~ | — ~ | getTypedErrorBody(type) ~ | — ~ | idiom (allow-list) |
| ApiException.typedErrorBody | typedErrorBody | — | getTypedErrorBody | GetTypedErrorBody | — | — | typed_error_body(klass) | — | — | — | typed_error_body | — | typed_error_body(type_name) |  |
| ApiException.detailMessage | detailMessage | — | — | — | — | — | detail_message ✗ | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in ruby. |
| ApiException.parseErrorBody | parseErrorBody | — | — | — | — | — | parse_error_body(body) ✗ | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in ruby. |
| ApiException.registerStatusError | registerStatusError | — | — | — | — | — | — | registerStatusError(statusCode, factory, statusClass?) ✗ | — | — | — | — | — | Not part of the canonical surface: make it non-public in node. |
| ApiException.underlyingError | underlyingError | — | — | — | — | — | — | — | underlyingError ✗ | — | — | underlyingError ~ | — | Not part of the canonical surface: make it non-public in dart. |
| ApiException.msg | msg | — | — | — | — | — | — | — | — | Msg ~ | — | — | — | idiom (allow-list) |
| ApiException.cause | cause | — | — | — | — | — | — | — | — | Cause ~ | — | — | cause ~ | idiom (allow-list) |
| ApiException.unwrap | unwrap | — | — | — | — | — | — | — | — | Unwrap ~ | — | — | — | idiom (allow-list) |
| ApiException.error | error | — | — | — | — | — | — | — | — | Error ~ | — | — | — | idiom (allow-list) |
| ApiException.as | as | — | — | — | — | — | — | — | — | As(target) ~ | — | — | — | idiom (allow-list) |
| ApiException.withSource | withSource | — | — | — | — | — | — | — | — | — | with_source(status_code, message, response_body, response_headers, source) ✗ | — | — | Not part of the canonical surface: make it non-public in rust. |
| ApiException.errorDescription | errorDescription | — | — | — | — | — | — | — | — | — | — | errorDescription ~ | — | idiom (allow-list) |
| ApiException.apiError | apiError | — | — | — | — | — | — | — | — | — | — | — | api_error?(term) ~ | idiom (allow-list) |
| **BadRequestException** | BadRequestException | BadRequestException | BadRequestException | BadRequestException | BadRequestException | BadRequestException | BadRequestError | BadRequestError | BadRequestException | BadRequestError | BadRequestError | BadRequestError | BadRequestError |  |
| BadRequestException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| BadRequestException.clientError | clientError | — | — | — | — | — | — | — | — | — | client_error ~ | — | — | idiom (allow-list) |
| **ClientException** | ClientException | ClientException | ClientException | ClientException | ClientException | ClientException | ClientError | ClientError | ClientException | ClientError | ClientError | ClientError | ClientError |  |
| ClientException ctor | new(statusCode, message, responseHeaders, responseBody, errorBody) | (statusCode, message, responseHeaders, responseBody, errorBody) | (statusCode, message, responseHeaders, responseBody, errorBody?) ✗ | (statusCode?, message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | — | — | — | (statusCode, message, responseHeaders?, responseBody?, errorBody?) ✗ | (statusCode, message, responseBody?, responseHeaders?, errorBody?) | — | — | — | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| ClientException.as | as | — | — | — | — | — | — | — | — | As(target) ~ | — | — | — | idiom (allow-list) |
| ClientException.apiError | apiError | — | — | — | — | — | — | — | — | — | api_error ~ | — | — | idiom (allow-list) |
| ClientException.clientError | clientError | — | — | — | — | — | — | — | — | — | — | — | client_error?(term) ~ | idiom (allow-list) |
| **ConflictException** | ConflictException | ConflictException | ConflictException | ConflictException | ConflictException | ConflictException | ConflictError | ConflictError | ConflictException | ConflictError | ConflictError | ConflictError | ConflictError |  |
| ConflictException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| ConflictException.clientError | clientError | — | — | — | — | — | — | — | — | — | client_error ~ | — | — | idiom (allow-list) |
| **ForbiddenException** | ForbiddenException | ForbiddenException | ForbiddenException | ForbiddenException | ForbiddenException | ForbiddenException | ForbiddenError | ForbiddenError | ForbiddenException | ForbiddenError | ForbiddenError | ForbiddenError | ForbiddenError |  |
| ForbiddenException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| ForbiddenException.clientError | clientError | — | — | — | — | — | — | — | — | — | client_error ~ | — | — | idiom (allow-list) |
| **InternalServerErrorException** | InternalServerErrorException | InternalServerErrorException | InternalServerErrorException | InternalServerErrorException | InternalServerErrorException | InternalServerErrorException | InternalServerError | InternalServerError | InternalServerErrorException | InternalServerError | InternalServerError | InternalServerError | InternalServerError |  |
| InternalServerErrorException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| InternalServerErrorException.serverError | serverError | — | — | — | — | — | — | — | — | — | server_error ~ | — | — | idiom (allow-list) |
| **NetworkException** | NetworkException | NetworkException | NetworkException | NetworkException | NetworkException | NetworkException | NetworkError | NetworkError | NetworkException | NetworkError | NetworkError | NetworkError | NetworkError |  |
| NetworkException ctor | new(message, cause) | (message, cause) | (message, cause) | (message?, innerException?) ~ | (message?, previous?) ~ | (message?) ✗ | (message?) ✗ | (message, options?) ~ | (message, underlyingError?) ✗ | (msg, cause) ✗ | — | — | (message?, status_code?, response_headers?, response_body?, error_body?, cause?) | D27 |
| NetworkException.cause | cause | — | — | — | — | — | — | — | — | Cause ~ | — | — | cause ~ | idiom (allow-list) |
| NetworkException.as | as | — | — | — | — | — | — | — | — | As(target) ~ | — | — | — | idiom (allow-list) |
| NetworkException.apiError | apiError | — | — | — | — | — | — | — | — | — | api_error ~ | — | — | idiom (allow-list) |
| NetworkException.networkError | networkError | — | — | — | — | — | — | — | — | — | — | — | network_error?(term) ~ | idiom (allow-list) |
| **NetworkTimeoutException** | NetworkTimeoutException | NetworkTimeoutException | NetworkTimeoutException | NetworkTimeoutException | NetworkTimeoutException | NetworkTimeoutException | NetworkTimeoutError | NetworkTimeoutError | NetworkTimeoutException | NetworkTimeoutError | NetworkTimeoutError | NetworkTimeoutError | NetworkTimeoutError |  |
| NetworkTimeoutException ctor | new(message, cause) | (message, cause) | (message, cause) | (message?, innerException?) ~ | — | — | — | (message, options?) ~ | (message, underlyingError?) ✗ | (msg, cause) ✗ | — | — | (message?, status_code?, response_headers?, response_body?, error_body?, cause?) | D28 |
| NetworkTimeoutException.cause | cause | — | — | — | — | — | — | — | — | Cause ~ | — | — | cause ~ | idiom (allow-list) |
| NetworkTimeoutException.networkError | networkError | — | — | — | — | — | — | — | — | — | network_error ~ | — | — | idiom (allow-list) |
| **NotFoundException** | NotFoundException | NotFoundException | NotFoundException | NotFoundException | NotFoundException | NotFoundException | NotFoundError | NotFoundError | NotFoundException | NotFoundError | NotFoundError | NotFoundError | NotFoundError |  |
| NotFoundException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| NotFoundException.clientError | clientError | — | — | — | — | — | — | — | — | — | client_error ~ | — | — | idiom (allow-list) |
| **OAuth2ServerException** | OAuth2ServerException | OAuth2ServerException | OAuth2ServerException | OAuth2ServerException | OAuth2ServerException | OAuth2ServerException | OAuth2ServerError | OAuth2ServerError | OAuth2ServerException | OAuth2ServerError | OAuth2ServerError | OAuth2ServerError | OAuth2ServerError |  |
| OAuth2ServerException ctor | new(statusCode, code, description, uri, rawBody) | (statusCode, code, description, uri, rawBody) | (statusCode, code, description, uri, rawBody) | (statusCode, code, description, uri, rawBody) | (statusCode, errorCode, description, uri, rawBody) ~ | (status_code, code, description, uri, raw_body) | (status_code, code, description, uri, raw_body) | (statusCode, code, description, uri, rawBody) | (statusCode, code, description, uri, rawBody) | (statusCode, code, description, uri, rawBody) | (status_code, code, description, uri, raw_body) | (statusCode, code, description, uri, rawBody) | (status_code?, code?, description?, uri?, raw_body?, message?) | idiom (allow-list) |
| OAuth2ServerException.statusCode | statusCode | getStatusCode | statusCode | StatusCode | statusCode | status_code | status_code | statusCode | statusCode | StatusCode | status_code | statusCode | status_code |  |
| OAuth2ServerException.code | code | getCode | code | Code | code | code | code | code | code | Code | code | code | code |  |
| OAuth2ServerException.uri | uri | getUri | uri | Uri | uri | uri | uri | uri | uri | URI | uri | uri | uri |  |
| OAuth2ServerException.rawBody | rawBody | getRawBody | rawBody | RawBody | rawBody | raw_body | raw_body | rawBody | rawBody | RawBody | raw_body | rawBody | raw_body |  |
| OAuth2ServerException.buildMessage | buildMessage | — | buildMessage(statusCode, code, description, rawBody) ✗ | — | — | — | — | — | — | — | — | — | — | Not part of the canonical surface: make it non-public in kotlin. |
| OAuth2ServerException.errorCode | errorCode | — | — | — | errorCode ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| OAuth2ServerException.error | error | — | — | — | — | — | — | — | — | Error ~ | — | — | — | idiom (allow-list) |
| OAuth2ServerException.errorDescription | errorDescription | — | — | — | — | — | — | — | — | — | — | errorDescription ~ | — | idiom (allow-list) |
| **OAuth2TokenException** | OAuth2TokenException | OAuth2TokenException | OAuth2TokenException | OAuth2TokenException | OAuth2TokenException | OAuth2TokenException | OAuth2TokenError | OAuth2TokenError | OAuth2TokenException | OAuth2TokenError | OAuth2TokenError | OAuth2TokenError ✗ | OAuth2TokenError | D31 |
| OAuth2TokenException ctor | new(message) | (message, cause?) ✗ | (message, cause?) ✗ | (message, innerException?) ~ | — | — | — | (message) | (message) | (message) | (message) | — | (message?) | D30 |
| OAuth2TokenException.error | error | — | — | — | — | — | — | — | — | Error ~ | — | — | — | idiom (allow-list) |
| OAuth2TokenException.errorDescription | errorDescription | — | — | — | — | — | — | — | — | — | — | errorDescription ~ | — | idiom (allow-list) |
| **OpenApiException** | OpenApiException | OpenAPIException | OpenAPIException | OpenAPIException | OpenAPIException | OpenAPIException | OpenAPIError | OpenAPIError | OpenAPIException | OpenAPIError | OpenAPIError | OpenAPIError | OpenAPIError |  |
| OpenApiException ctor | new(message, cause) | (message, cause?) | (message, cause?) | (message?, innerException?) ~ | — | — | — | (message, options?) ~ | () ✗ | — | — | — | — | D29 |
| OpenApiException.exceptions | exceptions | — | — | — | — | — | — | — | — | — | — | — | exceptions ~ | idiom (allow-list) |
| OpenApiException.openApiError | openApiError | — | — | — | — | — | — | — | — | — | — | — | open_api_error?(term) ~ | idiom (allow-list) |
| **SerializationException** | SerializationException | SerializationException | SerializationException | SerializationException | SerializationException | SerializationException | SerializationError | SerializationError | SerializationException | SerializationError | SerializationError | SerializationError | SerializationError |  |
| SerializationException ctor | new(message, cause) | (message, cause?) | (message, cause?) | (message?, innerException?) ~ | — | (message, cause?) | (message, cause?) | (message, cause?) | (message, cause?) | (message, cause) | (message, cause) | (message, cause?) | (message?, cause?) | idiom (allow-list) |
| SerializationException.cause | cause | — ~ | — ~ | — ~ | — ~ | cause | cause | cause | cause | Cause | cause | cause | cause | idiom (allow-list) |
| SerializationException.error | error | — | — | — | — | — | — | — | — | Error ~ | — | — | — | idiom (allow-list) |
| SerializationException.unwrap | unwrap | — | — | — | — | — | — | — | — | Unwrap ~ | — | — | — | idiom (allow-list) |
| SerializationException.errorDescription | errorDescription | — | — | — | — | — | — | — | — | — | — | errorDescription ~ | — | idiom (allow-list) |
| **ServerException** | ServerException | ServerException | ServerException | ServerException | ServerException | ServerException | ServerError | ServerError | ServerException | ServerError | ServerError | ServerError | ServerError |  |
| ServerException ctor | new(statusCode, message, responseHeaders, responseBody, errorBody) | (statusCode, message, responseHeaders, responseBody, errorBody) | (statusCode, message, responseHeaders, responseBody, errorBody?) ✗ | (statusCode?, message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | — | — | — | (statusCode, message, responseHeaders?, responseBody?, errorBody?) ✗ | (statusCode, message, responseBody?, responseHeaders?, errorBody?) | — | — | — | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| ServerException.as | as | — | — | — | — | — | — | — | — | As(target) ~ | — | — | — | idiom (allow-list) |
| ServerException.apiError | apiError | — | — | — | — | — | — | — | — | — | api_error ~ | — | — | idiom (allow-list) |
| ServerException.serverError | serverError | — | — | — | — | — | — | — | — | — | — | — | server_error?(term) ~ | idiom (allow-list) |
| **UnauthorizedException** | UnauthorizedException | UnauthorizedException | UnauthorizedException | UnauthorizedException | UnauthorizedException | UnauthorizedException | UnauthorizedError | UnauthorizedError | UnauthorizedException | UnauthorizedError | UnauthorizedError | UnauthorizedError | UnauthorizedError |  |
| UnauthorizedException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| UnauthorizedException.clientError | clientError | — | — | — | — | — | — | — | — | — | client_error ~ | — | — | idiom (allow-list) |
| **UnprocessableEntityException** | UnprocessableEntityException | UnprocessableEntityException | UnprocessableEntityException | UnprocessableEntityException | UnprocessableEntityException | UnprocessableEntityException | UnprocessableEntityError | UnprocessableEntityError | UnprocessableEntityException | UnprocessableEntityError | UnprocessableEntityError | UnprocessableEntityError | UnprocessableEntityError |  |
| UnprocessableEntityException ctor | new(message, responseHeaders, responseBody, errorBody) | (message, responseHeaders, responseBody, errorBody) ✗ | (message, responseHeaders, responseBody, errorBody?) ✗ | (message?, responseHeaders?, responseBody?, errorBody?, innerException?) ~ | (message?, responseHeaders?, responseBody?, errorBody?) | (message?, response_headers?, response_body?, error_body?) | (message?, response_body?, response_headers?, error_body?) | (message, responseHeaders?, responseBody?, errorBody?) ✗ | (message, responseBody?, responseHeaders?, errorBody?) | — | — | (message?, responseBody?, responseHeaders?, errorBody?, underlyingError?) | (message?, status_code?, response_headers?, response_body?, error_body?) | D71 |
| UnprocessableEntityException.clientError | clientError | — | — | — | — | — | — | — | — | — | client_error ~ | — | — | idiom (allow-list) |

## Serializer and support helpers

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **ObjectSerializer** | ObjectSerializer | ObjectSerializer | — ✗ | — ✗ | ObjectSerializer | ObjectSerializer | ObjectSerializer | ObjectSerializer | object_serializer | — ✗ | object_serializer | — ✗ | ObjectSerializer | D13 |
| ObjectSerializer ctor | new() | () | — | — | — | () | — | — | — | — | — | — | — |  |
| ObjectSerializer.maxJsonDepth | maxJsonDepth | MAX_JSON_DEPTH ✗ | — | — | — | — | MAX_JSON_DEPTH ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.serialize | serialize(object) | serialize(object) | — | — | serialize(data) ✗ | serialize(obj) ✗ | serialize(object) | serialize(obj) ✗ | serialize(object) | — | serialize(value) ✗ | — | serialize(object) | D15 |
| ObjectSerializer.deserialize | deserialize(jsonString, targetType) | deserialize(jsonString, type) ✗ | — | — | deserialize(data, class, httpHeaders?) ✗ | deserialize(json_string, target_type) | deserialize(json_string, target_type) | deserialize(json, cls) ✗ | deserialize(data, fromJson) ✗ | — | deserialize(data) ✗ | — | deserialize(json_string, target_type) | D15 |
| ObjectSerializer.stringify | stringify(value) | stringify(value) | — | — | stringify(value) | stringify(value) | stringify(value) | stringify(value) | stringify(value) | — | stringify(value) | — | stringify(dt) ✗ | D15 |
| ObjectSerializer.toPathValue | toPathValue(value) | toPathValue(value) | — | — | toPathValue(value) | to_path_value(value) | to_path_value(value) | toPathValue(value) | toPathValue(value) | — | to_path_value(value) | — | to_path_value(value) |  |
| ObjectSerializer.toQueryValue | toQueryValue(value, collectionFormat?) | toQueryValue(value, collectionFormat) ✗ | — | — | toQueryValue(value, collectionFormat?) | to_query_value(value, collection_format?) | to_query_value(value, collection_format?) | toQueryValue(value, collectionFormat?) | toQueryValue(value, collectionFormat) ✗ | — | to_query_value(value, collection_format) | — | to_query_value(value, collection_format?) | Optionality `(value, collectionformat?)` (majority) in java, dart. |
| ObjectSerializer.toHeaderValue | toHeaderValue(value) | toHeaderValue(value) | — | — | toHeaderValue(value) | to_header_value(value) | to_header_value(value) | toHeaderValue(value) | toHeaderValue(value) | — | to_header_value(value) | — | to_header_value(value) |  |
| ObjectSerializer.toCookieValue | toCookieValue(value) | toCookieValue(value) | — | — | toCookieValue(value) | to_cookie_value(value) | to_cookie_value(value) | toCookieValue(value) | toCookieValue(value) | — | to_cookie_value(value) | — | to_cookie_value(value) |  |
| ObjectSerializer.toFormValue | toFormValue(value) | toFormValue(value) | — | — | toFormValue(value) | to_form_value(value) | to_form_value(value) | toFormValue(value) | toFormValue(value) | — | to_form_value(value) | — | to_form_value(value) |  |
| ObjectSerializer.formatTime | formatTime | formatTime(value) ✗ | — | — | — | — | — | — | — | — | format_time(value) ✗ | — | — | D14 |
| ObjectSerializer.resolveOneOf | resolveOneOf(json, candidates) | resolveOneOf(json, candidates) | — | — | resolveOneOf(data, candidates) ✗ | — ✗ | — ✗ | resolveOneOf(json, candidates?) ✗ | resolveOneOf(data, fromJsonCandidates) ✗ | — | — ✗ | — | — ✗ | D14 D15 Optionality `(json, candidates)` (majority) in node. |
| ObjectSerializer.resolveAnyOf | resolveAnyOf(json, candidates) | resolveAnyOf(json, candidates) | — | — | resolveAnyOf(data, candidates) ✗ | — ✗ | — ✗ | resolveAnyOf(json, candidates?) ✗ | resolveAnyOf(data, fromJsonCandidates) ✗ | — | — ✗ | — | — ✗ | D14 D15 Optionality `(json, candidates)` (majority) in node. |
| ObjectSerializer.structuralEquals | structuralEquals | structuralEquals(left, right) ✗ | — | — | — | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.structuralHashCode | structuralHashCode | structuralHashCode(value) ✗ | — | — | — | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.parseRfc3339 | parseRfc3339 | — | — | — | parseRfc3339(text) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.encodeBase64Param | encodeBase64Param | — | — | — | encodeBase64Param(value) ✗ | — | encode_base64_param(value) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.isPrimitiveValue | isPrimitiveValue | — | — | — | isPrimitiveValue(value) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.isStrictBase64 | isStrictBase64 | — | — | — | isStrictBase64(text) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.toPathDateValue | toPathDateValue | — | — | — | toPathDateValue(value) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.resolveAnyOfAll | resolveAnyOfAll | — | — | — | resolveAnyOfAll(data, candidates) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.qualifySchemaName | qualifySchemaName | — | — | — | qualifySchemaName(schema) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.decodeBytes | decodeBytes | — | — | — | decodeBytes(encoded) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.encodeBytes | encodeBytes | — | — | — | encodeBytes(raw) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.decodeByteOneOfScalar | decodeByteOneOfScalar | — | — | — | decodeByteOneOfScalar(data) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.decodeByteOneOfArray | decodeByteOneOfArray | — | — | — | decodeByteOneOfArray(data) ✗ | — | — | — | — | — | — | — | — | D14 |
| ObjectSerializer.formatProtobufDuration | formatProtobufDuration | — | — | — | formatProtobufDuration(interval) ✗ | — | — | — | — | — | — | — | format_protobuf_duration(duration) ✗ | D14 D15 |
| ObjectSerializer.parseProtobufDuration | parseProtobufDuration | — | — | — | parseProtobufDuration(value) ✗ | — | — | — | — | — | — | — | parse_protobuf_duration(text) ✗ | D14 D15 |
| ObjectSerializer.parseJson | parseJson(data) | — ✗ | — | — | — ✗ | parse_json(json_string) ✗ | parse_json(json_string, symbolize_names?) ✗ | parseJson(json) ✗ | parseJson(data) | — | — ✗ | — | parse_json(data) | D14 D15 |
| ObjectSerializer.defaultDatetimeFormat | defaultDatetimeFormat | — | — | — | — | — | DEFAULT_DATETIME_FORMAT ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.rfc3339DatetimeRegex | rfc3339DatetimeRegex | — | — | — | — | — | RFC3339_DATETIME_REGEX ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.uriRegex | uriRegex | — | — | — | — | — | URI_REGEX ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.protobufDurationRegex | protobufDurationRegex | — | — | — | — | — | PROTOBUF_DURATION_REGEX ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.uuidRegex | uuidRegex | — | — | — | — | — | UUID_REGEX ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.jsonMaxDepth | jsonMaxDepth | — | — | — | — | — | json_max_depth(data) ✗ | — | — | — | — | — | json_max_depth(data) ✗ | D14 |
| ObjectSerializer.encodeOneofBody | encodeOneofBody | — | — | — | — | — | encode_oneof_body(value, type) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.sanitizeForSerialization | sanitizeForSerialization | — | — | — | — | — | sanitize_for_serialization(object, visited?) ✗ | — | — | — | — | — | sanitize_for_serialization(value) ✗ | D14 D15 |
| ObjectSerializer.sortScalarSet | sortScalarSet | — | — | — | — | — | sort_scalar_set(items) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.convertElement | convertElement | — | — | — | — | — | convert_element(item, sub_type) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.modelType | modelType | — | — | — | — | — | model_type?(type_name) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.convertToType | convertToType | — | — | — | — | — | convert_to_type(data, return_type) ✗ | — | — | — | — | — | convert_to_type(data, type) ✗ | D14 D15 |
| ObjectSerializer.deserializeModel | deserializeModel | — | — | — | — | — | deserialize_model(data, klass) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.applyFormatOnDeserialize | applyFormatOnDeserialize | — | — | — | — | — | apply_format_on_deserialize(value, fmt) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.decodeByte | decodeByte | — | — | — | — | — | decode_byte(value) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.validateUuid | validateUuid | — | — | — | — | — | validate_uuid(value) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.validateUri | validateUri | — | — | — | — | — | validate_uri(value) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.durationToProtobufJson | durationToProtobufJson | — | — | — | — | — | duration_to_protobuf_json(duration) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.durationFromProtobufJson | durationFromProtobufJson | — | — | — | — | — | duration_from_protobuf_json(value) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.applyFormatOnSerialize | applyFormatOnSerialize | — | — | — | — | — | apply_format_on_serialize(value, fmt) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.encodeByte | encodeByte | — | — | — | — | — | encode_byte(value) ✗ | — | — | — | — | — | — | D14 |
| ObjectSerializer.durationToProtoJson | durationToProtoJson | — | — | — | — | — | — | durationToProtoJson(d) ✗ | — | — | — | — | — | D14 |
| ObjectSerializer.durationFromProtoJson | durationFromProtoJson | — | — | — | — | — | — | durationFromProtoJson(value) ✗ | — | — | — | — | — | D14 |
| ObjectSerializer.decodeBase64 | decodeBase64 | — | — | — | — | — | — | decodeBase64(value) ✗ | decodeBase64(text) ✗ | — | — | — | — | D14 D15 |
| ObjectSerializer.deserializeArray | deserializeArray | — | — | — | — | — | — | deserializeArray(json, cls?) ✗ | deserializeArray(json, deserializeElement) ✗ | — | — | — | — | D14 D15 |
| ObjectSerializer.deserializeMap | deserializeMap | — | — | — | — | — | — | deserializeMap(json, cls?) ✗ | deserializeMap(json, deserializeValue) ✗ | — | — | — | — | D14 D15 |
| ObjectSerializer.deserializeEnum | deserializeEnum | — | — | — | — | — | — | deserializeEnum(value, enumObj) ✗ | — | — | — | — | — | D14 |
| ObjectSerializer.deserializeLeaf | deserializeLeaf | — | — | — | — | — | — | deserializeLeaf(value, kind) ✗ | — | — | — | — | — | D14 |
| ObjectSerializer.deserializeList | deserializeList | — | — | — | — | — | — | — | deserializeList(data, fromJson) ✗ | — | — | — | — | D14 |
| ObjectSerializer.wireList | wireList | — | — | — | — | — | — | — | wireList(source, encoded) ✗ | — | — | — | — | D14 |
| ObjectSerializer.wireListOrNull | wireListOrNull | — | — | — | — | — | — | — | wireListOrNull(source, encoded) ✗ | — | — | — | — | D14 |
| ObjectSerializer.deserializeMapFromJson | deserializeMapFromJson | — | — | — | — | — | — | — | deserializeMapFromJson(data, deserializeValue) ✗ | — | — | — | — | D14 |
| ObjectSerializer.deserializeArrayFromJson | deserializeArrayFromJson | — | — | — | — | — | — | — | deserializeArrayFromJson(data, deserializeElement) ✗ | — | — | — | — | D14 |
| ObjectSerializer.deserializeRaw | deserializeRaw | — | — | — | — | — | — | — | deserializeRaw(data) ✗ | — | — | — | — | D14 |
| ObjectSerializer.parseDateTime | parseDateTime | — | — | — | — | — | — | — | parseDateTime(text) ✗ | — | — | — | — | D14 |
| ObjectSerializer.parseUri | parseUri | — | — | — | — | — | — | — | parseUri(text) ✗ | — | — | — | — | D14 |
| ObjectSerializer.formatDateTimeOffset | formatDateTimeOffset | — | — | — | — | — | — | — | formatDateTimeOffset(date) ✗ | — | — | — | — | D14 |
| ObjectSerializer.toDeepObjectMap | toDeepObjectMap | — | — | — | — | — | — | — | — | — | to_deep_object_map(value) ✗ | — | — | D14 |
| ObjectSerializer.serializeExtras | serializeExtras | — | — | — | — | — | — | — | — | — | serialize_extras(extras, declared, serializer) ✗ | — | — | D14 |
| ObjectSerializer.findAndCastIntoType | findAndCastIntoType | — | — | — | — | — | — | — | — | — | — | — | find_and_cast_into_type(type_name, data) ✗ | D14 |
| **TraceContextUtil** | TraceContextUtil | TraceContextUtil | TraceContextUtil | TraceContextUtil | TraceContextUtil | trace_context_util | TraceContextUtil | trace-context-util | TraceContextUtil | trace_context_util | trace_context_util | TraceContextUtil | TraceContextUtil |  |
| TraceContextUtil.injectTraceContext | injectTraceContext(headers) | injectTraceContext(headers) | injectTraceContext(headers) | InjectTraceContext(headers) | injectTraceContext(headers) | inject_trace_context(headers) | inject_trace_context(headers) | injectTraceContext(headers) | injectTraceContext(headers) | InjectTraceContext(headers) | inject_trace_context(headers) | injectTraceContext(headers) | inject_trace_context(headers) |  |
| TraceContextUtil.propagator | propagator | — | — | — | — | — | — | — | propagator ~ | — | — | propagator ~ | — | idiom (allow-list) |
| TraceContextUtil.setTraceContextPropagator | setTraceContextPropagator | — | — | — | — | — | — | — | — | SetTraceContextPropagator(fn) ~ | — | — | — | idiom (allow-list) |
| **HeaderSelector** | (internal) | — ✗ | — ✗ | — ✗ | HeaderSelector | HeaderSelector | HeaderSelector | HeaderSelector | HeaderSelector | — ✗ | HeaderSelector | — ✗ | HeaderSelector | D08 |
| HeaderSelector ctor | — | — | — | — | — | — | — | — | — | — | () | — | — |  |
| HeaderSelector.selectHeaders | selectHeaders(accept, contentType, isMultipart) | — | — | — | selectHeaders(accept, contentType, isMultipart) | select_headers(accept, content_type, is_multipart) | select_headers(accept, content_type, is_multipart) | selectHeaders(accept, contentType, isMultipart) | selectHeaders(accept, contentType, isMultipart) | — | select_headers(accept, content_type, is_multipart) | — | select_headers(accept, content_type, is_multipart) |  |
| HeaderSelector.isJsonMime | isJsonMime(searchString) | — | — | — | isJsonMime(searchString) | is_json_mime(search_string) | — | isJsonMime(searchString) | isJsonMime(searchString) | — | is_json_mime(search_string) | — | — |  |
| HeaderSelector.jsonMimePattern | jsonMimePattern | — | — | — | — | — | JSON_MIME_PATTERN ✗ | — | — | — | — | — | — | D10 |
| HeaderSelector.jsonMime | jsonMime(searchString) | — | — | — | — | — | json_mime?(search_string) | — | — | — | — | — | json_mime?(search_string) |  |
| **ValueSerializer** | (internal) | — ✗ | — ✗ | — ✗ | ValueSerializer | ValueSerializer | ValueSerializer | ValueSerializer | value_serializer | — ✗ | value_serializer | — ✗ | ValueSerializer | D07 |
| ValueSerializer.serialize | serialize(value, location, schemaType, collectionFormat?) | — | — | — | serialize(value, location, schemaType, collectionFormat?) | serialize(value, location, schema_type, collection_format?) | serialize(value, location, _schema_type, collection_format?) | serialize(value, location, schemaType, collectionFormat?) | — ✗ | — | — ✗ | — | serialize(value, location, schema_type, opts?) | D09 |
| ValueSerializer.formatDateOnly | formatDateOnly | — | — | — | formatDateOnly(value) ✗ | — | — | — | — | — | — | — | — | D09 |
| ValueSerializer.encodePathSegment | encodePathSegment(value) | — | — | — | encodePathSegment(value) | encode_path_segment(value) | encode_path_segment(value) | encodePathSegment(value) | encodePathSegment(s) ✗ | — | — ✗ | — | encode_path_segment(value) | D09 |
| ValueSerializer.encodeQueryAllowingReserved | encodeQueryAllowingReserved(value) | — | — | — | encodeQueryAllowingReserved(value) | encode_query_allowing_reserved(value) | encode_query_allowing_reserved(value) | encodeQueryAllowingReserved(value) | encodeQueryAllowingReserved(s) ✗ | — | encode_query_allowing_reserved(s) ✗ | — | encode_query_allowing_reserved(value) | D09 |
| ValueSerializer.serializeStyled | serializeStyled(paramName, value, location, schemaType, collectionFormat, style, explode) | — | — | — | serializeStyled(paramName, value, location, schemaType, collectionFormat, style, explode) | serialize_styled(param_name, value, location, schema_type, collection_format?, style?, explode?) ✗ | serialize_styled(param_name, value, location, schema_type, collection_format, style, explode) | serializeStyled(paramName, value, location, schemaType, collectionFormat, style, explode) | serializeStyled(paramName, value, location, schemaType, collectionFormat, style, explode) | — | serialize_styled(param_name, value, items, location, _schema_type, _collection_format, style, explode) ✗ | — | serialize_styled(param_name, value, location, schema_type, collection_format, style, explode) | D09 |
| ValueSerializer.serializeDeepObject | serializeDeepObject(paramName, value) | — | — | — | serializeDeepObject(paramName, value) | serialize_deep_object(param_name, value) | serialize_deep_object(param_name, value) | serializeDeepObject(paramName, value) | serializeDeepObject(paramName, value) | — | serialize_deep_object(param_name, value) | — | serialize_deep_object(param_name, value) |  |
| ValueSerializer.maybeAllowReserved | maybeAllowReserved(value, allowReserved) | — | — | — | — ✗ | maybe_allow_reserved(value, allow_reserved) | maybe_allow_reserved(value, allow_reserved) | maybeAllowReserved(value, allowReserved) | maybeAllowReserved(value, allowReserved) | — | — ✗ | — | maybe_allow_reserved(value, allow_reserved) | D09 |
| ValueSerializer.preserveEncoded | preserveEncoded | — | — | — | — | — | PRESERVE_ENCODED ✗ | — | — | — | — | — | — | D09 |
| ValueSerializer.reservedPreserved | reservedPreserved | — | — | — | — | — | RESERVED_PRESERVED ✗ | — | — | — | — | — | — | D09 |
| ValueSerializer.serializeNil | serializeNil | — | — | — | — | — | serialize_nil(location) ✗ | — | — | — | — | — | — | D09 |
| ValueSerializer.serializeArray | serializeArray | — | — | — | — | — | serialize_array(value, location, collection_format) ✗ | — | — | — | — | — | — | D09 |
| ValueSerializer.serializeQueryArray | serializeQueryArray | — | — | — | — | — | serialize_query_array(value, collection_format) ✗ | — | — | — | — | — | — | D09 |
| ValueSerializer.encodeItem | encodeItem | — | — | — | — | — | encode_item(value, location) ✗ | — | — | — | — | — | — | D09 |
| ValueSerializer.normalize | normalize | — | — | — | — | — | — | normalize(value, schemaType, paramName) ✗ | — | — | — | — | — | D09 |
| ValueSerializer.stringifyDate | stringifyDate | — | — | — | — | — | — | stringifyDate(value) ✗ | stringifyDate(value) ✗ | — | — | — | — | D09 |
| ValueSerializer.encodeQueryComponent | encodeQueryComponent | — | — | — | — | — | — | encodeQueryComponent(value) ✗ | — | — | — | — | — | D09 |
| ValueSerializer.serializeValue | serializeValue | — | — | — | — | — | — | — | serializeValue(value, location, schemaType, collectionFormat) ✗ | — | serialize_value(value, location, _schema_type, _collection_format) ✗ | — | — | D09 |
| ValueSerializer.encodeRfc3986QueryComponent | encodeRfc3986QueryComponent | — | — | — | — | — | — | — | encodeRfc3986QueryComponent(s, spaceToPlus?) ✗ | — | — | — | — | D09 |
| ValueSerializer.serializeArrayValue | serializeArrayValue | — | — | — | — | — | — | — | — | — | serialize_array_value(items, location, collection_format) ✗ | — | — | D09 |
| **AllowReservedValue** | (internal) | — | — | — | — | AllowReservedValue ✗ | — | AllowReservedValue ✗ | AllowReservedValue ✗ | — | — | — | — | D11 |
| AllowReservedValue ctor | new(value) | — | — | — | — | (value) | — | (value) | (value) | — | — | — | — |  |
| AllowReservedValue.value | value | — | — | — | — | value | — | value | value | — | — | — | — |  |

## Language-only types

| Element | Canonical | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **PreciseDuration** | PreciseDuration | — | — | — | PreciseDuration ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| PreciseDuration ctor | new() | — | — | — | () | — | — | — | — | — | — | — | — |  |
| **Psr18ApiClient** | Psr18ApiClient | — | — | — | Psr18ApiClient ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| Psr18ApiClient ctor | new(httpClient, requestFactory, streamFactory, transportOptions?) | — | — | — | (httpClient, requestFactory, streamFactory, transportOptions?) | — | — | — | — | — | — | — | — |  |
| **RawHttpResponse** | RawHttpResponse | — | — | — | RawHttpResponse ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| RawHttpResponse ctor | new(statusCode, body, headers) | — | — | — | (statusCode, body, headers) | — | — | — | — | — | — | — | — |  |
| **NoAuth** | NoAuth | — | — | — | NoAuth ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **DsAwareObjectNormalizer** | DsAwareObjectNormalizer | — | — | — | DsAwareObjectNormalizer ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| DsAwareObjectNormalizer ctor | new(classMetadataFactory?, nameConverter?, propertyTypeExtractor?, propertyAccessor?, defaultContext?) | — | — | — | (classMetadataFactory?, nameConverter?, propertyTypeExtractor?, propertyAccessor?, defaultContext?) | — | — | — | — | — | — | — | — |  |
| **DsMapNormalizer** | DsMapNormalizer | — | — | — | DsMapNormalizer ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **DsSetNormalizer** | DsSetNormalizer | — | — | — | DsSetNormalizer ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **DsVectorNormalizer** | DsVectorNormalizer | — | — | — | DsVectorNormalizer ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **DurationNormalizer** | DurationNormalizer | — | — | — | DurationNormalizer ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **UriNormalizer** | UriNormalizer | — | — | — | UriNormalizer ~ | — | — | — | — | — | — | — | — | idiom (allow-list) |
| **DefaultApiClientQueryParamsEncoder** | DefaultApiClientQueryParamsEncoder | — | — | — | — | — | DefaultApiClientQueryParamsEncoder ~ | — | — | — | — | — | — | idiom (allow-list) |
| **Types** | Types | — | — | — | — | — | Types ~ | — | — | — | — | — | — | idiom (allow-list) |
| **ValueSerializerAllowReservedValue** | (internal) | — | — | — | — | — | ValueSerializerAllowReservedValue ✗ | — | — | — | — | — | — | D12 |
| ValueSerializerAllowReservedValue ctor | — | — | — | — | — | — | (value) | — | — | — | — | — | — |  |
| **PetTreatmentComposite** | PetTreatmentComposite | — | — | — | — | — | PetTreatmentComposite ~ | — | — | — | — | — | — | idiom (allow-list) |
| PetTreatmentComposite ctor | — | — | — | — | — | — | (instances) | — | — | — | — | — | — |  |
| **TreatmentRecordComposite** | TreatmentRecordComposite | — | — | — | — | — | TreatmentRecordComposite ~ | — | — | — | — | — | — | idiom (allow-list) |
| TreatmentRecordComposite ctor | — | — | — | — | — | — | (instances) | — | — | — | — | — | — |  |
| **SendRequestOptions** | SendRequestOptions | — | — | — | — | — | — | SendRequestOptions ~ | — | — | — | — | — | idiom (allow-list) |
| **Uuid** | Uuid | — | — | — | — | — | — | UUID ~ | — | — | — | — | — | idiom (allow-list) |
| **Brand** | Brand | — | — | — | — | — | — | brand ~ | — | — | — | — | — | idiom (allow-list) |
| **Uri** | Uri | — | — | — | — | — | — | URI ~ | — | — | — | — | — | idiom (allow-list) |
| **Email** | Email | — | — | — | — | — | — | Email ~ | — | — | — | — | — | idiom (allow-list) |
| **Decimal** | Decimal | — | — | — | — | — | — | Decimal ~ | — | — | — | — | — | idiom (allow-list) |
| **Primitive** | Primitive | — | — | — | — | — | — | Primitive ~ | — | — | — | — | — | idiom (allow-list) |
| **DeepInput** | DeepInput | — | — | — | — | — | — | DeepInput ~ | — | — | — | — | — | idiom (allow-list) |
| **FindPetsByStatusStatusEnum** | FindPetsByStatusStatusEnum | — | — | — | — | — | — | FindPetsByStatusStatusEnum ~ | — | — | — | — | — | idiom (allow-list) |
| **UploadPetDocumentDocumentTypeEnum** | UploadPetDocumentDocumentTypeEnum | — | — | — | — | — | — | UploadPetDocumentDocumentTypeEnum ~ | — | — | — | — | — | idiom (allow-list) |
| **StatusErrorFactory** | StatusErrorFactory | — | — | — | — | — | — | StatusErrorFactory ~ | — | — | — | — | — | idiom (allow-list) |
| **PetFoodType** | PetFoodType | — | — | — | — | — | — | PetFoodType ~ | — | — | — | — | — | idiom (allow-list) |
| **PetTreatmentType** | PetTreatmentType | — | — | — | — | — | — | PetTreatmentType ~ | — | — | — | — | — | idiom (allow-list) |
| **TreatmentRecordType** | TreatmentRecordType | — | — | — | — | — | — | TreatmentRecordType ~ | — | — | — | — | — | idiom (allow-list) |
| **Iso8601Duration** | Iso8601Duration | — | — | — | — | — | — | — | iso8601_duration ~ | — | — | ISO8601Duration ~ | — | idiom (allow-list) |
| Iso8601Duration.formatProtobufDuration | formatProtobufDuration(duration) | — | — | — | — | — | — | — | formatProtobufDuration(duration) | — | — | formatProtobufDuration(interval) ✗ | — | Parameters `(duration)` (majority) in swift. |
| Iso8601Duration.validatePartialTime | validatePartialTime(input) | — | — | — | — | — | — | — | validatePartialTime(input) | — | — | — ✗ | — | Add `validatepartialtime` in swift. |
| **UuidValue** | UuidValue | — | — | — | — | — | — | — | UuidValue ~ | — | — | — | — | idiom (allow-list) |
| **NoAuthAuthenticator** | NoAuthAuthenticator | — | — | — | — | — | — | — | NoAuthAuthenticator ~ | — | — | — | — | idiom (allow-list) |
| **RequestOptions** | RequestOptions | — | — | — | — | — | — | — | — | RequestOptions ~ | RequestOptions ~ | — | — | idiom (allow-list) |
| RequestOptions ctor | — | — | — | — | — | — | — | — | — | — | () | — | — |  |
| **Errors** | Errors | — | — | — | — | — | — | — | — | errors ~ | — | — | — | idiom (allow-list) |
| **Iso8601** | Iso8601 | — | — | — | — | — | — | — | — | iso8601 ~ | — | — | — | idiom (allow-list) |
| Iso8601.marshalDurationProtoJson | marshalDurationProtoJson | — | — | — | — | — | — | — | — | MarshalDurationProtoJSON(d) ✗ | — | — | — | D82 |
| Iso8601.unmarshalDurationProtoJson | unmarshalDurationProtoJson | — | — | — | — | — | — | — | — | UnmarshalDurationProtoJSON(s) ✗ | — | — | — | D82 |
| **TraceContextPropagatorFunc** | TraceContextPropagatorFunc | — | — | — | — | — | — | — | — | TraceContextPropagatorFunc ~ | — | — | — | idiom (allow-list) |
| **DateTime** | DateTime | — | — | — | — | — | — | — | — | DateTime ~ | DateTime ~ | — | — | idiom (allow-list) |
| DateTime ctor | — | — | — | — | — | — | — | — | — | — | (value) | — | — |  |
| DateTime.time | time | — | — | — | — | — | — | — | — | Time | — ✗ | — | — | Add `time` in rust. |
| DateTime.intoInner | intoInner | — | — | — | — | — | — | — | — | — ✗ | into_inner | — | — | Add `intoinner` in go. |
| **Duration** | Duration | — | — | — | — | — | — | — | — | Duration ~ | — | — | — | idiom (allow-list) |
| **Set** | Set | — | — | — | — | — | — | — | — | Set ~ | — | — | — | idiom (allow-list) |
| **RequestBody** | RequestBody | — | — | — | — | — | — | — | — | — | RequestBody ~ | — | — | idiom (allow-list) |
| **MultipartValue** | MultipartValue | — | — | — | — | — | — | — | — | — | MultipartValue ~ | — | — | idiom (allow-list) |
| **JsonValue** | JsonValue | — | — | — | — | — | — | — | — | — | JsonValue ~ | — | — | idiom (allow-list) |
| JsonValue ctor | — | — | — | — | — | — | — | — | — | — | (value) | — | — |  |
| **QueryValue** | QueryValue | — | — | — | — | — | — | — | — | — | QueryValue ~ | — | — | idiom (allow-list) |
| **ProtoDuration** | ProtoDuration | — | — | — | — | — | — | — | — | — | proto_duration ~ | — | — | idiom (allow-list) |
| **SerializedValue** | SerializedValue | — | — | — | — | — | — | — | — | — | SerializedValue ~ | — | — | idiom (allow-list) |
| **ApiErrorFields** | ApiErrorFields | — | — | — | — | — | — | — | — | — | ApiErrorFields ~ | — | — | idiom (allow-list) |
| **ApiErrorKind** | ApiErrorKind | — | — | — | — | — | — | — | — | — | ApiErrorKind ~ | — | — | idiom (allow-list) |
| **Base64Serde** | Base64Serde | — | — | — | — | — | — | — | — | — | base64_serde ~ | — | — | idiom (allow-list) |
| **FormUrlEncode** | FormUrlEncode | — | — | — | — | — | — | — | — | — | form_url_encode ~ | — | — | idiom (allow-list) |
| **AnyCodable** | AnyCodable | — | — | — | — | — | — | — | — | — | — | AnyCodable ~ | — | idiom (allow-list) |
| AnyCodable ctor | — | — | — | — | — | — | — | — | — | — | — | (value) | — |  |
| **Indirect** | Indirect | — | — | — | — | — | — | — | — | — | — | Indirect ~ | — | idiom (allow-list) |
| Indirect ctor | — | — | — | — | — | — | — | — | — | — | — | (wrappedValue) | — |  |
| **AnyOfComposite** | AnyOfComposite | — | — | — | — | — | — | — | — | — | — | — | AnyOfComposite ~ | idiom (allow-list) |
| AnyOfComposite ctor | — | — | — | — | — | — | — | — | — | — | — | — | (instances?, fields?) |  |

## Initialism casing

D82 applies. Every identifier below spells an initialism in capitals where the canonical form word-cases it; rename to the right-hand form.

| Language | Element | Actual | Canonical |
| --- | --- | --- | --- |
| go | configuration.baseurl | BaseURL | BaseUrl |
| go | configurationbuilder.baseurl | BaseURL | BaseUrl |
| go | iso8601.marshaldurationprotojson | MarshalDurationProtoJSON | MarshalDurationProtoJson |
| go | iso8601.unmarshaldurationprotojson | UnmarshalDurationProtoJSON | UnmarshalDurationProtoJson |
| go | petapi.addpetwithhttpinfo | AddPetWithHTTPInfo | AddPetWithHttpInfo |
| go | petapi.addpetphotoswithhttpinfo | AddPetPhotosWithHTTPInfo | AddPetPhotosWithHttpInfo |
| go | petapi.addpettreatmentwithhttpinfo | AddPetTreatmentWithHTTPInfo | AddPetTreatmentWithHttpInfo |
| go | petapi.deletepetwithhttpinfo | DeletePetWithHTTPInfo | DeletePetWithHttpInfo |
| go | petapi.downloadpetdocumentwithhttpinfo | DownloadPetDocumentWithHTTPInfo | DownloadPetDocumentWithHttpInfo |
| go | petapi.findpetsbornonwithhttpinfo | FindPetsBornOnWithHTTPInfo | FindPetsBornOnWithHttpInfo |
| go | petapi.findpetsbystatuswithhttpinfo | FindPetsByStatusWithHTTPInfo | FindPetsByStatusWithHttpInfo |
| go | petapi.getexternalpetinfowithhttpinfo | GetExternalPetInfoWithHTTPInfo | GetExternalPetInfoWithHttpInfo |
| go | petapi.getmultiserverpetinfowithhttpinfo | GetMultiServerPetInfoWithHTTPInfo | GetMultiServerPetInfoWithHttpInfo |
| go | petapi.getpetavatarwithhttpinfo | GetPetAvatarWithHTTPInfo | GetPetAvatarWithHttpInfo |
| go | petapi.getpetavatarthumbnailwithhttpinfo | GetPetAvatarThumbnailWithHTTPInfo | GetPetAvatarThumbnailWithHttpInfo |
| go | petapi.getpetbyidwithhttpinfo | GetPetByIdWithHTTPInfo | GetPetByIdWithHttpInfo |
| go | petapi.getpetbynamewithhttpinfo | GetPetByNameWithHTTPInfo | GetPetByNameWithHttpInfo |
| go | petapi.getpetpassportwithhttpinfo | GetPetPassportWithHTTPInfo | GetPetPassportWithHttpInfo |
| go | petapi.getpetphotowithhttpinfo | GetPetPhotoWithHTTPInfo | GetPetPhotoWithHttpInfo |
| go | petapi.getpetsummarywithhttpinfo | GetPetSummaryWithHTTPInfo | GetPetSummaryWithHttpInfo |
| go | petapi.getpettagwithhttpinfo | GetPetTagWithHTTPInfo | GetPetTagWithHttpInfo |
| go | petapi.getstagingpetinfowithhttpinfo | GetStagingPetInfoWithHTTPInfo | GetStagingPetInfoWithHttpInfo |
| go | petapi.setpetavatarwithhttpinfo | SetPetAvatarWithHTTPInfo | SetPetAvatarWithHttpInfo |
| go | petapi.setpetavatarthumbnailwithhttpinfo | SetPetAvatarThumbnailWithHTTPInfo | SetPetAvatarThumbnailWithHttpInfo |
| go | petapi.setpetpreferenceswithhttpinfo | SetPetPreferencesWithHTTPInfo | SetPetPreferencesWithHttpInfo |
| go | petapi.updatepetwithhttpinfo | UpdatePetWithHTTPInfo | UpdatePetWithHttpInfo |
| go | petapi.uploadpetcertificatewithhttpinfo | UploadPetCertificateWithHTTPInfo | UploadPetCertificateWithHttpInfo |
| go | petapi.uploadpetdocumentwithhttpinfo | UploadPetDocumentWithHTTPInfo | UploadPetDocumentWithHttpInfo |
| go | serverconfiguration.urltemplate | URLTemplate | UrlTemplate |
| go | storeapi.getdefaultswithhttpinfo | GetDefaultsWithHTTPInfo | GetDefaultsWithHttpInfo |
| go | storeapi.getdepartmentwithhttpinfo | GetDepartmentWithHTTPInfo | GetDepartmentWithHttpInfo |
| go | storeapi.getgroupedcategorieswithhttpinfo | GetGroupedCategoriesWithHTTPInfo | GetGroupedCategoriesWithHttpInfo |
| go | storeapi.getinventorywithhttpinfo | GetInventoryWithHTTPInfo | GetInventoryWithHttpInfo |
| go | storeapi.getmatrixwithhttpinfo | GetMatrixWithHTTPInfo | GetMatrixWithHttpInfo |
| go | storeapi.getswatchwithhttpinfo | GetSwatchWithHTTPInfo | GetSwatchWithHttpInfo |
| go | storeapi.getswatchgroupswithhttpinfo | GetSwatchGroupsWithHTTPInfo | GetSwatchGroupsWithHttpInfo |
| go | storeapi.gettimestampgroupswithhttpinfo | GetTimestampGroupsWithHTTPInfo | GetTimestampGroupsWithHttpInfo |
| go | storeapi.gettreewithhttpinfo | GetTreeWithHTTPInfo | GetTreeWithHttpInfo |
| go | storeapi.createaccountwithhttpinfo | CreateAccountWithHTTPInfo | CreateAccountWithHttpInfo |
| go | storeapi.deleteorderwithhttpinfo | DeleteOrderWithHTTPInfo | DeleteOrderWithHttpInfo |
| go | storeapi.getbyswatchwithhttpinfo | GetBySwatchWithHTTPInfo | GetBySwatchWithHttpInfo |
| go | storeapi.getorderbyidwithhttpinfo | GetOrderByIdWithHTTPInfo | GetOrderByIdWithHttpInfo |
| go | storeapi.getorderbytrackingidwithhttpinfo | GetOrderByTrackingIdWithHTTPInfo | GetOrderByTrackingIdWithHttpInfo |
| go | storeapi.getstockitemwithhttpinfo | GetStockItemWithHTTPInfo | GetStockItemWithHttpInfo |
| go | storeapi.placeorderwithhttpinfo | PlaceOrderWithHTTPInfo | PlaceOrderWithHttpInfo |
| go | storeapi.searchstorewithhttpinfo | SearchStoreWithHTTPInfo | SearchStoreWithHttpInfo |
| go | transportoptions.errinvalidproxyurl | ErrInvalidProxyURL | ErrInvalidProxyUrl |
| go | transportoptions.errinvalidcacertificate | ErrInvalidCACertificate | ErrInvalidCaCertificate |
| go | transportoptions.cacertpath | CACertPath | CaCertPath |
| go | transportoptions.injectrequestid | InjectRequestID | InjectRequestId |
| go | transportoptionsbuilder.cacertpath | CACertPath | CaCertPath |
| go | transportoptionsbuilder.injectrequestid | InjectRequestID | InjectRequestId |
| go | browserauthimplicitauthenticator.buildauthorizationurl | BuildAuthorizationURL | BuildAuthorizationUrl |
| go | oauth2authorizationcodeauthenticator.buildauthorizationurl | BuildAuthorizationURL | BuildAuthorizationUrl |
| go | oauth2authorizationcodeauthenticator.<ctor>(clientid) | clientID | clientId |
| go | oauth2authorizationcodeauthenticator.<ctor>(authorizationurl) | authorizationURL | authorizationUrl |
| go | oauth2authorizationcodeauthenticator.<ctor>(tokenurl) | tokenURL | tokenUrl |
| go | oauth2authorizationcodeauthenticator.<ctor>(redirecturi) | redirectURI | redirectUri |
| go | oauth2authorizationcodeauthenticator.<ctor>(refreshurl) | refreshURL | refreshUrl |
| go | oauth2clientcredentialsauthenticator.<ctor>(clientid) | clientID | clientId |
| go | oauth2clientcredentialsauthenticator.<ctor>(tokenurl) | tokenURL | tokenUrl |
| go | oauth2implicitauthenticator.buildauthorizationurl | BuildAuthorizationURL | BuildAuthorizationUrl |
| go | oauth2implicitauthenticator.<ctor>(clientid) | clientID | clientId |
| go | oauth2implicitauthenticator.<ctor>(authorizationurl) | authorizationURL | authorizationUrl |
| go | oauth2passwordauthenticator.<ctor>(clientid) | clientID | clientId |
| go | oauth2passwordauthenticator.<ctor>(tokenurl) | tokenURL | tokenUrl |
| go | oauth2passwordauthenticator.<ctor>(refreshurl) | refreshURL | refreshUrl |
| go | oauth2tokenmanager.getaccesstoken(tokenurl) | tokenURL | tokenUrl |
| go | oauth2tokenmanager.getaccesstokenwithheaders(tokenurl) | tokenURL | tokenUrl |
| go | oauth2tokenmanager.getaccesstokenwithrefresh(tokenurl) | tokenURL | tokenUrl |
| go | oauth2tokenmanager.getaccesstokenwithrefresh(refreshurl) | refreshURL | refreshUrl |
| go | oauth2tokenmanager.exchange(tokenurl) | tokenURL | tokenUrl |
| go | openidconnectauthenticator.buildauthorizationurl | BuildAuthorizationURL | BuildAuthorizationUrl |
| go | openidconnectauthenticator.<ctor>(openidconnecturl) | openIDConnectURL | openIdConnectUrl |
| go | openidconnectauthenticator.<ctor>(clientid) | clientID | clientId |
| go | openidconnectauthenticator.<ctor>(redirecturi) | redirectURI | redirectUri |
| go | ssoauthauthenticator.buildauthorizationurl | BuildAuthorizationURL | BuildAuthorizationUrl |
| go | userauthauthorizationcodeauthenticator.buildauthorizationurl | BuildAuthorizationURL | BuildAuthorizationUrl |
| swift | configuration.baseurl | baseURL | baseUrl |
| swift | configurationbuilder.baseurl | baseURL | baseUrl |
| swift | transportoptions.injectrequestid | injectRequestID | injectRequestId |
| swift | transportoptionsbuilder.injectrequestid | injectRequestID | injectRequestId |
| swift | petapi.addpetwithhttpinfo | addPetWithHTTPInfo | addPetWithHttpInfo |
| swift | petapi.addpetphotoswithhttpinfo | addPetPhotosWithHTTPInfo | addPetPhotosWithHttpInfo |
| swift | petapi.addpettreatmentwithhttpinfo | addPetTreatmentWithHTTPInfo | addPetTreatmentWithHttpInfo |
| swift | petapi.deletepetwithhttpinfo | deletePetWithHTTPInfo | deletePetWithHttpInfo |
| swift | petapi.downloadpetdocumentwithhttpinfo | downloadPetDocumentWithHTTPInfo | downloadPetDocumentWithHttpInfo |
| swift | petapi.findpetsbornonwithhttpinfo | findPetsBornOnWithHTTPInfo | findPetsBornOnWithHttpInfo |
| swift | petapi.findpetsbystatuswithhttpinfo | findPetsByStatusWithHTTPInfo | findPetsByStatusWithHttpInfo |
| swift | petapi.getexternalpetinfowithhttpinfo | getExternalPetInfoWithHTTPInfo | getExternalPetInfoWithHttpInfo |
| swift | petapi.getmultiserverpetinfowithhttpinfo | getMultiServerPetInfoWithHTTPInfo | getMultiServerPetInfoWithHttpInfo |
| swift | petapi.getpetavatarwithhttpinfo | getPetAvatarWithHTTPInfo | getPetAvatarWithHttpInfo |
| swift | petapi.getpetavatarthumbnailwithhttpinfo | getPetAvatarThumbnailWithHTTPInfo | getPetAvatarThumbnailWithHttpInfo |
| swift | petapi.getpetbyidwithhttpinfo | getPetByIdWithHTTPInfo | getPetByIdWithHttpInfo |
| swift | petapi.getpetbynamewithhttpinfo | getPetByNameWithHTTPInfo | getPetByNameWithHttpInfo |
| swift | petapi.getpetpassportwithhttpinfo | getPetPassportWithHTTPInfo | getPetPassportWithHttpInfo |
| swift | petapi.getpetphotowithhttpinfo | getPetPhotoWithHTTPInfo | getPetPhotoWithHttpInfo |
| swift | petapi.getpetsummarywithhttpinfo | getPetSummaryWithHTTPInfo | getPetSummaryWithHttpInfo |
| swift | petapi.getpettagwithhttpinfo | getPetTagWithHTTPInfo | getPetTagWithHttpInfo |
| swift | petapi.getstagingpetinfowithhttpinfo | getStagingPetInfoWithHTTPInfo | getStagingPetInfoWithHttpInfo |
| swift | petapi.setpetavatarwithhttpinfo | setPetAvatarWithHTTPInfo | setPetAvatarWithHttpInfo |
| swift | petapi.setpetavatarthumbnailwithhttpinfo | setPetAvatarThumbnailWithHTTPInfo | setPetAvatarThumbnailWithHttpInfo |
| swift | petapi.setpetpreferenceswithhttpinfo | setPetPreferencesWithHTTPInfo | setPetPreferencesWithHttpInfo |
| swift | petapi.updatepetwithhttpinfo | updatePetWithHTTPInfo | updatePetWithHttpInfo |
| swift | petapi.uploadpetcertificatewithhttpinfo | uploadPetCertificateWithHTTPInfo | uploadPetCertificateWithHttpInfo |
| swift | petapi.uploadpetdocumentwithhttpinfo | uploadPetDocumentWithHTTPInfo | uploadPetDocumentWithHttpInfo |
| swift | storeapi.getdefaultswithhttpinfo | getDefaultsWithHTTPInfo | getDefaultsWithHttpInfo |
| swift | storeapi.getdepartmentwithhttpinfo | getDepartmentWithHTTPInfo | getDepartmentWithHttpInfo |
| swift | storeapi.getgroupedcategorieswithhttpinfo | getGroupedCategoriesWithHTTPInfo | getGroupedCategoriesWithHttpInfo |
| swift | storeapi.getinventorywithhttpinfo | getInventoryWithHTTPInfo | getInventoryWithHttpInfo |
| swift | storeapi.getmatrixwithhttpinfo | getMatrixWithHTTPInfo | getMatrixWithHttpInfo |
| swift | storeapi.getswatchwithhttpinfo | getSwatchWithHTTPInfo | getSwatchWithHttpInfo |
| swift | storeapi.getswatchgroupswithhttpinfo | getSwatchGroupsWithHTTPInfo | getSwatchGroupsWithHttpInfo |
| swift | storeapi.gettimestampgroupswithhttpinfo | getTimestampGroupsWithHTTPInfo | getTimestampGroupsWithHttpInfo |
| swift | storeapi.gettreewithhttpinfo | getTreeWithHTTPInfo | getTreeWithHttpInfo |
| swift | storeapi.createaccountwithhttpinfo | createAccountWithHTTPInfo | createAccountWithHttpInfo |
| swift | storeapi.deleteorderwithhttpinfo | deleteOrderWithHTTPInfo | deleteOrderWithHttpInfo |
| swift | storeapi.getbyswatchwithhttpinfo | getBySwatchWithHTTPInfo | getBySwatchWithHttpInfo |
| swift | storeapi.getorderbyidwithhttpinfo | getOrderByIdWithHTTPInfo | getOrderByIdWithHttpInfo |
| swift | storeapi.getorderbytrackingidwithhttpinfo | getOrderByTrackingIdWithHTTPInfo | getOrderByTrackingIdWithHttpInfo |
| swift | storeapi.getstockitemwithhttpinfo | getStockItemWithHTTPInfo | getStockItemWithHttpInfo |
| swift | storeapi.placeorderwithhttpinfo | placeOrderWithHTTPInfo | placeOrderWithHttpInfo |
| swift | storeapi.searchstorewithhttpinfo | searchStoreWithHTTPInfo | searchStoreWithHttpInfo |
| swift | browserauthimplicitauthenticator.buildauthorizationurl | buildAuthorizationURL | buildAuthorizationUrl |
| swift | oauth2authorizationcodeauthenticator.buildauthorizationurl | buildAuthorizationURL | buildAuthorizationUrl |
| swift | oauth2authorizationcodeauthenticator.<ctor>(clientid) | clientID | clientId |
| swift | oauth2authorizationcodeauthenticator.<ctor>(authorizationurl) | authorizationURL | authorizationUrl |
| swift | oauth2authorizationcodeauthenticator.<ctor>(tokenurl) | tokenURL | tokenUrl |
| swift | oauth2authorizationcodeauthenticator.<ctor>(redirecturi) | redirectURI | redirectUri |
| swift | oauth2authorizationcodeauthenticator.<ctor>(refreshurl) | refreshURL | refreshUrl |
| swift | oauth2clientcredentialsauthenticator.<ctor>(clientid) | clientID | clientId |
| swift | oauth2clientcredentialsauthenticator.<ctor>(tokenurl) | tokenURL | tokenUrl |
| swift | oauth2implicitauthenticator.buildauthorizationurl | buildAuthorizationURL | buildAuthorizationUrl |
| swift | oauth2implicitauthenticator.<ctor>(clientid) | clientID | clientId |
| swift | oauth2implicitauthenticator.<ctor>(authorizationurl) | authorizationURL | authorizationUrl |
| swift | oauth2passwordauthenticator.<ctor>(clientid) | clientID | clientId |
| swift | oauth2passwordauthenticator.<ctor>(tokenurl) | tokenURL | tokenUrl |
| swift | oauth2passwordauthenticator.<ctor>(refreshurl) | refreshURL | refreshUrl |
| swift | oauth2tokenmanager.getaccesstoken(tokenurl) | tokenURL | tokenUrl |
| swift | oauth2tokenmanager.getaccesstoken(refreshurl) | refreshURL | refreshUrl |
| swift | oauth2tokenmanager.exchange(tokenurl) | tokenURL | tokenUrl |
| swift | openidconnectauthenticator.buildauthorizationurl | buildAuthorizationURL | buildAuthorizationUrl |
| swift | openidconnectauthenticator.<ctor>(openidconnecturl) | openIDConnectURL | openIdConnectUrl |
| swift | openidconnectauthenticator.<ctor>(clientid) | clientID | clientId |
| swift | openidconnectauthenticator.<ctor>(redirecturi) | redirectURI | redirectUri |
| swift | ssoauthauthenticator.buildauthorizationurl | buildAuthorizationURL | buildAuthorizationUrl |
| swift | userauthauthorizationcodeauthenticator.buildauthorizationurl | buildAuthorizationURL | buildAuthorizationUrl |

