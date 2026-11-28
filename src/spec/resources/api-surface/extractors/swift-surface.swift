// Prints the public API records of the Swift SDK (see AbstractApiSurfaceSpec) from the symbol
// graph SwiftPM writes for the library target:
//
//   swift package dump-symbol-graph --skip-synthesized-members
//   swift /tmp/api-surface/swift-surface.swift <symbolgraph dir> <module>
//
// @@S  one line per public symbol, `<Module>.<owner path>: <declaration>`, the declaration
//      rebuilt from its fragments without the internal parameter names (they are not part of
//      a Swift API), plus one `<type>: conforms to <protocol>` / `inherits from <class>` line
//      per relationship. A declaration that names a type of a module outside the Swift
//      standard library and Foundation ends with ` [uses <Module>.<Type>, ...]`.
// @@T  every public type (class, struct, enum, protocol, typealias).
// @@E  the same: every public symbol of the module is exported by construction.
// @@I  a public type whose name, or the name of a type it is nested in, starts with `_`.
import Foundation

let arguments = CommandLine.arguments
guard arguments.count == 3 else {
  FileHandle.standardError.write("usage: swift-surface <symbolgraph dir> <module>\n".data(using: .utf8)!)
  exit(2)
}
let directory = URL(fileURLWithPath: arguments[1])
let module = arguments[2]
let system: Set<String> = [
  "Swift", "_Concurrency", "_StringProcessing", "Foundation", "FoundationEssentials",
  "FoundationInternationalization", "FoundationNetworking", "Dispatch", module,
]
let typeKinds: Set<String> = [
  "swift.class", "swift.struct", "swift.enum", "swift.protocol", "swift.typealias",
]

/// The module of a Swift USR such as `s:20FoundationEssentials4DataV` (`s:S...` and `s:s...`
/// are the standard library).
func moduleOf(_ usr: String) -> String? {
  guard usr.hasPrefix("s:") else { return nil }
  let rest = usr.dropFirst(2)
  if rest.hasPrefix("S") || rest.hasPrefix("s") { return "Swift" }
  let digits = rest.prefix { $0.isNumber }
  guard let count = Int(digits) else { return nil }
  let name = rest.dropFirst(digits.count).prefix(count)
  return String(name)
}

func render(_ fragments: [[String: Any]]) -> (String, [String]) {
  var text = ""
  var uses: [String] = []
  for fragment in fragments {
    let kind = fragment["kind"] as? String ?? ""
    let spelling = fragment["spelling"] as? String ?? ""
    if kind == "internalParam" {
      continue
    }
    if kind == "typeIdentifier", let usr = fragment["preciseIdentifier"] as? String,
      let owner = moduleOf(usr), !system.contains(owner)
    {
      uses.append("\(owner).\(spelling)")
    }
    text += spelling
  }
  var flat = text.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
  // `_ value: Int` loses its internal name and reads `_ : Int`; close the gap.
  flat = flat.replacingOccurrences(of: " :", with: ":")
  return (flat, uses)
}

var out = Set<String>()
var names: [String: String] = [:]
let files = (try FileManager.default.contentsOfDirectory(atPath: directory.path))
  .filter { $0 == "\(module).symbols.json" || $0.hasPrefix("\(module)@") }
  .sorted()
var relationships: [[String: Any]] = []
for file in files {
  let data = try Data(contentsOf: directory.appendingPathComponent(file))
  guard let graph = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
    continue
  }
  for symbol in graph["symbols"] as? [[String: Any]] ?? [] {
    let kind = (symbol["kind"] as? [String: Any])?["identifier"] as? String ?? ""
    let path = symbol["pathComponents"] as? [String] ?? []
    let usr = (symbol["identifier"] as? [String: Any])?["precise"] as? String ?? ""
    let qualified = ([module] + path).joined(separator: ".")
    names[usr] = qualified
    let owner = ([module] + path.dropLast()).joined(separator: ".")
    let (declaration, uses) = render(symbol["declarationFragments"] as? [[String: Any]] ?? [])
    var line = "@@S \(owner): \(declaration)"
    if !uses.isEmpty {
      line += " [uses \(Set(uses).sorted().joined(separator: ", "))]"
    }
    out.insert(line)
    if typeKinds.contains(kind) {
      out.insert("@@T \(qualified)")
      out.insert("@@E \(qualified)")
      if path.contains(where: { $0.hasPrefix("_") }) {
        out.insert("@@I \(qualified)")
      }
    }
  }
  relationships += graph["relationships"] as? [[String: Any]] ?? []
}
for relationship in relationships {
  let kind = relationship["kind"] as? String ?? ""
  let verb: String
  switch kind {
  case "conformsTo": verb = "conforms to"
  case "inheritsFrom": verb = "inherits from"
  default: continue
  }
  guard let source = names[relationship["source"] as? String ?? ""] else { continue }
  let targetUsr = relationship["target"] as? String ?? ""
  var target = names[targetUsr] ?? relationship["targetFallback"] as? String ?? targetUsr
  if let owner = moduleOf(targetUsr), !system.contains(owner), !target.hasPrefix("\(owner).") {
    target = "\(owner).\(target)"
  }
  out.insert("@@S \(source): \(verb) \(target)")
}
for line in out.sorted() {
  print(line)
}
