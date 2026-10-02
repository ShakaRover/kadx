package jadx.core.dex.info

import jadx.core.dex.instructions.args.ArgType

/**
 * 元信息缓存：统一保存 [ClassInfo] / [FieldInfo] / [MethodInfo] / [PackageInfo]。
 *
 * 目的：保证同一个类/字段/方法在内存中只有一个实例，从而可以用 `==`（值判等）
 * 或身份比较来去重。方法有两套 key：
 * - [uniqueMethods]：按方法签名唯一化实例；
 * - [methods]：按输入文件的 uniq id 建索引（同一方法可能来自不同文件）。
 *
 * Kotlin 转换说明：原 Java 的 `synchronized (obj) { ... }` 用 Kotlin 的
 * `synchronized(obj) { ... }` 内联函数平替，锁对象与临界区语义保持一致。
 */
class InfoStorage {

	private val classes = HashMap<ArgType, ClassInfo>()
	private val fields = HashMap<FieldInfo, FieldInfo>()

	/** 唯一方法实例表（key 与 value 相同，用于去重） */
	private val uniqueMethods = HashMap<MethodInfo, MethodInfo>()

	/** uniq id → 方法（可能包含来自不同文件的同名方法） */
	private val methods = HashMap<Int, MethodInfo>()

	private val packages = HashMap<String, PackageInfo>()

	fun getCls(type: ArgType): ClassInfo? = classes[type]

	fun putCls(cls: ClassInfo): ClassInfo {
		synchronized(classes) {
			val prev = classes.put(cls.type, cls)
			return prev ?: cls
		}
	}

	fun getByUniqId(id: Int): MethodInfo? = synchronized(methods) {
		methods[id]
	}

	fun putByUniqId(id: Int, mth: MethodInfo) {
		synchronized(methods) {
			methods[id] = mth
		}
	}

	/** 按签名唯一化方法实例：已存在则返回旧实例。 */
	fun putMethod(newMth: MethodInfo): MethodInfo {
		synchronized(uniqueMethods) {
			val prev = uniqueMethods[newMth]
			if (prev != null) {
				return prev
			}
			uniqueMethods[newMth] = newMth
		}
		return newMth
	}

	/** 按值唯一化字段实例：已存在则返回旧实例。 */
	fun getField(field: FieldInfo): FieldInfo {
		synchronized(fields) {
			val f = fields[field]
			if (f != null) {
				return f
			}
			fields[field] = field
		}
		return field
	}

	fun getPkg(fullName: String): PackageInfo? = packages[fullName]

	fun putPkg(pkg: PackageInfo) {
		packages[pkg.fullName] = pkg
	}
}
