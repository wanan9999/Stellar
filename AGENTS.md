# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## 项目概述

Stellar 是 Shizuku 的深度定制分支，通过 ADB 或 Root 权限为应用提供系统级 API 框架。100% Kotlin，内置 Shizuku 兼容层。

## 构建命令

```bash
# 构建 debug APK
./gradlew :manager:assembleDebug

# 构建 release APK（需要 signing.properties）
./gradlew :manager:assembleRelease

# 仅构建 server 模块
./gradlew :server:assemble

# 仅构建 API 库
./gradlew :api:assemble

# 清理
./gradlew clean
```

Release 产物输出到 `out/apk/`，mapping 文件输出到 `out/mapping/`。

## 签名配置

Release 签名读取根目录 `signing.properties`（不入库），字段：
- `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEYSTORE_ALIAS` / `KEYSTORE_ALIAS_PASSWORD`

缺失时自动回退到 debug 签名。

## 项目架构

### 模块结构

```
manager/     → Android 应用（管理器 UI），applicationId: roro.stellar.manager
server/      → 特权服务端逻辑（运行在 ADB/Root 进程中）
api/         → 客户端 SDK（供第三方应用集成）
  ├── aidl/      → Stellar AIDL 接口（IStellarService, IRemoteProcess, IRemotePtyProcess 等）
  ├── api/       → 客户端 API 入口（Stellar.kt, StellarHelper.kt）
  ├── provider/  → StellarProvider（ContentProvider，接收服务端 Binder）
  ├── shared/    → 共享常量（StellarApiConstants）
  ├── userservice/ → UserService 框架
  └── demo/      → API 示例应用
shizuku/     → Shizuku 兼容层
  ├── aidl/      → Shizuku AIDL 接口（IShizukuService 等）
  └── api/       → ShizukuCompat, ShizukuProvider
```

### 核心数据流

1. **服务启动**：`manager/startup/` → 通过 Root（`Chid` 降权）或 ADB 启动 `server`
2. **Binder 分发**：`server/binder/BinderDistributor` + `BinderSender` → 通过 ContentProvider 将 Binder 发送到客户端及管理器
3. **客户端连接**：`api/provider/StellarProvider` 接收 Binder → `api/api/Stellar.kt` 封装调用
4. **Shizuku 兼容**：`server/shizuku/ShizukuServiceIntercept` 拦截 Shizuku API 调用并转发到 Stellar 服务

### Manager 应用架构

- UI：Jetpack Compose + Material Design 3，导航在 `ui/navigation/`
- 底部导航页面（4个）：Home（服务状态）、Apps（授权管理）、Terminal（终端）、Settings（设置）
- 二级页面：`ui/features/manager/` 下包含 Logs（服务日志）、Starter（启动器）、ManagerActivity
- ADB 无线配对：`adb/` 包实现完整的 ADB 协议栈（配对、mDNS 发现、连接）
- 数据层：`db/`（Room 数据库）、`model/`、`domain/`
- 授权管理：`authorization/`（RequestPermissionActivity、AuthorizationManager）
- 开机自启：广播/回调入口为 `receiver/BootCompleteReceiver`、`receiver/StellarReceiver`、`receiver/StellarReceiverStarter`；ADB 自启动核心在 `startup/worker/AdbStartWorker`；通知交互在 `startup/notification/BootStartNotifications`
- 开机启动模式：`StellarSettings.BootMode` 四选一（NONE / BROADCAST / ACCESSIBILITY / SCRIPT）
- 当前广播自启动仅监听 `BOOT_COMPLETED`，不再依赖 `LOCKED_BOOT_COMPLETED`
- 后台拉起路径依赖 `StellarSettings.LaunchMethod`，会按上次成功启动方式在 Root / ADB 之间选择
- `LaunchMethod.UNKNOWN` 当前保留兼容回退，优先尝试 Root，失败后再尝试 ADB
- JNI 层：`src/main/jni/` 包含 starter（服务启动器）、chid（降权工具）、adb_pairing、rish 等 native 组件
- 多语言：英语（默认）、简体中文

### Server 核心组件

经过模块化拆分，server 包结构如下：

- `StellarService`：AIDL Stub 主体，组装所有子系统
- `service/StellarServiceCore`：聚合各功能管理器（权限/进程/日志/系统属性/用户服务）
- `communication/`：通信层
  - `StellarCommunicationBridge`：统一请求分发（调用 StellarServiceCore 各子管理器）
  - `PermissionEnforcer`：调用级权限校验（管理器/自身/客户端三级判断）
  - `CallerContext`：封装调用者 uid/pid
- `bootstrap/ServerBootstrap`：服务引导（等待系统服务就绪、获取管理器信息）
- `binder/BinderDistributor`：Binder 分发到所有客户端和管理器
- `service/permission/`：PermissionManager、PermissionChecker、PermissionConfirmation、PermissionRequester
- `service/process/ProcessManager`：进程管理
- `service/log/LogManager`：日志管理（支持持久化）
- `service/system/SystemPropertyManager`：系统属性管理
- `service/info/`：ServiceInfoProvider、VersionProvider
- `service/userservice/UserServiceCoordinator`：用户服务协调
- `userservice/UserServiceManager` / `UserServiceStarter`：用户服务生命周期
- `shizuku/ShizukuServiceIntercept`：Shizuku 兼容层服务端实现
- `ClientManager` / `ClientRecord`：客户端连接管理
- `ConfigManager`：配置持久化（权限标记、开关等）
- `grant/ManagerGrantHelper`：管理器权限授予（WRITE_SECURE_SETTINGS、无障碍服务）
- `api/`：RemoteProcessHolder、RemotePtyProcessHolder、IContentProviderUtils
- `monitor/PackageMonitor`：包变更监听
- `ext/FollowStellarStartupExt`：跟随服务启动命令执行

## 技术栈

- compileSdk 36, minSdk 24, targetSdk 36, JVM 21
- AGP 8.10.1, Kotlin 2.2.0, Compose Compiler 2.1.21, KSP 2.2.0-2.0.2
- Compose BOM 2026.01.01, Navigation Compose 2.9.7
- Room 2.7.1（manager 本地数据库）
- NDK 29 + CMake 3.22.1+（JNI 组件）
- 版本目录：各模块独立 `*.versions.toml`（manager/server/api/demo），根目录 `gradle/libs.versions.toml` 管理 hidden-api/refine

## 版本号规则

`versionCode`: XYYZZZ（如 100129 = 1.0.129）
`versionName`: X.Y.Z[-suffix]（suffix: dev/alpha/beta/rc）
定义在根 `build.gradle` 的 `ext` 块中。

## CI/CD

- `.github/workflows/manager-ci.yml`：Manager 构建 CI
- `.github/workflows/publish.yml`：发布流程
- `.github/workflows/sync-release-gitee.yml`：同步 Release 到 Gitee

## 注意事项

- `local.properties` 中设置 `api.useLocal=true` + `api.dir=路径` 可切换到本地 API 源码
- JitPack 环境下（`JITPACK=true`）不包含 manager 和 server 模块
- Shizuku 兼容层自动拒绝来自 Shizuku Manager 的请求
- 更新检查使用 GitHub API（国内用户走 Gitee 渠道）
