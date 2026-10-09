fn main() {
    let keywords = vec![
        // Strict keywords
        "as", "async", "await", "break", "const", "continue", "crate", "dyn",
        "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
        "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
        "self", "Self", "static", "struct", "super", "trait", "true", "type",
        "unsafe", "use", "where", "while",
        // Reserved keywords (reserved for future use)
        "abstract", "become", "box", "do", "final", "macro", "override",
        "priv", "try", "typeof", "unsized", "virtual", "yield",
        // Weak keywords (special meaning in certain contexts)
        "macro_rules", "union",
    ];
    for kw in keywords {
        println!("{}", kw);
    }
}
