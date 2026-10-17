# 观鱼记

观鱼记是一款面向观赏鱼爱好者的 Android 本地记录应用。它用于管理鱼缸与鱼只档案，并把成长、喂食、水质、换水和养护任务记录在设备本地，方便查看日常安排与历史统计。

项目当前以单一 Android 应用模块交付，采用 Kotlin、Jetpack Compose 与 Room。仓库未配置业务后端或账号登录流程；主要记录由本地数据库保存，照片复制到应用管理的本地文件中。当前实现及其验收限制见下文。

## 功能状态

### 已实现

- **总览**：展示本地鱼缸、鱼只及养护信息，并提供快速喂食和相关记录入口；空数据提示以实际记录为准。
- **鱼只档案**：创建、编辑和查看鱼只，关联鱼缸、状态、照片及备注；支持成长、喂食和时间线记录入口。
- **鱼缸管理**：管理淡水缸或海水缸、容量与设备等信息，查看鱼缸详情，并记录水质和换水信息。
- **成长记录**：记录体长、体重、时间、照片和备注；缺失测量值保持为空，不以 0 代替。
- **喂食记录**：快速或完整记录喂食，可按全缸或指定多条鱼关联对象，并查看和编辑历史。
- **水质与换水**：保存鱼缸水质参数及其单位和测量信息；记录换水量、可计算的比例及下一次计划。
- **养护计划**：建立一次性或重复任务，支持任务状态管理；可启用本地通知提醒。
- **统计**：按鱼缸和日期范围汇总已有成长、喂食、水质、换水及养护数据。
- **设置与备份**：提供单位/主题/通知等设置，以及本地数据备份的检查、导出和恢复流程。
- **照片管理**：通过系统照片选择器或拍摄流程导入照片，并在应用管理的本地存储中管理照片和缩略图。

### 部分实现或仍有限制

- 自动化测试已覆盖多项业务计算、Room 仓储、迁移、照片和备份逻辑；专项验收报告记录的最新完整设备 runner 为 38 项通过、1 项失败、2 项未执行。失败与 Compose 导航 UI 测试在特定设备上挂起/崩溃有关，因此不能视为整套设备测试通过。
- 计划任务和通知调度代码已实现，应用也注册了开机、时区、系统时间和应用更新后的重新调度接收器。提醒在强停、设备重启、厂商省电策略等真实场景中的端到端投递仍待设备验收。
- 专项报告指出，鱼只/鱼缸页面 CRUD、筛选、表单返回保护、不同屏幕和系统字号等仍有手工验收项；水质表单的淡水/海水交互也需要设备复核。
- 专项报告还记录了一次默认包 instrumentation 清理导致正式应用被卸载的风险。APK 后续已重新安装，但报告无法确认卸载前的应用私有数据是否保留。该报告不是对当前使用者设备数据状态的判断。

### 设计文档中的范围，尚不能全部视为已交付

产品设计文档还描述了诸如提醒中心、日历视图、详细鱼只筛选排序、完整转缸历史交互等目标。虽然部分数据结构或相关代码已存在，README 不将设计文档中的需求自动认定为完整可用功能。请以当前界面实现和后续验收结果为准。项目没有可据以承诺发布日期的公开路线图。

## 截图

仓库中目前没有确认属于实际运行应用的截图。`docs/产品设计/UI设计总览图.png` 和其他产品设计图片是设计参考图，不代表当前应用实机界面，因此这里不将其作为应用截图展示。

## 技术栈

| 领域 | 当前配置 | 用途 |
| --- | --- | --- |
| 语言 | Kotlin 2.2.20 | Android 应用与测试代码 |
| UI | Jetpack Compose、Material 3、Compose BOM 2025.06.01 | 声明式界面与主题组件 |
| 架构组件 | AndroidX Lifecycle ViewModel、Lifecycle Compose | 页面状态与生命周期感知的状态收集 |
| 导航 | Navigation Compose 2.9.1 | 页面与底部主导航 |
| 本地数据库 | Room 2.7.2、SQLite、KSP | 实体、数据访问、事务和数据库迁移 |
| 异步处理 | Kotlin Coroutines、Flow（Room KTX） | 异步数据操作与持续数据流 |
| 图片 | Coil Compose 2.7.0、AndroidX ExifInterface 1.4.1 | 图片加载与图像方向处理 |
| 构建 | Android Gradle Plugin 8.13.2、Gradle Wrapper 8.14.4 | Android 构建与依赖配置 |
| 测试 | JUnit 4、AndroidX Test、Room Testing、Compose UI Test、Espresso | 单元、仪器、数据库和界面测试 |

