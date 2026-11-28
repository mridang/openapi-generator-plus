// Prints the public API records of the Rust SDK (see AbstractApiSurfaceSpec) from the rustdoc
// JSON of the crate, written with private and hidden items so the walk below can tell what is
// reachable from what is merely declared `pub`:
//
//   RUSTC_BOOTSTRAP=1 RUSTDOCFLAGS="-Z unstable-options --output-format json
//     --document-private-items --document-hidden-items" cargo doc --no-deps --all-features
//
// It is built as a one-file binary against the `serde_json` the SDK already depends on, so
// nothing is fetched, and run with the JSON file as its argument.
//
// @@S  one line per public item reachable from the crate root through `pub` modules and
//      `pub use` re-exports, named by its shortest public path: modules, types, fields,
//      variants, functions, methods, associated items, constants, statics, macros, and one
//      `impl Trait for Type` line per trait implementation (auto-trait and blanket impls
//      excluded). Parameter names are left out: they are not part of a Rust API. A type of
//      another crate is written with its full path (std, core and alloc types by their name).
// @@T  every type a caller can see: declared `pub` with every enclosing module `pub`, or
//      named by a reachable signature.
// @@E  every type reachable from the crate root.
// @@I  a reachable item marked `#[doc(hidden)]`.
use serde_json::Value;
use std::collections::{BTreeSet, HashMap, VecDeque};

struct Doc<'a> {
    index: &'a serde_json::Map<String, Value>,
    paths: &'a serde_json::Map<String, Value>,
    /// The shortest public path of every reachable local item.
    public: HashMap<String, String>,
}

fn key(v: &Value) -> Option<String> {
    match v {
        Value::Number(n) => Some(n.to_string()),
        Value::String(s) => Some(s.clone()),
        _ => None,
    }
}

fn kind_of(item: &Value) -> (&str, &Value) {
    let inner = &item["inner"];
    match inner {
        Value::Object(m) => m.iter().next().map(|(k, v)| (k.as_str(), v)).unwrap_or(("", inner)),
        Value::String(s) => (s.as_str(), inner),
        _ => ("", inner),
    }
}

fn is_public(item: &Value) -> bool {
    item["visibility"] == "public"
}

fn hidden(item: &Value) -> bool {
    item["attrs"].as_array().is_some_and(|attrs| {
        attrs.iter().any(|a| a["other"].as_str().is_some_and(|s| s.contains("doc(hidden)")))
    })
}

fn non_exhaustive(item: &Value) -> bool {
    item["attrs"].as_array().is_some_and(|attrs| attrs.iter().any(|a| a == "non_exhaustive"))
}

fn is_type_kind(kind: &str) -> bool {
    matches!(kind, "struct" | "enum" | "union" | "trait" | "type_alias" | "trait_alias")
}

