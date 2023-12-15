# Contributing to FlowCore

Thank you for your interest in contributing to FlowCore!

## Development Setup

1. Java 21 (Temurin recommended)
2. Maven 3.9+
3. Docker (for integration tests)

## Building

```bash
mvn verify
```

## Code Style

- Follow the Checkstyle rules defined in `build/checkstyle.xml`
- Run `mvn validate -P qa` to check

## Testing

- Unit tests: `src/test/java/**/*Test.java`
- Integration tests: `src/test/java/**/*IT.java`
- All tests must pass: `mvn verify`
- Coverage thresholds: core modules ≥ 80%, demos ≥ 60%

## Pull Request Process

1. Fork the repository
2. Create a feature branch (`feature/my-feature`)
3. Ensure all tests pass and coverage thresholds are met
4. Submit a pull request against `main`

## Commit Messages

Use Conventional Commits:
```
feat(scope): description
fix(scope): description
docs: description
test(scope): description
```
