#!/usr/bin/env python3
"""Extract and compare the public API surface of the twelve generated SDKs.

  api-surface.py extract <lang|all>   print the normalised public surface as JSON
  api-surface.py check [--all|--json]  compare the twelve and print the mismatches
                                      not covered by etc/api-surface-allow.json
                                      (--all also prints the allowed ones, --json
                                      prints every mismatch with its allow reason)
  api-surface.py casing               print identifiers whose acronym casing departs
                                      from the canonical word-cased form (Http, Id, Url)

The extractor reads only the NON-TEST source of each golden under
src/spec/resources/generated/<lang>/. It is line/brace based and tuned to the generated
code's actual style; it is not a general parser.

Normalisation. Every identifier is lower-cased with separators removed, so
getPetByIdWithHttpInfo, get_pet_by_id_with_http_info and GetPetByIdWithHTTPInfo share
the key `getpetbyidwithhttpinfo` (the `words` field keeps a readable split). On top of
that, these language idioms are folded before comparing:

  * type names: a trailing Exception is read as Error (the error contract's suffix
    rule); a C# interface's leading I is dropped; nested types are named by
    concatenating their enclosing types (java Pet.StatusEnum == PetStatusEnum), with
    the *Api class and namespace segments (Api, Models, Auth, Errors, OAuth, Options)
    skipped;
  * members: a zero-argument get/is accessor is the property it reads (getStatusCode()
    == status_code == StatusCode { get; }); a setter or a fluent with-er that takes one
    argument and shares a property's name is that property; C#/node/dart Async
    suffixes, elixir `!` bang variants and ruby `?` predicates fold onto the base name;
  * parameters: receivers (self, &self, elixir's %__MODULE__{} api, go's
    context.Context) are dropped; a java overload set is merged, a parameter missing
    from some overload being optional; optionality is compared only between languages
    with default arguments (java overloads count; go and rust never omit an argument).
  * idiom-only members (toString, equals, hashCode, __repr__, fmt, Codable init(from:),
    encode(to:), copy, ...) and std-trait impls are ignored.
  * inherited members are resolved per language; a member the majority only inherits is
    not required of rust/elixir, which have no implementation inheritance.

Aspects `check` reports: type (missing/extra type), kind (enum vs not), cases (enum
cases), member (missing/extra member), params (names and order), optional (which
parameters may be omitted), returns (the outermost SDK type returned), ctor and
ctor-optional (constructor arguments, for non-model types), prop-optional (model and
options fields), and casing (an initialism spelt in capitals, HTTPInfo/clientID, where
the canonical form word-cases it). API_SURFACE.md records the canonical decision for each.
"""
import ast
import collections
import fnmatch
import json
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
GEN = os.path.join(ROOT, "src", "spec", "resources", "generated")
ALLOW_PATH = os.path.join(ROOT, "etc", "api-surface-allow.json")
LANGS = ["java", "kotlin", "csharp", "php", "python", "ruby", "node", "dart", "go",
         "rust", "swift", "elixir"]

# Source roots (non-test) and file extensions per language.
SOURCES = {
    "java": (["src/main/java"], (".java",)),
    "kotlin": (["src/main/kotlin", "src/jvmMain/kotlin"], (".kt",)),
    "csharp": (["src/PetstoreClient"], (".cs",)),
    "php": (["lib"], (".php",)),
    "python": (["petstore_client"], (".py",)),
    "ruby": (["lib"], (".rb",)),
    "node": (["src"], (".ts",)),
    "dart": (["lib"], (".dart",)),
    "go": (["pkg"], (".go",)),
    "rust": (["src"], (".rs",)),
    "swift": (["Sources/PetstoreClient"], (".swift",)),
    "elixir": (["lib"], (".ex",)),
}
# Languages where a caller may omit a trailing argument (default value, overload,
# optional marker). go and rust have neither, so their optionality is not compared.
DEFAULT_ARG_LANGS = {"java", "kotlin", "csharp", "php", "python", "ruby", "node", "dart",
                     "swift", "elixir"}

UNTYPED_LANGS = {"ruby", "elixir"}
NO_INHERIT_LANGS = {"rust", "elixir"}
IGNORED_MEMBERS = {
    # identity/printing hooks every language spells its own way
    "tostring", "equals", "hashcode", "gethashcode", "repr", "str", "eq", "ne", "hash",
    "inspect", "tos", "eql", "description", "debugdescription", "fmt", "debuginfo",
    "gostring", "string", "clone", "copy", "tojson", "jsonserialize", "prettyprint",
    "init", "new", "post_init", "postinit", "dispose_async", "lt", "le", "gt", "ge",
    "bool", "len", "iter", "contains", "getitem", "enter", "exit", "aenter", "aexit",
    "del", "deinit", "finalize", "serialize_model", "model_post_init", "copywith",
    "marshaljson", "unmarshaljson", "marshaltext", "unmarshaltext", "encode", "decode",
    "hashinto", "component1", "component2", "component3", "component4",
    "exception", "message", "validate", "typeof", "valueof",
    # serializer metadata tables (ruby ATTRIBUTE_MAP constants, elixir functions)
    "attributemap", "jsonkeymap", "requiredfields", "openapitypes", "openapiformats",
}
# ast/regex visibility helpers
CAMEL_RE = re.compile(r"[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|\d+")
ACRONYMS = {"id", "url", "uri", "http", "https", "api", "json", "ssl", "tls", "ca",
            "html", "xml", "uuid", "ttl", "ip", "oidc", "jwt", "sso", "cdn"}
NAMESPACE_SEGMENTS = {"api", "apis", "models", "auth", "errors",
                      "oauth", "options", "petstoreclient", "serializer", "src", "lib",
                      "pkg", "utils"}


# ---------------------------------------------------------------- normalisation
def words(name):
    name = name.strip("`$!?").lstrip("_")
    if name.startswith("r#"):
        name = name[2:]
    parts = []
    for chunk in re.split(r"[_\-\s.]+", name):
        parts += CAMEL_RE.findall(chunk)
    return [p.lower() for p in parts if p]


def key(name):
    return "".join(words(name))


def type_key(name, lang=None, kind=None):
    k = key(name)
    if k.endswith("exception"):
        k = k[: -len("exception")] + "error"
    if k.endswith("errorerror"):
        k = k[: -len("error")]
    return k


def member_key(name, lang, nparams):
    n = name.strip("`")
    if n.startswith("r#"):
        n = n[2:]
    if lang == "kotlin" and n.startswith("_") and len(n) > 1:
        n = n[1:]
    if lang in ("ruby", "elixir"):
        n = n.rstrip("?!")
    if lang in ("csharp", "node", "dart") and n.endswith("Async") and len(n) > 5:
        n = n[:-5]
    if lang == "csharp" and n == "Dispose":
        n = "close"
    k = key(n)
    if nparams == 0:
        for pre in ("get", "is"):
            if k.startswith(pre) and len(k) > len(pre) + 1:
                stripped = name.lstrip("_")
                # only strip when it is a word boundary in the original spelling
                if re.match(r"(?i)%s(?:[A-Z_]|$)" % pre, stripped[: len(pre) + 1]) and (
                        stripped[len(pre): len(pre) + 1].isupper() or
                        stripped[len(pre): len(pre) + 1] == "_"):
                    k = k[len(pre):]
                break
    return k


# ---------------------------------------------------------------- model objects
class Surface:
    def __init__(self, lang):
        self.lang = lang
        self.types = collections.OrderedDict()

    def type(self, name, kind, file, line, category=None):
        k = type_key(name, self.lang, kind)
        t = self.types.get(k)
        if t is None:
            t = {"name": name, "kind": kind, "file": file, "line": line,
                 "category": category or categorise(name, file),
                 "members": collections.OrderedDict(), "ctors": [], "cases": [],
                 "props": collections.OrderedDict()}
            self.types[k] = t
        elif kind == "enum" and t["kind"] != "enum":
            t["kind"] = "enum"
        elif t["kind"] == "module" and kind != "module":
            # a free function seen first registered the file stem; the real type wins
            t.update(name=name, kind=kind, file=file, line=line,
                     category=category or categorise(name, file))
        return t


def categorise(name, file):
    f = file.replace("\\", "/").lower()
    n = name.lower()
    if "/options/" in f or (n.endswith("options") and n not in ("transportoptions",)
                            and "/api" in f):
        return "options"
    if "/models/" in f or "/model/" in f:
        return "model"
    if "/errors/" in f or "/error/" in f or n.endswith("exception") or (
            n.endswith("error") and "/models/" not in f):
        return "error"
    if "/auth/" in f:
        return "auth"
    if "/api/" in f or n.endswith("api"):
        return "api"
    return "core"


def add_member(t, name, lang, params, returns="", static=False, kind="method",
               optional=None, line=0):
    if kind == "prop":
        k = member_key(name, lang, 0)
        if k in IGNORED_MEMBERS or not k:
            return
        if k not in t["props"]:
            t["props"][k] = {"name": name, "optional": optional, "line": line}
        return
    k = member_key(name, lang, len(params))
    if k in IGNORED_MEMBERS or not k:
        return
    m = t["members"].setdefault(k, {"name": name, "overloads": [], "static": static,
                                    "line": line})
    m["overloads"].append({"params": params, "returns": returns})


def P(name, optional=False, kw=False, type_=""):
    return {"name": name, "optional": bool(optional), "kw": bool(kw), "type": type_}


# ---------------------------------------------------------------- lexing helpers
def blank(text, lang):
    """Remove comments and blank out string contents, keeping newlines and quotes."""
    out = []
    i, n = 0, len(text)
    line_comment = ["//"]
    if lang in ("php",):
        line_comment.append("#")
    if lang in ("python", "ruby", "elixir"):
        line_comment = ["#"]
    block = lang not in ("python", "ruby", "elixir")
    while i < n:
        c = text[i]
        two = text[i:i + 2]
        if block and two == "/*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r"[^\n]", " ", text[i:j]))
            i = j
            continue
        if any(text.startswith(lc, i) for lc in line_comment):
            if lang == "php" and c == "#" and text.startswith("#[", i):
                # attribute: drop it but keep parens balanced by blanking
                depth, j = 0, i + 1
                while j < n:
                    if text[j] == "[":
                        depth += 1
                    elif text[j] == "]":
                        depth -= 1
                        if depth == 0:
                            j += 1
                            break
                    j += 1
                out.append(re.sub(r"[^\n]", " ", text[i:j]))
                i = j
                continue
            if lang == "elixir" and c == "#" and i > 0 and text[i - 1] == "\\":
                out.append(c)
                i += 1
                continue
            j = text.find("\n", i)
            j = n if j < 0 else j
            i = j
            continue
        if lang == "rust" and c == "r" and re.match(r'r#*"', text[i:i + 10]) and (
                i == 0 or not (text[i - 1].isalnum() or text[i - 1] == "_")):
            m = re.match(r'r(#*)"', text[i:])
            close = '"' + m.group(1)
            j = text.find(close, i + len(m.group(0)))
            j = n if j < 0 else j + len(close)
            out.append('"' + re.sub(r"[^\n]", " ", text[i + 1:j - 1]) + '"')
            i = j
            continue
        if lang == "node" and c == "/" and re.search(r"(^|[(,=!:\[&|?;{}]|return|typeof)\s*$", "".join(out[-40:])):
            j = i + 1
            in_class = False
            while j < n and text[j] != "\n":
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == "[":
                    in_class = True
                elif text[j] == "]":
                    in_class = False
                elif text[j] == "/" and not in_class:
                    break
                j += 1
            if j < n and text[j] == "/":
                out.append("/" + " " * (j - i - 1) + "/")
                i = j + 1
                continue
        if c in "\"'`":
            if c == "'" and lang == "rust":
                m = re.match(r"'(\\.|[^\\'])'", text[i:i + 6]) or re.match(
                    r"'\\u\{[0-9a-fA-F]+\}'", text[i:i + 12])
                if not m:
                    out.append(c)
                    i += 1
                    continue
                out.append("'" + " " * (len(m.group(0)) - 2) + "'")
                i += len(m.group(0))
                continue
            if c == "'" and lang in ("swift", "go", "csharp", "java", "kotlin") and not \
                    re.match(r"'(\\.|[^\\'\n])+'", text[i:i + 12]):
                out.append(c)
                i += 1
                continue
            if c == "`" and lang not in ("node", "go", "kotlin"):
                out.append(c)
                i += 1
                continue
            if c == "`" and lang == "kotlin":
                j = text.find("`", i + 1)
                out.append(text[i:j + 1])
                i = j + 1
                continue
            triple = text[i:i + 3] if text[i:i + 3] in ('"""', "'''") else None
            if triple and lang in ("python", "kotlin", "swift", "dart", "elixir"):
                j = i + 3
                while j < n and not text.startswith(triple, j):
                    j += 2 if text[j] == "\\" and lang != "kotlin" else 1
                j = min(n, j + 3)
                out.append(triple + re.sub(r"[^\n]", " ", text[i + 3:j - 3]) + triple)
                i = j
                continue
            j = i + 1
            if lang == "csharp" and i > 0 and text[i - 1] == "@":
                while j < n:
                    if text[j] == '"' and text[j + 1:j + 2] == '"':
                        j += 2
                        continue
                    if text[j] == '"':
                        break
                    j += 1
                out.append(c + re.sub(r"[^\n]", " ", text[i + 1:j]) + c)
                i = j + 1
                continue
            interp = None
            if lang in ("kotlin", "dart") and c in "\"'":
                interp = "${"
            elif lang == "node" and c == "`":
                interp = "${"
            elif lang == "swift" and c == '"':
                interp = "\\("
            elif lang == "csharp" and c == '"' and i > 0 and text[i - 1] == "$":
                interp = "{"
            elif lang in ("ruby", "elixir") and c == '"':
                interp = "#{"
            elif lang == "php" and c == '"':
                interp = None
            while j < n:
                ch = text[j]
                if ch == "\\" and not (lang == "csharp" and False):
                    if interp == "\\(" and text[j:j + 2] == "\\(":
                        j = skip_interp(text, j + 2, "(", ")", lang)
                        continue
                    j += 2
                    continue
                if interp and interp != "\\(" and text.startswith(interp, j):
                    if interp == "{" and text[j:j + 2] == "{{":
                        j += 2
                        continue
                    j = skip_interp(text, j + len(interp), "{", "}", lang)
                    continue
                if ch == c:
                    break
                if ch == "\n" and c != "`" and lang not in ("ruby", "elixir", "php"):
                    break
                j += 1
            out.append(c + re.sub(r"[^\n]", " ", text[i + 1:j]) + c)
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def skip_interp(text, j, open_, close, lang):
    depth = 1
    n = len(text)
    while j < n and depth:
        ch = text[j]
        if ch in "\"'":
            k = j + 1
            while k < n and text[k] != ch and text[k] != "\n":
                k += 2 if text[k] == "\\" else 1
            j = k + 1
            continue
        if ch == open_:
            depth += 1
        elif ch == close:
            depth -= 1
        j += 1
    return j


