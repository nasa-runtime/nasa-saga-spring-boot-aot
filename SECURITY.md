# 安全策略

## 支持范围

当前发布线接收安全更新。底层 `nasa-saga`、Spring Boot、Jackson、gRPC、protobuf 与 MyBatis 的安全通告
由各自项目负责；使用者应保持目标库与适配器版本匹配。

## 私密报告

请通过 GitHub 仓库 **Security → Report a vulnerability** 私密提交。不要在公开 issue、讨论区或拉取请求中
披露尚未协调的信息。报告请包含受影响版本、部署形态、复现场景、影响与缓解建议，不要附带密钥、证书或业务数据。

## 边界

本项目只生成 reachability hints，不授予 capability、不执行 participant，也不保证业务效果幂等。消息认证、
租约、fencing、数据库事务与证书配置仍属于 `nasa-saga` 和宿主应用的安全边界。
