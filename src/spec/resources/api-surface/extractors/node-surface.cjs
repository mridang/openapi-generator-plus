// Prints the public API records of the Node SDK in /work (see AbstractApiSurfaceSpec).
//
// It reads the package with the TypeScript compiler the SDK's dev dependencies already install
// (the checker TypeDoc itself is built on), starting from the entry points package.json
// "exports" names, so nothing is fetched:
//
//   node /tmp/api-surface/node-surface.cjs
//
// @@S  every declaration an entry point exports, one line each: the declaration itself
//      (`class X`, `interface X`, `type X = ...`, `enum X`, `function f(...)`, `const c: T`)
//      and one line per own member as `<Owner>.<member>`. Declarations of the subpath entry
//      `./opentelemetry` are prefixed with `opentelemetry/`. Private, `#private` and
//      `@internal` members are left out (TypeDoc's excludePrivate and excludeInternal). A line
//      whose declaration names a type of another package ends with
//      ` [uses <package>:<Type>, ...]`; the TypeScript and Node standard libraries are not
//      listed. A value of another package that an entry point re-exports as is is listed as
//      `export <name> from <package>`.
// @@T  every public type a caller can see: the types the entry points export and the SDK types
//      an exported declaration names.
// @@E  every type an entry point exports.
// @@I  an exported declaration tagged @internal.
"use strict";

const path = require("path");
const fs = require("fs");

const root = process.cwd();
const ts = require(require.resolve("typescript", { paths: [root] }));

const pkg = JSON.parse(fs.readFileSync(path.join(root, "package.json"), "utf8"));
const configFile = ts.readConfigFile(path.join(root, "tsconfig.json"), ts.sys.readFile);
const parsed = ts.parseJsonConfigFileContent(configFile.config, ts.sys, root);
/* The "#transport" import is a package.json subpath import that points at the built output;
 * read it from the Node source the way the jest configuration does. */
const options = {
  ...parsed.options,
  noEmit: true,
  baseUrl: root,
  paths: { "#transport": ["./src/default-api-client.ts"] },
};
const program = ts.createProgram(parsed.fileNames, options);
const checker = program.getTypeChecker();

const srcDir = path.join(root, "src") + path.sep;
const flags =
  ts.TypeFormatFlags.NoTruncation |
  ts.TypeFormatFlags.UseAliasDefinedOutsideCurrentScope |
  ts.TypeFormatFlags.WriteClassExpressionAsTypeLiteral;

