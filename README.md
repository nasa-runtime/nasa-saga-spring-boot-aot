# nasa-saga-spring-boot-aot

`nasa-saga-spring-boot-aot` 为 `nasa-saga` participant 生成 Spring AOT 与 GraalVM native-image 提示。它
发现应用包内全部 `@Saga` 类型，包括非 Spring Bean 的托管步骤，并保留阶段方法、托管构造器、Saga JSON
模型、持久化 SQL，以及 MyBatis mapper、SQL provider 和绑定模型。

[English](README.en.md)

## 核心价值

- 让注解式 participant 的合同检查和动态创建在原生镜像中保持可用。
- 覆盖 `managed=true` 的非 Bean service，不把发现范围局限在 Spring 容器。
- 保留 Saga 严格 JSON codec 使用的 record 与私有 serializer/deserializer。
- MyBatis 不存在时不加载其适配类型；存在时覆盖日志、缓存、类型处理器、业务 Mapper 和 XML 资源。

## 运行架构

```text
应用基础包 ── @Saga ──┬─ execute / compensate / cancel / resolve
                      └─ managed=true 无参构造器

nasa-saga 类型 ───────┬─ JSON / HTTP / gRPC 模型反射
                      ├─ db/*.sql
                      └─ MyBatis 运行时、SagaMybatisMapper proxy

应用 MyBatis Mapper ──┬─ JDK proxy 与接口反射
                      ├─ SQL provider
                      └─ 参数、返回值与 mapper/*.xml
```

扫描边界由传递依赖 `nasa-core-spring-boot-aot` 从 Spring BeanDefinition、
`AutoConfigurationPackages` 和 `@SpringBootApplication` 元数据确定，不依赖固定的 `com` 根包。处理器只在
AOT 构建期读取类元数据，不启动 participant、不申请 capability lease，也不访问数据库或网络。

## 兼容性与安装

- JDK 21+，Spring Boot 3.5.x / Spring Framework 6.2.x
- `nasa-saga` 1.0.0
- `nasa-core-spring-boot-aot` 1.0.4（传递依赖）

```xml
<dependency>
    <groupId>io.github.nasa-runtime</groupId>
    <artifactId>nasa-saga-spring-boot-aot</artifactId>
    <version>1.0.0</version>
</dependency>
```

若 participant 位于启动包之外，通过 Spring Boot Maven Plugin 的 AOT JVM 参数显式追加：

```bash
./mvnw -Dspring-boot.aot.jvmArguments="-Dnasa.aot.scan-packages=com.acme.participant,dev.partner.steps" \
  -Pnative native:compile
```

`process-aot` 在独立 JVM 中执行，普通 Maven `-Dnasa.aot.scan-packages=...` 不会自动进入该进程。直接调用
AOT JVM 时可使用这个底层系统属性。

## 登记内容

- `@Saga` 类型的已声明构造器和阶段方法元数据。
- `execute`、`compensate`、`cancel`、`resolve` 的调用入口。
- `managed=true` 类型的无参构造器调用入口；缺少该构造器会使 AOT 构建失败。
- `nasa-saga` 自有模型、嵌套 serializer/deserializer 和 `db/*.sql` 资源。
- MyBatis 在 classpath 中时的日志实现、缓存、类型处理器、插件代理、DTD/XSD 与 `mapper/*.xml`。
- `SagaMybatisMapper` 及应用包内使用 MyBatis 注解的 Mapper JDK proxy，并递归登记 SQL provider、参数类型和
  返回类型；JDK 容器不会因此被无差别开放反射。
- hosted Feature 在 native-image 参数解析后将 gRPC shaded Netty channel 延迟到运行期初始化，使 transport
  的 logger 与平台能力基于真实运行环境建立，并覆盖上游依赖的构建期默认值。

Protobuf/gRPC 生成类型依赖其上游 reachability metadata；本适配器不会把所有传递依赖无差别开放反射。

## 安全与顺序边界

本项目只保留反射可达性，不改变 Saga 的权威与顺序不变量：participant 仍须在本地事务中提交 Inbox、业务
效果和 result Outbox；capability lease、消息认证、取消屏障和 fencing 仍由 `nasa-saga` 与宿主配置负责。
生成 hints 不代表外部效果天然幂等，也不替代持久化 schema 初始化。

## 失败语义与观测

- 无安全应用包、公共顶级根、类资源不可读或依赖不完整会终止 AOT。
- `@Saga` 类型未实现 `SagaStep`，或托管类型没有无参构造器，会在构建期给出精确类型名。
- 可通过 AOT 异常与 `target/spring-aot/` 生成配置核对 participant、proxy 和资源。

## 不解决的问题

本项目不实现 Orchestrator、participant transport、数据库 schema 迁移或 GraalVM 工具链，也不验证部署时
证书、数据库方言、endpoint 与 Rust 端合同。应用自定义 payload class 的动态反序列化仍需额外 hints。

## 构建与发布

```bash
mvn clean verify
```

参见 [CONTRIBUTING.md](CONTRIBUTING.md)、[SECURITY.md](SECURITY.md) 与 [RELEASE.md](RELEASE.md)。
本项目采用 Apache-2.0 OR MIT 双许可证。
