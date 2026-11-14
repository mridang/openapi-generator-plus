import json, os, re, sys, collections, importlib.util, fnmatch

ROOT = "/Users/mridang/Code/mridang/openapi-generator-plus/.claude/worktrees/api-surface"
spec = importlib.util.spec_from_file_location("a", ROOT + "/etc/api-surface.py")
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)
L = A.LANGS
S = {l: A.extract(l) for l in L}
mm = json.load(open("/tmp/claude-501/mm.json"))

# ------------------------------------------------------------ curated decisions
# key: (element-glob, aspect-glob) -> dict(decision=..., changes={lang: text} | None, canon=...)
CUR = []


def cur(el, asp, decision, changes=None, canon=None):
    CUR.append((el, asp, {"decision": decision, "changes": changes, "canon": canon}))


cur("error", "type", "Model named `Error` (majority). go renames `ModelError` to `Error` (no clash in go). java and swift keep `ModelError` (forced: java.lang.Error / Swift.Error).",
    {"go": "rename model `ModelError` to `Error` (BetterGoCodegen.toModelName collision rename)"}, "Error")
cur("modelerror", "type", "See `Error`: `ModelError` survives only where forced (java, swift).",
    {"go": "rename `ModelError` to `Error` (BetterGoCodegen collision rename)"}, "Error")
cur("result", "type", "Model named `Result` (majority). java renames `ModelResult` to `Result` (java has no implicit Result). rust and swift keep `ModelResult` (forced: prelude std Result / Swift.Result).",
    {"java": "rename model `ModelResult` to `Result` (BetterJavaCodegen.toModelName/getTypeDeclaration: drop \"Result\" from the Model-prefix set, keep \"Error\")"}, "Result")
cur("modelresult", "type", "See `Result`: `ModelResult` survives only where forced (rust, swift).",
    {"java": "rename `ModelResult` to `Result` (BetterJavaCodegen)"}, "Result")
cur("client", "type", "Entry point is a class named `Client` in every SDK. ruby renames `Petstore::Client::Petstore` to `Petstore::Client::Client` (a class may share its namespace's name).",
    {"ruby": "rename class `Petstore` to `Client` (client.mustache, sig, petstore_client.rb require)"}, "Client")
cur("petstore", "type", "ruby-only name of the entry class; renamed to `Client` (see `Client`).",
    {"ruby": "rename `Petstore` to `Client` (client.mustache)"})
cur("valueserializer", "type", "Internal helper: not part of the public surface. Hide it wherever the language can (node: drop from index exports; dart: stop exporting from the barrel; rust: `pub(crate)`); python/ruby/php/elixir keep it but mark it internal (`_`-free name kept, `@internal`/`:nodoc:`/`@moduledoc false`). java/kotlin/csharp/go/swift already hide it: no change.",
    {"node": "stop exporting `ValueSerializer` from index.ts (value_serializer.mustache, index.mustache)",
     "dart": "stop exporting `value_serializer.dart` from the barrel (barrel.mustache)",
     "rust": "make `ValueSerializer` `pub(crate)` (value_serializer.mustache, lib.mustache)",
     "php": "mark `ValueSerializer` `@internal` (value_serializer.mustache)",
     "python": "document `ValueSerializer` as internal (value_serializer.mustache)",
     "ruby": "mark `ValueSerializer` `# :nodoc:` (already) and `private_constant` where possible",
     "elixir": "add `@moduledoc false` to `ValueSerializer` (value_serializer.mustache)"}, "(internal)")
cur("headerselector", "type", "Internal helper, same rule as `ValueSerializer`: hide in node/dart/rust, mark internal in python/ruby/php/elixir.",
    {"node": "stop exporting `HeaderSelector` (index.mustache)", "dart": "stop exporting `header_selector.dart` (barrel.mustache)",
     "rust": "make `HeaderSelector` `pub(crate)` (header_selector.mustache)", "php": "mark `HeaderSelector` `@internal` (header_selector.mustache)",
     "elixir": "add `@moduledoc false` (header_selector.mustache)"}, "(internal)")
cur("valueserializer.*", "*", "Internal helper member: no public contract; the per-language differences are not harmonised. Follows from hiding `ValueSerializer`.", {}, "(internal)")
cur("headerselector.*", "*", "Internal helper member; follows from hiding `HeaderSelector`.", {}, "(internal)")
cur("allowreservedvalue", "type", "Internal helper of `ValueSerializer`; hide with it.",
    {"node": "stop exporting `AllowReservedValue`", "dart": "stop exporting `AllowReservedValue`"}, "(internal)")
cur("valueserializerallowreservedvalue", "type", "Internal helper of `ValueSerializer`; hide with it.", {}, "(internal)")
cur("objectserializer", "type", "`ObjectSerializer` is public (majority, 8 of 12): callers use `serialize`/`deserialize` to round-trip models and decode typed error bodies. kotlin, csharp and swift make it public; go exports `Serialize`/`Deserialize` as package functions.",
    {"kotlin": "make `ObjectSerializer` public (object_serializer.mustache)", "csharp": "make `ObjectSerializer` public (object_serializer.mustache)",
     "swift": "make `ObjectSerializer` public (object_serializer.mustache)", "go": "export `Serialize`/`Deserialize` package functions (object_serializer.mustache)"}, "ObjectSerializer")
