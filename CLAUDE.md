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

## Integration Tests

Integration tests for generated clients (Ruby, Python, PHP, Java) run inside Docker containers. Docker itself does not need to be inside devbox, but Maven commands that trigger these tests do.
