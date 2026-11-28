"""Prints the public API records of the Python SDK in /work (see AbstractApiSurfaceSpec).

It imports the installed package and reads it with the standard library's ``inspect`` and
``typing`` reflection, so nothing is fetched::

    python /tmp/api-surface/python-surface.py petstore_client

@@S  one line per public declaration, owned by its defining module (``pkg.module.Name``):
     ``class`` with its bases, every public method with its signature, every field, property
     and constant with its type, every enum member with its value, module functions and
     constants. Each name a package exports is listed as
     ``export <package>.<name> = <defining path>``. Type annotations are written with the
     fully qualified name of every class they name, so a type of another library shows up by
     its module (``pydantic.``, ``urllib3.``, ...).
@@T  every public type a caller can see: the types the packages export and the SDK classes an
     exported declaration names.
@@E  every type importable from the top-level package or a public submodule: the types a
     package of the SDK exports (its ``__all__``, else the names it imports from its own
     modules) and the named types whose defining module is public.
@@I  an exported type whose defining module is private (a leading-underscore module).
"""

import collections.abc
import enum
import importlib
import inspect
import pkgutil
import re
import sys
import types
import typing

ROOT = sys.argv[1]
OPT_IN = {ROOT + ".opentelemetry_call_hook"}
SIMPLE = (str, int, float, bool, type(None), bytes)
SKIP_DUNDERS = {
    "__module__",
    "__doc__",
    "__dict__",
    "__weakref__",
    "__annotations__",
    "__annotate__",
    "__annotate_func__",
    "__annotations_cache__",
    "__qualname__",
    "__slots__",
    "__orig_bases__",
    "__parameters__",
    "__firstlineno__",
    "__static_attributes__",
    "__abstractmethods__",
    "__signature__",
    "__class_vars__",
    "__private_attributes__",
    "__hash__",
    "__protocol_attrs__",
    "__non_callable_proto_members__",
    "__subclasshook__",
    "__init_subclass__",
    "__class_getitem__",
    "__match_args__",
    "__dataclass_fields__",
    "__dataclass_params__",
    "__replace__",
    "__get_pydantic_core_schema__",
    "__get_pydantic_json_schema__",
}

signatures = set()
records = set()
named = {}  # SDK class path -> class, every SDK class a signature names


def is_sdk(module):
    return module == ROOT or module.startswith(ROOT + ".")


def path_of(obj):
    return obj.__module__ + "." + obj.__qualname__


def private_module(module):
    return any(part.startswith("_") for part in module.split("."))


def fmt(t):
    """A type annotation as text, with every class fully qualified."""
    if t is None or t is type(None):
        return "None"
    if isinstance(t, str):
        return t
    if isinstance(t, typing.ForwardRef):
        return t.__forward_arg__
    if t is typing.Any:
        return "Any"
    if t is Ellipsis:
        return "..."
    if isinstance(t, typing.TypeVar):
        return "~" + t.__name__
    if isinstance(t, type) and not isinstance(t, types.GenericAlias):
        if t.__module__ == "builtins":
            return t.__qualname__
        if is_sdk(t.__module__):
            named[path_of(t)] = t
        return path_of(t)
    origin = typing.get_origin(t)
    args = typing.get_args(t)
    if origin is t:
        origin = None
    if origin is typing.Annotated:
        return fmt(args[0])
    if origin is typing.Union or (hasattr(types, "UnionType") and origin is types.UnionType):
        return " | ".join(fmt(a) for a in args)
    if origin is typing.Literal:
        return "Literal[" + ", ".join(repr(a) for a in args) + "]"
    if origin is not None:
        if origin is collections.abc.Callable and args:
            params, ret = args[:-1], args[-1]
            if len(params) == 1 and params[0] is Ellipsis:
                return "Callable[..., " + fmt(ret) + "]"
            if len(params) == 1 and isinstance(params[0], list):
                params = params[0]
            return "Callable[[" + ", ".join(fmt(a) for a in params) + "], " + fmt(ret) + "]"
        name = fmt(origin)
        return name + ("[" + ", ".join(fmt(a) for a in args) + "]" if args else "")
    if isinstance(t, list):
        return "[" + ", ".join(fmt(a) for a in t) + "]"
    name = getattr(t, "__qualname__", None) or getattr(t, "__name__", None)
    module = getattr(t, "__module__", None)
    if name and module:
        return name if module in ("builtins", "typing") else module + "." + name
    return re.sub(r" at 0x[0-9a-f]+", "", repr(t))


