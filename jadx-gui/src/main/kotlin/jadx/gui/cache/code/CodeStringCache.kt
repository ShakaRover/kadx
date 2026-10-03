package jadx.gui.cache.code

import jadx.api.ICodeCache
import jadx.api.ICodeInfo
import jadx.api.impl.DelegateCodeCache
import jadx.gui.utils.UiUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 代码字符串缓存（用于加速全局搜索）。
 *
 * **做什么**：在真实缓存 [DelegateCodeCache.backCache] 之前再加一层 `Map<String, String>`
 * 字符串缓存，避免每次搜索都从磁盘/内存重新读取 [ICodeInfo] 并拼接字符串。
 *
 * **内存保护**：通过 Flow 的 `debounce` 操作符，在缓存发生变化后延迟 3 秒检查一次
 * 可用内存；若内存不足则清空字符串缓存并触发 GC。使用 debounce 是为了在应用空闲时
 * 减少后台检查频率。
 *
 * **线程模型（N1c 协程化）**：内存检查在 [scope]（`Dispatchers.Default`）上执行，
 * 变更通知通过线程安全的 [MutableSharedFlow] 发布；[close] 取消 [scope]。
 */
@OptIn(FlowPreview::class)
class CodeStringCache(backCache: ICodeCache) : DelegateCodeCache(backCache) {

	private val codeCache: MutableMap<String, String> = ConcurrentHashMap()

	/** 协程作用域：随 [close] 取消，禁止使用 GlobalScope。 */
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

	init {
		// 仅在有变更（经 debounce 合并）时检查内存，避免空闲时频繁触发后台检查
		scope.launch {
			changes
				.debounce(CACHE_CHECK_DELAY_MS.milliseconds)
				.collect {
					if (!UiUtils.isFreeMemoryAvailable) {
						LOG.warn("Free memory is low! Reset code strings cache. Cache size {}", codeCache.size)
						codeCache.clear()
						System.gc()
					}
				}
		}
	}

	override fun getCode(clsFullName: String): String? {
		changes.tryEmit(Unit)
		val code = codeCache[clsFullName]
		if (code != null) {
			return code
		}
		val backCode = backCache.getCode(clsFullName)
		if (backCode != null) {
			codeCache[clsFullName] = backCode
		}
		return backCode
	}

	override fun get(clsFullName: String): ICodeInfo {
		changes.tryEmit(Unit)
		return super.get(clsFullName)
	}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		changes.tryEmit(Unit)
		codeCache[clsFullName] = codeInfo.getCodeStr()
		backCache.add(clsFullName, codeInfo)
	}

	override fun remove(clsFullName: String) {
		codeCache.remove(clsFullName)
		backCache.remove(clsFullName)
	}

	@Throws(IOException::class)
	override fun close() {
		try {
			backCache.close()
		} finally {
			codeCache.clear()
			scope.cancel()
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CodeStringCache::class.java)

		/** 变更后检查内存的延迟（毫秒）。 */
		private const val CACHE_CHECK_DELAY_MS = 3000L
	}
}
