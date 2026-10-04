package io.github.nasaruntime.saga.aot;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

/**
 * 业务作用：在 native-image 参数解析完成后约束 gRPC shaded Netty 的初始化时机，避免构建主机状态进入运行镜像。
 */
public final class NasaSagaNativeFeature implements Feature {

    /**
     * 业务作用：将 shaded Netty transport 延迟到运行期初始化，使日志系统与平台能力基于部署环境建立。
     *
     * @param access native-image setup 阶段访问入口
     * 返回：无返回值；初始化策略写入当前 native-image 构建上下文。
     */
    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeClassInitialization.initializeAtRunTime(
                "io.grpc.netty.shaded.io.netty");
    }
}