def split_top(s, sep=","):
    parts, depth, cur = [], 0, []
    pairs = {"(": ")", "[": "]", "{": "}", "<": ">"}
    for i, ch in enumerate(s):
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "<" and depth >= 0:
            depth += 1
        elif ch == ">" and depth > 0 and not (i > 0 and s[i - 1] in "-="):
            depth -= 1
        if ch == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def paren_body(s, start=None):
    """Return (inner, end_index) of the first balanced (...) group at/after start."""
    i = s.find("(") if start is None else start
    if i < 0:
        return None, -1
    depth = 0
    for j in range(i, len(s)):
        if s[j] == "(":
            depth += 1
        elif s[j] == ")":
            depth -= 1
            if depth == 0:
                return s[i + 1:j], j
    return s[i + 1:], len(s)


def balance(s):
    b = 0
    for ch in s:
        if ch in "([":
            b += 1
        elif ch in ")]":
            b -= 1
    return b


def angle_open(s):
    """An unclosed generic (`Task<` ... `> Name()` split over lines)."""
    t = re.sub(r"=>|->|>=|<=|<<", "", s)
    return t.count("<") > t.count(">") and t.rstrip().endswith(("<", ",", ">"))


def braces(s):
    return s.count("{") - s.count("}")


# ---------------------------------------------------------------- C-like engine
class Ctx:
    def __init__(self, surface, file, lang):
        self.s = surface
        self.file = file
        self.lang = lang
        self.stack = []  # list of dicts: {t, body_depth, public, name, kind}

    def owner(self):
        for fr in reversed(self.stack):
            if fr.get("t") is not None or not fr["public"]:
                return fr
        return None


def file_stem_type(surface, file):
    stem = os.path.splitext(os.path.basename(file))[0]
    return stem


def run_clike(surface, path, rel, lang, handler):
    text = open(path, encoding="utf-8").read()
    lines = blank(text, lang).split("\n")
    ctx = Ctx(surface, rel, lang)
    depth = 0
    i = 0
    pending_type = None
    annotations = []
    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        # pop finished frames
        while ctx.stack and depth < ctx.stack[-1]["body_depth"]:
            ctx.stack.pop()
        if not line:
            i += 1
            continue
        member_depth = ctx.stack[-1]["body_depth"] if ctx.stack else 0
        if pending_type is not None and line.startswith("{") and depth == member_depth:
            fr = pending_type
            fr["body_depth"] = depth + 1
            ctx.stack.append(fr)
            pending_type = None
            depth += braces(line)
            i += 1
            continue
        pending_type = None
        if depth != member_depth:
            depth += braces(line)
            i += 1
            continue
        # annotation lines attach to the next declaration
        if handler.is_annotation(line):
            annotations.append(line)
            depth += braces(line)
            i += 1
            continue
        # accumulate a statement until parens balance
        stmt = line
        j = i
        while (balance(stmt) > 0 or angle_open(stmt)) and j + 1 < len(lines):
            j += 1
            stmt += " " + lines[j].strip()
        while j + 1 < len(lines) and (handler.continues(stmt, lines[j + 1].strip()) or
                                      re.search(r"(=>|[^=!<>]=|&&|\|\|)$", stmt.rstrip())):
            j += 1
            stmt += " " + lines[j].strip()
            while balance(stmt) > 0 and j + 1 < len(lines):
                j += 1
                stmt += " " + lines[j].strip()
        ann = " ".join(annotations)
        annotations = []
        ev = handler.decl(stmt, ctx, ann, i + 1)
        opened = braces(stmt)
        if ev and ev[0] == "type":
            fr = ev[1]
            if fr.get("t") is not None and fr.get("kind") not in ("impl", "extension", "object"):
                fr["t"].setdefault("parents", [])
                fr["t"]["parents"] += header_parents(lang, stmt, fr["name"])
            if opened > 0:
                fr["body_depth"] = depth + 1
                ctx.stack.append(fr)
            else:
                pending_type = fr  # Allman style: the body brace is on the next line
        depth += opened
        i = j + 1


def header_parents(lang, stmt, name):
    head = stmt.split("{")[0]
    i = head.find(name)
    rest = head[i + len(name):] if i >= 0 else head
    rest = re.sub(r"^\s*<[^>]*(?:<[^>]*>[^>]*)*>", "", rest)
    rest = re.sub(r"^\s*(?:(?:public|private|internal|protected)\s+)?constructor\s*(?=\()", "", rest)
    if rest.lstrip().startswith("("):
        _, end = paren_body(rest, rest.find("("))
        rest = rest[end + 1:]
    found = []
    if lang in ("java", "php", "node", "dart"):
        for kw in ("extends", "implements", "with"):
            m = re.search(r"\b%s\s+(.+?)(?=\b(?:extends|implements|with)\b|$)" % kw, rest)
            if m:
                found += split_top(m.group(1))
    elif lang in ("kotlin", "csharp", "swift"):
        m = re.match(r"\s*:\s*(.+?)(?:\bwhere\b|$)", rest)
        if m:
            found += split_top(m.group(1))
    out = []
    for f in found:
        f = re.sub(r"\(.*$", "", f).strip()
        f = re.sub(r"<.*$", "", f).strip()
        f = f.split(".")[-1].split("\\")[-1].lstrip("@")
        if lang == "csharp" and re.match(r"I[A-Z]", f):
            f = f[1:]
        if f:
            out.append(f)
    return out


class Clike:
    lang = ""
    no_body_ok = False

    def is_annotation(self, line):
        return False

    def continues(self, stmt, nxt):
        return False

    def ends_without_body(self, stmt):
        return stmt.rstrip().endswith(";") or stmt.rstrip().endswith("}")

    def new_type(self, ctx, name, kind, public, line, enum_like=False, extra=None):
        frame = {"name": name, "kind": kind, "public": public, "t": None,
                 "enum_body": kind == "enum", "body_depth": 0, "line": line}
        parent = ctx.owner()
        if parent is not None and not parent["public"]:
            frame["public"] = False
        if frame["public"]:
            full = nested_name(ctx, name)
            frame["t"] = ctx.s.type(full, kind, ctx.file, line)
            frame["full"] = full
        if extra:
            frame.update(extra)
        return ("type", frame)


def nested_name(ctx, name):
    outer = [fr["name"] for fr in ctx.stack if fr.get("t") is not None or fr.get("ns")]
    outer = [o for o in outer if not o.endswith("Api") and not o.endswith("API")
             and key(o) not in NAMESPACE_SEGMENTS]
    if outer and outer[-1] in ("Companion",):
        outer = outer[:-1]
    return "".join(outer) + name


# ---------------------------------------------------------------- Java
JAVA_MODS = r"(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|" \
            r"default|synchronized|native|strictfp|transient|volatile)\s+)*"


class Java(Clike):
    lang = "java"

    def continues(self, stmt, nxt):
        return re.match(r"(extends|implements|permits)\b", nxt) is not None and \
            not stmt.rstrip().endswith("{")

    def is_annotation(self, line):
        return line.startswith("@") and not line.startswith("@interface") and \
            not re.match(r"@\w+(\([^)]*\))?\s+(public|private|protected|static|final|\w+\s+\w+)", line)

    def decl(self, s, ctx, ann, line):
        s = re.sub(r"@[\w.]+(\((?:[^()]|\([^()]*\))*\))?\s*", "", s).strip()
        owner = ctx.owner()
        in_iface = owner is not None and owner["kind"] in ("interface",)
        m = re.match(JAVA_MODS + r"(class|interface|enum|record|@interface)\s+(\w+)", s)
        if m:
            mods = s[: m.start(1)]
            public = "public" in mods or (owner is not None and owner["kind"] == "interface"
                                          and "private" not in mods)
            kind = {"class": "class", "interface": "interface", "enum": "enum",
                    "record": "record"}.get(m.group(1), "class")
            ev = self.new_type(ctx, m.group(2), kind, public, line)
            t = ev[1]["t"]
            if t is not None and kind == "record":
                inner, _ = paren_body(s, s.find("(", m.end(2)))
                ps = [self.param(p) for p in split_top(inner or "")]
                t["ctors"].append([P(p["name"]) for p in ps])
                for p in ps:
                    add_member(t, p["name"], "java", [], kind="prop", optional=False, line=line)
            return ev
        if owner is None or owner["t"] is None:
            return None
        t = owner["t"]
        if owner["kind"] == "enum" and owner.get("enum_body"):
            if ";" in s or "(" not in s and "," not in s and not re.match(r"^[A-Z0-9_]+$", s):
                pass
            head = s.split(";")[0]
            for item in split_top(head):
                cm = re.match(r"^([A-Z][A-Z0-9_]*)\s*(\(.*\))?\s*(\{.*)?$", item)
                if cm:
                    t["cases"].append(cm.group(1))
            if ";" in s or s.endswith("{") and not re.match(r"^[A-Z]", s):
                owner["enum_body"] = False
            return None
        mods_m = re.match(JAVA_MODS, s)
        mods = mods_m.group(0)
        public = "public" in mods or (in_iface and "private" not in mods)
        if not public:
            return None
        rest = s[len(mods):]
        static = "static" in mods
        rest = re.sub(r"^<[^>]*(?:<[^>]*>[^>]*)*>\s*", "", rest)
        # constructor
        cm = re.match(r"(\w+)\s*\(", rest)
        if cm and cm.group(1) == owner["name"]:
            inner, _ = paren_body(rest)
            t["ctors"].append([self.param(p) for p in split_top(inner)])
            return None
        mm = re.match(r"(.+?)\s+(\w+)\s*\(", rest)
        if mm and "=" not in mm.group(1):
            inner, end = paren_body(rest, mm.end() - 1)
            ret = mm.group(1)
            params = [self.param(p) for p in split_top(inner)]
            add_member(t, mm.group(2), "java", params, returns=ret, static=static, line=line)
            return None
        fm = re.match(r"(.+?)\s+(\w+)\s*(=.*|;)", rest)
        if fm:
            add_member(t, fm.group(2), "java", [], kind="prop",
                       optional="@Nullable" in ann or "Nullable" in s, line=line)
        return None

    def param(self, p):
        p = re.sub(r"@[\w.]+(\((?:[^()]|\([^()]*\))*\))?\s*", "", p).strip()
        p = re.sub(r"^final\s+", "", p)
        m = re.match(r"(.+?)\s*(?:\.\.\.)?\s+(\w+)$", p)
        if not m:
            return P(p.split()[-1] if p.split() else p)
        return P(m.group(2), type_=m.group(1))


