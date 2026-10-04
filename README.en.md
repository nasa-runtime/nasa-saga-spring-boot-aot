# nasa-saga-spring-boot-aot

`nasa-saga-spring-boot-aot` generates Spring AOT and GraalVM native-image hints for `nasa-saga` participants.
It discovers every `@Saga` type in the application scope, including managed steps that are not Spring beans, and
preserves phase methods, managed constructors, Saga JSON models, persistence SQL, and MyBatis mapper, SQL-provider,
and binding-model contracts.

[中文](README.md)

## Why it exists

- Keeps annotation-driven participant validation and dynamic construction available in a native executable.
- Covers non-bean services with `managed=true`; discovery is not limited to the Spring container.
- Preserves records and private serializers/deserializers used by the strict Saga JSON codec.
- Avoids loading MyBatis adapter types when MyBatis is absent; when present, it covers logging, caches, type
  handlers, application mappers, and XML resources.

## Architecture

```text
application packages ── @Saga ──┬─ execute / compensate / cancel / resolve
                                └─ managed=true no-arg constructor

nasa-saga types ────────────────┬─ JSON / HTTP / gRPC reflection
                                ├─ db/*.sql
                                └─ MyBatis runtime and SagaMybatisMapper proxy

application MyBatis mappers ────┬─ JDK proxies and interface reflection
                                ├─ SQL providers
                                └─ parameters, returns, and mapper/*.xml
```

The transitive `nasa-core-spring-boot-aot` dependency derives scope from Spring BeanDefinitions,
`AutoConfigurationPackages`, and `@SpringBootApplication`; no fixed `com` root is used. The processor only reads
class metadata during AOT. It does not start a participant, acquire a capability lease, or access a database or
network.

## Compatibility and installation

- JDK 21+, Spring Boot 3.5.x / Spring Framework 6.2.x
- `nasa-saga` 1.0.0
- `nasa-core-spring-boot-aot` 1.0.4 as a transitive dependency

```xml
<dependency>
    <groupId>io.github.nasa-runtime</groupId>
    <artifactId>nasa-saga-spring-boot-aot</artifactId>
    <version>1.0.0</version>
</dependency>
```

Forward participant packages outside the main application package to the Spring Boot Maven Plugin's AOT JVM:

```bash
./mvnw -Dspring-boot.aot.jvmArguments="-Dnasa.aot.scan-packages=com.acme.participant,dev.partner.steps" \
  -Pnative native:compile
```

`process-aot` runs in a separate JVM, so a plain Maven `-Dnasa.aot.scan-packages=...` is not propagated to it.
Use that underlying system property when invoking the AOT JVM directly.

## Registered contracts

- Declared constructor and phase-method metadata for `@Saga` types.
- Invocation access for `execute`, `compensate`, `cancel`, and `resolve`.
- No-argument constructor invocation for `managed=true`; a missing constructor stops the AOT build.
- Internal `nasa-saga` models, nested serializers/deserializers, and `db/*.sql` resources.
- MyBatis logging implementations, caches, type handlers, plugin proxies, DTD/XSD files, and `mapper/*.xml` when
  MyBatis is on the classpath.
- The `SagaMybatisMapper` and every MyBatis-annotated application mapper proxy, including recursively discovered SQL
  providers, parameter types, and return types. JDK containers are not broadly exposed to reflection as a result.
- A hosted feature delays gRPC's shaded Netty channel initialization until runtime after native-image argument
  parsing. Transport loggers and platform capabilities are therefore established against the actual runtime
  environment even when an upstream dependency supplies a build-time default.

Generated protobuf/gRPC types rely on upstream reachability metadata. This adapter does not expose every transitive
dependency to reflection.

## Safety and ordering boundaries

This project only preserves reachability. It does not change Saga authority or ordering invariants: a participant
must still commit Inbox state, business effects, and result Outbox in its local transaction; capability leases,
message authentication, cancellation barriers, and fencing remain owned by `nasa-saga` and host configuration.
Generated hints do not make external effects idempotent and do not initialize persistence schemas.

## Failure and observability

- Missing safe scope, public top-level roots, unreadable class resources, or incomplete dependencies stop AOT.
- An `@Saga` type that does not implement `SagaStep`, or a managed type without a no-arg constructor, fails with the
  exact class name.
- Use AOT exceptions and generated files under `target/spring-aot/` to inspect participants, proxies, and resources.

## Non-goals

This project does not implement the Orchestrator, participant transport, schema migrations, or the GraalVM toolchain.
It does not validate deployment certificates, database dialects, endpoints, or Rust-side contracts. Dynamically
deserialized application payload classes still need application-provided hints.

## Build and release

```bash
mvn clean verify
```

See [CONTRIBUTING.en.md](CONTRIBUTING.en.md), [SECURITY.en.md](SECURITY.en.md), and
[RELEASE.en.md](RELEASE.en.md). The project is dual-licensed under Apache-2.0 OR MIT.