cur("objectserializer.*", "member", "Public surface of `ObjectSerializer` is `serialize(object)`, `deserialize(jsonString, targetType)`, `parseJson(data)`, `stringify(value)`, the `to*Value` parameter encoders and `resolveOneOf`/`resolveAnyOf` where composed schemas need them; every other helper becomes non-public (private/internal/`@internal`/`@doc false`).")
cur("objectserializer.*", "params", "Parameter names follow java's `ObjectSerializer`: `serialize(object)`, `deserialize(jsonString, targetType)`, `parseJson(data)`.")
cur("defaultapiclient.*", "member", "`DefaultApiClient` exposes only the `ApiClient` contract (`sendRequest`, `close`) and its constructor; every other member becomes non-public.")
cur("abstractapiclient", "type", "No public abstract base: fold `AbstractApiClient` into `DefaultApiClient` or make it internal (php `@internal`, node not exported, dart not exported).",
    {"php": "mark `AbstractApiClient` `@internal` (abstract_api_client.mustache)", "node": "stop exporting `AbstractApiClient` (abstract_api_client.mustache, index.mustache)",
     "dart": "stop exporting `AbstractApiClient` (abstract_api_client.mustache, barrel.mustache)"}, "(internal)")
cur("abstractapiclient.*", "*", "Follows from making `AbstractApiClient` internal.", {})
cur("baseapi.*", "member", "`BaseApi` is a base class for the generated api classes; its plumbing (`invokeApi`, `apiClient`, `config`, `NO_AUTH`) is not public. Make the extra members protected/private.")
cur("baseapi", "type", "go keeps `baseApi` unexported (no inheritance; embedded); elixir has no base module. Not harmonised further.", {})
cur("petapi.config", "*", "Api classes do not expose `config`/`apiClient`/`authenticator`; make them private.", None)
cur("storeapi.config", "*", "Api classes do not expose `config`/`apiClient`/`authenticator`; make them private.", None)
cur("petapi.apiclient", "*", "Api classes do not expose `apiClient`; make it private.", None)
cur("storeapi.apiclient", "*", "Api classes do not expose `apiClient`; make it private.", None)
cur("apierror.fromresponse", "params", "`fromResponse(statusCode, responseHeaders, responseBody)`: the factory's parameter names match the constructor and the properties they fill (the 6/6 tie is broken by least surprise).")
cur("apierror.<ctor>", "ctor", "`ApiError(statusCode, message, responseHeaders, responseBody, errorBody, cause)`: headers before body everywhere (dart, go, rust, swift reorder); the message parameter is `message` (go renames `msg`); dart renames `underlyingError` to `cause`; python and rust add `cause`/`errorBody`; ruby replaces the polymorphic `initialize(arg)` with keyword arguments of the same names.")
cur("networkerror.<ctor>", "ctor", "`NetworkError(message, cause)`: python and ruby add the `cause` argument, dart renames `underlyingError` to `cause`, go renames `msg` to `message`.")
cur("networktimeouterror.<ctor>", "ctor", "`NetworkTimeoutError(message, cause)`, as `NetworkError`.")
cur("openapierror.<ctor>", "ctor", "Root `OpenAPIError(message, cause)`; dart gives its root a `(message, cause)` constructor.")
cur("oauth2tokenerror.<ctor>", "ctor", "`OAuth2TokenError(message)` (majority); java and kotlin drop the extra `cause` overload (csharp's innerException is the .NET-mandated chaining ctor and stays).")
cur("oauth2tokenerror", "kind", "`OAuth2TokenError` is a class carrying a message in every SDK; swift turns its enum into a class (swift's typed-case style is kept for ConfigurationError only, per AGENTS.md).")
cur("oauth2authorizationcodeauthenticator.<ctor>", "ctor", "`OAuth2AuthorizationCodeAuthenticator(host, clientId, clientSecret, authorizationUrl, tokenUrl, redirectUri, scopes, refreshUrl?)`: `refreshUrl` is the LAST, optional argument (java moves it from position 6 and keeps a 7-arg overload); ruby and node drop the injectable `tokenManager` from the public constructor (test seam only).")
cur("oauth2passwordauthenticator.<ctor>", "ctor", "`OAuth2PasswordAuthenticator(host, clientId, clientSecret, tokenUrl, username, password, scopes, refreshUrl?, clientAuthMethod?)`: java moves `refreshUrl` after `scopes`.")
cur("*authenticator.<ctor>", "ctor-optional", "Required: every credential and endpoint; optional: `scopes` defaults to empty only where the majority defaults it, `refreshUrl`/`clientAuthMethod` always optional. dart/swift stop defaulting `scopes`; python stops defaulting secrets (`client_secret`, `password`, `token`, `api_key` are required).")
cur("*authenticator.setapiclient", "params", "`setApiClient(apiClient)` (6/6 tie broken toward the parameter's type name, which the property and the docs already use).")
cur("httpawareauthenticator.setapiclient", "params", "`setApiClient(apiClient)`.")
cur("oauth2tokenmanager.setapiclient", "params", "`setApiClient(apiClient)`.")
cur("oauth2tokenmanager.exchanged", "member", "`isExchanged()` (property `exchanged` in property languages) is public on `OAuth2TokenManager` (majority); kotlin, csharp, dart, go add it.")
cur("oauth2tokenmanager.markexchanged", "member", "`markExchanged` is internal (minority); ruby, node, rust, elixir make it non-public.")
cur("*.tokenmanager", "member", "The token manager is an implementation detail; ruby/elixir stop exposing it (elixir: covered by struct transparency only if it stays a field).")
cur("*.actualinstance", "member", "Composed schemas expose `actualInstance` (oneOf and anyOf: the matched value) and anyOf also `matchedInstances` (all matches). Every SDK spells these two names; enum-backed languages (dart/swift/go `value`/`values`) rename theirs; rust keeps its enum (forced) and adds no accessor.")
cur("*.matchedinstances", "member", "See `actualInstance`: anyOf exposes `matchedInstances`; java/kotlin rename `getInstances`/`instances`, php renames `getActualInstances`, dart/swift rename `values`.")
cur("*.instances", "member", "Renamed to `matchedInstances` (see `actualInstance`).")
cur("*.actualinstances", "member", "Renamed to `matchedInstances` (see `actualInstance`).")
cur("*.value", "member", "Composed-schema accessor is `actualInstance` (see above); dart/go/swift rename `value`.")
cur("*.values", "member", "Composed-schema accessor is `matchedInstances` (see above); dart/swift rename `values`.")
cur("*asbytes", "member", "php drops the `get*AsBytes`/`set*FromBytes` helpers and types byte properties as decoded binary strings, like the other eleven SDKs type them as bytes.")
cur("*frombytes", "member", "See `*AsBytes`: php drops these helpers.")
cur("*.frombytelist", "member", "See `*AsBytes`: php drops these helpers.")
cur("*.actualinstanceasbytelist", "member", "See `*AsBytes`: php drops these helpers.")
cur("*.setadditionalproperty", "member", "Additional properties are a plain `additionalProperties` map in every SDK; java drops `setAdditionalProperty`.")
cur("stricttag.*", "member", "Strict (unevaluatedProperties: false) decoding is internal to the serializer; the per-language `parseStrict`/`fromJsonStrict`/`assertNoUnknownProperties`/`UNEVALUATED_PROPERTIES_FALSE` helpers become non-public.")
cur("petapi.setpetavatar*", "params", "The request-content-type argument is `requestContentType` (majority); php, ruby and node rename `contentType`.")
cur("petapi.uploadpetdocument*", "params", "The request-content-type argument is `requestContentType` (majority); php, ruby and node rename `contentType`.")
cur("configurationbuilder.*", "params", "A builder setter's parameter is named after the property it sets (`baseUrl(baseUrl)`), not `val`; `server(serverConfig, variables)`.")
cur("transportoptionsbuilder.*", "params", "A builder setter's parameter is named after the property it sets (`verifySsl(verifySsl)`), not `val`.")
cur("client.withtoken", "params", "`Client.withToken(host, accessToken, transportOptions?)`; php drops the trailing `apiClient` injection parameter (tests inject through the constructor of `DefaultApiClient`).")
cur("client.withauthenticator", "params", "`Client.withAuthenticator(authenticator, transportOptions?)`; php drops `apiClient`.")
cur("client.<ctor>", "ctor", "`Client(authenticator, transportOptions?)`; php drops the `apiClient` injection parameter; swift drops its convenience `init(host:accessToken:transportOptions:)` because `Client.withToken` is the token form in every SDK.")
cur("apiclient.close", "member", "`ApiClient.close()` is part of the transport contract (majority); python, node and dart add it to the interface (csharp: IDisposable.Dispose, forced).")
cur("configuration.<ctor>", "ctor-optional", "Configuration is built by the builder; where a constructor is public, both arguments are optional. ruby makes `base_url`/`default_headers` optional.")
cur("transportoptions.<ctor>", "ctor-optional", "Every TransportOptions argument is optional (they all have defaults); ruby adds the defaults.")
cur("serverconfiguration.<ctor>", "ctor-optional", "`ServerConfiguration(urlTemplate, description?, variables?)`; kotlin, csharp and node default the last two; java adds the 1-arg overload.")
cur("servervariable.<ctor>", "ctor-optional", "`ServerVariable(defaultValue, description?, enumValues?)`; kotlin, csharp and node default the last two; java adds the overload.")
cur("serverconfiguration.geturl", "member", "`getUrl(overrides?)` (url in getter-less languages is the idiom spelling, folded by the extractor); every SDK accepts the optional overrides map.")
cur("servers.all", "member", "`Servers.ALL` (all/`ALL`/`all`) is the list of every server; dart, go and rust rename `allServers` to `all`; ruby adds `ALL` as a public constant (it is computed but not exposed).")
cur("servers.allservers", "member", "Renamed to `all` (see `Servers.ALL`).")
cur("getmultiserverpetinfoserver*.*", "member", "Per-operation server variants expose their variables as public read-only properties (majority); php, python, ruby and dart make them public.")
cur("getstagingpetinfoserver*.*", "member", "Per-operation server variants expose their variables as public read-only properties (majority); php, python, ruby and dart make them public.")
cur("*options.*", "prop-optional", "An Options field is required exactly when the operation parameter is required: go types required options fields as values (not pointers) and optional ones as pointers; elixir adds `@enforce_keys` for required fields.")
cur("*.<ctor>", "ctor-optional", "Constructor defaults follow the majority shown in the canonical column.")
cur("defaultsmodeenum", "type", "Inline property enums are named types (`<Model><Prop>Enum`) in every SDK; ruby and elixir generate them (ruby as a constants class, elixir as an atom module) instead of inlining the allowed values.",
    {"ruby": "generate `<Model><Prop>Enum` constant classes for inline enums (models/model.mustache)", "elixir": "generate `<Model><Prop>Enum` modules for inline enums (models/model.mustache)"})