# ---------------------------------------------------------------- Kotlin
class Kotlin(Clike):
    lang = "kotlin"

    def is_annotation(self, line):
        return re.match(r"^@[\w.:]+(\(.*\))?$", line) is not None

    def continues(self, stmt, nxt):
        return nxt.startswith(":") or (stmt.rstrip().endswith(")") and nxt.startswith("{")
                                       and False)

    def decl(self, s, ctx, ann, line):
        s = re.sub(r"@[\w.:]+(\((?:[^()]|\([^()]*\))*\))?\s*", "", s).strip()
        owner = ctx.owner()
        mods_re = r"((?:(?:public|private|internal|protected|open|abstract|sealed|data|" \
                  r"enum|inner|value|override|suspend|inline|operator|infix|tailrec|" \
                  r"external|const|lateinit|actual|expect|final|fun)\s+)*)"
        m = re.match(mods_re + r"(class|interface|object)\s+(\w+)", s)
        cm = re.match(mods_re + r"companion\s+object\b\s*(\w+)?", s)
        if cm:
            frame = {"name": "Companion", "kind": "object", "public": "private" not in cm.group(1),
                     "t": owner["t"] if owner else None, "body_depth": 0, "line": line,
                     "companion": True}
            return ("type", frame)
        if m:
            mods = m.group(1)
            if "fun " in mods + " " and m.group(2) == "interface":
                pass
            public = not re.search(r"\b(private|internal|protected)\b", mods)
            kind = "enum" if "enum" in mods else (
                "interface" if m.group(2) == "interface" else "class")
            ev = self.new_type(ctx, m.group(3), kind, public, line)
            t = ev[1]["t"]
            if t is not None:
                rest = s[m.end():]
                rest = re.sub(r"^\s*<[^>]*>", "", rest)
                cpm = re.match(r"\s*(?:(?:public|private|internal|protected)\s+)?(?:constructor\s*)?\(", rest)
                if cpm:
                    inner, _ = paren_body(rest, rest.find("("))
                    params = []
                    priv_ctor = re.match(r"\s*(private|internal)\s", rest)
                    for p in split_top(inner):
                        pp, isprop, pub = self.param(p)
                        params.append(pp)
                        if isprop and pub:
                            add_member(t, pp["name"], "kotlin", [], kind="prop",
                                       optional=pp["nullable"], line=line)
                    if not priv_ctor:
                        t["ctors"].append(params)
            return ev
        if owner is None or owner["t"] is None:
            return None
        t = owner["t"]
        if owner["kind"] == "enum" and owner.get("enum_body"):
            head = s.split(";")[0]
            for item in split_top(head):
                em = re.match(r"^([A-Z][A-Za-z0-9_]*)\s*(\(.*\))?\s*(\{.*)?$", item)
                if em:
                    t["cases"].append(em.group(1))
            if ";" in s:
                owner["enum_body"] = False
            return None
        mm = re.match(mods_re + r"(constructor)\s*\(", s)
        if mm:
            if re.search(r"\b(private|internal|protected)\b", mm.group(1)):
                return None
            inner, _ = paren_body(s, s.find("(", mm.end(1)))
            t["ctors"].append([self.param(p)[0] for p in split_top(inner)])
            return None
        fm = re.match(mods_re + r"(?:<[^>]*>\s*)?(?:([\w.<>?, ]+)\.)?(`?\w+`?)\s*\(", s)
        if fm and "fun" in fm.group(1).split():
            if re.search(r"\b(private|internal|protected)\b", fm.group(1)):
                return None
            if fm.group(2):  # extension function on another type
                return None
            inner, end = paren_body(s, fm.end() - 1)
            after = s[end + 1:]
            rm = re.match(r"\s*:\s*([^={]+)", after)
            params = [self.param(p)[0] for p in split_top(inner)]
            add_member(t, fm.group(3), "kotlin", params, returns=rm.group(1) if rm else "",
                       static=bool(owner.get("companion")), line=line)
            return None
        pm = re.match(mods_re + r"(val|var)\s+(?:<[^>]*>\s*)?(`?\w+`?)\s*(?::\s*([^=]+))?", s)
        if pm:
            mods = pm.group(1)
            if re.search(r"\b(private|internal|protected)\b", mods):
                return None
            typ = (pm.group(4) or "").strip()
            add_member(t, pm.group(3), "kotlin", [], kind="prop",
                       optional=typ.endswith("?"), line=line)
        return None

    def param(self, p):
        p = re.sub(r"@[\w.:]+(\((?:[^()]|\([^()]*\))*\))?\s*", "", p).strip()
        mods = re.match(r"((?:(?:private|public|internal|protected|override|open|vararg|"
                        r"crossinline|noinline)\s+)*)(val\s+|var\s+)?", p)
        rest = p[mods.end():]
        isprop = bool(mods.group(2))
        pub = not re.search(r"\b(private|internal|protected)\b", mods.group(1))
        m = re.match(r"(`?\w+`?)\s*:\s*([^=]+?)\s*(=.*)?$", rest)
        if not m:
            return P(rest), isprop, pub
        pp = P(m.group(1), optional=bool(m.group(3)), type_=m.group(2))
        pp["nullable"] = m.group(2).strip().endswith("?")
        return pp, isprop, pub


# ---------------------------------------------------------------- C#
class CSharp(Clike):
    lang = "csharp"

    def is_annotation(self, line):
        return line.startswith("[") and line.endswith("]")

    def ends_without_body(self, stmt):
        st = stmt.rstrip()
        return st.endswith(";") or st.endswith("}")

    def decl(self, s, ctx, ann, line):
        s = re.sub(r"^\[[^\]]*\]\s*", "", s).strip()
        owner = ctx.owner()
        mods_re = r"((?:(?:public|private|protected|internal|static|sealed|abstract|partial|" \
                  r"readonly|virtual|override|async|new|unsafe|extern|required|const|file|" \
                  r"ref|volatile)\s+)*)"
        m = re.match(mods_re + r"(class|interface|enum|struct|record(?:\s+struct|\s+class)?)\s+(\w+)", s)
        if m:
            mods = m.group(1)
            in_iface = owner is not None and owner["kind"] == "interface"
            public = "public" in mods or (in_iface and "private" not in mods)
            name = m.group(3)
            kind = {"interface": "interface", "enum": "enum"}.get(m.group(2), "class")
            if kind == "interface" and re.match(r"I[A-Z]", name):
                name = name[1:]
            ev = self.new_type(ctx, name, kind, public, line, extra={"cname": m.group(3)})
            t = ev[1]["t"]
            if t is not None:
                rest = s[m.end():]
                rest = re.sub(r"^\s*<[^>]*>", "", rest)
                if rest.lstrip().startswith("("):
                    inner, _ = paren_body(rest)
                    params = [self.param(p) for p in split_top(inner)]
                    t["ctors"].append(params)
                    if "record" in m.group(2):
                        for p in params:
                            add_member(t, p["name"], "csharp", [], kind="prop", line=line)
            return ev
        if owner is None or owner["t"] is None:
            return None
        t = owner["t"]
        if owner["kind"] == "enum":
            for item in split_top(s.rstrip(",")):
                em = re.match(r"^(?:\[[^\]]*\]\s*)*([A-Z]\w*)\s*(=.*)?$", item)
                if em:
                    t["cases"].append(em.group(1))
            return None
        in_iface = owner["kind"] == "interface"
        mm = re.match(mods_re, s)
        mods = mm.group(1)
        public = "public" in mods or (in_iface and not re.search(r"\b(private|protected|internal)\b", mods))
        if not public:
            return None
        rest = s[mm.end():]
        static = "static" in mods
        cname = owner.get("cname", owner["name"])
        cm = re.match(r"(\w+)\s*\(", rest)
        if cm and cm.group(1) == cname:
            inner, _ = paren_body(rest)
            t["ctors"].append([self.param(p) for p in split_top(inner)])
            return None
        if re.match(r"(implicit|explicit)\s+operator|.*\boperator\b", rest):
            return None
        fm = re.match(r"(.+?)\s+(\w+)\s*(?:<[^>]*>)?\s*\(", rest)
        if fm and "=" not in fm.group(1):
            inner, _ = paren_body(rest, fm.end() - 1)
            add_member(t, fm.group(2), "csharp", [self.param(p) for p in split_top(inner)],
                       returns=fm.group(1), static=static, line=line)
            return None
        pm = re.match(r"(.+?)\s+(\w+)\s*(\{|=>|=|;|$)", rest)
        if pm:
            typ = pm.group(1)
            if static and typ.strip() == cname:
                t["cases"].append(pm.group(2))
                if t["kind"] != "enum":
                    t["kind"] = "enum"
                return None
            add_member(t, pm.group(2), "csharp", [], kind="prop",
                       optional=typ.strip().endswith("?"), line=line)
        return None

    def param(self, p):
        p = re.sub(r"^\[[^\]]*\]\s*", "", p).strip()
        p = re.sub(r"^(this|params|ref|out|in)\s+", "", p)
        m = re.match(r"(.+?)\s+(@?\w+)\s*(=.*)?$", p)
        if not m:
            return P(p)
        return P(m.group(2).lstrip("@"), optional=bool(m.group(3)), type_=m.group(1))


# ---------------------------------------------------------------- PHP
class Php(Clike):
    lang = "php"

    def continues(self, stmt, nxt):
        return re.match(r"(extends|implements)\b", nxt) is not None and \
            not stmt.rstrip().endswith("{")

    def decl(self, s, ctx, ann, line):
        owner = ctx.owner()
        m = re.match(r"((?:(?:final|abstract|readonly)\s+)*)(class|interface|enum|trait)\s+(\w+)", s)
        if m:
            kind = {"interface": "interface", "enum": "enum"}.get(m.group(2), "class")
            if m.group(2) == "trait":
                kind = "trait"
            return self.new_type(ctx, m.group(3), kind, True, line)
        if owner is None or owner["t"] is None:
            return None
        t = owner["t"]
        cm = re.match(r"case\s+(\w+)", s)
        if cm and owner["kind"] == "enum":
            t["cases"].append(cm.group(1))
            return None
        mm = re.match(r"((?:(?:public|private|protected|static|final|abstract|readonly)\s+)*)"
                      r"function\s+&?(\w+)\s*\(", s)
        if mm:
            mods = mm.group(1)
            if re.search(r"\b(private|protected)\b", mods):
                return None
            if owner["kind"] != "interface" and "public" not in mods and mods.strip():
                return None
            inner, end = paren_body(s, mm.end() - 1)
            after = s[end + 1:]
            rm = re.match(r"\s*:\s*([^{;]+)", after)
            params = []
            for p in split_top(inner):
                pp = self.param(p)
                params.append(pp)
                if mm.group(2) == "__construct" and pp.get("promoted"):
                    add_member(t, pp["name"], "php", [], kind="prop",
                               optional=pp["type"].startswith("?"), line=line)
            if mm.group(2) == "__construct":
                t["ctors"].append(params)
            elif not mm.group(2).startswith("__"):
                add_member(t, mm.group(2), "php", params, returns=rm.group(1) if rm else "",
                           static="static" in mods, line=line)
            return None
        pm = re.match(r"((?:(?:public|private|protected|static|readonly|var)\s+)+)"
                      r"(?:(\??[\\\w|]+)\s+)?\$(\w+)", s)
        if pm:
            if re.search(r"\b(private|protected)\b", pm.group(1)):
                return None
            add_member(t, pm.group(3), "php", [], kind="prop",
                       optional=(pm.group(2) or "").startswith("?") or "null" in (pm.group(2) or "").lower(),
                       line=line)
        return None

    def param(self, p):
        promoted = bool(re.match(r"(public|private|protected|readonly)\b", p)) and not \
            re.match(r"(private|protected)\b", p)
        p2 = re.sub(r"^((public|private|protected|readonly)\s+)+", "", p)
        m = re.match(r"(?:([?\\\w|]+)\s+)?(?:&)?(?:\.\.\.)?\$(\w+)\s*(=.*)?$", p2)
        if not m:
            return P(p2)
        pp = P(m.group(2), optional=bool(m.group(3)), type_=m.group(1) or "")
        pp["promoted"] = promoted
        return pp


# ---------------------------------------------------------------- TypeScript (node)
class Node(Clike):
    lang = "node"

    def ends_without_body(self, stmt):
        return True

    def decl(self, s, ctx, ann, line):
        owner = ctx.owner()
        m = re.match(r"export\s+(?:default\s+)?(?:declare\s+)?((?:abstract\s+)?)"
                     r"(class|interface|enum|const enum|type|function|const|async function)\s+(\w+)", s)
        if m and owner is None:
            kind = m.group(2)
            name = m.group(3)
            if kind in ("class", "interface", "enum", "const enum"):
                k = {"const enum": "enum"}.get(kind, kind)
                ev = self.new_type(ctx, name, k, True, line)
                if kind == "interface" or kind == "class":
                    pass
                return ev
            if kind == "type":
                rest = s[m.end():]
                t = ctx.s.type(name, "type", ctx.file, line)
                # string-literal union types are enums
                body = rest.split("=", 1)[1] if "=" in rest else ""
                lits = re.findall(r'"([^"]*)"', body)
                if lits and re.fullmatch(r'[\s|"]*', re.sub(r'"[^"]*"', '"', body).rstrip(";")):
                    t["kind"] = "enum"
                    t["cases"] += [x for x in re.findall(r'"\s*"', body)]
                return None
            if kind in ("function", "async function"):
                stem = file_stem_type(ctx.s, ctx.file)
                t = ctx.s.type(stem, "module", ctx.file, line)
                inner, end = paren_body(s, s.find("(", m.end()))
                add_member(t, name, "node", [self.param(p) for p in split_top(inner or "")],
                           returns=s[end + 1:], static=True, line=line)
                return None
            return None
        if owner is None or owner["t"] is None:
            return None
        t = owner["t"]
        if owner["kind"] == "enum":
            for item in split_top(s.rstrip(",")):
                em = re.match(r'^"?(\w+)"?\s*(=.*)?$', item)
                if em:
                    t["cases"].append(em.group(1))
            return None
        mods_re = r"((?:(?:public|private|protected|static|readonly|abstract|async|override|declare|get|set)\s+)*)"
        mm = re.match(mods_re + r"(#?\w+)(\?)?\s*(<[^(]*>)?\s*\(", s)
        if mm:
            mods = mm.group(1)
            name = mm.group(2)
            if re.search(r"\b(private|protected)\b", mods) or name.startswith("#") or name.startswith("__"):
                return None
            inner, end = paren_body(s, mm.end() - 1)
            params = [self.param(p) for p in split_top(inner)]
            if name == "constructor":
                t["ctors"].append(params)
                for p in split_top(inner):
                    if re.match(r"(public|readonly)\s", p):
                        pp = self.param(p)
                        add_member(t, pp["name"], "node", [], kind="prop", optional=pp["optional"], line=line)
                return None
            if re.search(r"\bset\b", mods):
                return None
            rm = re.match(r"\s*:\s*([^{;]+)", s[end + 1:])
            if re.search(r"\bget\b", mods):
                add_member(t, name, "node", [], kind="prop", line=line)
                return None
            add_member(t, name, "node", params, returns=rm.group(1) if rm else "",
                       static="static" in mods, line=line)
            return None
        pm = re.match(mods_re + r"(#?\w+)([?!])?\s*(:|=|;)", s)
        if pm:
            mods = pm.group(1)
            name = pm.group(2)
            if re.search(r"\b(private|protected)\b", mods) or name.startswith("#") or name.startswith("__"):
                return None
            add_member(t, name, "node", [], kind="prop", optional=pm.group(3) == "?",
                       static="static" in mods, line=line)
        return None

    def param(self, p):
        p = re.sub(r"^((public|private|protected|readonly)\s+)+", "", p)
        m = re.match(r"(\.\.\.)?(\w+)(\?)?\s*(?::\s*(.+?))?\s*(=.*)?$", p)
        if not m:
            return P(p)
        return P(m.group(2), optional=bool(m.group(3) or m.group(5)), type_=m.group(4) or "")


