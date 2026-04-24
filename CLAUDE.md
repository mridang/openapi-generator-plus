# Claude Code Instructions

## Devbox Requirement

This project uses [Devbox](https://www.jetify.com/devbox) to manage all development tools (JDK, Maven, etc.). **All shell commands must be run inside `devbox shell`.**

Prefix every Bash command with `devbox run --` or wrap it in `devbox shell`:

```bash
# Correct
devbox run -- mvn test -Dtest=GenerateClientsTest
devbox run -- mvn compile
devbox run -- mvn spotless:apply

# Wrong - do NOT call mvn, java, etc. directly
mvn test
```

The `devbox.json` in the project root defines all available packages and scripts. Use `devbox run <script>` for predefined scripts:

- `devbox run test` - Run `mvn verify`
- `devbox run format` - Run `mvn spotless:apply`
- `devbox run check` - Run `mvn compile`

## Use Mustache Templates for File Generation

When generating file content programmatically (e.g., per-operation Options classes, authenticator classes), **always use Mustache template files** rendered with a context map. Never build file content with Java `StringBuilder` or string concatenation. Create `.mustache` template files under `src/main/resources/templates/<language>/` and render them using `renderOptionsTemplate()` or the same pattern. The structure and layout of generated files belong in templates, not in Java code.

## No Vendor Extensions in Templates

**Never use vendor extensions** (`vendorExtensions.x-*`) in Mustache templates. Always use native CodegenOperation/CodegenParameter fields instead (e.g., `{{#content}}`, `{{#servers}}`, `{{#cookieParams}}`). Vendor extensions are fragile and require custom Java code to set them.

## Integration Tests

Integration tests for generated clients (Ruby, Python, PHP, Java) run inside Docker containers. Docker itself does not need to be inside devbox, but Maven commands that trigger these tests do.