cur("*statusenum", "type", "As `DefaultsModeEnum`: ruby and elixir generate the named inline enum.",
    {"ruby": "generate the named inline enum (models/model.mustache)", "elixir": "generate the named inline enum (models/model.mustache)"})
cur("authenticator.isnoauth", "*", "The NO_AUTH check is identity comparison against the sentinel; node `isNoAuth()` and dart `noAuth` become internal.")
cur("httpawareauthenticator.ishttpawareauthenticator", "member", "node's type guard becomes internal (callers use `instanceof`).")
cur("configurationerror", "cases", "swift's ConfigurationError cases match rust's: rename `invalidProxyUrl` to `invalidProxy`, and fold `proxyUnsupported`/`tlsOptionsUnsupported` into `invalidProxy`/`invalidCaCertificate`, adding `invalidTimeout`.")
cur("metadata.additionalproperties", "*", "`additionalProperties` is a non-null map (empty when absent) on every model with additionalProperties; csharp, php and elixir stop typing it nullable; node adds the field.")
cur("requirednullable.note", "prop-optional", "A required-nullable property is required (must be present) and nullable; kotlin drops its default.")
cur("dryfood.foodtype", "prop-optional", "Discriminator properties are required; elixir adds them to `@enforce_keys`.")
cur("wetfood.foodtype", "prop-optional", "Discriminator properties are required; elixir adds them to `@enforce_keys`.")
cur("*.<ctor>", "ctor", "Constructor parameters follow the canonical order in the canonical column.")
cur("*", "casing", "Initialisms are word-cased in every camel/Pascal-case SDK (`WithHttpInfo`, `clientId`, `baseUrl`, `ApiClient`). go and swift already word-case most (`PetApi`, `GetPetById`) and capitalise only some (`WithHTTPInfo`, `clientID`); they converge on word-casing rather than renaming every type to full Go/Swift initialism style.")