# ---------------------------------------------------------------- Dart
class Dart(Clike):
    lang = "dart"

    def is_annotation(self, line):
        return re.match(r"^@\w+(\(.*\))?$", line) is not None

    def ends_without_body(self, stmt):
        return stmt.rstrip().endswith(";")

    def continues(self, stmt, nxt):
        return (nxt.startswith("implements ") or nxt.startswith("extends ") or
                nxt.startswith("with ")) and not stmt.rstrip().endswith("{")

    def decl(self, s, ctx, ann, line):
        s = re.sub(r"^@\w+(\((?:[^()]|\([^()]*\))*\))?\s*", "", s).strip()
        s = re.sub(r"\bFunction\s*\((?:[^()]|\([^()]*\))*\)\??", "Fn", s)
        owner = ctx.owner()
        m = re.match(r"((?:(?:abstract|sealed|final|base|interface|mixin)\s+)*)(class|enum|mixin|extension type|typedef)\s+(\w+)", s)
        if m and (owner is None):
            name = m.group(3)
            kind = {"enum": "enum"}.get(m.group(2), "class")
            if "interface" in m.group(1) or ("abstract" in m.group(1) and False):
                kind = "interface"
            if m.group(2) == "typedef":
                return None
            ev = self.new_type(ctx, name, kind, not name.startswith("_"), line)
            return ev
        em = re.match(r"extension\s+(\w+)?\s*on\s+(\w+)", s)
        if em and owner is None:
            frame = {"name": em.group(2), "kind": "extension", "public": True,
                     "t": ctx.s.types.get(type_key(em.group(2))), "body_depth": 0, "line": line}
            return ("type", frame)
        if owner is None:
            vm = re.match(r"(?:const|final)\s+(?:[\w<>?, ]+\s+)?([a-zA-Z]\w*)\s*=", s)
            if vm:
                stem = file_stem_type(ctx.s, ctx.file)
                t = ctx.s.type(stem, "module", ctx.file, line)
                add_member(t, vm.group(1), "dart", [], kind="prop", line=line)
                return None
            fm = re.match(r"(?:[\w<>?, ]+\s+)?([a-zA-Z]\w*)\s*(?:<[^(]*>)?\s*\(", s)
            if fm and not s.startswith(("import", "export", "part", "library")) and \
                    not fm.group(1).startswith("_") and (s.rstrip().endswith("{") or "=>" in s):
                stem = file_stem_type(ctx.s, ctx.file)
                t = ctx.s.type(stem, "module", ctx.file, line)
                inner, end = paren_body(s, fm.end() - 1)
                add_member(t, fm.group(1), "dart", self.params(inner), static=True, line=line)
            return None
        if owner["t"] is None:
            return None
        t = owner["t"]
        if owner["kind"] == "enum" and owner.get("enum_body"):
            head = s.split(";")[0]
            for item in split_top(head):
                cm = re.match(r"^([A-Za-z]\w*)\s*(\(.*\))?$", item)
                if cm:
                    t["cases"].append(cm.group(1))
            if ";" in s:
                owner["enum_body"] = False
            return None
        cname = owner["name"]
        cm = re.match(r"(?:(?:const|factory|external)\s+)*" + re.escape(cname) + r"(?:\.(\w+))?\s*\(", s)
        if cm:
            if cm.group(1) and cm.group(1).startswith("_"):
                return None
            inner, _ = paren_body(s, cm.end() - 1)
            params = self.params(inner)
            if cm.group(1):
                add_member(t, cm.group(1), "dart", params, static=True, line=line)
            else:
                if s.lstrip().startswith(cname + "._") or cname.startswith("_"):
                    return None
                t["ctors"].append(params)
                for p in params:
                    if p.get("this") and not p["name"].startswith("_"):
                        add_member(t, p["name"], "dart", [], kind="prop",
                                   optional=p["optional"], line=line)
            return None
        mods_re = r"((?:(?:static|final|const|late|external|abstract|covariant|var|@override)\s+)*)"
        gm = re.match(mods_re + r"(?:([\w<>?,. ]+?)\s+)?get\s+(\w+)", s)
        if gm:
            if not gm.group(3).startswith("_"):
                add_member(t, gm.group(3), "dart", [], kind="prop",
                           optional=(gm.group(2) or "").endswith("?"),
                           static="static" in gm.group(1), line=line)
            return None
        if re.match(mods_re + r"(?:[\w<>?,. ]+?\s+)?set\s+\w+", s):
            return None
        if re.match(r"(operator|.*\soperator\s*[=<>\[\-+*/~%|&^]+\s*\()", s):
            return None
        mm = re.match(mods_re + r"(?:([\w<>?,. ()]+?)\s+)?([a-zA-Z_]\w*)\s*(<[^(]*>)?\s*\(", s)
        if mm and not s.startswith(("return", "if", "for", "while", "switch", "throw", "await", "super", "this")):
            name = mm.group(3)
            if name.startswith("_"):
                return None
            if mm.group(2) is None and not (s.rstrip().endswith("{") or "=>" in s or s.rstrip().endswith(";") and owner["kind"] in ("interface", "class")):
                return None
            inner, end = paren_body(s, s.find("(", mm.end(3)))
            add_member(t, name, "dart", self.params(inner), returns=mm.group(2) or "",
                       static="static" in mm.group(1), line=line)
            return None
        pm = re.match(mods_re + r"(?:([\w<>?,. ()]+?)\s+)?([a-zA-Z]\w*)\s*(=[^>].*|;|,)", s)
        if pm and (pm.group(1).strip() or pm.group(2)):
            name = pm.group(3)
            if name.startswith("_") or name in ("return", "throw"):
                return None
            add_member(t, name, "dart", [], kind="prop",
                       optional=(pm.group(2) or "").strip().endswith("?"), line=line)
        return None

    def params(self, inner):
        out = []
        inner = inner.strip()
        pos = inner
        named = ""
        optpos = ""
        bi = inner.find("{")
        if bi >= 0 and inner.rstrip().rstrip(",").endswith("}"):
            pos, named = inner[:bi], inner[bi + 1:inner.rstrip().rstrip(",").rfind("}")]
        bi = pos.find("[")
        if bi >= 0 and pos.rstrip().rstrip(",").endswith("]"):
            pos, optpos = pos[:bi], pos[bi + 1:pos.rstrip().rstrip(",").rfind("]")]
        for p in split_top(pos):
            out.append(self.param(p, False, False))
        for p in split_top(optpos):
            out.append(self.param(p, True, False))
        for p in split_top(named):
            out.append(self.param(p, None, True))
        return out

    def param(self, p, optional, kw):
        p = re.sub(r"^@\w+\s+", "", p.strip())
        req = p.startswith("required ")
        p = re.sub(r"^(required|final|covariant)\s+", "", p)
        default = "=" in p
        p = p.split("=")[0].strip()
        isthis = p.startswith("this.") or " this." in p
        name = re.split(r"[\s.]", p)[-1]
        typ = p[: -len(name)].strip()
        if optional is None:
            optional = not req
        pp = P(name, optional=optional or default, kw=kw, type_=typ)
        pp["this"] = isthis
        return pp


# ---------------------------------------------------------------- Swift
class Swift(Clike):
    lang = "swift"

    def is_annotation(self, line):
        return re.match(r"^@\w+(\(.*\))?$", line) is not None

    def ends_without_body(self, stmt):
        return True

    def continues(self, stmt, nxt):
        return stmt.rstrip().endswith(")") and re.match(r"^(async|throws|rethrows|->|where)\b|^->", nxt) is not None or \
            (re.search(r"\b(class|struct|protocol|enum|extension|actor)\b", stmt) and stmt.rstrip().endswith(",") )

    def decl(self, s, ctx, ann, line):
        s = re.sub(r"^(@\w+(\([^)]*\))?\s+)+", "", s).strip()
        owner = ctx.owner()
        mods_re = r"((?:(?:public|open|private|fileprivate|internal|final|static|class|override|" \
                  r"mutating|nonmutating|convenience|required|indirect|lazy|weak|unowned|" \
                  r"nonisolated|package|dynamic|@\w+)\s+)*)"
        m = re.match(mods_re + r"(class|struct|protocol|enum|actor|extension|typealias)\s+([\w.]+)", s)
        if m and not (m.group(2) == "class" and re.match(r"class\s+(func|var|let)\b", s)):
            mods = m.group(1)
            kind = m.group(2)
            name = m.group(3)
            if kind == "typealias":
                return None
            if kind == "extension":
                frame = {"name": name, "kind": "extension", "public": True,
                         "t": ctx.s.types.get(type_key(name)), "body_depth": 0, "line": line,
                         "ext_public": "public" in mods}
                if frame["t"] is None and "." not in name:
                    frame["t"] = None
                    frame["ext_target"] = name
                return ("type", frame)
            in_proto = owner is not None and owner["kind"] == "protocol"
            public = bool(re.search(r"\b(public|open)\b", mods)) or (
                owner is not None and owner.get("ext_public") and not re.search(r"\b(private|fileprivate|internal)\b", mods))
            k = {"protocol": "interface", "enum": "enum"}.get(kind, "class")
            ev = self.new_type(ctx, name, k, public, line, extra={"swift_kind": kind})
            return ev
        if owner is None:
            fm = re.match(mods_re + r"func\s+(\w+)", s)
            if fm and re.search(r"\b(public|open)\b", fm.group(1)):
                stem = file_stem_type(ctx.s, ctx.file)
                t = ctx.s.type(stem, "module", ctx.file, line)
                inner, end = paren_body(s, s.find("(", fm.end()))
                add_member(t, fm.group(2), "swift", self.params(inner), static=True, line=line)
            return None
        t = owner["t"]
        if t is None and owner.get("ext_target"):
            t = ctx.s.types.get(type_key(owner["ext_target"]))
            owner["t"] = t
        if t is None:
            return None
        in_proto = owner["kind"] == "interface"
        cm = re.match(mods_re + r"case\s+(.+)$", s)
        if cm and owner["kind"] == "enum":
            for item in split_top(cm.group(2)):
                im = re.match(r"`?(\w+)`?", item)
                if im:
                    t["cases"].append(im.group(1))
            return None
        mm = re.match(mods_re, s)
        mods = mm.group(1)
        public = bool(re.search(r"\b(public|open)\b", mods)) or in_proto or (
            owner.get("ext_public") and not re.search(r"\b(private|fileprivate|internal)\b", mods))
        if not public:
            return None
        rest = s[mm.end():]
        static = bool(re.search(r"\b(static|class)\b", mods))
        im = re.match(r"init[?!]?\s*(<[^(]*>)?\s*\(", rest)
        if im:
            inner, _ = paren_body(rest, rest.find("("))
            params = self.params(inner)
            if len(params) == 1 and params[0]["label"] in ("from",) and "Decoder" in params[0]["type"]:
                return None
            t["ctors"].append(params)
            return None
        fm = re.match(r"func\s+(`?\w+`?)\s*(<[^(]*>)?\s*\(", rest)
        if fm:
            inner, end = paren_body(rest, rest.find("(", fm.end(1)))
            rm = re.search(r"->\s*(.+?)\s*(\{|where|$)", rest[end + 1:])
            params = self.params(inner)
            if fm.group(1) == "encode" and params and params[0]["label"] == "to":
                return None
            add_member(t, fm.group(1), "swift", params, returns=rm.group(1) if rm else "",
                       static=static, line=line)
            return None
        pm = re.match(r"(?:let|var)\s+(`?\w+`?)\s*(?::\s*([^={]+))?", rest)
        if pm:
            if static and owner["kind"] != "enum" and pm.group(2) and pm.group(2).strip() == owner["name"]:
                pass
            add_member(t, pm.group(1), "swift", [], kind="prop",
                       optional=(pm.group(2) or "").strip().endswith("?"), static=static, line=line)
        return None

    def params(self, inner):
        out = []
        for p in split_top(inner):
            m = re.match(r"(`?\w+`?)(?:\s+(`?\w+`?))?\s*:\s*(.+?)\s*(=.*)?$", p)
            if not m:
                continue
            label, name = m.group(1), m.group(2) or m.group(1)
            pp = P(name.strip("`"), optional=bool(m.group(4)), kw=label != "_", type_=m.group(3))
            pp["label"] = label
            out.append(pp)
        return out


