// Kotlin keywords from https://kotlinlang.org/docs/keyword-reference.html
val keywords = listOf(
    // Hard keywords
    "as", "break", "class", "continue", "do", "else", "false", "for",
    "fun", "if", "in", "interface", "is", "null", "object", "package",
    "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
    // Soft keywords
    "by", "catch", "constructor", "delegate", "dynamic", "field", "file",
    "finally", "get", "import", "init", "param", "property", "receiver",
    "set", "setparam", "where",
    // Modifier keywords
    "abstract", "actual", "annotation", "companion", "const", "crossinline",
    "data", "enum", "expect", "external", "final", "infix", "inline",
    "inner", "internal", "lateinit", "noinline", "open", "operator", "out",
    "override", "private", "protected", "public", "reified", "sealed",
    "suspend", "tailrec", "value", "vararg"
)
keywords.forEach { println(it) }