def match_cur(el, asp):
    for pat, ap, d in CUR:
        if fnmatch.fnmatchcase(el, pat) and fnmatch.fnmatchcase(asp, ap):
            return d
    return None


# ------------------------------------------------------------ helpers
def lname(l, k, mk=None):
    t = S[l].get(k)
    if t is None:
        return None
    if mk is None:
        return t["name"]
    m = t["props"].get(mk) or t["methods"].get(mk)
    return m["name"] if m else None


def canon_name(k, mk=None, kind="member"):
    # prefer the snake spelling (word boundaries), then camel
    for l in ("python", "rust", "ruby", "elixir", "java", "kotlin", "node") + tuple(L):
        n = lname(l, k, mk)
        if n:
            ws = A.words(n)
            if kind == "type":
                return "".join(w.capitalize() for w in ws)
            return ws[0] + "".join(w.capitalize() for w in ws[1:])
    return mk or k


def sig(ps):
    return "(" + ", ".join(canon_param(p) for p in ps) + ")"


def canon_param(p):
    ws = A.words(p["name"]) if isinstance(p, dict) and "name" in p else [p]
    n = ws[0] + "".join(w.capitalize() for w in ws[1:]) if ws else ""
    return n + ("?" if p.get("optional") else "")


def fmt_maj(m):
    s = m["majority"]
    if s.startswith("("):
        # camel-case the keys using the snake names of the canonical languages
        return s
    return s


# index mismatches per element
by_el = collections.defaultdict(list)
for m in mm:
    by_el[m["element"]].append(m)


def live_langs(m):
    return [l for l in m["odd"] if l not in m["allowed"]]


def default_decision(m):
    asp, maj = m["aspect"], m["majority"]
    odd = live_langs(m)
    lj = ", ".join(odd)
    if asp == "type":
        if maj == "present":
            return "Generate `%s` in %s (majority has it)." % (canon_name(m["element"], kind="type"), lj)
        return "Language-only public type: make it non-public in %s (or document it as an extension point in API_SURFACE.md if callers need it)." % lj
    if asp == "member":
        el = m["element"]
        if maj == "present":
            return "Add `%s` in %s." % (el.split(".", 1)[1], lj)
        return "Not part of the canonical surface: make it non-public in %s." % lj
    if asp == "params":
        return "Parameters `%s` (majority) in %s." % (maj, lj)
    if asp == "optional":
        return "Optionality `%s` (majority) in %s." % (maj, lj)
    if asp == "returns":
        return "Return `%s` (majority) in %s." % (maj, lj)
    if asp == "ctor":
        return "Constructor `%s` (majority) in %s." % (maj, lj)
    if asp == "ctor-optional":
        return "Constructor defaults `%s` (majority) in %s." % (maj, lj)
    if asp == "prop-optional":
        return "Property is %s (majority) in %s." % (maj, lj)
    if asp == "kind":
        return "Make it a%s %s in %s." % ("n" if maj == "enum" else "", maj, lj)
    if asp == "cases":
        return "Cases `%s` in %s." % (maj, lj)
    if asp == "casing":
        return "Word-case initialisms."
    return maj