版本信息来自根目录 `build.gradle.kts`、`app/build.gradle.kts` 与 `gradle/wrapper/gradle-wrapper.properties`。项目当前未使用 `gradle/libs.versions.toml`。

## 架构与目录

项目只有一个 Gradle 子模块 `:app`。应用代码按导航、功能、数据和共享 UI 组件组织，页面通过 ViewModel 与 Repository 访问本地数据。

```text
.
├── app/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/guanyji/app/
│   │   │   │   ├── core/designsystem/     # 通用 Compose 组件与未保存更改处理
│   │   │   │   ├── data/                  # Room、本地 Repository、照片和备份
│   │   │   │   ├── feature/               # 总览、鱼只、鱼缸、喂食、养护、统计、个人中心
│   │   │   │   ├── navigation/            # 主导航与页面路由
│   │   │   │   └── ui/theme/              # Compose 主题和颜色
│   │   │   └── res/                       # 应用图标、照片路径及界面图片资源
│   │   ├── test/                          # 本地 JVM 单元测试
│   │   └── androidTest/                   # Android 仪器测试
│   └── schemas/                           # Room schema（如项目中生成）
├── docs/
│   ├── 产品设计/                           # 产品设计与专项回归报告
│   └── 验收/                               # 验收矩阵
├── gradle/wrapper/                        # Gradle Wrapper 配置
├── build.gradle.kts
└── settings.gradle.kts
```

关键入口：

- `app/src/main/java/com/guanyji/app/MainActivity.kt`：应用 Activity、主题偏好和提醒同步入口。
- `app/src/main/java/com/guanyji/app/navigation/FishApp.kt`：Compose 导航图和页面路由。
- `app/src/main/java/com/guanyji/app/data/local/Entities.kt`：Room 数据实体。
- `app/src/main/java/com/guanyji/app/data/local/FishDatabase.kt`：DAO、数据库定义与显式迁移。
- `app/src/main/java/com/guanyji/app/data/repository/LocalRepository.kt`：本地数据访问和业务校验。
- `app/src/main/java/com/guanyji/app/data/backup/LocalBackupManager.kt`：本地备份导入导出。
- `app/src/main/java/com/guanyji/app/feature/`：各业务页面、ViewModel 和计算逻辑。

## 数据存储与隐私

鱼缸、鱼只、成长、喂食、水质、换水、养护任务、养护记录和设置通过 Room/SQLite 保存在设备本地。照片由应用复制并保存到本地应用管理的文件区域，数据库保存照片引用。项目配置中未发现业务后端或账号登录依赖；Android Manifest 未声明 `INTERNET` 权限。备份功能由用户通过应用流程导出或恢复本地备份文件。

这些说明仅描述当前仓库配置和代码，不构成“绝对安全”或适用于所有系统备份行为的隐私保证。卸载应用、设备故障或用户恢复操作可能影响本地数据；重要记录应使用应用内备份并自行妥善保存导出的文件。

## 环境要求

根据当前配置确认：

- JDK 17（Java/Kotlin 编译目标为 17）。
- Gradle Wrapper 8.14.4；首次使用时 Wrapper 需要能取得对应 Gradle 发行包。
- Android Gradle Plugin 8.13.2、Kotlin 2.2.20。
- Android SDK Platform 35（`compileSdk` 和 `targetSdk` 均为 35）。
- Android 8.0 / API 26 或更高版本（`minSdk = 26`）。
- 运行仪器测试还需要已启动且可访问的 Android 模拟器或连接设备。

项目没有提供 `local.properties`。请在本机 Android Studio SDK Manager 安装 Android SDK Platform 35，并由 Android Studio 配置本机 SDK 路径。

## 安装与运行

