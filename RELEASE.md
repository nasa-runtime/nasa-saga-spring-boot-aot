# Maven Central 发布

## 前置条件

- POM 为非 SNAPSHOT，`nasa-saga` 与 core AOT 版本和中英文 README 一致。
- `nasa-core-spring-boot-aot` 对应版本已经发布到 Maven Central，空本地仓库能够解析该坐标。
- Maven `central` server 已配置 Central token，签名环境已配置 GPG 密钥。
- README、贡献指南、安全策略、双许可证、sources jar 与 javadoc jar 均进入归档。

## 本地验收

```bash
mvn clean verify
mvn package
jar tf target/nasa-saga-spring-boot-aot-1.0.0.jar
jar tf target/nasa-saga-spring-boot-aot-1.0.0-sources.jar
jar tf target/nasa-saga-spring-boot-aot-1.0.0-javadoc.jar
```

从最终 jar 核对 `META-INF/spring/aot.factories`、native-image properties、实现类、README、许可证；确认
SQL pattern、hosted Feature 与可选 MyBatis 行为和源码一致。核对 POM 的坐标、SCM、开发者、许可证和非
SNAPSHOT 依赖。

## 发布顺序

1. 提交并推送目标分支。
2. 等待远端 CI 全绿，核对 tag、分支和远端提交 SHA。
3. 获得维护者对该坐标与提交的明确发布授权。
4. 执行 `mvn -Pcentral-release clean deploy` 并检查 GPG 与 Central 校验。
5. 在 Central Portal 复核后发布 deployment；`autoPublish` 保持关闭。
6. 回读 POM、主 jar、sources、javadoc、README 和校验和，并从空 Maven 项目解析坐标。

不可变坐标若遗漏公开文档或归档内容，应停止验收并使用新的补丁版本，不得把本地内容表述为已公开。未取得
第 3 步授权时不得上传。