def change_text(m, l):
    """Concrete per-language change for language l from mismatch m."""
    d = match_cur(m["element"], m["aspect"])
    if d and d["changes"] is not None:
        if l in d["changes"]:
            return d["changes"][l]
        if d["changes"] == {} or m["aspect"] == "type":
            return None
    el, asp, maj, v = m["element"], m["aspect"], m["majority"], m["odd"][l]
    k = el.split(".")[0]
    mk = el.split(".", 1)[1] if "." in el else None
    nm = (lname(l, k, mk) if mk and not mk.startswith("<") else lname(l, k)) or (mk or k)
    if asp == "casing":
        fixed = re.sub(r"[A-Z]{2,}(?=[A-Z][a-z]|$|\d)", lambda x: x.group(0)[0] + x.group(0)[1:].lower(), v)
        if fixed == v:
            return None
        return "rename `%s` to `%s`" % (v, fixed)
    if asp == "type":
        if maj == "present":
            return "add type `%s`" % canon_name(k, kind="type")
        return "make `%s` non-public" % nm
    if asp == "member":
        if maj == "present":
            return "add `%s` to `%s`" % (canon_name(k, mk), lname(l, k) or k)
        return "make `%s.%s` non-public (or remove)" % (lname(l, k) or k, nm)
    if asp == "params":
        return "`%s.%s`: parameters %s -> %s" % (lname(l, k) or k, nm, v, maj)
    if asp == "optional":
        return "`%s.%s`: optionality %s -> %s" % (lname(l, k) or k, nm, v, maj)
    if asp == "returns":
        return "`%s.%s`: return %s -> %s" % (lname(l, k) or k, nm, v, maj)
    if asp == "ctor":
        return "`%s` constructor %s -> %s" % (lname(l, k) or k, v, maj)
    if asp == "ctor-optional":
        return "`%s` constructor defaults %s -> %s" % (lname(l, k) or k, v, maj)
    if asp == "prop-optional":
        return "`%s.%s`: %s -> %s" % (lname(l, k) or k, nm, v, maj)
    if asp == "kind":
        return "`%s`: %s -> %s" % (lname(l, k) or k, v, maj)
    if asp == "cases":
        return "`%s` cases %s -> %s" % (lname(l, k) or k, v, maj)
    return "%s %s" % (asp, v)


# ------------------------------------------------------------ template resolution
TPL = {}
for l in L:
    base = os.path.join(ROOT, "src/main/resources/templates", l)
    lst = []
    for dp, dn, fn in os.walk(base):
        for f in fn:
            if f.endswith(".mustache"):
                lst.append(os.path.relpath(os.path.join(dp, f), base))
    TPL[l] = lst

SPECIAL = {"oauth2authorizationcodeauthenticator": "oauth2authcodeauthenticator", "openapierror": "rooterror",
           "openapiexception": "rootexception", "apiresponse": "apiresponse"}
SCHEME = {"petstorebasicauthenticator", "petstorebearerauthenticator", "adminbasicauthenticator", "servicetokenauthenticator",
          "internalapikeyauthenticator", "sessioncookieauthenticator", "machineauthclientcredentialsauthenticator",
          "userauthauthorizationcodeauthenticator", "browserauthimplicitauthenticator", "legacyauthpasswordauthenticator",
          "ssoauthauthenticator", "apikeyheaderauthenticator", "apikeyqueryauthenticator"}


def template_for(l, k):
    t = S[l].get(k)
    if t is None:
        # missing type: find the template that would hold it in the majority languages
        for o in L:
            if k in S[o]:
                ref = template_for(o, k)
                if ref and os.path.basename(ref) != "model.mustache" or ref == "models/model.mustache":
                    cand = [x for x in TPL[l] if A.key(os.path.basename(x)[:-9]) == A.key(os.path.basename(ref)[:-9])]
                    if cand:
                        return cand[0]
                    return ref
        return None
    f = t["file"].replace("\\", "/").lower()
    cat = t["category"]
    if cat == "model" or "/models/" in f:
        if l == "php" and k.endswith("enum") and "/models/" in f and not any(k == x for x in ()):
            pass
        return "models/model.mustache"
    if cat == "options":
        return "api/options.mustache"
    if "/api/" in f or "pet_api" in f or "store_api" in f or "petapi" in f or "storeapi" in f:
        if k == "baseapi":
            return next((x for x in TPL[l] if x.endswith("base_api.mustache")), "base_api.mustache")
        if l == "php" and k not in ("petapi", "storeapi"):
            if k.endswith("server"):
                return "api/server_type.mustache"
            if t["kind"] == "enum":
                return "api/server_variable_enum.mustache"
            return "api/server_variant.mustache"
        return next((x for x in TPL[l] if x in ("api/api.mustache", "api/apis.mustache")), "api/api.mustache")
    kk = A.key(os.path.splitext(os.path.basename(t["file"]))[0])
    kk = SPECIAL.get(kk, kk)
    if kk in SCHEME:
        return "auth/scheme_authenticator.mustache"
    cands = [x for x in TPL[l] if A.key(os.path.basename(x)[:-9]) == kk]
    if not cands and kk.endswith("exception"):
        cands = [x for x in TPL[l] if A.key(os.path.basename(x)[:-9]) == kk[:-9] + "error"]
    if not cands and kk.endswith("error"):
        cands = [x for x in TPL[l] if A.key(os.path.basename(x)[:-9]) in (kk[:-5] + "exception", kk)]
    if kk in ("openapierror", "openapiexception", "rooterror"):
        cands = [x for x in TPL[l] if os.path.basename(x) in ("root_error.mustache", "root_exception.mustache", "root-error.mustache", "errors.mustache", "error_parent.mustache")]
    if not cands and l == "python" and "/errors/" in f:
        cands = ["errors.mustache"]
    if cands:
        cands.sort(key=lambda x: (x.count("/"), x))
        return cands[0]
    return None


