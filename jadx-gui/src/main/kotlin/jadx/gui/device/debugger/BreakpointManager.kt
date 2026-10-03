package jadx.gui.device.debugger

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jadx.core.dex.nodes.ClassNode
import jadx.core.utils.GsonUtils
import jadx.gui.device.debugger.smali.Smali
import jadx.gui.treemodel.JClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Reader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.AbstractMap.SimpleEntry
import java.util.Objects

/**
 * 断点管理器（保存/恢复断点，并转发到调试器）。
 *
 * **做什么**：
 * - 把用户在 smali 代码里设置的断点持久化到 `breakpoints.json`；
 * - 在调试会话中把断点下发给远端 JVM（[DebugController]）；
 * - 维护「断点设置失败」的回调监听（[Listener]）。
 *
 * **为什么保留显式函数与 `FileBreakpoint` 自定义 equals**：这些是跨类协作的公共表面，
 * 原 Java 的 JSON 序列化与身份/值比较语义必须保持不变。
 */
object BreakpointManager {
	private val LOG: Logger = LoggerFactory.getLogger(BreakpointManager::class.java)

	private val GSON: Gson = GsonUtils.buildGson()

	private val TYPE_TOKEN: Type = object : TypeToken<Map<String, MutableList<FileBreakpoint>>>() {}.type

	/** 类全名 -> 该类下的断点列表。 */
	private var bpm: MutableMap<String, MutableList<FileBreakpoint>> = HashMap()

	/** 断点持久化文件路径。 */
	private var savePath: Path? = null

	/** 当前调试控制器。 */
	private var debugController: DebugController? = null

	/** 类全名 -> (类节点, 断点失败监听)。 */
	private var listeners: MutableMap<String, Map.Entry<ClassNode, Listener>> = HashMap()

	/** 退出前保存并清空内存状态。 */
	@JvmStatic
	fun saveAndExit() {
		sync()
		bpm = HashMap()
		savePath = null
		listeners = HashMap()
	}

	/** 从 [baseDir] 目录加载 `breakpoints.json`。 */
	@JvmStatic
	fun init(baseDir: Path?) {
		val saveDir = baseDir ?: Paths.get(".")
		val path = saveDir.resolve("breakpoints.json") // TODO: 移到项目文件或与项目文件同目录
		savePath = path
		if (Files.exists(path)) {
			try {
				Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader: Reader ->
					val parsed: MutableMap<String, MutableList<FileBreakpoint>>? = GSON.fromJson(reader, TYPE_TOKEN)
					bpm = parsed ?: HashMap()
				}
			} catch (e: Exception) {
				LOG.error("Failed to read breakpoints config: {}", path, e)
			}
		}
	}

	/**
	 * 注册断点失败监听。
	 *
	 * @param listener 断点下发失败时回调
	 */
	@JvmStatic
	fun addListener(topCls: JClass, listener: Listener) {
		listeners[DbgUtils.getRawFullName(topCls)] =
			SimpleEntry(topCls.getCls().getClassNode(), listener)
	}

	@JvmStatic
	fun removeListener(topCls: JClass) {
		listeners.remove(DbgUtils.getRawFullName(topCls))
	}

	/** @return 指定类中所有断点对应的 smali 行位置 */
	@JvmStatic
	fun getPositions(topCls: JClass): List<Int> {
		val bps = bpm[DbgUtils.getRawFullName(topCls)]
		if (bps != null && bps.isNotEmpty()) {
			val smali: Smali = DbgUtils.getSmali(topCls.getCls().getClassNode())
			val posList = ArrayList<Int>(bps.size)
			for (bp in bps) {
				val pos = smali.getInsnPosByCodeOffset(bp.getFullMthRawID(), bp.codeOffset)
				if (pos > -1) {
					posList.add(pos)
				}
			}
			return posList
		}
		return emptyList()
	}

	/** 在指定 smali 行设置断点，必要时同步到调试器。 */
	@JvmStatic
	fun set(topCls: JClass, line: Int): Boolean {
		val lineInfo = DbgUtils.getCodeOffsetInfoByLine(topCls, line)
		if (lineInfo != null) {
			val name = DbgUtils.getRawFullName(topCls)
			val list = bpm.computeIfAbsent(name) { ArrayList() }
			val bkp = list.firstOrNull {
				it.codeOffset == lineInfo.value.toLong() && it.getFullMthRawID() == lineInfo.key
			}
			var ok = true
			if (bkp == null) {
				val sigs = DbgUtils.sepClassAndMthSig(lineInfo.key)
				if (sigs != null && sigs.size == 2) {
					val bp = FileBreakpoint(sigs[0], sigs[1], lineInfo.value.toLong())
					list.add(bp)
					val dc = debugController
					if (dc != null) {
						ok = dc.setBreakpoint(bp)
					}
				}
			}
			return ok
		}
		return false
	}

	/** 移除指定 smali 行的断点，必要时同步到调试器。 */
	@JvmStatic
	fun remove(topCls: JClass, line: Int): Boolean {
		val lineInfo = DbgUtils.getCodeOffsetInfoByLine(topCls, line)
		if (lineInfo != null) {
			val bps = bpm[DbgUtils.getRawFullName(topCls)]
			if (bps != null) {
				val it = bps.iterator()
				while (it.hasNext()) {
					val bp = it.next()
					if (bp.codeOffset == lineInfo.value.toLong() && bp.getFullMthRawID() == lineInfo.key) {
						it.remove()
						val dc = debugController
						if (dc != null) {
							return dc.removeBreakpoint(bp)
						}
						break
					}
				}
			}
		}
		return true
	}

	private fun sync() {
		val path = savePath ?: return
		if (bpm.isEmpty() && !Files.exists(path)) {
			// 用户没有操作过断点，就不输出断点文件。
			return
		}
		try {
			Files.write(path, GSON.toJson(bpm).toByteArray(StandardCharsets.UTF_8))
		} catch (e: Exception) {
			LOG.error("Failed to write breakpoints config: {}", path, e)
		}
	}

	/** 断点下发失败时的回调。 */
	fun interface Listener {
		fun breakpointDisabled(codeOffset: Int)
	}

	/** 单个文件断点。 */
	internal class FileBreakpoint() {
		var cls: String = ""
		var mth: String = ""
		var codeOffset: Long = 0

		internal constructor(cls: String, mth: String, codeOffset: Long) : this() {
			this.cls = cls
			this.mth = mth
			this.codeOffset = codeOffset
		}

		internal fun getFullMthRawID(): String = "$cls.$mth"

		override fun hashCode(): Int = Objects.hash(codeOffset, cls, mth)

		override fun equals(other: Any?): Boolean {
			if (other is FileBreakpoint) {
				if (other === this) {
					return true
				}
				return other.codeOffset == codeOffset && other.cls == cls && other.mth == mth
			}
			return false
		}
	}

	/** @return 所有已保存的断点 */
	internal fun getAllBreakpoints(): List<FileBreakpoint> {
		val bpList = ArrayList<FileBreakpoint>()
		for (entry in bpm.entries) {
			bpList.addAll(entry.value)
		}
		return bpList
	}

	/** 通知监听器某断点设置失败。 */
	internal fun failBreakpoint(bp: FileBreakpoint) {
		val entry = listeners[bp.cls]
		if (entry != null) {
			var pos = DbgUtils.getSmali(entry.key).getInsnPosByCodeOffset(bp.getFullMthRawID(), bp.codeOffset)
			pos = maxOf(0, pos)
			entry.value.breakpointDisabled(pos)
		}
	}

	internal fun setDebugController(controller: DebugController?) {
		debugController = controller
	}
}
