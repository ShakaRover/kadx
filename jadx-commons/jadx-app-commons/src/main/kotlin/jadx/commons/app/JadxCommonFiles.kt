package jadx.commons.app

import dev.dirs.ProjectDirectories
import dev.dirs.impl.Windows
import dev.dirs.impl.WindowsPowerShell
import dev.dirs.jni.WindowsJni
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * 公共文件/目录管理：配置目录与缓存目录。
 *
 * 与原 Java 版一致，两个目录在类加载时（companion 对象的 init 块）通过内部类 DirsLoader 一次性初始化；
 * get 方法加了 @JvmStatic，Java 代码仍可按 JadxCommonFiles.getConfigDir() / getCacheDir() 静态调用。
 */
class JadxCommonFiles {
	companion object {
		val LOG: Logger = LoggerFactory.getLogger(JadxCommonFiles::class.java)

		// 配置目录：存放用户的配置文件（对应原 Java 的 private static final Path CONFIG_DIR）
		private lateinit var CONFIG_DIR: Path

		// 缓存目录：存放 jadx 运行时产生的各类缓存
		private lateinit var CACHE_DIR: Path

		/** 类加载时初始化目录，等价于原来 Java 的 static {} 代码块 */
		init {
			val loader = DirsLoader()
			CONFIG_DIR = loader.configDir
			CACHE_DIR = loader.cacheDir
		}

		@JvmStatic
		fun getConfigDir(): Path = CONFIG_DIR

		@JvmStatic
		fun getCacheDir(): Path = CACHE_DIR

		/**
		 * 返回 Windows 下的目录实现：JNI、Foreign API 或 PowerShell 版本。
		 */
		fun getWinDirs(): Windows {
			var impl: Windows = Windows.getDefaultSupplier().get() // dev.dirs 提供的默认（优先 Foreign/JNI）实现
			if (impl is WindowsPowerShell && JadxSystemInfo.IS_AMD64) {
				// JNI 库只编译了 x86-64 版本，在 amd64 上改用 JNI 版本性能更好
				impl = WindowsJni()
			}
			LOG.debug("Using win dirs implementation: {}", impl.javaClass.simpleName) // 对应 Java 的 impl.getClass().getSimpleName()
			return impl
		}
	}

	/**
	 * 负责加载系统目录（配置/缓存）。对应原 Java 版的 private static final class DirsLoader。
	 *
	 * 支持用环境变量 JADX_CONFIG_DIR / JADX_CACHE_DIR 覆盖默认位置；
	 * dev.dirs 库的查询结果缓存在 [pdRef] 中，避免重复调用系统目录查询（JNI/PowerShell）。
	 */
	class DirsLoader {
		lateinit var configDir: Path

		lateinit var cacheDir: Path

		init {
			try {
				// dev.dirs 库加载结果的缓存容器，loadDirs() 会把结果存进来供下次直接取用
				val pdRef = AtomicReference<ProjectDirectories?>()
				configDir = loadEnvDir("JADX_CONFIG_DIR") { loadDirs(pdRef).configDir }
				cacheDir = loadEnvDir("JADX_CACHE_DIR") { loadDirs(pdRef).cacheDir }
			} catch (e: Exception) {
				throw RuntimeException("Failed to init common directories", e)
			}
		}
	}

}

/**
 * 读取目录：优先使用环境变量覆盖值，否则执行 dirFunc 获取系统默认目录。
 * 目录不存在时自动创建（Files.createDirectories）。
 */
private fun loadEnvDir(envVar: String, dirFunc: () -> String): Path {
	val envDir = JadxCommonEnv.get(envVar, null)
	// 环境变量优先，否则回退到 dev.dirs 提供的系统默认目录
	val dirStr: String = if (envDir != null) envDir else dirFunc()
	val path = Path.of(dirStr).toAbsolutePath() // 统一转为绝对路径
	Files.createDirectories(path) // 目录不存在则创建（已存在时不报错）
	return path
}

/**
 * 通过 dev.dirs 库加载系统目录，结果缓存在 pdRef 中避免重复加载。
 */
private fun loadDirs(pdRef: AtomicReference<ProjectDirectories?>): ProjectDirectories {
	val currentDirs = pdRef.get() ?: run {
		JadxCommonFiles.LOG.debug("Loading system dirs ...")
		val start = System.currentTimeMillis()

		// 使用 dev.dirs 库按 (group, vendor, product) 定位目录，Windows 下用 getWinDirs() 提供实现
		val loadedDirs = ProjectDirectories.from("io.github", "skylot", "jadx") { JadxCommonFiles.getWinDirs() }

		if (JadxCommonFiles.LOG.isDebugEnabled()) {
			JadxCommonFiles.LOG.debug(
				"Loaded system dirs ({}ms): config: {}, cache: {}",
				System.currentTimeMillis() - start, loadedDirs.configDir, loadedDirs.cacheDir)
		}
		pdRef.set(loadedDirs) // 缓存加载结果，之后直接复用不再重复查询系统目录
		return loadedDirs
	}
	return currentDirs
}