# ------------------------------------------------------------ areas
def area_of(k):
    cats = collections.Counter(S[l][k]["category"] for l in L if k in S[l])
    kinds = collections.Counter(S[l][k]["kind"] for l in L if k in S[l])
    n = sum(cats.values())
    cat = cats.most_common(1)[0][0]
    if n <= 3 and k not in ("modelerror", "modelresult", "configurationerror", "oauth2authorizationcodeerror", "abstractapiclient", "allowreservedvalue", "petstore"):
        return "lang"
    if k in ("oauth2tokenmanager",):
        return "tokenmanager"
    if k in ("transportoptions", "transportoptionsbuilder"):
        return "transport"
    if k in ("serverconfiguration", "servervariable", "servers"):
        return "servers"
    if k in ("client", "configuration", "configurationbuilder", "apiclient", "defaultapiclient", "abstractapiclient", "apiresult", "apihttpresponse", "petstore"):
        return "client"
    if k in ("objectserializer", "valueserializer", "headerselector", "tracecontextutil", "allowreservedvalue", "valueserializerallowreservedvalue"):
        return "serializer"
    if kinds.most_common(1)[0][0] == "enum" or any(S[l][k]["kind"] == "enum" for l in L if k in S[l]) and k.endswith(("enum", "region", "channel", "environment", "version", "location", "method")):
        return "enums"
    if cat == "error" or k in ("configurationerror", "oauth2authorizationcodeerror"):
        return "errors"
    if cat == "options":
        return "options"
    if cat == "model" or k in ("modelerror", "modelresult"):
        return "models"
    if cat == "auth":
        return "auth"
    if cat == "api" or k.startswith("get") and "server" in k:
        return "api"
    return "serializer"


AREAS = [
    ("api", "API classes and operations"),
    ("options", "Options (request) types"),
    ("models", "Models"),
    ("enums", "Enums"),
    ("auth", "Authenticators and constructor arguments"),
    ("tokenmanager", "OAuth2TokenManager"),
    ("transport", "TransportOptions"),
    ("servers", "Server configuration"),
    ("client", "Client, Configuration and transport contract"),
    ("errors", "Errors and exceptions"),
    ("serializer", "Serializer and support helpers"),
    ("lang", "Language-only types"),
]

all_keys = []
for l in L:
    for k in S[l]:
        if k not in all_keys:
            all_keys.append(k)
by_area = collections.defaultdict(list)
for k in all_keys:
    by_area[area_of(k)].append(k)


def cell(l, k, mk=None, mism=None):
    t = S[l].get(k)
    if t is None:
        n = "—"
    elif mk is None:
        n = t["name"]
    elif mk == "<ctor>":
        n = "(" + ", ".join(p["name"] + ("?" if p["optional"] else "") for p in t["ctor"]) + ")" if t["ctor"] is not None else "—"
    else:
        m = t["props"].get(mk) or t["methods"].get(mk)
        if m is None:
            n = "—"
        else:
            n = m["name"]
            mm_ = t["methods"].get(mk)
            if mm_ is not None and mm_["params"]:
                n += "(" + ", ".join(p["name"] + ("?" if p["optional"] else "") for p in mm_["params"]) + ")"
    mark = ""
    if mism:
        if any(l in x["odd"] and l not in x["allowed"] for x in mism):
            mark = " ✗"
        elif any(l in x["allowed"] for x in mism):
            mark = " ~"
    return (n + mark).replace("|", "\\|")


def canon_member(k, mk, mism):
    for x in mism:
        if x["aspect"] in ("params",) and x["majority"].startswith("("):
            pass
    d = None
    # build canonical signature from the majority-shaped language
    best = None
    for l in L:
        t = S[l].get(k)
        if not t:
            continue
        m = t["methods"].get(mk)
        p = t["props"].get(mk)
        if m is None and p is None:
            continue
        ok = all(not (l in x["odd"]) for x in mism)
        if ok:
            best = (l, m, p)
            if l in ("java", "kotlin", "python", "node"):
                break
    nm = canon_name(k, mk)
    if best and best[1] is not None and best[1]["params"]:
        return nm + sig(best[1]["params"])
    if best and best[1] is not None:
        return nm + "()"
    return nm


def keyname(k, pkey):
    for l in L:
        t = S[l].get(k)
        if t and t["ctor"]:
            for p in t["ctor"]:
                if p["key"] == pkey and l in ("python", "rust", "ruby", "elixir"):
                    ws = A.words(p["name"])
                    return ws[0] + "".join(w.capitalize() for w in ws[1:])
    for l in L:
        t = S[l].get(k)
        if t and t["ctor"]:
            for p in t["ctor"]:
                if p["key"] == pkey:
                    return p["name"]
    return pkey


def canon_ctor(k, mism):
    order = [x for x in mism if x["aspect"] == "ctor"] + [x for x in mism if x["aspect"] == "ctor-optional"]
    for x in order:
        maj = x["majority"]
        return "new(" + ", ".join(keyname(k, p.rstrip("?")) + ("?" if p.endswith("?") else "") for p in maj.strip("()").split(", ") if p) + ")"
    for l in ("kotlin", "python", "csharp", "php", "java", "node"):
        t = S[l].get(k)
        if t and t["ctor"] is not None:
            return "new" + sig(t["ctor"])
    return "—"


def camel(p):
    opt = p.endswith("?")
    p = p.rstrip("?")
    return p + ("?" if opt else "")