def default_text(value):
    if isinstance(value, enum.Enum):
        return fmt(type(value)) + "." + value.name
    if isinstance(value, SIMPLE):
        return repr(value)
    if type(value).__name__ == "_HAS_DEFAULT_FACTORY_CLASS":
        return "<factory>"
    if isinstance(value, (tuple, list, frozenset, set)) and all(
        isinstance(v, SIMPLE) for v in value
    ):
        return repr(value) if not isinstance(value, (set, frozenset)) else repr(sorted(value))
    return "<" + fmt(type(value)) + ">"


def hints(obj):
    try:
        return typing.get_type_hints(obj, include_extras=False)
    except Exception:
        try:
            return inspect.get_annotations(obj)
        except Exception:
            return {}


def sig_text(func):
    try:
        sig = inspect.signature(func)
    except (TypeError, ValueError):
        return "(...)"
    resolved = hints(func)
    parts = []
    seen_kw_only = False
    for p in sig.parameters.values():
        if p.kind is p.VAR_POSITIONAL:
            seen_kw_only = True
            text = "*" + p.name
        elif p.kind is p.VAR_KEYWORD:
            text = "**" + p.name
        else:
            if p.kind is p.KEYWORD_ONLY and not seen_kw_only:
                parts.append("*")
                seen_kw_only = True
            text = p.name
        if p.annotation is not p.empty:
            text += ": " + fmt(resolved.get(p.name, p.annotation))
        if p.default is not p.empty:
            text += " = " + default_text(p.default)
        parts.append(text)
        if p.kind is p.POSITIONAL_ONLY and (
            list(sig.parameters.values()).index(p) + 1 == len(sig.parameters)
            or list(sig.parameters.values())[list(sig.parameters.values()).index(p) + 1].kind
            is not p.POSITIONAL_ONLY
        ):
            parts.append("/")
    text = "(" + ", ".join(parts) + ")"
    if sig.return_annotation is not sig.empty:
        text += " -> " + fmt(resolved.get("return", sig.return_annotation))
    return text


def public_member(name):
    if name.startswith("__") and name.endswith("__"):
        return name not in SKIP_DUNDERS and not name.startswith("__pydantic")
    return not name.startswith("_")


def members(owner, cls, inherited_from=None):
    source = inherited_from or cls
    own_hints = {}
    try:
        own_hints = inspect.get_annotations(source)
    except Exception:
        pass
    resolved = hints(source)
    fields = getattr(source, "__pydantic_fields__", None) or {}
    listed = set()
    for name in sorted(own_hints):
        if not public_member(name) or name in vars(source) and callable(vars(source)[name]):
            continue
        ann = resolved.get(name, own_hints[name])
        origin = typing.get_origin(ann)
        prefix = "classvar " if origin is typing.ClassVar else ""
        if origin is typing.ClassVar:
            args = typing.get_args(ann)
            ann = args[0] if args else typing.Any
        line = f"{owner}.{prefix}{name}: {fmt(ann)}"
        info = fields.get(name)
        if info is not None and getattr(info, "alias", None) and info.alias != name:
            line += f" (alias {info.alias!r})"
        signatures.add(line)
        listed.add(name)
    for name, value in sorted(vars(source).items()):
        if name in listed or not public_member(name):
            continue
        if isinstance(value, staticmethod):
            signatures.add(f"{owner}.static {name}{sig_text(value.__func__)}")
        elif isinstance(value, classmethod):
            signatures.add(f"{owner}.classmethod {name}{sig_text(value.__func__)}")
        elif isinstance(value, property):
            ret = hints(value.fget).get("return") if value.fget else None
            kind = "property" + (" settable" if value.fset else "")
            signatures.add(f"{owner}.{kind} {name}: {fmt(ret) if ret is not None else '?'}")
        elif inspect.isfunction(value):
            signatures.add(f"{owner}.{name}{sig_text(value)}")
        elif inspect.isclass(value):
            continue  # nested classes are listed on their own
        elif isinstance(value, enum.Enum) or (
            isinstance(source, enum.EnumMeta) and name in source.__members__
        ):
            signatures.add(f"{owner}.{name} = {value.value!r}")
        elif isinstance(value, SIMPLE) or isinstance(value, (tuple, frozenset)):
            signatures.add(f"{owner}.{name} = {default_text(value)}")
        elif callable(value):
            signatures.add(f"{owner}.{name}{sig_text(value)}")
        else:
            signatures.add(f"{owner}.{name}: {fmt(type(value))}")
    if isinstance(source, enum.EnumMeta) and inherited_from is None:
        for name, member in source.__members__.items():
            signatures.add(f"{owner}.{name} = {member.value!r}")


