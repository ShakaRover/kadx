package jadx.core.dex.info

import jadx.api.plugins.input.data.IFieldRef
import jadx.core.codegen.TypeGen
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.IFieldInfoRef
import jadx.core.dex.nodes.RootNode

/**
 * 字段元信息（FieldInfo）。
 *
 * 描述“哪个类的哪个字段、类型是什么、是否有别名”，并作为字段缓存 key。
 * 原 Java 重写了 `equals`/`hashCode`（按 名称+类型+声明类 判等），Kotlin 侧原样保留，
 * 不使用 `data class`。
 *
 * Kotlin 转换说明：简单 getter 改为属性（`name`/`type`/`declClass`/`alias`/`shortId`/...），
 * JVM 访问器名与原 Java 一致；静态工厂方法放入 companion + `@JvmStatic`。
 */
class FieldInfo private constructor(
	val declClass: ClassInfo,
	val name: String,
	val type: ArgType,
) : IFieldInfoRef {

	/** 字段别名（重命名后），默认与 [name] 相同 */
	var alias: String = name

	companion object {
		/** 从缓存获取或创建 [FieldInfo]。 */
		@JvmStatic
		fun from(root: RootNode, declClass: ClassInfo, name: String, type: ArgType): FieldInfo {
			val field = FieldInfo(declClass, name, type)
			return root.getInfoStorage().getField(field)
		}

		/** 从输入层的字段引用 [IFieldRef] 构造 [FieldInfo]。 */
		@JvmStatic
		fun fromRef(root: RootNode, fieldRef: IFieldRef): FieldInfo {
			val declClass = ClassInfo.fromName(root, checkNotNull(fieldRef.parentClassType))
			val field = FieldInfo(declClass, checkNotNull(fieldRef.name), ArgType.parse(fieldRef.type))
			return root.getInfoStorage().getField(field)
		}
	}

	/** 是否被重命名（别名与原名不同）。 */
	fun hasAlias(): Boolean = name != alias

	/** 完整 id：`声明类.字段名:类型签名`。 */
	val fullId: String get() = declClass.fullName + '.' + name + ':' + TypeGen.signature(type)

	/** 短 id：`字段名:类型签名`。 */
	val shortId: String get() = name + ':' + TypeGen.signature(type)

	/** 原始完整 id：内部类用 '$' 分隔的声明类 + `字段名:类型签名`。 */
	val rawFullId: String get() = declClass.makeRawFullName() + '.' + name + ':' + TypeGen.signature(type)

	/** 名称与类型是否都与 [other] 相同（忽略声明类）。 */
	fun equalsNameAndType(other: FieldInfo): Boolean = name == other.name && type == other.type

	/** 清除别名，恢复原名。 */
	fun removeAlias() {
		alias = name
	}

	override fun getFieldInfo(): FieldInfo = this

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is FieldInfo) {
			return false
		}
		return name == other.name &&
			type == other.type &&
			declClass == other.declClass
	}

	override fun hashCode(): Int {
		var result = name.hashCode()
		result = 31 * result + type.hashCode()
		result = 31 * result + declClass.hashCode()
		return result
	}

	override fun toString(): String = "$declClass.$name $type"
}