def row(cells):
    return "| " + " | ".join(cells) + " |"


out = []
W = out.append

# ------------------------------------------------------------ header and counts
live = [m for m in mm if any(l not in m["allowed"] for l in m["odd"])]
per_lang = collections.Counter()
per_lang_cas = collections.Counter()
for m in live:
    for l in live_langs(m):
        if m["aspect"] == "casing":
            per_lang_cas[l] += 1
        else:
            per_lang[l] += 1
allowed_n = sum(len(m["allowed"]) for m in mm)

changes = collections.defaultdict(lambda: collections.defaultdict(list))
for m in live:
    for l in live_langs(m):
        txt = change_text(m, l)
        if not txt:
            continue
        k = m["element"].split(".")[0].split("(")[0]
        tpl = template_for(l, k) or "(generator code: see Better%sCodegen)" % l.capitalize()
        if txt not in changes[l][tpl]:
            changes[l][tpl].append(txt)

TOTAL = sum(per_lang.values()) + sum(per_lang_cas.values())
open("/tmp/claude-501/counts.json", "w").write(json.dumps({"surface": per_lang, "casing": per_lang_cas}))



if False:
    for l in L:
        print("==", l, per_lang[l], per_lang_cas[l])
        for tpl, items in changes[l].items():
            print("  ", tpl, len(items), items[:2])


# ------------------------------------------------------------ invert ("add everywhere") curated rows
INVERT = {"*.actualinstance": "actualInstance", "*.matchedinstances": "matchedInstances"}


def invert_changes(m):
    for pat, nm in INVERT.items():
        if fnmatch.fnmatchcase(m["element"], pat) and m["aspect"] == "member" and m["majority"] == "absent":
            k = m["element"].split(".")[0]
            mk = m["element"].split(".", 1)[1]
            lacking = [l for l in L if k in S[l] and mk not in S[l][k]["props"] and mk not in S[l][k]["methods"]]
            return {l: "add `%s` to `%s`" % (nm, S[l][k]["name"]) for l in lacking if l != "rust"}
    return None


# recompute the per-language change lists honouring invert rows
changes = collections.defaultdict(lambda: collections.defaultdict(list))
for m in mm:
    inv = invert_changes(m)
    targets = {}
    if inv is not None:
        targets = inv
    else:
        for l in live_langs(m):
            txt = change_text(m, l)
            if txt:
                targets[l] = txt
    for l, txt in targets.items():
        k = m["element"].split(".")[0]
        tpl = template_for(l, k) or "(codegen: Better%sCodegen.java)" % {"php": "PHP", "csharp": "CSharp"}.get(l, l.capitalize())
        if txt not in changes[l][tpl]:
            changes[l][tpl].append(txt)

# decisions numbering
DEC_IDS = {}
for i, (pat, ap, d) in enumerate(CUR, 1):
    DEC_IDS[id(d)] = "D%02d" % i


def decision_for(el, ms):
    livems = [m for m in ms if live_langs(m)]
    if not livems:
        if any(m["allowed"] for m in ms):
            return "idiom (allow-list)"
        return ""
    parts = []
    for m in livems:
        d = match_cur(el, m["aspect"])
        if d:
            tag = DEC_IDS[id(d)]
            if tag not in parts:
                parts.append(tag)
        else:
            parts.append(default_decision(m))
    return " ".join(parts)


def canon_type(k):
    d = None
    for m in by_el.get(k, []):
        d = match_cur(k, m["aspect"])
        if d and d.get("canon"):
            return d["canon"]
    return canon_name(k, kind="type")


W("# Canonical API surface")
W("")
W("This file is the single public API that all twelve generated SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir) implement. "
  "It is derived from the goldens under `src/spec/resources/generated/<lang>/` by `etc/api-surface.py`, which extracts every SDK's public surface into one normalised form "
  "(identifiers lower-cased with separators removed, so `getPetByIdWithHttpInfo`, `get_pet_by_id_with_http_info` and `GetPetByIdWithHTTPInfo` are one element) and compares them. "
  "`python3 etc/api-surface.py check` prints every mismatch not covered by `etc/api-surface-allow.json` and exits non-zero while any remain; `--all` also prints the allowed ones, `--json` everything.")
W("")
W("Languages differ only by idiom. These are folded before comparing and are never reported: naming case (camelCase, PascalCase, snake_case, kebab file names); the error suffix (`Exception` in java, kotlin, csharp, php, python, dart; `Error` elsewhere); "
  "a get/is accessor versus a property (`getStatusCode()` = `status_code` = `StatusCode { get; }`), and the getter-less spelling (`url(overrides)` in ruby/go/rust/swift/dart/elixir = `getUrl(overrides)`); fluent/with-er setters versus properties; "
  "C#/node/dart `Async` suffixes; elixir `!` variants; ruby `?` predicates and `x=` writers; receivers (`self`, `&self`, elixir's api struct, go's `context.Context`); java overload sets (a parameter some overload omits is optional); "
  "optionality where the language has no default arguments (go, rust); members a type inherits (resolved per language) and, in rust/elixir, members reached by composition; elixir's trailing keyword `opts`; and identity hooks (`toString`, `equals`, `hashCode`, `__repr__`, `fmt`, Codable `init(from:)`). "
  "Every other forced difference is an allow-list entry with the language feature that forces it.")