def declare(path, obj, exported_classes):
    if inspect.isclass(obj):
        def base_text(b):
            # An SDK base no package exports is shown with its own bases, since a caller
            # reaches what it inherits only through this class.
            text = fmt(b)
            if is_sdk(b.__module__) and b not in exported_classes:
                inner = [base_text(x) for x in b.__bases__ if x is not object]
                if inner:
                    text += "(" + ", ".join(inner) + ")"
            return text

        bases = ", ".join(base_text(b) for b in obj.__bases__ if b is not object)
        kind = "class"
        if typing.Protocol in getattr(obj, "__mro__", ()) and getattr(obj, "_is_protocol", False):
            kind = "protocol"
        signatures.add(f"{kind} {path}" + (f"({bases})" if bases else ""))
        members(path, obj)
        for base in obj.__mro__[1:]:
            if base is object or not is_sdk(base.__module__) or base in exported_classes:
                continue
            members(path, obj, inherited_from=base)
        for name, value in sorted(vars(obj).items()):
            if inspect.isclass(value) and public_member(name) and value.__qualname__.startswith(
                obj.__qualname__ + "."
            ):
                declare(path_of(value), value, exported_classes)
    elif inspect.isfunction(obj) or inspect.isbuiltin(obj):
        signatures.add(f"def {path}{sig_text(obj)}")
    elif typing.get_origin(obj) is not None or isinstance(obj, (typing.TypeVar,)):
        signatures.add(f"alias {path} = {fmt(obj)}")
    elif hasattr(typing, "TypeAliasType") and isinstance(obj, typing.TypeAliasType):
        signatures.add(f"alias {path} = {fmt(obj.__value__)}")
    elif isinstance(obj, SIMPLE) or isinstance(obj, (tuple, frozenset)):
        signatures.add(f"const {path} = {default_text(obj)}")
    else:
        signatures.add(f"const {path}: {fmt(type(obj))}")


def main():
    root = importlib.import_module(ROOT)
    packages = [ROOT] + sorted(
        m.name
        for m in pkgutil.walk_packages(root.__path__, ROOT + ".")
        if m.ispkg and not private_module(m.name)
    )
    entries = packages + sorted(OPT_IN)
    exported = {}  # defining path -> object
    for entry in entries:
        module = importlib.import_module(entry)
        names = getattr(module, "__all__", None)
        if names is None:
            names = [
                n
                for n, v in vars(module).items()
                if not n.startswith("_")
                and not inspect.ismodule(v)
                and (
                    getattr(v, "__module__", None) == entry
                    or str(getattr(v, "__module__", None)).startswith(entry + ".")
                )
            ]
        for name in sorted(set(names)):
            obj = getattr(module, name)
            if inspect.ismodule(obj):
                continue
            module_name = getattr(obj, "__module__", None)
            if (inspect.isclass(obj) or inspect.isfunction(obj)) and module_name:
                path = path_of(obj)
                if not is_sdk(module_name):
                    signatures.add(f"export {entry}.{name} = {path}")
                    continue
            else:
                path = entry + "." + name
            signatures.add(f"export {entry}.{name} = {path}")
            exported[path] = obj
    exported_classes = {o for o in exported.values() if inspect.isclass(o)}
    for path, obj in sorted(exported.items()):
        if inspect.isclass(obj):
            records.add("@@T " + path)
            records.add("@@E " + path)
            if private_module(obj.__module__):
                records.add("@@I " + path)
        declare(path, obj, exported_classes)
    for path, cls in named.items():
        if cls not in exported_classes and "<locals>" not in path:
            records.add("@@T " + path)
            if not private_module(cls.__module__):
                records.add("@@E " + path)
    for s in signatures:
        records.add("@@S " + s)
    for line in sorted(records):
        print(line)


main()
