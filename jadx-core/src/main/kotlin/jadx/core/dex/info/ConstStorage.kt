package jadx.core.dex.info

import jadx.api.JadxArgs
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.IFieldInfoRef
import jadx.core.dex.nodes.RootNode
import java.util.concurrent.ConcurrentHashMap

/**
 * 常量字段存储：用于把代码中的字面量替换回常量字段名（如 `FLAG_ENABLED`）。
 *
 * 分两级存储：
 * - 全局常量（public 字段）放在 [globalValues]；
 * - 类内常量按类存放于 [classes]，查找时沿内部类链向上回溯。
 *
 * 同一个值若对应多个字段（重复常量），会移入 duplicates 并从可用表中删除，
 * 避免替换产生歧义。
 *
 * Kotlin 转换说明：原 Java 的 `getXxx()`/`setXxx()` 保持为显式方法（Kotlin 调用点
 * 使用显式方法调用），私有字段改名以避免与访问器同名冲突。
 */
class ConstStorage(args: JadxArgs) {

	private val replaceEnabled: Boolean = args.isReplaceConsts
	private val globalValues = ValueStorage()
	private val classes = HashMap<ClassNode, ValueStorage>()

	/** 资源名映射：资源 id → `type/name`，用于把整数常量还原成 `R.xxx.yyy`。 */
	private var resourcesNamesMap: Map<Int, String> = HashMap()

	/** 某个类的常量表：维护值→字段映射，并记录重复值。 */
	private class ValueStorage {
		private val values: MutableMap<Any, IFieldInfoRef> = ConcurrentHashMap()
		private val duplicates: MutableSet<Any> = HashSet()

		fun getValues(): MutableMap<Any, IFieldInfoRef> = values

		fun get(key: Any): IFieldInfoRef? = values[key]

		/** @return 若该值发生重复则返回 true（并把它从可用表中移除）。 */
		fun put(value: Any, fld: IFieldInfoRef): Boolean {
			if (duplicates.contains(value)) {
				values.remove(value)
				return true
			}
			val prev = values.put(value, fld)
			if (prev != null) {
				values.remove(value)
				duplicates.add(value)
				return true
			}
			return false
		}

		fun contains(value: Any): Boolean = duplicates.contains(value) || values.containsKey(value)

		/** 移除属于指定类的所有常量（类被卸载/重命名时调用）。 */
		fun removeForCls(cls: ClassNode) {
			values.entries.removeAll { entry ->
				val field = entry.value
				field is FieldNode && field.parentClass == cls
			}
		}
	}

	fun addConstField(fld: FieldNode, value: Any, isPublic: Boolean) {
		if (isPublic) {
			addGlobalConstField(fld, value)
		} else {
			getClsValues(fld.parentClass).put(value, fld)
		}
	}

	fun addGlobalConstField(fld: IFieldInfoRef, value: Any) {
		globalValues.put(value, fld)
	}

	fun removeForClass(cls: ClassNode) {
		classes.remove(cls)
		globalValues.removeForCls(cls)
	}

	private fun getClsValues(cls: ClassNode): ValueStorage = classes.getOrPut(cls) { ValueStorage() }

	/**
	 * 在类（及其外部类）的常量表中查找字面量对应的常量字段。
	 *
	 * @param searchGlobal 是否允许回退到全局常量表
	 * @return 匹配的字段引用；找不到或存在歧义时返回 null
	 */
	fun getConstField(cls: ClassNode, value: Any, searchGlobal: Boolean): IFieldInfoRef? {
		if (!replaceEnabled) {
			return null
		}
		val root = cls.root()
		if (value is Int) {
			val rField = getResourceField(value, root)
			if (rField != null) {
				return rField
			}
		}
		val foundInGlobal = globalValues.contains(value)
		if (foundInGlobal && !searchGlobal) {
			return null
		}
		var current: ClassNode? = cls
		while (current != null) {
			val classValues = classes[current]
			if (classValues != null) {
				val field = classValues.get(value)
				if (field != null) {
					if (foundInGlobal) {
						return null
					}
					return field
				}
			}
			val parentClass = current.classInfo.parentClass ?: break
			current = root.resolveClass(parentClass)
		}
		if (searchGlobal) {
			return globalValues.get(value)
		}
		return null
	}

	/** 把资源 id 还原成 `R.type.name` 对应的字段。 */
	private fun getResourceField(value: Int, root: RootNode): FieldNode? {
		val str = resourcesNamesMap[value] ?: return null
		val appResClass = root.getAppResClass() ?: return null
		val parts = str.split("/", limit = 2)
		if (parts.size != 2) {
			return null
		}
		val typeName = parts[0]
		val fieldName = parts[1]
		for (innerClass in appResClass.innerClasses) {
			if (innerClass.classInfo.shortName == typeName) {
				return innerClass.searchFieldByName(fieldName)
			}
		}
		appResClass.addWarn("Not found resource field with id: $value, name: " + str.replace('/', '.'))
		return null
	}

	/**
	 * 根据字面量参数的类型查找常量字段。
	 *
	 * 小数值（如 0/1/true/false）只在较小范围内搜索，避免把普通数字误判成常量；
	 * 大数值才允许回退到全局常量表。
	 */
	fun getConstFieldByLiteralArg(cls: ClassNode, arg: LiteralArg): IFieldInfoRef? {
		if (!replaceEnabled) {
			return null
		}
		val type = arg.getType().getPrimitiveType() ?: return null
		val literal = arg.literal
		return when (type) {
			PrimitiveType.BOOLEAN -> getConstField(cls, literal == 1L, false)

			PrimitiveType.CHAR -> getConstField(cls, literal.toInt().toChar(), Math.abs(literal) > 10L)

			PrimitiveType.BYTE -> getConstField(cls, literal.toByte(), Math.abs(literal) > 10L)

			PrimitiveType.SHORT -> getConstField(cls, literal.toShort(), Math.abs(literal) > 100L)

			PrimitiveType.INT -> getConstField(cls, literal.toInt(), Math.abs(literal) > 100L)

			PrimitiveType.LONG -> getConstField(cls, literal, Math.abs(literal) > 1000L)

			PrimitiveType.FLOAT -> {
				val f = java.lang.Float.intBitsToFloat(literal.toInt())
				getConstField(cls, f, java.lang.Float.compare(f, 0f) == 0)
			}

			PrimitiveType.DOUBLE -> {
				val d = java.lang.Double.longBitsToDouble(literal)
				getConstField(cls, d, java.lang.Double.compare(d, 0.0) == 0)
			}

			else -> null
		}
	}
	fun setResourcesNames(resourcesNames: Map<Int, String>) {
		this.resourcesNamesMap = resourcesNames
	}

	fun getResourcesNames(): Map<Int, String> = resourcesNamesMap

	fun getGlobalConstFields(): MutableMap<Any, IFieldInfoRef> = globalValues.getValues()

	fun isReplaceEnabled(): Boolean = replaceEnabled
}