W("")
W("Legend for the tables: each language cell is that SDK's actual spelling; `✗` marks a mismatch to fix, `~` an allowed idiom difference, `—` absent. The Decision column cites a numbered canonical decision (`Dnn`, listed below) or states the default (the majority shape).")
W("")

# counts
W("## Mismatches per language")
W("")
W(row(["Language", "Surface mismatches", "Initialism casing", "Total"]))
W(row(["---"] * 4))
for l in L:
    W(row([l, str(per_lang[l]), str(per_lang_cas[l]), str(per_lang[l] + per_lang_cas[l])]))
W(row(["**all**", "**%d**" % sum(per_lang.values()), "**%d**" % sum(per_lang_cas.values()), "**%d**" % TOTAL]))
W("")
W("Counts are per (element, aspect, language) as `etc/api-surface.py check` reports them; %d further differences are allowed idioms (`etc/api-surface-allow.json`)." % allowed_n)
W("")

# per-language change lists
W("## Per-language change list")
W("")
W("One fix agent per language can execute its list. Paths are under `src/main/resources/templates/<lang>/`; `(codegen: …)` means the name comes from the generator class under `src/main/java/io/github/mridang/codegen/generators/<lang>/`. "
  "Parameter lists use the normalised keys (`clientid` = `clientId`/`client_id`); apply them in the language's own case. After the change, regenerate the goldens and rerun `python3 etc/api-surface.py check` until the language's count is zero.")
W("")
for l in L:
    n = sum(len(v) for v in changes[l].values())
    W("### %s (%d changes)" % (l, n))
    W("")
    for tpl in sorted(changes[l], key=lambda x: (x.startswith("("), x)):
        W("- `%s`" % tpl)
        for c in changes[l][tpl]:
            W("  - %s" % c)
    W("")

# decisions
W("## Canonical decisions")
W("")
W("Chosen by (1) the majority, (2) the documented rules in AGENTS.md, (3) least surprise for callers, in that order. A decision that departs from the majority says why.")
W("")
for i, (pat, ap, d) in enumerate(CUR, 1):
    W("- **D%02d** `%s` (%s): %s" % (i, pat, ap, d["decision"]))
W("")

# area tables
for area, title in AREAS:
    keys = by_area.get(area, [])
    if not keys:
        continue
    W("## %s" % title)
    W("")
    W(row(["Element", "Canonical"] + L + ["Decision"]))
    W(row(["---"] * (len(L) + 3)))
    for k in keys:
        tms = by_el.get(k, [])
        ct = canon_type(k)
        if area == "enums":
            cases = collections.Counter(tuple(sorted(S[l][k]["cases"])) for l in L if S[l].get(k) and S[l][k]["kind"] == "enum")
            if cases:
                ct += " {" + ", ".join(cases.most_common(1)[0][0]) + "}"
        W(row(["**%s**" % canon_name(k, kind="type"), ct] + [cell(l, k, None, tms) for l in L] + [decision_for(k, tms)]))
        # constructor
        cms = by_el.get(k + ".<ctor>", [])
        if area not in ("models", "options", "enums") and any(S[l].get(k) and S[l][k]["ctor"] is not None for l in L):
            W(row(["%s ctor" % canon_name(k, kind="type"), canon_ctor(k, cms)] + [cell(l, k, "<ctor>", cms) for l in L] + [decision_for(k + ".<ctor>", cms)]))
        # enum cases
        if area == "enums":
            cs = by_el.get(k, [])
            continue
        mkeys = []
        for l in L:
            t = S[l].get(k)
            if t:
                for mk in list(t["props"]) + list(t["methods"]):
                    if mk not in mkeys:
                        mkeys.append(mk)
        shown = 0
        for mk in mkeys:
            el = k + "." + mk
            ms = by_el.get(el, [])
            if area in ("models", "options") and not ms:
                continue
            if area in ("lang",) and not ms:
                continue
            # skip consistent members most languages inherit (they appear on the parent)
            inh = [l for l in L if S[l].get(k) and ((S[l][k]["props"].get(mk) or S[l][k]["methods"].get(mk) or {}).get("inherited"))]
            have = [l for l in L if S[l].get(k) and (mk in S[l][k]["props"] or mk in S[l][k]["methods"])]
            if not ms and have and len(inh) * 2 >= len(have):
                continue
            W(row(["%s.%s" % (canon_name(k, kind="type"), canon_name(k, mk)), canon_member(k, mk, ms)] + [cell(l, k, mk, ms) for l in L] + [decision_for(el, ms)]))
            shown += 1
        if area in ("models", "options"):
            total = len(mkeys)
            if total - shown:
                W(row(["%s (%d other properties)" % (canon_name(k, kind="type"), total - shown), "identical"] + [""] * len(L) + [""]))
    W("")

# casing table
W("## Initialism casing")
W("")
W("D%02d applies. Every identifier below spells an initialism in capitals where the canonical form word-cases it; rename to the right-hand form." % len(CUR))
W("")
W(row(["Language", "Element", "Actual", "Canonical"]))
W(row(["---"] * 4))
for m in mm:
    if m["aspect"] != "casing":
        continue
    for l in live_langs(m):
        v = m["odd"][l]
        fixed = re.sub(r"[A-Z]{2,}(?=[A-Z][a-z]|$|\d)", lambda x: x.group(0)[0] + x.group(0)[1:].lower(), v)
        W(row([l, m["element"], v, fixed]))
W("")

open(ROOT + "/API_SURFACE.md", "w").write("\n".join(out) + "\n")
print("wrote", len(out), "lines")