# ---------------------------------------------------------------- Rust
STD_TRAITS = {"Debug", "Display", "Clone", "Default", "From", "Into", "PartialEq", "Eq",
              "Hash", "Drop", "Serialize", "Deserialize", "Error", "Send", "Sync", "FromStr",
              "TryFrom", "AsRef", "Deref", "DerefMut", "Iterator", "IntoIterator", "Ord",
              "PartialOrd", "Copy", "Visitor", "Borrow", "Extend", "FromIterator", "Future",
              "Stream", "Write", "Read", "Serializer", "Deserializer", "Fmt", "Any"}


class Rust(Clike):
    lang = "rust"

    def is_annotation(self, line):
        return line.startswith("#[") or line.startswith("#![")

    def ends_without_body(self, stmt):
        return True

    def continues(self, stmt, nxt):
        return stmt.rstrip().endswith(")") and (nxt.startswith("->") or nxt.startswith("where"))

    def decl(self, s, ctx, ann, line):
        owner = ctx.owner()
        m = re.match(r"pub(\([\w:]+\))?\s+(struct|enum|trait|type|mod|union)\s+(\w+)", s)
        if m and owner is None:
            if m.group(1):
                return None
            kind = {"struct": "class", "enum": "enum", "trait": "interface"}.get(m.group(2))
            if kind is None:
                return None
            ev = self.new_type(ctx, m.group(3), kind, True, line)
            if kind == "class" and "(" in s.split("{")[0] and "{" not in s.split("(")[0]:
                pass
            return ev
        mp = re.match(r"(?:pub(\([\w:]+\))?\s+)?(struct|enum|trait)\s+(\w+)", s)
        if mp and owner is None:
            return ("type", {"name": mp.group(3), "kind": "private", "public": False, "t": None,
                             "body_depth": 0, "line": line})
        im = re.match(r"impl(?:<[^>]*(?:<[^>]*>[^>]*)*>)?\s+(?:([\w:]+)(?:<[^{]*?>)?\s+for\s+)?([\w:]+)", s)
        if im and owner is None:
            trait = im.group(1)
            target = im.group(2).split("::")[-1]
            tname = (trait or "").split("::")[-1]
            hide = tname in STD_TRAITS or (trait and tname not in [x["name"] for x in ctx.s.types.values()] and False)
            frame = {"name": target, "kind": "impl", "public": not hide,
                     "t": None if hide else ctx.s.types.get(type_key(target)), "trait": trait,
                     "body_depth": 0, "line": line, "impl_target": target}
            return ("type", frame)
        if owner is None:
            fm = re.match(r"pub\s+(?:const\s+)?(?:async\s+)?(?:unsafe\s+)?fn\s+(\w+)", s)
            if fm:
                stem = file_stem_type(ctx.s, ctx.file)
                t = ctx.s.type(stem, "module", ctx.file, line)
                inner, end = paren_body(s, s.find("(", fm.end()))
                add_member(t, fm.group(1), "rust", self.params(inner), static=True, line=line)
            return None
        t = owner["t"]
        if t is None and owner.get("impl_target") and owner["public"]:
            t = ctx.s.types.get(type_key(owner["impl_target"]))
            owner["t"] = t
        if t is None:
            return None
        if owner["kind"] == "enum":
            vm = re.match(r"^([A-Z]\w*)\s*(\(|\{|,|=|$)", s)
            if vm:
                t["cases"].append(vm.group(1))
            return None
        if owner["kind"] == "class":
            fm = re.match(r"pub\s+(r#\w+|\w+)\s*:\s*(.+?),?$", s)
            if fm:
                add_member(t, fm.group(1), "rust", [], kind="prop",
                           optional=fm.group(2).strip().startswith("Option<"), line=line)
            return None
        in_trait = owner["kind"] == "interface" or (owner["kind"] == "impl" and owner.get("trait"))
        fm = re.match(r"(pub(\([\w:]+\))?\s+)?(?:const\s+)?(?:async\s+)?(?:unsafe\s+)?fn\s+(\w+)\s*(<[^(]*>)?\s*\(", s)
        if fm:
            if not in_trait and (not fm.group(1) or fm.group(2)):
                return None
            inner, end = paren_body(s, s.find("(", fm.end(3)))
            params = self.params(inner)
            has_self = bool(re.match(r"\s*(&\s*('\w+\s+)?(mut\s+)?)?(mut\s+)?self\b", inner))
            rm = re.search(r"->\s*(.+?)\s*(\{|where|;|$)", s[end + 1:])
            name = fm.group(3)
            if name == "new" and not has_self:
                t["ctors"].append(params)
                return None
            add_member(t, name, "rust", params, returns=rm.group(1) if rm else "",
                       static=not has_self, line=line)
        return None

    def params(self, inner):
        out = []
        for p in split_top(inner):
            if re.match(r"^(&\s*('\w+\s+)?(mut\s+)?)?(mut\s+)?self\b", p):
                continue
            m = re.match(r"(?:mut\s+)?(\w+)\s*:\s*(.+)$", p)
            if m:
                out.append(P(m.group(1), type_=m.group(2)))
        return out


# ---------------------------------------------------------------- Go
class Go(Clike):
    lang = "go"

    def ends_without_body(self, stmt):
        return True

    def decl(self, s, ctx, ann, line):
        owner = ctx.owner()
        if owner is None:
            tm = re.match(r"type\s+([A-Z]\w*)(\[[^\]]*\])?\s+(struct|interface|func|=)?\s*(.*)$", s)
            if tm:
                kind = {"struct": "class", "interface": "interface"}.get(tm.group(3) or "", "alias")
                name = tm.group(1)
                if kind == "alias":
                    base = tm.group(4).strip()
                    t = ctx.s.type(name, "alias", ctx.file, line)
                    t["alias_of"] = base
                    return None
                return self.new_type(ctx, name, kind, True, line)
            if re.match(r"type\s+[a-z]", s):
                return ("type", {"name": "_", "kind": "private", "public": False, "t": None,
                                 "body_depth": 0, "line": line})
            if s.startswith("const (") or s.startswith("var ("):
                for cm in re.finditer(r"\b([A-Z]\w*)\s+([A-Z]\w*)\s*=", s):
                    self.const_case(ctx, cm.group(1), cm.group(2))
                im = re.search(r"\b([A-Z]\w*)\s+([A-Z]\w*)\s*=\s*iota\b(.*)$", s)
                if im:
                    for tok in re.findall(r"\b([A-Z]\w*)\b", im.group(3)):
                        if tok.startswith(im.group(2)) and tok != im.group(2):
                            self.const_case(ctx, tok, im.group(2))
                if s.startswith("var ("):
                    inner, _ = paren_body(s)
                    for vm in re.finditer(r"(?:^|\s)([A-Z]\w*)\s*(?:[\w.*\[\]]+\s*)?=", inner or ""):
                        self.module_var(ctx, vm.group(1), line)
                return None
            vm = re.match(r"var\s+([A-Z]\w*)\b", s)
            if vm:
                self.module_var(ctx, vm.group(1), line)
                return None
            if False:
                return ("type", {"name": "_const", "kind": "constblock", "public": True,
                                 "t": None, "body_depth": 0, "line": line, "consts": True})
            cm = re.match(r"const\s+([A-Z]\w*)\s+([A-Z]\w*)\s*=", s)
            if cm:
                self.const_case(ctx, cm.group(1), cm.group(2))
                return None
            fm = re.match(r"func\s+(?:\((\w+\s+)?\*?(\w+)(\[[^\]]*\])?\)\s+)?([A-Z]\w*)\s*(\[[^\]]*\])?\s*\(", s)
            if fm:
                recv = fm.group(2)
                name = fm.group(4)
                inner, end = paren_body(s, fm.end() - 1)
                params = self.params(inner)
                ret = s[end + 1:].split("{")[0].strip()
                if recv:
                    if not recv[0].isupper():
                        return None
                    t = ctx.s.types.get(type_key(recv))
                    if t is None:
                        t = ctx.s.type(recv, "class", ctx.file, line)
                    add_member(t, name, "go", params, returns=ret, line=line)
                    return None
                if name.startswith("New") and type_key(name[3:]) in ctx.s.types:
                    ctx.s.types[type_key(name[3:])]["ctors"].append(params)
                    return None
                wm = re.match(r"New([A-Z]\w*?)(With[A-Z]\w*)$", name)
                if wm and type_key(wm.group(1)) in ctx.s.types:
                    # a static factory: NewClientWithToken is Client.withToken
                    add_member(ctx.s.types[type_key(wm.group(1))], wm.group(2), "go", params,
                               returns=ret, static=True, line=line)
                    return None
                if name.startswith("New"):
                    ctx.s.pending_ctors = getattr(ctx.s, "pending_ctors", [])
                    ctx.s.pending_ctors.append((name[3:], params, ctx.file, line))
                    return None
                stem = file_stem_type(ctx.s, ctx.file)
                t = ctx.s.type(stem, "module", ctx.file, line)
                add_member(t, name, "go", params, returns=ret, static=True, line=line)
            return None
        if owner.get("consts"):
            cm = re.match(r"([A-Z]\w*)\s+([A-Z]\w*)\s*=", s)
            if cm:
                self.const_case(ctx, cm.group(1), cm.group(2))
            return None
        t = owner["t"]
        if t is None:
            return None
        if owner["kind"] in ("class", "interface"):
            em = re.match(r"^\*?(?:\w+\.)?([A-Z]\w*)(\[[^\]]*\])?$", s)
            if em:
                t.setdefault("parents", []).append(em.group(1))
                return None
        if owner["kind"] == "class":
            fm = re.match(r"([A-Z]\w*(?:\s*,\s*[A-Z]\w*)*)\s+([^`]+?)(\s+`.*`)?$", s)
            if fm:
                for nm in fm.group(1).split(","):
                    add_member(t, nm.strip(), "go", [], kind="prop",
                               optional=fm.group(2).strip().startswith("*"), line=line)
            return None
        if owner["kind"] == "interface":
            mm = re.match(r"([A-Z]\w*)\s*\(", s)
            if mm:
                inner, end = paren_body(s, mm.end() - 1)
                add_member(t, mm.group(1), "go", self.params(inner), returns=s[end + 1:], line=line)
        return None

    def module_var(self, ctx, name, line):
        stem = file_stem_type(ctx.s, ctx.file)
        t = ctx.s.type(stem, "module", ctx.file, line)
        add_member(t, name, "go", [], kind="prop", line=line)

    def const_case(self, ctx, name, typ):
        t = ctx.s.types.get(type_key(typ))
        if t is None:
            return
        case = name[len(typ):] if name.startswith(typ) else name
        if t["kind"] in ("alias", "enum"):
            t["kind"] = "enum"
            t["cases"].append(case or name)

    def params(self, inner):
        out = []
        pending = []
        for p in split_top(inner):
            bits = p.split(None, 1)
            if len(bits) == 1:
                pending.append(bits[0])
                continue
            for nm in pending:
                out.append(P(nm, type_=bits[1]))
            pending = []
            out.append(P(bits[0], type_=bits[1]))
        for nm in pending:  # unnamed params: types only
            out.append(P(nm))
        return [p for p in out if "context.Context" not in p["type"]]


