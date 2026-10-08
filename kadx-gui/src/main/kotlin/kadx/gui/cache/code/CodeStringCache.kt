package kadx.gui.cache.code

import kadx.api.ICodeCache
import kadx.api.ICodeInfo
import kadx.api.impl.DelegateCodeCache
import kadx.gui.utils.ExplicitGc
import kadx.gui.utils.UiUtils
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
import java.util.LinkedHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 代码字符串缓存（用于加速全局搜索）。
 *
 * **做什么**：在真实缓存 [DelegateCodeCache.backCache] 之前再加一层 `Map<String, String>`
 * 字符串缓存，避免每次搜索都从磁盘/内存重新读取 [ICodeInfo] 并拼接字符串。
 *
 * **容量与淘汰（S5-4）**：原实现是**无界** `ConcurrentHashMap`，只靠「低内存时整体清空」兜底。
 * 但那个兜底有两个问题：(1) 微信规模下全量源码字符串是 GB 级，清空是「全有或全无」；
 * (2) 它在搜索期间**根本不会触发** —— 内存检查挂在 `changes.debounce(3s)` 上，而搜索逐类
 * 调用 `getCode` 会不停发变更，debounce 被反复重置，永远等不到 3 秒静默。
 * 现在改为**按访问顺序淘汰最久未用的条目**（容量 [CACHE_SIZE]），低内存整体清空仅作最后手段。
 *
 * **淘汰不丢数据**：真正的代码在 [backCache]（磁盘缓存）里，被淘汰后再取会回源。
 *
 * **线程模型（N1c 协程化）**：内存检查在 [scope]（`Dispatchers.Default`）上执行，
 * 变更通知通过线程安全的 [MutableSharedFlow] 发布；[close] 取消 [scope]。
 * 字符串缓存用 `synchronized` 保护（`LinkedHashMap` 的访问序 LRU 需要互斥）。
 */
@OptIn(FlowPreview::class)
class CodeStringCache(backCache: ICodeCache) : DelegateCodeCache(backCache) {

	/** LRU 字符串缓存：`accessOrder = true` + [removeEldestEntry] 实现容量上限。 */
	private val codeCache: MutableMap<String, String> =
		object : LinkedHashMap<String, String>(CACHE_SIZE, 0.75f, true) {
			override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > CACHE_SIZE
		}

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
						synchronized(codeCache) { codeCache.clear() }
						ExplicitGc.run("low memory: code string cache cleared")
					}
				}
		}
	}

	override fun getCode(clsFullName: String): String? {
		changes.tryEmit(Unit)
		synchronized(codeCache) {
			val code = codeCache[clsFullName]
			if (code != null) {
				return code
			}
		}
		val backCode = backCache.getCode(clsFullName)
		if (backCode != null) {
			synchronized(codeCache) { codeCache[clsFullName] = backCode }
		}
		return backCode
	}

	override fun get(clsFullName: String): ICodeInfo {
		changes.tryEmit(Unit)
		return super.get(clsFullName)
	}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		changes.tryEmit(Unit)
		synchronized(codeCache) { codeCache[clsFullName] = codeInfo.codeStr }
		backCache.add(clsFullName, codeInfo)
	}

	override fun remove(clsFullName: String) {
		synchronized(codeCache) { codeCache.remove(clsFullName) }
		backCache.remove(clsFullName)
	}

	@Throws(IOException::class)
	override fun close() {
		try {
			backCache.close()
		} finally {
			synchronized(codeCache) { codeCache.clear() }
			scope.cancel()
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CodeStringCache::class.java)

		/** 变更后检查内存的延迟（毫秒）。 */
		private const val CACHE_CHECK_DELAY_MS = 3000L

		/**
		 * 字符串缓存条目上限。
		 *
		 * 单个类的源码字符串通常在几 KB 量级，512 条约几 MB —— 对搜索加速够用，
		 * 又不会像原来那样随全量搜索无限增长。
		 */
		internal const val CACHE_SIZE = 512
	}
}
