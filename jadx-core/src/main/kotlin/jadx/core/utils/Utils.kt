package jadx.core.utils

import jadx.api.ICodeWriter
import jadx.api.JadxDecompiler
import jadx.core.dex.visitors.DepthTraversal
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.Objects
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * jadx 通用工具集：命名转换、字符串拼接、集合操作、堆栈过滤、线程工厂等。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic` 保持 Java 调用不变。
 */
object Utils {

	private val JADX_API_PACKAGE: String = JadxDecompiler::class.java.getPackage().name
	private val STACKTRACE_STOP_CLS_NAME: String = DepthTraversal::class.java.name

	fun cleanObjectName(obj: String): String {
		if (obj[0] == 'L') {
			val last = obj.length - 1
			if (obj[last] == ';') {
				return obj.substring(1, last).replace('/', '.')
			}
		}
		return obj
	}

	fun cutObject(obj: String): String {
		if (obj[0] == 'L') {
			return obj.substring(1, obj.length - 1)
		}
		return obj
	}

	fun makeQualifiedObjectName(obj: String): String = 'L' + obj.replace('.', '/') + ';'

	fun smaliNameToJavaName(descString: String): String {
		if (descString.isEmpty()) {
			return descString
		}
		return when (descString[0]) {
			'V' -> "void"
			'Z' -> "boolean"
			'C' -> "char"
			'B' -> "byte"
			'S' -> "short"
			'I' -> "int"
			'F' -> "float"
			'J' -> "long"
			'D' -> "double"
			'L' -> cleanObjectNameWithInnerClass(descString)
			'[' -> "${smaliNameToJavaName(descString.substring(1, descString.length))}[]"
			else -> descString
		}
	}

	private fun cleanObjectNameWithInnerClass(obj: String): String {
		// 内部类名里的 '$' 转成 '.'
		return cleanObjectName(obj).replace('$', '.')
	}

	fun javaNameToSmaliName(descString: String): String {
		if (descString.isEmpty()) {
			return descString
		}
		if (descString.endsWith("[]")) {
			return "[${javaNameToSmaliName(descString.substring(0, descString.length - 2))}"
		}
		return when (descString) {
			"void" -> "V"
			"boolean" -> "Z"
			"char" -> "C"
			"byte" -> "B"
			"short" -> "S"
			"int" -> "I"
			"float" -> "F"
			"long" -> "J"
			"double" -> "D"
			else -> makeQualifiedObjectName(descString)
		}
	}

	fun strRepeat(str: String, count: Int): String {
		if (count < 1) {
			return ""
		}
		if (count == 1) {
			return str
		}
		val sb = StringBuilder(str.length * count)
		for (i in 0 until count) {
			sb.append(str)
		}
		return sb.toString()
	}

	fun listToString(objects: Iterable<*>?): String = listToString(objects, ", ")

	fun listToString(objects: Iterable<*>?, joiner: String): String {
		if (objects == null) {
			return ""
		}
		return listToString(objects, joiner) { obj -> Objects.toString(obj) }
	}

	fun <T> listToString(objects: Iterable<T>, toStr: (T) -> String): String = listToString(objects, ", ", toStr)

	fun <T> listToString(objects: Iterable<T>, joiner: String, toStr: (T) -> String): String {
		val sb = StringBuilder()
		listToString(sb, objects, joiner, toStr)
		return sb.toString()
	}

	fun <T> listToString(sb: StringBuilder, objects: Iterable<T>, joiner: String) {
		listToString(sb, objects, joiner) { obj -> Objects.toString(obj) }
	}

	fun <T> listToString(sb: StringBuilder, objects: Iterable<T>?, joiner: String, toStr: (T) -> String) {
		if (objects == null) {
			return
		}
		val it = objects.iterator()
		if (it.hasNext()) {
			sb.append(toStr(it.next()))
		}
		while (it.hasNext()) {
			sb.append(joiner).append(toStr(it.next()))
		}
	}

	fun <T> arrayToStr(arr: Array<T>?): String {
		val len = arr?.size ?: 0
		if (len == 0) {
			return ""
		}
		val a = checkNotNull(arr)
		val sb = StringBuilder()
		sb.append(a[0])
		for (i in 1 until len) {
			sb.append(", ").append(a[i])
		}
		return sb.toString()
	}

	fun concatStrings(list: List<String>?): String {
		if (list == null || list.isEmpty()) {
			return ""
		}
		if (list.size == 1) {
			return list[0]
		}
		val sb = StringBuilder()
		list.forEach { sb.append(it) }
		return sb.toString()
	}

	fun currentStackTrace(): String = getStackTrace(Exception())

	fun currentStackTrace(skipFrames: Int): String {
		val e = Exception()
		val stackTrace = e.stackTrace
		val len = stackTrace.size
		if (skipFrames < len) {
			e.stackTrace = Arrays.copyOfRange(stackTrace, skipFrames, len)
		}
		return getStackTrace(e)
	}

	fun getFullStackTrace(throwable: Throwable?): String = getStackTrace(throwable, false)

	fun getStackTrace(throwable: Throwable?): String = getStackTrace(throwable, true)

	private fun getStackTrace(throwable: Throwable?, filter: Boolean): String {
		if (throwable == null) {
			return ""
		}
		val sw = StringWriter()
		val pw = PrintWriter(sw, true)
		if (filter) {
			filterRecursive(throwable)
		}
		throwable.printStackTrace(pw)
		return sw.buffer.toString()
	}

	fun appendStackTrace(code: ICodeWriter, throwable: Throwable?) {
		if (throwable == null) {
			return
		}
		code.startLine()
		val w: OutputStream = object : OutputStream() {
			override fun write(b: Int) {
				val c = b.toChar()
				when (c) {
					'\n' -> code.startLine()
					'\r' -> {}
					else -> code.add(c)
				}
			}
		}
		PrintWriter(w, true, StandardCharsets.UTF_8).use { pw ->
			filterRecursive(throwable)
			throwable.printStackTrace(pw)
			pw.flush()
		}
	}

	private fun filterRecursive(th: Throwable) {
		try {
			filter(th)
		} catch (e: Exception) {
			// 忽略过滤异常
		}
		val cause = th.cause
		if (cause != null) {
			filterRecursive(cause)
		}
	}

	private fun filter(th: Throwable) {
		val stackTrace = th.stackTrace
		val length = stackTrace.size
		var prevElement: StackTraceElement? = null
		for (i in 0 until length) {
			val stackTraceElement = stackTrace[i]
			val clsName = stackTraceElement.className
			if (clsName == STACKTRACE_STOP_CLS_NAME ||
				clsName.startsWith(JADX_API_PACKAGE) ||
				Objects.equals(prevElement, stackTraceElement)
			) {
				th.stackTrace = Arrays.copyOfRange(stackTrace, 0, i)
				return
			}
			prevElement = stackTraceElement
		}
		// 找不到停止条件：截断到任意 jadx 类
		for (i in length - 1 downTo 0) {
			val clsName = stackTrace[i].className
			if (clsName.startsWith("jadx.")) {
				if (clsName.startsWith("jadx.tests.")) {
					continue
				}
				th.stackTrace = Arrays.copyOfRange(stackTrace, 0, i)
				return
			}
		}
	}

	fun <T, R> collectionMap(list: Collection<T>?, mapFunc: (T) -> R): List<R> {
		if (list == null || list.isEmpty()) {
			return Collections.emptyList()
		}
		val result = ArrayList<R>(list.size)
		for (t in list) {
			result.add(mapFunc(t))
		}
		return result
	}

	fun <T, R> collectionMapNoNull(list: Collection<T>?, mapFunc: (T) -> R): List<R> {
		if (list == null || list.isEmpty()) {
			return Collections.emptyList()
		}
		val result = ArrayList<R>(list.size)
		for (t in list) {
			val r = mapFunc(t)
			if (r != null) {
				result.add(r)
			}
		}
		return result
	}

	fun <T> containsInListByRef(list: List<T>?, element: T): Boolean {
		if (list == null || list.isEmpty()) {
			return false
		}
		for (t in list) {
			if (t === element) {
				return true
			}
		}
		return false
	}

	fun <T> indexInListByRef(list: List<T>?, element: T): Int {
		if (list == null || list.isEmpty()) {
			return -1
		}
		val size = list.size
		for (i in 0 until size) {
			if (list[i] === element) {
				return i
			}
		}
		return -1
	}

	fun <T> lockList(list: List<T>): List<T> {
		if (list.isEmpty()) {
			return Collections.emptyList()
		}
		if (list.size == 1) {
			return Collections.singletonList(list[0])
		}
		return ImmutableList(list)
	}

	/**
	 * 返回从 startIndex（含）到列表末尾的子列表。
	 */
	fun <T> listTail(list: List<T>, startIndex: Int): List<T> {
		if (startIndex == 0) {
			return list
		}
		val size = list.size
		if (startIndex >= size) {
			return Collections.emptyList()
		}
		return list.subList(startIndex, size)
	}

	fun <T> mergeLists(first: List<T>?, second: List<T>?): List<T>? {
		if (first == null || first.isEmpty()) {
			return second
		}
		if (second == null || second.isEmpty()) {
			return first
		}
		val result = ArrayList<T>(first.size + second.size)
		result.addAll(first)
		result.addAll(second)
		return result
	}

	fun <T> mergeSets(first: Set<T>?, second: Set<T>?): Set<T>? {
		if (first == null || first.isEmpty()) {
			return second
		}
		if (second == null || second.isEmpty()) {
			return first
		}
		val result = HashSet<T>(first.size + second.size)
		result.addAll(first)
		result.addAll(second)
		return result
	}

	fun newConstStringMap(vararg parameters: String): Map<String, String> {
		val len = parameters.size
		if (len == 0) {
			return Collections.emptyMap()
		}
		if (len % 2 != 0) {
			throw IllegalArgumentException("Incorrect arguments count: $len")
		}
		val result = HashMap<String, String>(len / 2)
		for (i in 0 until len - 1 step 2) {
			result[parameters[i]] = parameters[i + 1]
		}
		return Collections.unmodifiableMap(result)
	}

	/**
	 * 合并两个 map，返回 HashMap；第二个 map 覆盖第一个的同名 key。
	 */
	fun <K, V> mergeMaps(first: Map<K, V>?, second: Map<K, V>?): Map<K, V>? {
		if (first == null || first.isEmpty()) {
			return second
		}
		if (second == null || second.isEmpty()) {
			return first
		}
		val result = HashMap<K, V>(first.size + second.size)
		result.putAll(first)
		result.putAll(second)
		return result
	}

	/**
	 * 根据 key 映射函数把值列表转成 map（类似 `Collectors.toMap`）。
	 */
	fun <K, V> groupBy(list: List<V>, mapKey: (V) -> K): Map<K, V> {
		val map = HashMap<K, V>(list.size)
		for (v in list) {
			map[mapKey(v)] = v
		}
		return map
	}

	/**
	 * 简单的树 DFS 遍历（不允许有环）。
	 */
	fun <T> treeDfsVisit(root: T, childrenProvider: (T) -> List<T>, visitor: (T) -> Unit) {
		multiRootTreeDfsVisit(Collections.singletonList(root), childrenProvider, visitor)
	}

	fun <T> multiRootTreeDfsVisit(roots: List<T>, childrenProvider: (T) -> List<T>, visitor: (T) -> Unit) {
		val queue = ArrayDeque(roots)
		while (true) {
			val current = queue.pollLast() ?: return
			visitor(current)
			for (child in childrenProvider(current)) {
				queue.addLast(child)
			}
		}
	}

	fun <T> getOne(list: List<T>?): T? {
		if (list == null || list.size != 1) {
			return null
		}
		return list[0]
	}

	fun <T> getOne(collection: Collection<T>?): T? {
		if (collection == null || collection.size != 1) {
			return null
		}
		return collection.iterator().next()
	}

	fun <T> isSetContainsAny(inputSet: Set<T>, searchKeys: Set<T>): Boolean {
		for (t in inputSet) {
			if (searchKeys.contains(t)) {
				return true
			}
		}
		return false
	}

	fun <T> first(list: List<T>): T? {
		if (list.isEmpty()) {
			return null
		}
		return list[0]
	}

	fun <T> first(list: Iterable<T>): T? {
		val it = list.iterator()
		if (!it.hasNext()) {
			return null
		}
		return it.next()
	}

	fun <T> last(list: List<T>): T? {
		if (list.isEmpty()) {
			return null
		}
		return list[list.size - 1]
	}

	fun <T> last(list: Iterable<T>): T? {
		val it = list.iterator()
		if (!it.hasNext()) {
			return null
		}
		while (true) {
			val next = it.next()
			if (!it.hasNext()) {
				return next
			}
		}
	}

	fun <T> getOrElse(obj: T?, defaultObj: T): T = obj ?: defaultObj

	fun <T> isEmpty(col: Collection<T>?): Boolean = col == null || col.isEmpty()

	fun <T> notEmpty(col: Collection<T>?): Boolean = col != null && !col.isEmpty()

	fun <K, V> isEmpty(map: Map<K, V>?): Boolean = map == null || map.isEmpty()

	fun <T> isEmpty(arr: Array<T>?): Boolean = arr == null || arr.isEmpty()

	fun <T> notEmpty(arr: Array<T>?): Boolean = arr != null && arr.isNotEmpty()

	fun checkThreadInterrupt() {
		if (Thread.currentThread().isInterrupted) {
			throw JadxRuntimeException("Thread interrupted")
		}
	}

	fun simpleThreadFactory(name: String): ThreadFactory = SimpleThreadFactory(name)

	private class SimpleThreadFactory(private val name: String) : ThreadFactory {
		private val number = AtomicInteger(0)

		override fun newThread(r: Runnable): Thread {
			val thread = Thread(r, "jadx-" + name + '-' + POOL.incrementAndGet() + '-' + number.incrementAndGet())
			thread.uncaughtExceptionHandler = EXC_HANDLER
			return thread
		}

		companion object {
			private val POOL = AtomicInteger(0)
			private val EXC_HANDLER: Thread.UncaughtExceptionHandler = SimpleUncaughtExceptionHandler()
		}
	}

	private class SimpleUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {
		override fun uncaughtException(thread: Thread, e: Throwable) {
			if (e is OutOfMemoryError) {
				thread.interrupt()
				LOG.error("OutOfMemoryError in thread: {}, forcing interrupt", thread.name)
			} else {
				LOG.error("Uncaught thread exception, thread: {}", thread.name, e)
			}
		}

		companion object {
			private val LOG: Logger = LoggerFactory.getLogger(SimpleUncaughtExceptionHandler::class.java)
		}
	}

	@Deprecated("env vars shouldn't be used in core modules. Prefer parsing in app and passing via jadx args")
	fun getEnvVarBool(varName: String, defValue: Boolean): Boolean {
		val strValue = System.getenv(varName)
		if (strValue == null) {
			return defValue
		}
		return strValue.equals("true", ignoreCase = true)
	}

	@Deprecated("env vars shouldn't be used in core modules. Prefer parsing in app and passing via jadx args")
	fun getEnvVarInt(varName: String, defValue: Int): Int {
		val strValue = System.getenv(varName) ?: return defValue
		return Integer.parseInt(strValue)
	}

	fun safeParseInt(value: String?, defValue: Int): Int {
		if (value == null || value.isEmpty()) {
			return defValue
		}
		return try {
			Integer.parseInt(value)
		} catch (e: Exception) {
			defValue
		}
	}

	fun safeParseInteger(value: String?): Int? {
		if (value == null || value.isEmpty()) {
			return null
		}
		return try {
			Integer.parseInt(value)
		} catch (e: Exception) {
			null
		}
	}
}