/** The entry points: "exports" subpath -> source file. */
function entryPoints() {
  const entries = [];
  for (const [subpath, target] of Object.entries(pkg.exports || {})) {
    const types = typeof target === "string" ? target : target.types;
    if (!types || !types.endsWith(".d.ts")) continue;
    const rel = types.replace(/^\.\/dist\//, "").replace(/\.d\.ts$/, ".ts");
    const prefix = subpath === "." ? "" : subpath.replace(/^\.\//, "") + "/";
    entries.push({ prefix, file: path.join(root, rel) });
  }
  return entries.sort((a, b) => a.prefix.localeCompare(b.prefix));
}

function norm(text) {
  return text.replace(/\s+/g, " ").replace(/\( /g, "(").replace(/ \)/g, ")").trim();
}

function isInternal(node) {
  return ts.getJSDocTags(node).some((t) => t.tagName.text === "internal");
}

function packageOf(fileName) {
  const i = fileName.lastIndexOf("/node_modules/");
  if (i < 0) return null;
  const parts = fileName.substring(i + "/node_modules/".length).split("/");
  let name = parts[0].startsWith("@") ? parts[0] + "/" + parts[1] : parts[0];
  if (name.startsWith("@types/")) name = name.substring("@types/".length);
  return name;
}

/** SDK symbol -> exported name, filled while walking the entry points. */
const exportedNames = new Map();
const out = new Set();
const signatures = new Set();

function resolve(symbol) {
  return symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol;
}

/** Collects the named types of a type: SDK types and other packages' types. */
function collect(type, uses, seen, depth) {
  if (!type || seen.has(type) || depth > 6) return;
  seen.add(type);
  const note = (symbol) => {
    if (!symbol || !symbol.declarations || symbol.declarations.length === 0) return false;
    const file = symbol.declarations[0].getSourceFile().fileName;
    const owner = packageOf(file);
    if (owner === null) {
      if (file.startsWith(srcDir)) {
        uses.sdk.add(symbol);
        return true;
      }
      return false;
    }
    if (owner === "typescript" || owner === "node") return false;
    let name = symbol.name;
    const parent = symbol.parent;
    if (parent && parent.flags & ts.SymbolFlags.Namespace && !/^"/.test(parent.name)) {
      name = parent.name + "." + name;
    }
    uses.foreign.add(owner + ":" + name);
    return true;
  };
  if (type.aliasSymbol) {
    /* A named alias (the SDK's, another package's or a standard one such as Partial) is
     * named by its own name and its arguments, never by what it expands to. */
    note(type.aliasSymbol);
    for (const t of type.aliasTypeArguments || []) collect(t, uses, seen, depth + 1);
    return;
  }
  if (type.isUnionOrIntersection()) {
    for (const t of type.types) collect(t, uses, seen, depth + 1);
    return;
  }
  if (type.flags & ts.TypeFlags.EnumLiteral && type.symbol) {
    note(type.symbol.parent || type.symbol);
    return;
  }
  if (type.flags & ts.TypeFlags.Object) {
    const objectFlags = type.objectFlags;
    if (objectFlags & ts.ObjectFlags.Reference) {
      note(type.target.symbol);
      for (const t of checker.getTypeArguments(type)) collect(t, uses, seen, depth + 1);
      return;
    }
    if (objectFlags & (ts.ObjectFlags.Class | ts.ObjectFlags.Interface)) {
      note(type.symbol);
      return;
    }
    if (objectFlags & ts.ObjectFlags.Mapped) return;
    if (type.symbol && note(type.symbol) && !(type.symbol.flags & ts.SymbolFlags.TypeLiteral)) {
      return;
    }
    for (const sig of [...type.getCallSignatures(), ...type.getConstructSignatures()]) {
      collectSignature(sig, uses, seen, depth + 1);
    }
    for (const prop of type.getProperties()) {
      const decl = prop.valueDeclaration || (prop.declarations || [])[0];
      if (decl) collect(checker.getTypeOfSymbolAtLocation(prop, decl), uses, seen, depth + 1);
    }
    const index = checker.getIndexInfosOfType(type);
    for (const info of index) collect(info.type, uses, seen, depth + 1);
  }
}

function collectSignature(sig, uses, seen, depth) {
  for (const p of sig.getParameters()) {
    const decl = p.valueDeclaration;
    if (decl) collect(checker.getTypeOfSymbolAtLocation(p, decl), uses, seen, depth);
  }
  for (const tp of sig.getTypeParameters() || []) {
    const c = checker.getBaseConstraintOfType(tp);
    if (c) collect(c, uses, seen, depth);
  }
  collect(sig.getReturnType(), uses, seen, depth);
}

function newUses() {
  return { sdk: new Set(), foreign: new Set() };
}

const namedTypes = new Set();

function emit(line, uses) {
  for (const s of uses.sdk) namedTypes.add(s);
  const foreign = [...uses.foreign].sort();
  signatures.add(foreign.length ? `${line} [uses ${foreign.join(", ")}]` : line);
}

function typeParams(decl) {
  const tps = ts.getEffectiveTypeParameterDeclarations(decl);
  return tps.length ? "<" + tps.map((tp) => norm(tp.getText())).join(", ") + ">" : "";
}

function modifiers(decl) {
  const mods = ts.getCombinedModifierFlags(decl);
  const words = [];
  if (mods & ts.ModifierFlags.Protected) words.push("protected");
  if (mods & ts.ModifierFlags.Abstract) words.push("abstract");
  if (mods & ts.ModifierFlags.Static) words.push("static");
  if (mods & ts.ModifierFlags.Readonly) words.push("readonly");
  return words.length ? words.join(" ") + " " : "";
}

function hidden(decl) {
  const mods = ts.getCombinedModifierFlags(decl);
  if (mods & ts.ModifierFlags.Private) return true;
  if (decl.name && ts.isPrivateIdentifier(decl.name)) return true;
  return isInternal(decl);
}

function sigText(sig, decl) {
  return norm(checker.signatureToString(sig, decl, flags));
}

function member(owner, symbol) {
  const decls = symbol.declarations || [];
  if (decls.length === 0 || decls.every(hidden)) return;
  const decl = decls.find((d) => !hidden(d));
  if (symbol.flags & ts.SymbolFlags.TypeParameter) return;
  const uses = newUses();
  const seen = new Set();
  const name = symbol.name;
  if (name === "__constructor") {
    const ctor = decls.filter((d) => !hidden(d) && (d.body === undefined || decls.length === 1));
    for (const d of ctor.length ? ctor : [decl]) {
      const sig = checker.getSignatureFromDeclaration(d);
      const u = newUses();
      collectSignature(sig, u, new Set(), 0);
      emit(`${owner}.${modifiers(d)}constructor${sigText(sig, d).replace(/: [^:]*$/, "")}`, u);
    }
    return;
  }
  if (name === "__index") {
    for (const d of decls) {
      emit(`${owner}.${modifiers(d)}${norm(d.getText()).replace(/;$/, "")}`, uses);
    }
    return;
  }
  if (name === "__call" || name === "__new") {
    for (const d of decls) {
      const sig = checker.getSignatureFromDeclaration(d);
      collectSignature(sig, uses, seen, 0);
      emit(`${owner}.${name === "__new" ? "new " : ""}${sigText(sig, d)}`, uses);
    }
    return;
  }
  const type = checker.getTypeOfSymbolAtLocation(symbol, decl);
  const optional = symbol.flags & ts.SymbolFlags.Optional ? "?" : "";
  if (symbol.flags & ts.SymbolFlags.Method) {
    for (const sig of type.getCallSignatures()) {
      const u = newUses();
      collectSignature(sig, u, new Set(), 0);
      emit(`${owner}.${modifiers(decl)}${name}${optional}${sigText(sig, decl)}`, u);
    }
    return;
  }
  if (symbol.flags & ts.SymbolFlags.Accessor) {
    const get = decls.find(ts.isGetAccessorDeclaration);
    const set = decls.find(ts.isSetAccessorDeclaration);
    collect(type, uses, seen, 0);
    const kind = get && set ? "get set" : get ? "get" : "set";
    emit(`${owner}.${modifiers(decl)}${kind} ${name}: ${checker.typeToString(type, decl, flags)}`, uses);
    return;
  }
  if (symbol.flags & ts.SymbolFlags.EnumMember) {
    const value = checker.getConstantValue(decl);
    emit(`${owner}.${name} = ${JSON.stringify(value)}`, uses);
    return;
  }
  collect(type, uses, seen, 0);
  emit(
    `${owner}.${modifiers(decl)}${name}${optional}: ${norm(checker.typeToString(type, decl, flags))}`,
    uses,
  );
}

function heritage(decl) {
  const parts = [];
  for (const clause of decl.heritageClauses || []) {
    const word = clause.token === ts.SyntaxKind.ExtendsKeyword ? "extends" : "implements";
    parts.push(word + " " + clause.types.map((t) => norm(t.getText())).join(", "));
  }
  return parts.length ? " " + parts.join(" ") : "";
}

function heritageUses(decl, uses) {
  for (const clause of decl.heritageClauses || []) {
    for (const t of clause.types) collect(checker.getTypeAtLocation(t), uses, new Set(), 0);
  }
}

function sortedSymbols(table) {
  const list = [];
  if (table) table.forEach((s) => list.push(s));
  return list.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
}

function declaration(prefix, name, symbol) {
  const owner = prefix + name;
  const decls = (symbol.declarations || []).filter((d) => !isInternal(d));
  for (const decl of decls) {
    const uses = newUses();
    if (ts.isClassDeclaration(decl)) {
      heritageUses(decl, uses);
      const abs = ts.getCombinedModifierFlags(decl) & ts.ModifierFlags.Abstract ? "abstract " : "";
      emit(`${abs}class ${owner}${typeParams(decl)}${heritage(decl)}`, uses);
      for (const m of sortedSymbols(symbol.members)) member(owner, m);
      for (const m of sortedSymbols(symbol.exports)) {
        if (m.flags & ts.SymbolFlags.Prototype) continue;
        member(owner, m);
      }
      inheritedMembers(owner, symbol);
    } else if (ts.isInterfaceDeclaration(decl)) {
      if (decl !== decls.find(ts.isInterfaceDeclaration)) continue;
      heritageUses(decl, uses);
      emit(`interface ${owner}${typeParams(decl)}${heritage(decl)}`, uses);
      if (!decls.some(ts.isClassDeclaration)) {
        for (const m of sortedSymbols(symbol.members)) member(owner, m);
      }
    } else if (ts.isTypeAliasDeclaration(decl)) {
      const type = checker.getTypeAtLocation(decl.type);
      collect(type, uses, new Set(), 0);
      emit(`type ${owner}${typeParams(decl)} = ${norm(decl.type.getText())}`, uses);
    } else if (ts.isEnumDeclaration(decl)) {
      emit(`enum ${owner}`, uses);
      for (const m of sortedSymbols(symbol.exports)) member(owner, m);
    } else if (ts.isFunctionDeclaration(decl)) {
      if (decl.body && decls.filter(ts.isFunctionDeclaration).length > 1) continue;
      const sig = checker.getSignatureFromDeclaration(decl);
      collectSignature(sig, uses, new Set(), 0);
      emit(`function ${owner}${sigText(sig, decl)}`, uses);
    } else if (ts.isVariableDeclaration(decl)) {
      const type = checker.getTypeOfSymbolAtLocation(symbol, decl);
      collect(type, uses, new Set(), 0);
      const isConst = ts.getCombinedNodeFlags(decl) & ts.NodeFlags.Const ? "const" : "let";
      emit(`${isConst} ${owner}: ${norm(checker.typeToString(type, decl, flags))}`, uses);
    } else if (ts.isModuleDeclaration(decl)) {
      emit(`namespace ${owner}`, uses);
      for (const m of sortedSymbols(checker.getExportsOfModule(symbol))) {
        declaration(owner + ".", m.name, resolve(m));
      }
    }
  }
}

function isType(symbol) {
  return (
    (symbol.flags &
      (ts.SymbolFlags.Class |
        ts.SymbolFlags.Interface |
        ts.SymbolFlags.TypeAlias |
        ts.SymbolFlags.Enum)) !==
    0
  );
}

/* The members a class inherits from an SDK base class no entry point exports: a caller reaches
 * them only through the subclass, so they are listed under it. */
function inheritedMembers(owner, symbol) {
  const own = new Set();
  if (symbol.members) symbol.members.forEach((_, k) => own.add(k));
  if (symbol.exports) symbol.exports.forEach((_, k) => own.add(k));
  let type = checker.getDeclaredTypeOfSymbol(symbol);
  for (;;) {
    const bases = type.isClassOrInterface() ? checker.getBaseTypes(type) : [];
    const base = bases.find((b) => b.symbol && b.symbol.flags & ts.SymbolFlags.Class);
    if (!base) return;
    const baseSymbol = base.symbol;
    const file = baseSymbol.declarations[0].getSourceFile().fileName;
    if (!file.startsWith(srcDir) || exportedSymbols.has(baseSymbol)) return;
    const add = (m, k) => {
      if (own.has(k) || k === "__constructor" || m.flags & ts.SymbolFlags.Prototype) return;
      own.add(k);
      member(owner, m);
    };
    for (const m of sortedSymbols(baseSymbol.members)) add(m, m.escapedName);
    for (const m of sortedSymbols(baseSymbol.exports)) add(m, m.escapedName);
    type = checker.getDeclaredTypeOfSymbol(baseSymbol);
  }
}

/* Every SDK symbol an entry point exports, known before any declaration is printed. */
const exportedSymbols = new Set();
for (const entry of entryPoints()) {
  const source = program.getSourceFile(entry.file);
  if (!source) continue;
  for (const e of checker.getExportsOfModule(checker.getSymbolAtLocation(source))) {
    exportedSymbols.add(resolve(e));
  }
}

for (const entry of entryPoints()) {
  const source = program.getSourceFile(entry.file);
  if (!source) {
    process.stderr.write(`no source for entry point ${entry.file}\n`);
    process.exit(1);
  }
  const moduleSymbol = checker.getSymbolAtLocation(source);
  for (const exported of sortedSymbols(checker.getExportsOfModule(moduleSymbol))) {
    const name = exported.name;
    const symbol = resolve(exported);
    const decls = symbol.declarations || [];
    if (decls.length === 0) continue;
    const file = decls[0].getSourceFile().fileName;
    const owner = packageOf(file);
    if (owner !== null) {
      signatures.add(`export ${entry.prefix}${name} from ${owner}`);
      continue;
    }
    if (!file.startsWith(srcDir)) continue;
    exportedNames.set(symbol, entry.prefix + name);
    if (isType(symbol)) {
      out.add(`@@T ${entry.prefix}${name}`);
      out.add(`@@E ${entry.prefix}${name}`);
    }
    if (decls.some(isInternal)) out.add(`@@I ${entry.prefix}${name}`);
    declaration(entry.prefix, name, symbol);
  }
}

/* The SDK types an exported declaration names: a caller sees them, so each must be exported. */
for (const symbol of namedTypes) {
  if (!isType(symbol) || exportedNames.has(symbol)) continue;
  if (symbol.flags & ts.SymbolFlags.TypeParameter) continue;
  const rel = path.relative(root, symbol.declarations[0].getSourceFile().fileName);
  out.add(`@@T ${rel}:${symbol.name}`);
}

for (const s of signatures) out.add(`@@S ${s}`);
for (const line of [...out].sort()) process.stdout.write(line + "\n");