impl<'a> Doc<'a> {
    fn item(&self, id: &str) -> Option<&'a Value> {
        self.index.get(id)
    }

    fn path_of(&self, id: &str, written: &str) -> String {
        if let Some(p) = self.public.get(id) {
            return p.clone();
        }
        if let Some(entry) = self.paths.get(id) {
            let segments: Vec<&str> =
                entry["path"].as_array().map(|a| a.iter().filter_map(|s| s.as_str()).collect()).unwrap_or_default();
            if let Some(first) = segments.first() {
                if matches!(*first, "std" | "core" | "alloc") {
                    return segments.last().unwrap().to_string();
                }
                if entry["crate_id"] == 0 {
                    return format!("{}(private)", segments.join("::"));
                }
                /* Another crate's item by its crate and name, not its definition path, which
                 * moves between that crate's releases (serde's traits live in serde_core). */
                let owner = match *first {
                    "serde_core" => "serde",
                    other => other,
                };
                return format!("{}::{}", owner, segments.last().unwrap());
            }
        }
        written.to_string()
    }

    fn ty(&self, t: &Value) -> String {
        let Some(obj) = t.as_object() else {
            return match t.as_str() {
                Some("infer") => "_".into(),
                Some(s) => s.into(),
                None => "?".into(),
            };
        };
        let Some((k, v)) = obj.iter().next() else { return "?".into() };
        match k.as_str() {
            "resolved_path" => self.path(v),
            "dyn_trait" => {
                let mut parts: Vec<String> = v["traits"]
                    .as_array()
                    .map(|a| a.iter().map(|p| self.poly(p)).collect())
                    .unwrap_or_default();
                if let Some(l) = v["lifetime"].as_str() {
                    parts.push(l.into());
                }
                format!("dyn {}", parts.join(" + "))
            }
            "generic" => v.as_str().unwrap_or("?").into(),
            "primitive" => v.as_str().unwrap_or("?").into(),
            "function_pointer" => {
                let sig = &v["sig"];
                format!("fn({}){}", self.inputs(sig, false), self.output(sig))
            }
            "tuple" => {
                let items: Vec<String> = v.as_array().map(|a| a.iter().map(|x| self.ty(x)).collect()).unwrap_or_default();
                if items.len() == 1 { format!("({},)", items[0]) } else { format!("({})", items.join(", ")) }
            }
            "slice" => format!("[{}]", self.ty(v)),
            "array" => format!("[{}; {}]", self.ty(&v["type"]), v["len"].as_str().unwrap_or("?")),
            "impl_trait" => format!("impl {}", self.bounds(v)),
            "raw_pointer" => format!(
                "*{} {}",
                if v["is_mutable"] == true { "mut" } else { "const" },
                self.ty(&v["type"])
            ),
            "borrowed_ref" => {
                let mut s = String::from("&");
                if let Some(l) = v["lifetime"].as_str() {
                    s.push_str(l);
                    s.push(' ');
                }
                if v["is_mutable"] == true {
                    s.push_str("mut ");
                }
                s.push_str(&self.ty(&v["type"]));
                s
            }
            "qualified_path" => {
                let name = v["name"].as_str().unwrap_or("?");
                let self_ty = self.ty(&v["self_type"]);
                match v["trait"].as_object() {
                    Some(_) => format!("<{} as {}>::{}{}", self_ty, self.path(&v["trait"]), name, self.args(&v["args"])),
                    None => format!("{}::{}", self_ty, name),
                }
            }
            "pat" => self.ty(&v["type"]),
            other => format!("?{}", other),
        }
    }

    fn path(&self, p: &Value) -> String {
        let written = p["path"].as_str().unwrap_or("?");
        let id = key(&p["id"]).unwrap_or_default();
        format!("{}{}", self.path_of(&id, written), self.args(&p["args"]))
    }

    fn poly(&self, p: &Value) -> String {
        let mut s = String::new();
        if let Some(params) = p["generic_params"].as_array() {
            if !params.is_empty() {
                s.push_str(&format!("for<{}> ", params.iter().map(|g| self.param(g)).collect::<Vec<_>>().join(", ")));
            }
        }
        s.push_str(&self.path(&p["trait"]));
        s
    }

    fn args(&self, a: &Value) -> String {
        let Some(obj) = a.as_object() else { return String::new() };
        if let Some(ab) = obj.get("angle_bracketed") {
            let mut parts: Vec<String> = Vec::new();
            for arg in ab["args"].as_array().into_iter().flatten() {
                if let Some(t) = arg.get("type") {
                    parts.push(self.ty(t));
                } else if let Some(l) = arg.get("lifetime") {
                    parts.push(l.as_str().unwrap_or("?").into());
                } else if let Some(c) = arg.get("const") {
                    parts.push(c["expr"].as_str().unwrap_or("?").into());
                } else {
                    parts.push("_".into());
                }
            }
            for c in ab["constraints"].as_array().into_iter().flatten() {
                let name = format!("{}{}", c["name"].as_str().unwrap_or("?"), self.args(&c["args"]));
                if let Some(eq) = c["binding"].get("equality") {
                    let rhs = eq.get("type").map(|t| self.ty(t)).unwrap_or_else(|| "?".into());
                    parts.push(format!("{} = {}", name, rhs));
                } else if let Some(b) = c["binding"].get("constraint") {
                    parts.push(format!("{}: {}", name, self.bounds(b)));
                }
            }
            if parts.is_empty() { String::new() } else { format!("<{}>", parts.join(", ")) }
        } else if let Some(p) = obj.get("parenthesized") {
            let inputs: Vec<String> = p["inputs"].as_array().map(|a| a.iter().map(|x| self.ty(x)).collect()).unwrap_or_default();
            let out = if p["output"].is_null() { String::new() } else { format!(" -> {}", self.ty(&p["output"])) };
            format!("({}){}", inputs.join(", "), out)
        } else {
            String::new()
        }
    }

    fn bounds(&self, b: &Value) -> String {
        let mut parts = Vec::new();
        for bound in b.as_array().into_iter().flatten() {
            if let Some(tb) = bound.get("trait_bound") {
                let modifier = match tb["modifier"].as_str() {
                    Some("maybe") => "?",
                    Some("maybe_const") => "~const ",
                    _ => "",
                };
                parts.push(format!("{}{}", modifier, self.poly(tb)));
            } else if let Some(l) = bound.get("outlives") {
                parts.push(l.as_str().unwrap_or("?").into());
            } else if let Some(u) = bound.get("use") {
                let names: Vec<String> = u
                    .as_array()
                    .into_iter()
                    .flatten()
                    .map(|x| x.as_str().map(String::from).or_else(|| x.as_object().and_then(|o| o.values().next()).and_then(|v| v.as_str()).map(String::from)).unwrap_or_default())
                    .collect();
                parts.push(format!("use<{}>", names.join(", ")));
            }
        }
        parts.join(" + ")
    }

    fn param(&self, g: &Value) -> String {
        let name = g["name"].as_str().unwrap_or("?");
        let kind = &g["kind"];
        if let Some(l) = kind.get("lifetime") {
            let outlives: Vec<&str> = l["outlives"].as_array().into_iter().flatten().filter_map(|x| x.as_str()).collect();
            if outlives.is_empty() { name.into() } else { format!("{}: {}", name, outlives.join(" + ")) }
        } else if let Some(t) = kind.get("type") {
            let bounds = self.bounds(&t["bounds"]);
            let mut s = name.to_string();
            if !bounds.is_empty() {
                s.push_str(": ");
                s.push_str(&bounds);
            }
            if !t["default"].is_null() {
                s.push_str(&format!(" = {}", self.ty(&t["default"])));
            }
            s
        } else if let Some(c) = kind.get("const") {
            format!("const {}: {}", name, self.ty(&c["type"]))
        } else {
            name.into()
        }
    }

    fn generics(&self, g: &Value) -> (String, String) {
        let params: Vec<String> = g["params"]
            .as_array()
            .into_iter()
            .flatten()
            .filter(|p| p["kind"]["type"]["is_synthetic"] != true)
            .map(|p| self.param(p))
            .collect();
        let preds: Vec<String> = g["where_predicates"]
            .as_array()
            .into_iter()
            .flatten()
            .map(|w| {
                if let Some(b) = w.get("bound_predicate") {
                    format!("{}: {}", self.ty(&b["type"]), self.bounds(&b["bounds"]))
                } else if let Some(l) = w.get("lifetime_predicate") {
                    let outlives: Vec<&str> = l["outlives"].as_array().into_iter().flatten().filter_map(|x| x.as_str()).collect();
                    format!("{}: {}", l["lifetime"].as_str().unwrap_or("?"), outlives.join(" + "))
                } else if let Some(e) = w.get("eq_predicate") {
                    let rhs = e["rhs"].get("type").map(|t| self.ty(t)).unwrap_or_else(|| "?".into());
                    format!("{} = {}", self.ty(&e["lhs"]), rhs)
                } else {
                    "?".into()
                }
            })
            .collect();
        (
            if params.is_empty() { String::new() } else { format!("<{}>", params.join(", ")) },
            if preds.is_empty() { String::new() } else { format!(" where {}", preds.join(", ")) },
        )
    }

    fn inputs(&self, sig: &Value, with_self: bool) -> String {
        let mut parts = Vec::new();
        for input in sig["inputs"].as_array().into_iter().flatten() {
            let name = input[0].as_str().unwrap_or("_");
            let t = &input[1];
            if with_self && name == "self" {
                let rendered = self.ty(t);
                parts.push(match rendered.as_str() {
                    "Self" => "self".to_string(),
                    "&Self" => "&self".to_string(),
                    "&mut Self" => "&mut self".to_string(),
                    other => format!("self: {}", other),
                });
            } else {
                parts.push(self.ty(t));
            }
        }
        parts.join(", ")
    }

    fn output(&self, sig: &Value) -> String {
        if sig["output"].is_null() { String::new() } else { format!(" -> {}", self.ty(&sig["output"])) }
    }

    fn function(&self, path: &str, f: &Value) -> String {
        let header = &f["header"];
        let mut quals = String::new();
        if header["is_const"] == true {
            quals.push_str("const ");
        }
        if header["is_async"] == true {
            quals.push_str("async ");
        }
        if header["is_unsafe"] == true {
            quals.push_str("unsafe ");
        }
        let (params, wheres) = self.generics(&f["generics"]);
        format!(
            "{}fn {}{}({}){}{}",
            quals,
            path,
            params,
            self.inputs(&f["sig"], true),
            self.output(&f["sig"]),
            wheres
        )
    }
}

