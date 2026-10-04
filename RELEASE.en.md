# Maven Central Release

## Prerequisites

- The POM is non-SNAPSHOT, and `nasa-saga` and core AOT versions match both READMEs.
- The required `nasa-core-spring-boot-aot` version is available from Maven Central and resolves from an empty local
  repository.
- A Central token is configured for the Maven `central` server and the signing environment has a GPG key.
- READMEs, contribution guides, security policies, both licenses, source jar, and Javadoc jar are archived.

## Local acceptance

```bash
mvn clean verify
mvn package
jar tf target/nasa-saga-spring-boot-aot-1.0.0.jar
jar tf target/nasa-saga-spring-boot-aot-1.0.0-sources.jar
jar tf target/nasa-saga-spring-boot-aot-1.0.0-javadoc.jar
```

Inspect `META-INF/spring/aot.factories`, native-image properties, implementation classes, READMEs, and licenses from
the final jar. Confirm that the SQL pattern, hosted feature, and optional MyBatis behavior match the code. Check POM
coordinates, SCM, developers, licenses, and the absence of SNAPSHOT dependencies.

## Release order

1. Commit and push the target branch.
2. Wait for green remote CI and verify the tag, branch, and remote commit SHA.
3. Obtain explicit maintainer authorization for the coordinate and commit.
4. Run `mvn -Pcentral-release clean deploy` and inspect GPG and Central validation.
5. Publish the deployment after review in Central Portal; keep `autoPublish` disabled.
6. Read back the POM, main jar, sources, Javadoc, README, and checksums, then resolve the coordinate from an empty
   Maven project.

If an immutable coordinate omits public documents or archive content, stop acceptance and use a new patch release;
local content is not published content. Do not upload before step 3 authorization.