# ---------------------------------------------------------------- Python (ast)
def run_python(surface, path, rel):
    src = open(path, encoding="utf-8").read()
    tree = ast.parse(src)
    stem = os.path.splitext(os.path.basename(path))[0]

    def args_of(fn, drop_self=True):
        a = fn.args
        out = []
        positional = list(a.posonlyargs) + list(a.args)
        defaults = [None] * (len(positional) - len(a.defaults)) + list(a.defaults)
        for arg, d in zip(positional, defaults):
            out.append(P(arg.arg, optional=d is not None))
        for arg, d in zip(a.kwonlyargs, a.kw_defaults):
            out.append(P(arg.arg, optional=d is not None, kw=True))
        if drop_self and out and out[0]["name"] in ("self", "cls"):
            out = out[1:]
        return out

    def ret_of(fn):
        return ast.unparse(fn.returns) if fn.returns is not None else ""

    def visit_class(node, prefix):
        name = node.name
        if name.startswith("_"):
            return
        bases = [ast.unparse(b) for b in node.bases]
        kind = "enum" if any("Enum" in b for b in bases) else (
            "interface" if any(b in ("ABC", "Protocol") for b in bases) else "class")
        t = surface.type(prefix + name, kind, rel, node.lineno)
        t.setdefault("parents", []).extend(b.split(".")[-1].split("[")[0] for b in bases)
        is_dataclass = any("dataclass" in ast.unparse(d) for d in node.decorator_list)
        is_model = any(b in ("BaseModel",) or b.endswith("Model") for b in bases)
        fields = []
        for st in node.body:
            if isinstance(st, ast.ClassDef):
                visit_class(st, prefix + name)
            elif isinstance(st, ast.AnnAssign) and isinstance(st.target, ast.Name):
                nm = st.target.id
                if nm.startswith("_") or "ClassVar" in ast.unparse(st.annotation):
                    continue
                ann = ast.unparse(st.annotation)
                has_default = st.value is not None
                if has_default and isinstance(st.value, ast.Call) and \
                        ast.unparse(st.value.func) in ("Field", "field", "dataclasses.field"):
                    has_default = bool(st.value.args) and ast.unparse(st.value.args[0]) != "..." or \
                        any(k.arg in ("default", "default_factory") for k in st.value.keywords)
                if kind == "enum":
                    t["cases"].append(nm)
                    continue
                optional = "Optional" in ann or "None" in ann
                add_member(t, nm, "python", [], kind="prop", optional=optional, line=st.lineno)
                fields.append(P(nm, optional=has_default, kw=is_model))
            elif isinstance(st, ast.Assign):
                for tg in st.targets:
                    if isinstance(tg, ast.Name) and not tg.id.startswith("_"):
                        if kind == "enum":
                            t["cases"].append(tg.id)
            elif isinstance(st, (ast.FunctionDef, ast.AsyncFunctionDef)):
                nm = st.name
                decos = [ast.unparse(d) for d in st.decorator_list]
                if nm == "__init__":
                    t["ctors"].append(args_of(st))
                    # self.x assignments of public attributes are properties
                    for sub in ast.walk(st):
                        if isinstance(sub, (ast.Assign, ast.AnnAssign)):
                            tgs = sub.targets if isinstance(sub, ast.Assign) else [sub.target]
                            for tg in tgs:
                                if isinstance(tg, ast.Attribute) and isinstance(tg.value, ast.Name) \
                                        and tg.value.id == "self" and not tg.attr.startswith("_"):
                                    add_member(t, tg.attr, "python", [], kind="prop", line=sub.lineno)
                    continue
                if nm.startswith("_"):
                    continue
                if any(d.endswith(".setter") or d.endswith(".deleter") for d in decos):
                    continue
                if "property" in decos or "cached_property" in decos:
                    add_member(t, nm, "python", [], kind="prop",
                               optional="Optional" in ret_of(st), line=st.lineno)
                    continue
                static = "staticmethod" in decos or "classmethod" in decos
                if any(d.startswith("field_validator") or d.startswith("model_validator") or
                       d.startswith("field_serializer") or d.startswith("model_serializer")
                       for d in decos):
                    continue
                add_member(t, nm, "python", args_of(st), returns=ret_of(st), static=static,
                           line=st.lineno)
        if (is_dataclass or is_model) and not t["ctors"]:
            t["ctors"].append(fields)

    has_class = any(isinstance(n, ast.ClassDef) for n in tree.body)
    for node in tree.body:
        if not has_class and stem != "__init__" and isinstance(node, (ast.Assign, ast.AnnAssign)):
            tgs = node.targets if isinstance(node, ast.Assign) else [node.target]
            for tg in tgs:
                if isinstance(tg, ast.Name) and not tg.id.startswith("_") and tg.id != "__all__":
                    t = surface.type(stem, "module", rel, node.lineno)
                    add_member(t, tg.id, "python", [], kind="prop", line=node.lineno)
        if isinstance(node, ast.ClassDef):
            visit_class(node, "")
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and not node.name.startswith("_"):
            t = surface.type(stem if stem != "__init__" else os.path.basename(os.path.dirname(path)),
                             "module", rel, node.lineno)
            add_member(t, node.name, "python", args_of(node, False), returns=ret_of(node),
                       static=True, line=node.lineno)


# ---------------------------------------------------------------- Ruby (indentation)
def ruby_params(sig):
    out = []
    for p in split_top(sig):
        p = p.strip()
        if p.startswith("&") or p.startswith("**") or p == "*":
            continue
        m = re.match(r"\*?(\w+):\s*(.*)$", p)
        if m:
            out.append(P(m.group(1), optional=bool(m.group(2).strip()), kw=True))
            continue
        m = re.match(r"\*?(\w+)\s*(=\s*(.*))?$", p)
        if m:
            out.append(P(m.group(1), optional=bool(m.group(2)) or p.startswith("*")))
    return out


def run_ruby(surface, path, rel):
    text = blank(open(path, encoding="utf-8").read(), "ruby")
    lines = text.split("\n")
    stack = []  # (indent, name, kind, t, private, is_namespace)
    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        indent = len(raw) - len(raw.lstrip())
        if not line:
            i += 1
            continue
        while stack and indent < stack[-1]["indent"]:
            closing = indent == stack[-1]["indent"] - 2 and line == "end"
            stack.pop()
            if closing:
                break
        if line == "end":
            i += 1
            continue
        m = re.match(r"(class|module)\s+([\w:]+)(\s*<\s*([\w:]+))?\s*$", line) or \
            re.match(r"(class|module)\s+([\w:]+)(\s*<\s*([\w:.()]+))?\s*(;.*)?$", line)
        if m and m.group(2) != "<<":
            segs = m.group(2).split("::")
            name = segs[-1]
            outer = [fr["name"] for fr in stack if fr["t"] is not None]
            for s_ in segs[:-1]:
                outer.append(s_)
            outer = [o for o in outer if key(o) not in NAMESPACE_SEGMENTS and not o.endswith("Api")]
            hidden = any(fr["private"] or fr["hidden"] for fr in stack)
            is_ns = key(name) in NAMESPACE_SEGMENTS or (
                m.group(1) == "module" and name in ("Petstore", "Client") and
                all(fr["ns"] for fr in stack))
            outer = [o for o in outer if o not in ("Petstore", "Client")] if not stack or \
                all(fr["ns"] for fr in stack) else outer
            t = None
            if not is_ns and not hidden:
                kind = "class" if m.group(1) == "class" else "module"
                parent = m.group(4) or ""
                t = surface.type("".join(outer) + name, kind, rel, i + 1)
                if parent:
                    t.setdefault("parents", []).append(parent.split("::")[-1])
            stack.append({"indent": indent + 2, "name": name, "t": t, "private": False,
                          "hidden": hidden, "ns": is_ns, "selfclass": False})
            i += 1
            continue
        if line.startswith("class << self"):
            stack.append({"indent": indent + 2, "name": "<<self", "t": stack[-1]["t"] if stack else None,
                          "private": False, "hidden": False, "ns": False, "selfclass": True})
            i += 1
            continue
        owner = stack[-1] if stack else None
        if owner is None or indent != owner["indent"] or owner["t"] is None:
            i += 1
            continue
        t = owner["t"]
        im = re.match(r"include\s+([\w:]+)$", line)
        if im:
            t.setdefault("parents", []).append(im.group(1).split("::")[-1])
            i += 1
            continue
        if line in ("private", "protected"):
            owner["private"] = True
            i += 1
            continue
        if line == "public":
            owner["private"] = False
            i += 1
            continue
        if re.match(r"(private_class_method|private_constant)\b", line):
            i += 1
            continue
        pm = re.match(r"private\s+(def\s+.*|:\w+.*)$", line)
        if pm:
            i += 1
            continue
        am = re.match(r"attr_(reader|accessor|writer)\s+(.*)$", line)
        if am and not owner["private"]:
            stmt = am.group(2)
            while stmt.rstrip().endswith(",") and i + 1 < len(lines):
                i += 1
                stmt += " " + lines[i].strip()
            for nm in re.findall(r":(\w+)", stmt):
                if am.group(1) != "writer":
                    add_member(t, nm, "ruby", [], kind="prop", line=i + 1)
                if am.group(1) != "reader":
                    add_member(t, "set_" + nm, "ruby", [P(nm)], line=i + 1)
            i += 1
            continue
        atm = re.match(r"attribute(\?)?\s+:(\w+)\s*,\s*(.*)$", line)
        if atm:
            opt = bool(atm.group(1)) or "optional" in atm.group(3) or "omittable" in atm.group(3)
            add_member(t, atm.group(2), "ruby", [], kind="prop", optional=opt, line=i + 1)
            t.setdefault("_attrs", []).append(P(atm.group(2), optional=opt, kw=True))
            i += 1
            continue
        cm = re.match(r"([A-Z][A-Z0-9_]*)\s*=", line)
        if cm and not owner["private"]:
            if re.match(r"[A-Z][A-Z0-9_]*\s*=\s*(\"|'|:\w|-?\d)", line):
                t.setdefault("_consts", []).append(cm.group(1))
            elif cm.group(1) not in ("VALUES", "ALL", "ALL_VALUES"):
                t["_nonliteral"] = True
            if cm.group(1) not in ("ATTRIBUTE_MAP", "JSON_KEY_MAP", "OPENAPI_TYPES",
                                   "OPENAPI_FORMATS", "REQUIRED_NULLABLE", "VERSION"):
                add_member(t, cm.group(1), "ruby", [], kind="prop", line=i + 1)
            i += 1
            continue
        dm = re.match(r"def\s+(self\.)?([\w?!=\[\]<>+\-]+)\s*(\(|$|\s)", line)
        if dm:
            stmt = line
            if "(" in line.split("def", 1)[1][:len(dm.group(2)) + 8]:
                while balance(stmt) > 0 and i + 1 < len(lines):
                    i += 1
                    stmt += " " + lines[i].strip()
            name = dm.group(2)
            static = bool(dm.group(1)) or owner["selfclass"]
            sig = ""
            if "(" in stmt:
                inner, _ = paren_body(stmt, stmt.find("(", stmt.find(name) + len(name) - 1))
                sig = inner or ""
            else:
                sig = stmt.split(name, 1)[1].strip()
                if sig.startswith("="):
                    sig = ""
            if owner["private"]:
                i += 1
                continue
            params = ruby_params(sig)
            if name == "initialize":
                t["ctors"].append(params)
            elif name.endswith("=") and name not in ("==", "===", "!="):
                add_member(t, "set_" + name[:-1], "ruby", params, static=static, line=i + 1)
            else:
                add_member(t, name, "ruby", params, static=static, line=i + 1)
        i += 1
    for t in surface.types.values():
        if "_attrs" in t and not t["ctors"]:
            t["ctors"].append(t["_attrs"])
        if t.get("_consts") and t["kind"] in ("module", "class") and not t.get("parents") and \
                all(m["static"] for m in t["members"].values()) and not t["ctors"] and \
                not t.get("_enum_done") and not t.get("_nonliteral"):
            t["_enum_done"] = True
            t["kind"] = "enum"
            t["cases"] = [c for c in t["_consts"] if c not in ("ALL", "VALUES", "ALL_VALUES")]
            t["props"].clear()


# ---------------------------------------------------------------- Elixir (indentation)
def elixir_params(sig):
    out = []
    for p in split_top(sig):
        p = p.strip()
        if re.match(r"%__MODULE__\{\}\s*=\s*\w+|%__MODULE__\{.*\}|^_?api$|^_?self$", p):
            continue
        m = re.match(r"(?:%[\w.]+\{\}\s*=\s*)?(_?\w+)\s*(\\\\\s*(.*))?$", p)
        if m and m.group(1) not in ("nil", "true", "false") and not m.group(1)[0].isdigit():
            out.append(P(m.group(1).lstrip("_"), optional=bool(m.group(2))))
        else:
            m = re.match(r".*=\s*(\w+)$", p)
            out.append(P(m.group(1) if m else "_pattern"))
    return out


