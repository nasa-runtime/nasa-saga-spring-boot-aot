# Contributing

## Environment and checks

Use JDK 21+, Maven 3.6.3+, and resolvable `nasa-saga` and core AOT dependencies.

```bash
mvn clean verify
```

## Change policy

- This repository only adapts `nasa-saga` to Spring AOT; it does not copy participant or transport behavior.
- New hints must map to real dynamic paths in `@Saga` contracts, the JSON codec, persistence resources, or optional
  MyBatis integration.
- Scanning and hint generation must not start a participant, acquire a lease, access a database, or connect to a
  network.
- Optional integrations such as MyBatis must remain loadable when the dependency is absent.
- Changes to managed constructors or phase methods need focused `RuntimeHintsPredicates` checks.
- Production comments are written in Chinese and explain purpose, parameters, returns, and important boundaries.
  Update both READMEs when behavior changes.

Before submitting, inspect tests, Javadoc, the dependency tree, the source jar, final jar, and SQL resources.
Protocol or ordering changes belong in `nasa-saga`; this adapter only expresses reachability.
