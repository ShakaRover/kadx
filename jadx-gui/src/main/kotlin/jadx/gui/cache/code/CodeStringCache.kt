package jadx.gui.cache.code

import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.processors.PublishProcessor
import jadx.api.ICodeCache
import jadx.api.ICodeInfo
import jadx.api.impl.DelegateCodeCache
import jadx.gui.utils.UiUtils
import org.reactivestreams.Subscriber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 代码字符串缓存（用于加速全局搜索）。
 *
 * **做什么**：在真实缓存 [DelegateCodeCache.backCache] 之前再加一层 `Map<String, String>`
 * 字符串缓存，避免每次搜索都从磁盘/内存重新读取 [ICodeInfo] 并拼接字符串。
 *
 * **内存保护**：通过 RxJava 的 `debounce` 操作符，在缓存发生变化后延迟 3 秒检查一次
 * 可用内存；若内存不足则清空字符串缓存并触发 GC。使用 debounce 是为了在应用空闲时
 * 减少后台检查频率。
 *
 * **线程模型（阶段 5.1 保持不变）**：仍使用 RxJava 的 `PublishProcessor` + `debounce`，
 * 未改写为协程。
 */
class CodeStringCache(backCache: ICodeCache) : DelegateCodeCache(backCache) {

	private val codeCache: MutableMap<String, String> = ConcurrentHashMap()
	private val subscriber: Subscriber<Boolean>
	private val disposable: Disposable

	init {
		// 仅在有变更（经 debounce 合并）时检查内存，避免空闲时频繁触发后台检查
		val processor = PublishProcessor.create<Boolean>()
		subscriber = processor
		disposable = processor.debounce(3, TimeUnit.SECONDS)
			.map { UiUtils.isFreeMemoryAvailable() }
			.filter { v -> !v }
			.subscribe {
				LOG.warn("Free memory is low! Reset code strings cache. Cache size {}", codeCache.size)
				codeCache.clear()
				System.gc()
			}
	}

	override fun getCode(clsFullName: String): String? {
		subscriber.onNext(true)
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
		subscriber.onNext(true)
		return super.get(clsFullName)
	}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		subscriber.onNext(true)
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
			subscriber.onComplete()
			disposable.dispose()
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CodeStringCache::class.java)
	}
}