def run_elixir(surface, path, rel):
    text = blank(open(path, encoding="utf-8").read(), "elixir")
    lines = text.split("\n")
    stack = []
    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        indent = len(raw) - len(raw.lstrip())
        if not line:
            i += 1
            continue
        while stack and indent < stack[-1]["indent"]:
            closing = indent == stack[-1]["indent"] - 2 and line == "end"
            stack.pop()
            if closing:
                break
        m = re.match(r"defmodule\s+([\w.]+)\s+do", line)
        if m:
            segs = m.group(1).split(".")
            if stack:
                segs = [s_ for fr in stack for s_ in fr["segs"]] + segs
            keep = [s_ for s_ in segs[:-1] if key(s_) not in NAMESPACE_SEGMENTS
                    and not s_.endswith("Api")] + [segs[-1]]
            if key(segs[-1]) in NAMESPACE_SEGMENTS:
                keep = []
            name = "".join(keep)
            t = surface.type(name, "module", rel, i + 1) if keep else None
            stack.append({"indent": indent + 2, "segs": m.group(1).split(".") if not stack else m.group(1).split("."), "t": t})
            i += 1
            continue
        owner = stack[-1] if stack else None
        if owner is None or owner["t"] is None or indent != owner["indent"]:
            i += 1
            continue
        t = owner["t"]
        sm = re.match(r"(defstruct|defexception)\s*(.*)$", line)
        if sm:
            stmt = sm.group(2)
            while (balance(stmt) > 0 or stmt.rstrip().endswith(",")) and i + 1 < len(lines):
                i += 1
                stmt += " " + lines[i].strip()
            body = stmt.strip().strip("[]")
            for item in split_top(body):
                im = re.match(r":?(\w+):?(\s*(.*))?$", item.strip())
                if im:
                    add_member(t, im.group(1), "elixir", [], kind="prop", line=i + 1)
                    t.setdefault("_fields", []).append(im.group(1))
            if sm.group(1) == "defexception":
                t["kind"] = "error"
            i += 1
            continue
        cbm = re.match(r"@callback\s+(\w+)\((.*)\)\s*::", line)
        if cbm:
            add_member(t, cbm.group(1), "elixir",
                       [P("arg%d" % n) for n, _ in enumerate(split_top(cbm.group(2)))][1:]
                       if cbm.group(2).strip() else [], line=i + 1)
            i += 1
            continue
        em = re.match(r"@enforce_keys\s*(.*)$", line)
        if em:
            stmt = em.group(1)
            while balance(stmt) > 0 and i + 1 < len(lines):
                i += 1
                stmt += " " + lines[i].strip()
            t["_enforce"] = re.findall(r":(\w+)", stmt)
            i += 1
            continue
        tm = re.match(r"@type\s+t\s*::\s*(.*)$", line)
        if tm:
            stmt = tm.group(1)
            while i + 1 < len(lines) and lines[i + 1].strip().startswith("|"):
                i += 1
                stmt += " " + lines[i].strip()
            if re.fullmatch(r"\s*:\w+(\s*\|\s*:\w+)*\s*", stmt):
                t["kind"] = "enum"
                t["cases"] = re.findall(r":(\w+)", stmt)
            i += 1
            continue
        if re.match(r"@doc\s+false", line):
            owner["doc_false"] = True
            i += 1
            continue
        if re.match(r"@moduledoc\s+false", line):
            owner["t"]["hidden"] = True
            i += 1
            continue
        dm = re.match(r"(def|defmacro|defdelegate)\s+([\w?!]+)\s*(\(|,|\s+do\b|$)", line)
        if dm and owner.pop("doc_false", False):
            owner["last_hidden"] = dm.group(2)
            i += 1
            continue
        if dm and owner.get("last_hidden") == dm.group(2):
            i += 1
            continue
        if dm:
            stmt = line
            while balance(stmt) > 0 and i + 1 < len(lines):
                i += 1
                stmt += " " + lines[i].strip()
            name = dm.group(2)
            sig = ""
            if dm.group(3) == "(":
                inner, _ = paren_body(stmt, stmt.find("(", stmt.find(name)))
                sig = inner or ""
            if t["kind"] == "enum" and not sig and name not in ("all_values", "values"):
                i += 1
                continue
            params = elixir_params(sig)
            last_word = (words(t["name"]) or [""])[-1]
            if params and (params[0]["name"] == last_word or key(params[0]["name"]) in
                           (key(t["name"]), "self", "server", "manager", "api", "state")) \
                    and "%__MODULE__" not in sig.split(",")[0]:
                params = params[1:]
            if any(p["name"] == "_pattern" for p in params):
                params = [p if p["name"] != "_pattern" else dict(P("value"), pattern=True)
                          for p in params]
            if name in ("exception", "message") and t["kind"] == "error":
                i += 1
                continue
            arity = (name, len(params))
            seen = t.setdefault("_arities", {})
            if arity in seen:
                # another clause of the same function: keep the first clause whose
                # arguments are all plain names
                prev = seen[arity]
                if any(p["name"] == "value" and p.get("pattern") for p in prev) and \
                        not any(p.get("pattern") for p in params):
                    prev[:] = params
                i += 1
                continue
            seen[arity] = params
            if re.match(r"def\s+\w+,\s*do:\s*(\"|:)", stmt):
                t["_literal_defs"] = t.get("_literal_defs", []) + [name]
            else:
                t["_other_defs"] = t.get("_other_defs", 0) + 1
            if name == "new" and dm.group(1) == "def":
                t["ctors"].append(params)
                t["_has_new"] = True
            else:
                add_member(t, name, "elixir", params, line=i + 1)
            i += 1
            continue
        i += 1
    for t in surface.types.values():
        if t.get("_literal_defs") and not t.get("_other_defs") and t["kind"] != "enum":
            t["kind"] = "enum"
            t["cases"] = list(t["_literal_defs"])
            t["members"].clear()
        if "_fields" in t and not t.get("_has_new") and not t.get("_ctor_done"):
            t["_ctor_done"] = True
            enf = set(t.get("_enforce", []))
            t["ctors"].append([P(f, optional=f not in enf, kw=True) for f in t["_fields"]])
            for f in t["_fields"]:
                if key(f) in t["props"]:
                    t["props"][key(f)]["optional"] = (f not in enf) if enf else True


HANDLERS = {"java": Java, "kotlin": Kotlin, "csharp": CSharp, "php": Php, "node": Node,
            "dart": Dart, "swift": Swift, "rust": Rust, "go": Go}


def is_test(rel, lang):
    r = rel.replace("\\", "/")
    if re.search(r"(^|/)(test|tests|spec|Tests|testdata|integration_test)/", r):
        return True
    b = os.path.basename(r)
    return b.endswith("_test.go") or b.endswith(".test.ts") or b == "conftest.py" or \
        b.endswith("Test.java") or b.endswith("Tests.swift")


def extract(lang):
    surface = Surface(lang)
    base = os.path.join(GEN, lang)
    roots, exts = SOURCES[lang]
    files = []
    for r in roots:
        for dp, dn, fn in os.walk(os.path.join(base, r)):
            dn.sort()
            for f in sorted(fn):
                if f.endswith(exts):
                    rel = os.path.relpath(os.path.join(dp, f), base)
                    if not is_test(rel, lang):
                        files.append((os.path.join(dp, f), rel))
    # go/rust/swift attach members to types declared in other files: two passes
    if lang in ("go", "rust", "swift", "dart"):
        order = sorted(files, key=lambda x: 0)
        h = HANDLERS[lang]()
        pre = Surface(lang)
        for full, rel in files:
            run_clike(pre, full, rel, lang, h)
        surface.types = collections.OrderedDict(
            (k, {**v, "members": collections.OrderedDict(), "ctors": [], "cases": [],
                 "props": collections.OrderedDict()}) for k, v in pre.types.items())
        for full, rel in files:
            run_clike(surface, full, rel, lang, h)
    elif lang in HANDLERS:
        h = HANDLERS[lang]()
        for full, rel in files:
            run_clike(surface, full, rel, lang, h)
    elif lang == "python":
        for full, rel in files:
            if os.path.basename(full).startswith("_") and not full.endswith("__init__.py"):
                continue  # _types.py, _duration.py: private modules by convention
            run_python(surface, full, rel)
    elif lang == "ruby":
        for full, rel in files:
            run_ruby(surface, full, rel)
    elif lang == "elixir":
        for full, rel in files:
            run_elixir(surface, full, rel)
    for name, params, f, line in getattr(surface, "pending_ctors", []):
        t = surface.types.get(type_key(name))
        if t is not None:
            t["ctors"].append(params)
        else:
            mt = surface.type(os.path.splitext(os.path.basename(f))[0], "module", f, line)
            add_member(mt, "New" + name, lang, params, static=True, line=line)
    return finish(surface)


def merge_overloads(overloads):
    """Merge overloads into one ordered parameter list; a parameter that some
    overload omits, or that has a default, is optional."""
    if not overloads:
        return []
    longest = max(overloads, key=lambda o: len(o))
    out = []
    for p in longest:
        k = key(p["name"])
        present = all(any(key(q["name"]) == k for q in o) for o in overloads)
        out.append({"name": p["name"], "key": k, "optional": p["optional"] or not present,
                    "kw": p.get("kw", False)})
    # parameters that appear only in shorter overloads
    seen = {x["key"] for x in out}
    for o in overloads:
        for p in o:
            if key(p["name"]) not in seen:
                out.append({"name": p["name"], "key": key(p["name"]), "optional": True,
                            "kw": p.get("kw", False)})
                seen.add(key(p["name"]))
    return out


def inherit(surface):
    """Copy inherited members (props, methods) from parents into each type."""
    done = set()

    def resolve(k, seen):
        t = surface.types[k]
        if k in done or k in seen:
            return t
        seen = seen | {k}
        for pn in t.get("parents", []):
            pk = type_key(pn)
            if pk == k or pk not in surface.types:
                continue
            p = resolve(pk, seen)
            for mk, pv in p["props"].items():
                t["props"].setdefault(mk, dict(pv, inherited=True))
            for mk, mv in p["members"].items():
                if mk not in t["members"] and not mv.get("static"):
                    t["members"][mk] = dict(mv, inherited=True)
        done.add(k)
        return t

    for k in list(surface.types):
        resolve(k, set())


def finish(surface):
    inherit(surface)
    lang = surface.lang
    known = {k for k in surface.types}
    res = collections.OrderedDict()
    for k, t in surface.types.items():
        if t["kind"] == "private" or t.get("hidden"):
            continue
        members = collections.OrderedDict()
        props = t["props"]
        # getUrl() and getUrl(overrides) key differently (get- is stripped only from a
        # zero-argument accessor); fold the accessor overload onto the method
        for mk in list(t["members"]):
            for pre in ("get", "is"):
                if mk.startswith(pre) and mk[len(pre):] in t["members"] and mk in t["members"]:
                    t["members"][mk]["overloads"] += t["members"].pop(mk[len(pre):])["overloads"]
        for mk, m in t["members"].items():
            ovs = [o["params"] for o in m["overloads"]]
            nparams = {len(o) for o in ovs}
            # accessor folding: zero-arg + one-arg (fluent/setter) sharing a name
            if mk in props and max(nparams) <= 1:
                continue
            one = [o for o in ovs if len(o) == 1]
            if nparams <= {0, 1} and 0 in nparams and 1 in nparams and \
                    all(key(o[0]["name"]) == mk or member_key(m["name"], lang, 0) == key(o[0]["name"])
                        for o in one):
                props.setdefault(mk, {"name": m["name"], "optional": None, "line": m["line"],
                                      "inherited": m.get("inherited", False)})
                continue
            if max(nparams) == 0 and not m["static"]:
                props.setdefault(mk, {"name": m["name"], "optional": None, "line": m["line"],
                                      "method": True, "inherited": m.get("inherited", False)})
                continue
            rets = set()
            for o in m["overloads"]:
                rtxt = o["returns"] or ""
                if lang in ("rust", "go", "swift"):
                    rtxt = re.sub(r"\b(Error|error)\b", "", rtxt)
                for ident in re.findall(r"[A-Za-z_]\w*", rtxt):
                    tk = type_key(ident)
                    if ident in ("Self", "self", "static", "this") or (
                            ident == "Builder" and t["name"].endswith("Builder")):
                        tk = k
                    if tk in known and tk != "void" and \
                            surface.types[tk]["category"] != "error":
                        rets.add(tk)
                        break  # the outermost SDK type is the call's shape
            members[mk] = {"name": m["name"], "static": m["static"], "line": m["line"],
                           "params": merge_overloads(ovs), "returns": sorted(rets),
                           "inherited": m.get("inherited", False)}
        # setter-style one-arg methods named set<Prop>/with<Prop> fold onto props
        for mk in list(members):
            for pre in ("set", "with"):
                if mk.startswith(pre) and mk[len(pre):] in props and len(members[mk]["params"]) == 1:
                    del members[mk]
                    break
        ctors = [c for c in t["ctors"]]
        if len(ctors) > 1 and t["category"] in ("model", "options"):
            # a no-arg constructor beside the real one is the deserializer's hook
            nonzero = [c for c in ctors if c]
            if nonzero:
                ctors = nonzero
        ctor = merge_overloads(ctors) if ctors else None
        if t["kind"] == "enum" and not t["cases"]:
            t["kind"] = "class"  # a caseless enum is a namespace (swift)
        res[k] = {"name": t["name"], "words": "_".join(words(t["name"])), "kind": t["kind"],
                  "category": t["category"], "file": t["file"], "line": t["line"],
                  "ctor": ctor,
                  "props": collections.OrderedDict(
                      (pk, {"name": p["name"], "optional": p.get("optional"),
                            "inherited": p.get("inherited", False)})
                      for pk, p in props.items() if pk not in IGNORED_MEMBERS),
                  "methods": members,
                  "cases": [key(c) for c in t["cases"]] if t["kind"] == "enum" else []}
    return res


# ---------------------------------------------------------------- comparison
def load_allow():
    if not os.path.exists(ALLOW_PATH):
        return []
    return json.load(open(ALLOW_PATH, encoding="utf-8")).get("allow", [])


def allowed(rules, element, aspect, lang, value="", kind=""):
    """First allow-list rule covering this (element, aspect, language). A rule may
    narrow itself with `value` (a glob on the reported value, e.g. "extra  (Err*") and
    `kind` ("prop" or "method": what the member is in that language)."""
    for r in rules:
        if not any(fnmatch.fnmatchcase(element, e) for e in
                   (r["element"] if isinstance(r["element"], list) else [r["element"]])):
            continue
        if r.get("aspect", "*") != "*" and not fnmatch.fnmatchcase(aspect, r["aspect"]):
            continue
        if "value" in r and not fnmatch.fnmatchcase(value, r["value"]):
            continue
        if "kind" in r and r["kind"] != kind:
            continue
        if lang in r["languages"] or "*" in r["languages"]:
            return r
    return None


def majority(values):
    """values: {lang: hashable}; return (majority_value, count)."""
    c = collections.Counter(values.values())
    if not c:
        return None, 0
    best = max(c.items(), key=lambda kv: (kv[1], -LANGS.index(min(
        (l for l, v in values.items() if v == kv[0]), key=LANGS.index))))
    return best


def fmt_params(ps, with_opt=True):
    return "(" + ", ".join(p["key"] + ("?" if with_opt and p["optional"] else "") for p in ps) + ")"


def opts_tail(lang, sig):
    return lang == "elixir" and bool(sig) and sig[-1]["key"] == "opts" and sig[-1]["optional"]


def opts_matches(lang, sig, canon):
    """elixir folds trailing optional arguments into a keyword `opts` list."""
    if not opts_tail(lang, sig):
        return False
    head = [p["key"] for p in sig[:-1]]
    ck = [p["key"] for p in canon]
    return ck[:len(head)] == head and all(p["optional"] for p in canon[len(head):])


