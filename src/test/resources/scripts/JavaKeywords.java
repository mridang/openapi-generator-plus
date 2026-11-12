import javax.lang.model.SourceVersion;
import java.util.TreeSet;

public class JavaKeywords {
    private static final String[] CANDIDATES = {
        "abstract", "assert", "boolean", "break", "byte", "case", "catch",
        "char", "class", "const", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for",
        "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        // Literals that SourceVersion.isKeyword() also returns true for
        "true", "false", "null"
    };

    public static void main(String[] args) {
        TreeSet<String> keywords = new TreeSet<>();
        for (String candidate : CANDIDATES) {
            if (SourceVersion.isKeyword(candidate)) {
                // Exclude literal values - they are not language keywords
                if (!"true".equals(candidate)
                        && !"false".equals(candidate)
                        && !"null".equals(candidate)) {
                    keywords.add(candidate);
                }
            }
        }
        for (String kw : keywords) {
            System.out.println(kw);
        }
    }
}
