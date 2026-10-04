# tests/ —— 真实开源项目反编译验证工具

用真实开源 Android 项目验证本仓库 jadx 的反编译质量。**所有产物都在仓库内，不使用 `/tmp`。**

详细结果见仓库根目录的 [`OSS_DECOMPILE_REPORT.md`](../OSS_DECOMPILE_REPORT.md)。

## 自动化回归测试（推荐）

仓库内置了针对真实 APK 的反编译回归测试 `RealApkDecompileTest`
（`jadx-cli/src/test/kotlin/jadx/cli/RealApkDecompileTest.kt`），用于「错误数不得上升」看门狗：

```bash
# 先构建 APK（见上）
JADX_REAL_APKS=$PWD/tests/apks ./gradlew :jadx-cli:realApkTest
# 或
./gradlew :jadx-cli:realApkTest -PjadxRealApks=$PWD/tests/apks
```

- 只检查 `jadx-cli/src/test/resources/real-apk-baseline.properties` 中有条目的 APK（键=文件名，值=允许的最大错误数）。
- 修复问题后应**下调**基线值。
- 该任务默认不参与 `build`/`check`（真实 APK 大、需外部目录），且已从普通 `test` 任务中排除。

## 用法（手动）

```bash
# 1) 构建 jadx CLI
./gradlew :jadx-cli:installDist

# 2) 克隆被测项目（示例）
mkdir -p tests/oss && cd tests/oss
git clone --depth 1 https://github.com/FossifyOrg/Calculator.git
cd -

# 3) 构建 APK（自动写 local.properties，使用 tests/work 作为 GRADLE_USER_HOME、tests/tmp 作为 TMPDIR）
bash tests/build-apk.sh Calculator

# 4) 反编译 + 统计错误
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/tests/tmp"
jadx-cli/build/install/jadx/bin/jadx -d tests/out/Calculator --show-bad-code \
    tests/apks/Calculator-calculator-10-foss-debug.apk > tests/work/logs/Calculator.log 2>&1
grep -rl "JADX ERROR" tests/out/Calculator/sources | wc -l
```

## 目录

| 目录 | 内容 | 是否入库 |
|------|------|----------|
| `oss/` | 克隆的开源项目 | 忽略 |
| `work/` | Gradle user home、日志 | 忽略 |
| `tmp/` | 临时目录（替代 `/tmp`） | 忽略 |
| `apks/` | 构建出的 APK | 忽略 |
| `out/` | 反编译输出 | 忽略 |
| `build-apk.sh` | 构建脚本 | **入库** |

## 环境要求

- JDK 11+（本机 21）
- Android SDK（`ANDROID_HOME` 或 `~/Android/Sdk`）
- 网络（下载 Gradle 发行版与依赖）