fn main() {
    let file = std::env::args().nth(1).expect("usage: rust-surface <crate.json>");
    let text = std::fs::read_to_string(&file).expect("read rustdoc JSON");
    let json: Value = serde_json::from_str(&text).expect("parse rustdoc JSON");
    let index = json["index"].as_object().expect("index");
    let paths = json["paths"].as_object().expect("paths");
    let root = key(&json["root"]).expect("root");
    let crate_name = index[&root]["name"].as_str().unwrap_or("crate").to_string();
    let mut doc = Doc { index, paths, public: HashMap::new() };

    // Breadth-first walk from the root through `pub` items and `pub use` re-exports: the
    // first path found for an item is its shortest public path.
    let mut out: BTreeSet<String> = BTreeSet::new();
    let mut reexports: Vec<String> = Vec::new();
    let mut queue: VecDeque<(String, String)> = VecDeque::new();
    doc.public.insert(root.clone(), crate_name.clone());
    queue.push_back((root.clone(), crate_name.clone()));
    let mut order: Vec<String> = vec![root.clone()];
    while let Some((id, path)) = queue.pop_front() {
        let Some(item) = doc.item(&id) else { continue };
        let (kind, inner) = kind_of(item);
        let children: Vec<String> = match kind {
            "module" => inner["items"].as_array().into_iter().flatten().filter_map(key).collect(),
            _ => continue,
        };
        let mut visit = |child_id: String, child_path: String, doc: &mut Doc| {
            if !doc.public.contains_key(&child_id) {
                doc.public.insert(child_id.clone(), child_path.clone());
                order.push(child_id.clone());
                queue.push_back((child_id, child_path));
            }
        };
        for child in children {
            let Some(ci) = doc.item(&child) else { continue };
            if !is_public(ci) {
                continue;
            }
            let (ck, cv) = kind_of(ci);
            if ck == "use" {
                let name = cv["name"].as_str().unwrap_or("?");
                match key(&cv["id"]) {
                    Some(target) if doc.item(&target).is_some() => {
                        if cv["is_glob"] == true {
                            let t = doc.item(&target).unwrap();
                            let (tk, tv) = kind_of(t);
                            let items: Vec<String> = match tk {
                                "module" => tv["items"].as_array().into_iter().flatten().filter_map(key).collect(),
                                _ => Vec::new(),
                            };
                            for g in items {
                                if let Some(gi) = doc.item(&g) {
                                    if is_public(gi) {
                                        if let Some(n) = gi["name"].as_str() {
                                            visit(g.clone(), format!("{}::{}", path, n), &mut doc);
                                        }
                                    }
                                }
                            }
                        } else {
                            visit(target, format!("{}::{}", path, name), &mut doc);
                        }
                    }
                    _ => reexports.push(format!("use {}::{} = {}", path, name, cv["source"].as_str().unwrap_or("?"))),
                }
            } else if let Some(n) = ci["name"].as_str() {
                visit(child, format!("{}::{}", path, n), &mut doc);
            }
        }
    }
    for r in reexports {
        out.insert(format!("@@S {}", r));
    }

    let doc = doc;
    let mut signatures: BTreeSet<String> = BTreeSet::new();
    let mut exported_types: BTreeSet<String> = BTreeSet::new();
    for id in &order {
        let item = &doc.index[id];
        let path = &doc.public[id];
        let (kind, v) = kind_of(item);
        if hidden(item) {
            out.insert(format!("@@I {}", path));
        }
        if is_type_kind(kind) {
            exported_types.insert(path.clone());
        }
        let ne = if non_exhaustive(item) { "#[non_exhaustive] " } else { "" };
        match kind {
            "module" => {
                signatures.insert(format!("mod {}", path));
            }
            "struct" | "union" => {
                let (params, wheres) = doc.generics(&v["generics"]);
                let skind = &v["kind"];
                let mut head = format!("{}{} {}{}", ne, kind, path, params);
                if let Some(fields) = skind.get("tuple") {
                    let parts: Vec<String> = fields
                        .as_array()
                        .into_iter()
                        .flatten()
                        .map(|f| match key(f).and_then(|fid| doc.item(&fid)) {
                            Some(fi) if is_public(fi) => format!("pub {}", doc.ty(&fi["inner"]["struct_field"])),
                            _ => "_".into(),
                        })
                        .collect();
                    head.push_str(&format!("({})", parts.join(", ")));
                }
                head.push_str(&wheres);
                signatures.insert(head);
                let named = skind.get("plain").map(|p| p["fields"].clone()).or_else(|| v.get("fields").cloned());
                for f in named.as_ref().and_then(|f| f.as_array()).into_iter().flatten() {
                    if let Some(fi) = key(f).and_then(|fid| doc.item(&fid)) {
                        if is_public(fi) {
                            signatures.insert(format!(
                                "field {}::{}: {}",
                                path,
                                fi["name"].as_str().unwrap_or("?"),
                                doc.ty(&fi["inner"]["struct_field"])
                            ));
                        }
                    }
                }
                impls(&doc, path, v, &mut signatures);
            }
            "enum" => {
                let (params, wheres) = doc.generics(&v["generics"]);
                signatures.insert(format!("{}enum {}{}{}", ne, path, params, wheres));
                for var in v["variants"].as_array().into_iter().flatten() {
                    let Some(vi) = key(var).and_then(|vid| doc.item(&vid)) else { continue };
                    let name = vi["name"].as_str().unwrap_or("?");
                    let vk = &vi["inner"]["variant"]["kind"];
                    let shape = if let Some(t) = vk.get("tuple") {
                        let parts: Vec<String> = t
                            .as_array()
                            .into_iter()
                            .flatten()
                            .map(|f| key(f).and_then(|fid| doc.item(&fid)).map(|fi| doc.ty(&fi["inner"]["struct_field"])).unwrap_or_else(|| "_".into()))
                            .collect();
                        format!("({})", parts.join(", "))
                    } else if let Some(s) = vk.get("struct") {
                        let parts: Vec<String> = s["fields"]
                            .as_array()
                            .into_iter()
                            .flatten()
                            .filter_map(|f| key(f).and_then(|fid| doc.item(&fid)))
                            .map(|fi| format!("{}: {}", fi["name"].as_str().unwrap_or("?"), doc.ty(&fi["inner"]["struct_field"])))
                            .collect();
                        format!(" {{ {} }}", parts.join(", "))
                    } else {
                        String::new()
                    };
                    let disc = vi["inner"]["variant"]["discriminant"]["value"]
                        .as_str()
                        .map(|d| format!(" = {}", d))
                        .unwrap_or_default();
                    let vne = if non_exhaustive(vi) { "#[non_exhaustive] " } else { "" };
                    signatures.insert(format!("{}variant {}::{}{}{}", vne, path, name, shape, disc));
                }
                impls(&doc, path, v, &mut signatures);
            }
            "trait" => {
                let (params, wheres) = doc.generics(&v["generics"]);
                let supers = doc.bounds(&v["bounds"]);
                let supers = if supers.is_empty() { String::new() } else { format!(": {}", supers) };
                let unsafety = if v["is_unsafe"] == true { "unsafe " } else { "" };
                signatures.insert(format!("{}trait {}{}{}{}", unsafety, path, params, supers, wheres));
                for t in v["items"].as_array().into_iter().flatten() {
                    if let Some(ti) = key(t).and_then(|tid| doc.item(&tid)) {
                        assoc(&doc, path, ti, &mut signatures, true);
                    }
                }
            }
            "type_alias" => {
                let (params, wheres) = doc.generics(&v["generics"]);
                signatures.insert(format!("type {}{}{} = {}", path, params, wheres, doc.ty(&v["type"])));
            }
            "function" => {
                signatures.insert(doc.function(path, v));
            }
            "constant" => {
                signatures.insert(format!("const {}: {}", path, doc.ty(&v["type"])));
            }
            "static" => {
                let m = if v["is_mutable"] == true { "mut " } else { "" };
                signatures.insert(format!("static {}{}: {}", m, path, doc.ty(&v["type"])));
            }
            "macro" | "proc_macro" => {
                signatures.insert(format!("macro {}!", path));
            }
            _ => {}
        }
    }

    // The types a caller can see: declared `pub` along a chain of `pub` modules, or named by a
    // reachable signature.
    let mut visible: BTreeSet<String> = exported_types.clone();
    let mut stack: Vec<(String, String, bool)> = vec![(root.clone(), crate_name.clone(), true)];
    while let Some((id, path, open)) = stack.pop() {
        let Some(item) = doc.item(&id) else { continue };
        for child in item["inner"]["module"]["items"].as_array().into_iter().flatten().filter_map(key) {
            let Some(ci) = doc.item(&child) else { continue };
            let (ck, _) = kind_of(ci);
            let Some(name) = ci["name"].as_str() else { continue };
            let child_path = format!("{}::{}", path, name);
            if ck == "module" {
                stack.push((child, child_path, open && is_public(ci)));
            } else if is_type_kind(ck) && is_public(ci) && !doc.public.contains_key(&child) {
                let named = format!("{}(private)", doc.paths.get(&child).and_then(|p| p["path"].as_array()).map(|a| a.iter().filter_map(|s| s.as_str()).collect::<Vec<_>>().join("::")).unwrap_or(child_path.clone()));
                let pattern = |s: &String| {
                    s.match_indices(&named).any(|(i, _)| {
                        let end = i + named.len();
                        !s[end..].starts_with(|c: char| c.is_alphanumeric() || c == '_')
                    })
                };
                if open || signatures.iter().any(pattern) {
                    visible.insert(if open { child_path } else { named });
                }
            }
        }
    }

    for t in &visible {
        out.insert(format!("@@T {}", t));
    }
    for t in &exported_types {
        out.insert(format!("@@E {}", t));
    }
    for s in signatures {
        out.insert(format!("@@S {}", s));
    }
    for line in out {
        println!("{}", line);
    }
}

