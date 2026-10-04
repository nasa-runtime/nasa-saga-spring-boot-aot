# 贡献指南

## 环境与检查

需要 JDK 21+、Maven 3.6.3+，以及可解析的 `nasa-saga` 与 core AOT 依赖。

```bash
mvn clean verify
```

## 变更原则

- 本仓库只维护 `nasa-saga` 的 Spring AOT 适配，不复制 participant 或传输实现。
- 新提示必须对应 `@Saga` 合同、JSON codec、持久化资源或可选 MyBatis 集成的真实动态路径。
- 扫描与提示生成不能启动 participant、获取租约、访问数据库或连接网络。
- MyBatis 等可选集成必须在依赖不存在时保持可加载。
- `managed=true` 构造器和阶段方法变化应有精确的 `RuntimeHintsPredicates` 检查。
- 源码注释使用中文说明业务作用、参数、返回与关键边界；行为变化同步维护中英文 README。

提交前核对测试、Javadoc、依赖树、sources jar、最终 jar 与 SQL 资源。协议或顺序边界变化必须先在
`nasa-saga` 中定义，本适配器只表达 reachability。