def compare(surfaces):
    """Yield mismatch dicts: element, aspect, majority, odd {lang: value}."""
    out = []
    all_types = collections.OrderedDict()
    for lang in LANGS:
        for k in surfaces[lang]:
            all_types.setdefault(k, None)

    def present_in(k):
        return {l for l in LANGS if k in surfaces[l]}

    for k in all_types:
        langs = present_in(k)
        sample = next(surfaces[l][k] for l in LANGS if l in langs)
        cat = sample["category"]
        if sample["kind"] == "module" and all(surfaces[l][k]["kind"] == "module" for l in langs) \
                and len(langs) < 3:
            pass
        if len(langs) != len(LANGS):
            missing = [l for l in LANGS if l not in langs]
            out.append({"element": k, "aspect": "type", "category": cat,
                        "majority": "present" if len(langs) >= 6 else "absent",
                        "count": max(len(langs), len(missing)),
                        "odd": ({l: "missing" for l in missing} if len(langs) >= 6 else
                                {l: surfaces[l][k]["name"] for l in langs}),
                        "names": {l: surfaces[l][k]["name"] for l in langs}})
        if len(langs) < 2:
            continue
        # kind: enum vs not
        kinds = {l: ("enum" if surfaces[l][k]["kind"] == "enum" else "type") for l in langs}
        mv, cnt = majority(kinds)
        odd = {l: v for l, v in kinds.items() if v != mv}
        if odd:
            out.append({"element": k, "aspect": "kind", "category": cat, "majority": mv,
                        "count": cnt, "odd": odd})
        # enum cases
        if mv == "enum":
            cases = {l: tuple(sorted(surfaces[l][k]["cases"])) for l in langs if kinds[l] == "enum"}
            cv, cc = majority(cases)
            oddc = {l: v for l, v in cases.items() if v != cv}
            if oddc:
                out.append({"element": k, "aspect": "cases", "category": cat,
                            "majority": ",".join(cv), "count": cc,
                            "odd": {l: ",".join(v) for l, v in oddc.items()}})
            continue
        # an accessor some languages spell getX() and others x: one element, keyed getx
        allm = set()
        for l in langs:
            allm |= set(surfaces[l][k]["props"]) | set(surfaces[l][k]["methods"])
        for l in langs:
            st = surfaces[l][k]
            for pre in ("get", "is"):
                for coll in ("props", "methods"):
                    for mk in list(st[coll]):
                        if pre + mk in allm and pre + mk not in st["methods"] and \
                                pre + mk not in st["props"]:
                            st[coll][pre + mk] = st[coll].pop(mk)
        # members: union of props and methods
        mkeys = collections.OrderedDict()
        for l in LANGS:
            if l in langs:
                for mk in surfaces[l][k]["props"]:
                    mkeys.setdefault(mk, None)
                for mk in surfaces[l][k]["methods"]:
                    mkeys.setdefault(mk, None)
        for mk in mkeys:
            have = {l for l in langs if mk in surfaces[l][k]["props"] or mk in surfaces[l][k]["methods"]}
            if mk.startswith("set") and len(mk) > 3:
                # `x = value` on a settable property is the setter in kotlin/c#/swift/...
                have_prop = {l for l in langs if mk[3:] in surfaces[l][k]["props"]}
                have_any = have | have_prop
            else:
                have_any = have
            el = k + "." + mk
            def inh(l):
                m = surfaces[l][k]["props"].get(mk) or surfaces[l][k]["methods"].get(mk)
                return m.get("inherited", False)
            if have and all(inh(l) for l in have):
                continue
            if len(have_any) != len(langs):
                # rust and elixir have no implementation inheritance: a member the
                # majority only inherits is reached by composition there
                inherited_major = sum(inh(l) for l in have) * 2 > len(have)
                if inherited_major:
                    langs_cmp = langs - NO_INHERIT_LANGS
                else:
                    langs_cmp = langs
            else:
                langs_cmp = langs
            if len(have_any & langs_cmp) != len(langs_cmp):
                langs_ = langs_cmp
                major_present = len(have_any) * 2 >= len(langs)
                odd = ({l: "missing" for l in langs_ - have_any} if major_present else
                       {l: "extra" for l in have})
                out.append({"element": el, "aspect": "member", "category": cat,
                            "majority": "present" if major_present else "absent",
                            "count": len(have) if major_present else len(langs - have),
                            "odd": odd,
                            "names": {l: (surfaces[l][k]["props"].get(mk) or
                                          surfaces[l][k]["methods"].get(mk))["name"] for l in have},
                            "kinds": {l: ("prop" if mk in surfaces[l][k]["props"] else "method")
                                      for l in have}})
            # parameters: only for callables with params somewhere
            sigs = {}
            for l in have:
                m = surfaces[l][k]["methods"].get(mk)
                sigs[l] = m["params"] if m else []
            if not any(sigs.values()):
                continue
            names = {l: tuple(p["key"] for p in v) for l, v in sigs.items()}
            nv, nc = majority({l: v for l, v in names.items() if not opts_tail(l, sigs[l])}
                              or names)
            canon_sig = next(sigs[l] for l in LANGS if l in names and names[l] == nv)
            oddn = {l: "(" + ", ".join(v) + ")" for l, v in names.items()
                    if v != nv and not opts_matches(l, sigs[l], canon_sig)}
            if oddn:
                out.append({"element": el, "aspect": "params", "category": cat,
                            "majority": "(" + ", ".join(nv) + ")", "count": nc, "odd": oddn})
            opts = {l: tuple((p["key"], p["optional"]) for p in v) for l, v in sigs.items()
                    if l in DEFAULT_ARG_LANGS and names[l] == nv}
            if opts:
                ov, oc = majority(opts)
                oddo = {l: fmt_params([{"key": a, "optional": b} for a, b in v])
                        for l, v in opts.items() if v != ov}
                if oddo:
                    out.append({"element": el, "aspect": "optional", "category": cat,
                                "majority": fmt_params([{"key": a, "optional": b} for a, b in ov]),
                                "count": oc, "odd": oddo})
            rets = {}
            for l in have - UNTYPED_LANGS:
                m = surfaces[l][k]["methods"].get(mk)
                if m and m["returns"]:
                    # an empty value means the language does not spell the type
                    # (php arrays, untyped containers); it is not compared
                    rets[l] = tuple(m["returns"])
            if len(rets) >= 2:
                rv, rc = majority(rets)
                oddr = {l: ",".join(v) or "-" for l, v in rets.items() if v != rv}
                if oddr:
                    out.append({"element": el, "aspect": "returns", "category": cat,
                                "majority": ",".join(rv) or "-", "count": rc, "odd": oddr})
        # property optionality for models/options
        if cat in ("model", "options"):
            for mk in mkeys:
                vals = {}
                for l in langs:
                    p = surfaces[l][k]["props"].get(mk)
                    if p is not None and p.get("optional") is not None:
                        vals[l] = bool(p["optional"])
                if len(vals) >= 2:
                    pv, pc = majority(vals)
                    oddp = {l: ("optional" if v else "required") for l, v in vals.items() if v != pv}
                    if oddp:
                        out.append({"element": k + "." + mk, "aspect": "prop-optional",
                                    "category": cat, "majority": "optional" if pv else "required",
                                    "count": pc, "odd": oddp})
            continue
        # constructor shape for service types
        ctors = {l: surfaces[l][k]["ctor"] for l in langs if surfaces[l][k]["ctor"] is not None}
        if len(ctors) >= 2:
            full = {l: tuple(p["key"] for p in v) for l, v in ctors.items()}
            fv, fc = majority({l: v for l, v in full.items() if l in DEFAULT_ARG_LANGS} or full)
            canon = next(ctors[l] for l in LANGS if l in ctors and full[l] == fv) if fv is not None else []
            required = tuple(p["key"] for p in canon if not p["optional"])
            oddf = {}
            for l, v in full.items():
                if v == fv:
                    continue
                if l not in DEFAULT_ARG_LANGS or l == "java":
                    # no default arguments: a required-only constructor (the rest set as
                    # fields or through a builder) is the idiom
                    if v == required or (l == "java" and v == fv):
                        continue
                if opts_matches(l, ctors[l], canon):
                    continue
                if ctors[l] and all(p["kw"] for p in ctors[l]) and set(fv) <= set(v):
                    # keyword-only construction (elixir struct / exception fields):
                    # order is not part of the call shape
                    continue
                oddf[l] = "(" + ", ".join(v) + ")"
            if oddf:
                out.append({"element": k + ".<ctor>", "aspect": "ctor", "category": cat,
                            "majority": "(" + ", ".join(fv) + ")", "count": fc, "odd": oddf})
            opts = {l: tuple((p["key"], p["optional"]) for p in v) for l, v in ctors.items()
                    if l in DEFAULT_ARG_LANGS and full[l] == fv and
                    not (ctors[l] and all(p["kw"] for p in ctors[l]) and l == "elixir")}
            if opts:
                ov, oc = majority(opts)
                oddo = {l: fmt_params([{"key": a, "optional": b} for a, b in v])
                        for l, v in opts.items() if v != ov}
                if oddo:
                    out.append({"element": k + ".<ctor>", "aspect": "ctor-optional",
                                "category": cat,
                                "majority": fmt_params([{"key": a, "optional": b} for a, b in ov]),
                                "count": oc, "odd": oddo})
    return out


def all_surfaces():
    return {l: extract(l) for l in LANGS}


def odd_value(m, lang):
    """The reported value an allow rule's `value` glob is matched against, e.g.
    "extra  (ErrInvalidApiKey)" or "(statuscode, message, previous)"."""
    v = m["odd"][lang]
    nm = m.get("names", {}).get(lang)
    return v + "  (" + nm + ")" if nm and v in ("extra", "missing") else v


def cmd_check(show_all, as_json=False):
    surfaces = all_surfaces()
    rules = load_allow()
    mism = compare(surfaces)
    seen = set()
    for lang, items in casing_rows(surfaces).items():
        for el, nm, tok in items:
            if (el, lang) in seen:
                continue
            seen.add((el, lang))
            mism.append({"element": el, "aspect": "casing", "category": "casing",
                         "majority": "word-cased initialisms (Http, Id, Url)", "count": 0,
                         "odd": {lang: nm}})
    if as_json:
        for m in mism:
            m["allowed"] = {}
            for l, v in m["odd"].items():
                r = allowed(rules, m["element"], m["aspect"], l, odd_value(m, l),
                            m.get("kinds", {}).get(l, ""))
                if r is not None:
                    m["allowed"][l] = r["reason"]
        json.dump(mism, sys.stdout, indent=1)
        print()
        return 0
    per_lang = collections.Counter()
    allowed_n = 0
    groups = collections.OrderedDict()
    for m in mism:
        live = {}
        for l, v in m["odd"].items():
            r = allowed(rules, m["element"], m["aspect"], l, odd_value(m, l),
                        m.get("kinds", {}).get(l, ""))
            if r is None:
                live[l] = v
            else:
                allowed_n += 1
                if show_all:
                    live[l] = v + "  [allowed: " + r["reason"][:60] + "]"
        if not live:
            continue
        for l in live:
            if "[allowed" not in live[l]:
                per_lang[l] += 1
        groups.setdefault(m["element"], []).append((m, live))
    for el, items in groups.items():
        print(el)
        for m, live in items:
            print("  %-13s majority(%d): %s" % (m["aspect"], m["count"], m["majority"]))
            for l in LANGS:
                if l in live:
                    nm = m.get("names", {}).get(l)
                    print("    %-7s %s%s" % (l, live[l], ("  (" + nm + ")") if nm and live[l] != nm else ""))
    print()
    print("mismatches per language (not allowed): " +
          ", ".join("%s=%d" % (l, per_lang.get(l, 0)) for l in LANGS))
    print("allowed: %d" % allowed_n)
    return 1 if sum(per_lang.values()) else 0


def casing_rows(surfaces=None):
    """Identifiers that spell an initialism in capitals (HTTPInfo, clientID, baseURL)
    where the canonical form word-cases it (HttpInfo, clientId, baseUrl)."""
    rows = collections.defaultdict(list)
    for lang in ("java", "kotlin", "csharp", "php", "node", "dart", "go", "swift"):
        s = surfaces[lang] if surfaces else extract(lang)
        names = []
        for k, t in s.items():
            names.append((t["name"], k))
            for pk, p in t["props"].items():
                names.append((p["name"], k + "." + pk))
            for mk, m in t["methods"].items():
                names.append((m["name"], k + "." + mk))
                for p in m["params"]:
                    names.append((p["name"], k + "." + mk + "(" + p["key"] + ")"))
            for p in t["ctor"] or []:
                names.append((p["name"], k + ".<ctor>(" + p["key"] + ")"))
        for nm, el in names:
            n2 = nm.replace("OpenAPI", "Openapi").replace("OAuth", "Oauth")
            toks = CAMEL_RE.findall(n2)
            if re.fullmatch(r"[A-Z0-9_]+", nm) or len(toks) < 2:
                continue  # SCREAMING_CASE constants and bare acronyms (node's UUID brand)
            for tok in toks:
                if tok.lower() in ACRONYMS and tok.isupper() and len(tok) > 1:
                    rows[lang].append((el, nm, tok))
                    break
    return rows


def cmd_casing():
    rows = casing_rows()
    for lang, items in rows.items():
        print("%s: %d identifiers spell an initialism in capitals" % (lang, len(items)))
        for el, nm, tok in items:
            print("    %-40s %s" % (nm, el))
    return 0


def main(argv):
    if len(argv) < 2 or argv[1] not in ("extract", "check", "casing"):
        print(__doc__)
        return 2
    if argv[1] == "extract":
        which = argv[2] if len(argv) > 2 else "all"
        langs = LANGS if which == "all" else [which]
        json.dump({l: extract(l) for l in langs}, sys.stdout, indent=1)
        print()
        return 0
    if argv[1] == "casing":
        return cmd_casing()
    return cmd_check("--all" in argv, "--json" in argv)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