1. 使用 Android Studio 打开仓库根目录 `FishApp`。
2. 安装并选择 JDK 17，等待 Gradle Sync 完成；确保 SDK Platform 35 已安装。
3. 连接 API 26 或更高版本的设备，或启动相应模拟器。
4. 在 Android Studio 运行 `app` 配置，或在 Windows PowerShell 中执行：

```powershell
.\gradlew.bat assembleDebug
```

构建成功后，Debug APK 通常位于 `app\build\outputs\apk\debug\app-debug.apk`。可通过 Android Studio 的 Run/安装功能安装，也可在已连接设备并启用 ADB 的情况下执行：

```powershell
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

本仓库没有提供正式发布版本或 APK 下载地址。仓库中出现的 `app/build/outputs` 文件是本地构建产物，不等同于项目发布。

## 开发与测试

在 Windows PowerShell 的仓库根目录运行：

```powershell
# 编译 Debug APK
.\gradlew.bat assembleDebug

# 运行本地 JVM 单元测试
.\gradlew.bat testDebugUnitTest

# 执行 Android Lint
.\gradlew.bat lintDebug

# 编译 Android 仪器测试代码
.\gradlew.bat compileDebugAndroidTestKotlin

# 在已连接的模拟器或设备运行仪器测试
.\gradlew.bat connectedDebugAndroidTest
```

`testDebugUnitTest` 运行 `app/src/test/` 中不依赖设备的测试；`connectedDebugAndroidTest` 运行 `app/src/androidTest/` 中需要 Android 环境的数据库、Repository 和界面测试。设备测试可能受设备、Android Test Orchestrator/UTP 清理行为和 Compose UI 自动化环境影响。专项回归报告为避免测试清理正式包，使用了隔离 applicationId：

```powershell
.\gradlew.bat connectedDebugAndroidTest "-PacceptanceApplicationId=com.guanyji.app.qa"
```

该属性在 `app/build.gradle.kts` 中定义，用于隔离验收测试包。若在带有真实用户数据的设备上做仪器测试，应先阅读专项验收报告中的数据隔离风险并使用隔离包名。

### 已有验证记录

`docs/产品设计/第一阶段专项回归验收报告.md` 记录了一次 2026-10-15 的验证：`testDebugUnitTest` 的 27 项通过，`lintDebug` 与 `assembleDebug` 成功；完整设备 runner 声明 41 项，最终 38 项通过、1 项失败、2 项未执行。因此设备自动化整体状态为部分通过，而不是全部通过。以上是仓库已有报告中的结果，不代表本次 README 修改后重新运行的结果。详细设备、数据风险和未完成手工验收项请阅读该报告。

## 项目文档

- [第一阶段本地版 App 设计文档](docs/产品设计/第一阶段本地版App设计文档.md)：产品范围、页面交互、数据模型及验收要求。
- [UI 设计总览图](docs/产品设计/UI设计总览图.png)：设计方向参考图，不是当前应用截图。
- [第一阶段专项回归验收报告](docs/产品设计/第一阶段专项回归验收报告.md)：已执行的构建、测试、缺陷回归及剩余风险。
- [全项目实现度审计与下一阶段计划](docs/验收/全项目实现度审计与下一阶段计划.md)：当前实现度、已知风险与后续计划。
- [未提交改动代码审计报告](docs/验收/未提交改动代码审计报告.md)：卡片视觉改动的审计发现与修复记录。

## 路线图

仓库包含第一阶段设计文档和验收报告，但没有独立、可靠的后续版本路线图。设计文档中尚未完全验收的需求不能据此视为承诺的开发计划；后续优先级与交付时间尚未指定。

## 贡献

目前仓库未发现独立贡献指南或已配置的贡献自动化流程。提交问题或建议时，请提供清晰的复现步骤、设备/API 版本和预期结果；代码修改建议可通过项目托管平台的 Issue 或 Pull Request 提交（仓库未在当前配置中声明托管地址）。提交前建议运行与改动相关的单元测试、Lint；涉及数据库时应补充迁移与数据保留测试，并遵守现有非破坏性迁移约束。

## 许可证

仓库根目录当前没有 `LICENSE` 文件，项目许可证尚未指定。使用、分发或贡献代码前，请向项目维护者确认适用条款。