fn impls(doc: &Doc, path: &str, v: &Value, signatures: &mut BTreeSet<String>) {
    for imp in v["impls"].as_array().into_iter().flatten() {
        let Some(ii) = key(imp).and_then(|iid| doc.item(&iid)) else { continue };
        let i = &ii["inner"]["impl"];
        if i["is_synthetic"] == true || !i["blanket_impl"].is_null() {
            continue;
        }
        let (params, wheres) = doc.generics(&i["generics"]);
        let for_ty = doc.ty(&i["for"]);
        if i["trait"].is_null() {
            for m in i["items"].as_array().into_iter().flatten() {
                if let Some(mi) = key(m).and_then(|mid| doc.item(&mid)) {
                    if is_public(mi) {
                        assoc(doc, &for_ty, mi, signatures, false);
                    }
                }
            }
        } else {
            let neg = if i["is_negative"] == true { "!" } else { "" };
            signatures.insert(format!("impl{} {}{} for {}{}", params, neg, doc.path(&i["trait"]), for_ty, wheres));
        }
    }
    let _ = path;
}

fn assoc(doc: &Doc, owner: &str, item: &Value, signatures: &mut BTreeSet<String>, in_trait: bool) {
    let name = item["name"].as_str().unwrap_or("?");
    let path = format!("{}::{}", owner, name);
    let (kind, v) = kind_of(item);
    let provided = |has: bool| if in_trait && has { " (provided)" } else { "" };
    match kind {
        "function" => {
            let s = format!("{}{}", doc.function(&path, v), provided(v["has_body"] == true));
            signatures.insert(s);
        }
        "assoc_const" => {
            signatures.insert(format!("const {}: {}{}", path, doc.ty(&v["type"]), provided(!v["value"].is_null())));
        }
        "assoc_type" => {
            let bounds = doc.bounds(&v["bounds"]);
            let mut s = format!("type {}", path);
            if !bounds.is_empty() {
                s.push_str(&format!(": {}", bounds));
            }
            if !v["type"].is_null() {
                s.push_str(&format!(" = {}", doc.ty(&v["type"])));
            }
            signatures.insert(s);
        }
        _ => {}
    }
}
