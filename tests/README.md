# tests/ —— 真实开源项目反编译验证工具

用真实开源 Android 项目验证本仓库 kadx 的反编译质量。**所有产物都在仓库内，不使用 `/tmp`。**

当前覆盖 6 个开源项目（release 变体，R8 minify）：
`FossifyOrg/Calculator`、`FossifyOrg/Notes`、`android/architecture-samples`、
`AntennaPod/AntennaPod`、`iSoron/uhabits`、`gsantner/markor`。

详细结果见仓库根目录的 [`OSS_DECOMPILE_REPORT.md`](../OSS_DECOMPILE_REPORT.md)。

## 自动化回归测试（推荐）

仓库内置两类真实 APK 回归测试（默认跳过、不参与 `build`/`check`）：

```bash
# 先构建 kadx CLI
./gradlew :kadx-cli:installDist

# 跑全部真实 APK 回归（错误基线看门狗 + 已知慢类限时测试）
KADX_REAL_APKS=$PWD/tests/apks ./gradlew :kadx-cli:realApkTest
```

1. `RealApkDecompileTest` —— 全量反编译 APK，断言 `getErrorsCount()` 不超过
   `kadx-cli/src/test/resources/real-apk-baseline.properties` 中的基线（"错误数不得上升"看门狗）。
   修复问题后应**下调**基线值。
2. `RealApkSingleClassTest` —— 对曾导致挂死/崩溃的已知类做**限时单类反编译**
   （当前覆盖 `BasicTextFieldKt` 的 ProcessVariables O(n²) 挂死回归、`em1` 的 ModVisitor
   codeVar 崩溃回归）。超时或错误超标即失败。

两个测试都需要 `KADX_REAL_APKS`（或 `-PkadxRealApks=`）指向含真实 `*.apk` 的目录；
真实 APK 不入库。

## 用法（手动）

```bash
# 1) 构建 kadx CLI
./gradlew :kadx-cli:installDist

# 2) 克隆被测项目（示例）
mkdir -p tests/oss && cd tests/oss
git clone --depth 1 https://github.com/FossifyOrg/Calculator.git
cd -

# 3) 构建 APK（自动写 local.properties，使用 tests/work 作为 GRADLE_USER_HOME、tests/tmp 作为 TMPDIR）
#    release 变体（R8 minify）：
bash tests/build-apk.sh Calculator release assembleFossRelease
bash tests/build-apk.sh markor release assembleFlavorDefaultRelease
bash tests/build-apk.sh AntennaPod release :app:assembleFreeRelease \
    -PreleaseStoreFile=$PWD/tests/work/keystore/oss-test.keystore \
    -PreleaseStorePassword=oss-test-pass -PreleaseKeyAlias=oss-test -PreleaseKeyPassword=oss-test-pass
#    debug 变体：
bash tests/build-apk.sh Calculator debug assembleFossDebug

# 4) 反编译 + 统计错误
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/tests/tmp"
kadx-cli/build/install/kadx/bin/kadx -d tests/out/Calc-foss-release --show-bad-code \
    tests/apks/Calculator-release-calculator-10-foss-release.apk > tests/work/logs/dec.log 2>&1
grep -rl "KADX ERROR" tests/out/Calc-foss-release/sources | wc -l

# 5) 定位卡点类（卡死时最后一行即卡点类）
kadx-cli/build/install/kadx/bin/kadx --log-level debug --threads-count 4 -d tests/out/x <apk> 2>&1 | grep "Decompiling class:" | tail -1
```

## 目录

| 目录 | 内容 | 是否入库 |
|------|------|----------|
| `oss/` | 克隆的开源项目 | 忽略 |
| `work/` | Gradle user home、日志、上游对照源码/二进制 | 忽略 |
| `tmp/` | 临时目录（替代 `/tmp`） | 忽略 |
| `apks/` | 构建出的 APK | 忽略 |
| `out/` | 反编译输出 | 忽略 |
| `build-apk.sh` | 构建脚本（支持 debug/release 变体与附加 gradle 任务/参数） | **入库** |

## 环境要求

- JDK 11+（本机 21）
- Android SDK（`ANDROID_HOME` 或 `~/Android/Sdk`）
- 网络（下载 Gradle 发行版与依赖）
