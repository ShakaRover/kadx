package jadx.core.dex.info

import jadx.api.plugins.input.data.AccessFlags
import jadx.core.Consts
import jadx.core.utils.exceptions.JadxRuntimeException
import org.intellij.lang.annotations.MagicConstant

/**
 * 访问标志（access flags）封装类。
 *
 * 类、字段、方法在 DEX/class 文件里都用位掩码表示修饰符（public/static/final/...）。
 * 本类把原始 `int` 掩码和元素类型（类/字段/方法）包起来，提供一组语义化的
 * 判断与生成 Java 修饰符字符串的方法。
 *
 * Kotlin 转换说明：
 * - 原 Java 静态常量 [VISIBILITY_FLAGS] 改为 companion 中的 `@JvmField val`，
 *   Java 调用方仍写 `AccessInfo.VISIBILITY_FLAGS`；
 * - 所有 `isXxx()` / `getXxx()` 保持为显式方法（不转属性），因为仓库中已有大量
 *   Kotlin 调用点使用显式 `isXxx()` 形式，保持方法最机械、最安全。
 */
class AccessInfo(
	private val accFlags: Int,
	private val type: AFType,
) {

	/** 元素类型：类 / 字段 / 方法（影响 `makeString` 的输出内容）。 */
	enum class AFType {
		CLASS,
		FIELD,
		METHOD,
	}

	companion object {
		/** 三个互斥的可见性标志位（public/protected/private）的合并掩码。 */
		@JvmField
		val VISIBILITY_FLAGS: Int = AccessFlags.PUBLIC or AccessFlags.PROTECTED or AccessFlags.PRIVATE
	}

	/** 是否包含指定标志位。 */
	@MagicConstant(valuesFromClass = AccessFlags::class)
	fun containsFlag(flag: Int): Boolean = (accFlags and flag) != 0

	/** 是否同时包含所有给定标志位。 */
	@MagicConstant(valuesFromClass = AccessFlags::class)
	fun containsFlags(vararg flags: Int): Boolean {
		for (flag in flags) {
			if ((accFlags and flag) == 0) {
				return false
			}
		}
		return true
	}

	/** 去掉某个标志位；若本来就没有则返回自身（避免无意义的新对象）。 */
	fun remove(flag: Int): AccessInfo {
		if (containsFlag(flag)) {
			return AccessInfo(accFlags and flag.inv(), type)
		}
		return this
	}

	/** 添加某个标志位；若本来就有则返回自身。 */
	fun add(flag: Int): AccessInfo {
		if (!containsFlag(flag)) {
			return AccessInfo(accFlags or flag, type)
		}
		return this
	}

	/** 替换可见性标志（先清空三个可见性位，再写入新的）。 */
	fun changeVisibility(flag: Int): AccessInfo {
		val currentVisFlags = accFlags and VISIBILITY_FLAGS
		if (currentVisFlags == flag) {
			return this
		}
		val unsetAllVisFlags = accFlags and VISIBILITY_FLAGS.inv()
		return AccessInfo(unsetAllVisFlags or flag, type)
	}

	/** 只保留可见性位，返回一个新的 [AccessInfo]。 */
	fun getVisibility(): AccessInfo = AccessInfo(accFlags and VISIBILITY_FLAGS, type)

	/** 当前可见性是否弱于另一个（private < package-private < protected < public）。 */
	fun isVisibilityWeakerThan(otherAccInfo: AccessInfo): Boolean {
		val thisVis = accFlags and VISIBILITY_FLAGS
		val otherVis = otherAccInfo.accFlags and VISIBILITY_FLAGS
		if (thisVis == otherVis) {
			return false
		}
		return orderedVisibility(thisVis) < orderedVisibility(otherVis)
	}

	private fun orderedVisibility(flag: Int): Int = when (flag) {
		AccessFlags.PRIVATE -> 1

		0 -> 2

		// package-private
		AccessFlags.PROTECTED -> 3

		AccessFlags.PUBLIC -> 4

		else -> throw JadxRuntimeException("Unexpected visibility flag: $flag")
	}

	fun isPublic(): Boolean = (accFlags and AccessFlags.PUBLIC) != 0

	fun isProtected(): Boolean = (accFlags and AccessFlags.PROTECTED) != 0

	fun isPrivate(): Boolean = (accFlags and AccessFlags.PRIVATE) != 0

	fun isPackagePrivate(): Boolean = (accFlags and VISIBILITY_FLAGS) == 0

	fun isAbstract(): Boolean = (accFlags and AccessFlags.ABSTRACT) != 0

	fun isInterface(): Boolean = (accFlags and AccessFlags.INTERFACE) != 0

	fun isAnnotation(): Boolean = (accFlags and AccessFlags.ANNOTATION) != 0

	fun isNative(): Boolean = (accFlags and AccessFlags.NATIVE) != 0

	fun isStatic(): Boolean = (accFlags and AccessFlags.STATIC) != 0

	fun isFinal(): Boolean = (accFlags and AccessFlags.FINAL) != 0

	fun isConstructor(): Boolean = (accFlags and AccessFlags.CONSTRUCTOR) != 0

	fun isEnum(): Boolean = (accFlags and AccessFlags.ENUM) != 0

	fun isSynthetic(): Boolean = (accFlags and AccessFlags.SYNTHETIC) != 0

	fun isBridge(): Boolean = (accFlags and AccessFlags.BRIDGE) != 0

	fun isVarArgs(): Boolean = (accFlags and AccessFlags.VARARGS) != 0

	fun isSynchronized(): Boolean = (accFlags and (AccessFlags.SYNCHRONIZED or AccessFlags.DECLARED_SYNCHRONIZED)) != 0

	fun isTransient(): Boolean = (accFlags and AccessFlags.TRANSIENT) != 0

	fun isVolatile(): Boolean = (accFlags and AccessFlags.VOLATILE) != 0

	fun isModuleInfo(): Boolean = (accFlags and AccessFlags.MODULE) != 0

	fun isData(): Boolean = (accFlags and AccessFlags.DATA) != 0

	fun getType(): AFType = type

	/** 生成 Java 源码里的修饰符字符串（如 `public static final `）。 */
	fun makeString(showHidden: Boolean): String {
		val code = StringBuilder()
		if (isPublic()) {
			code.append("public ")
		}
		if (isPrivate()) {
			code.append("private ")
		}
		if (isProtected()) {
			code.append("protected ")
		}
		if (isStatic()) {
			code.append("static ")
		}
		if (isFinal()) {
			code.append("final ")
		}
		if (isAbstract()) {
			code.append("abstract ")
		}
		if (isNative()) {
			code.append("native ")
		}
		when (type) {
			AFType.METHOD -> {
				if (isSynchronized()) {
					code.append("synchronized ")
				}
				if (showHidden) {
					if (isBridge()) {
						code.append("/* bridge */ ")
					}
					if (Consts.DEBUG && isVarArgs()) {
						code.append("/* varargs */ ")
					}
				}
			}

			AFType.FIELD -> {
				if (isVolatile()) {
					code.append("volatile ")
				}
				if (isTransient()) {
					code.append("transient ")
				}
			}

			AFType.CLASS -> {
				if ((accFlags and AccessFlags.STRICT) != 0) {
					code.append("strict ")
				}
				if (showHidden) {
					if (isData()) {
						code.append("/* data */ ")
					}
					if (isModuleInfo()) {
						code.append("/* module-info */ ")
					}
					if (Consts.DEBUG) {
						if ((accFlags and AccessFlags.SUPER) != 0) {
							code.append("/* super */ ")
						}
						if ((accFlags and AccessFlags.ENUM) != 0) {
							code.append("/* enum */ ")
						}
					}
				}
			}
		}
		if (isSynthetic() && showHidden) {
			code.append("/* synthetic */ ")
		}
		return code.toString()
	}

	/** 返回可见性的文字描述（package-private / public / private / protected）。 */
	fun visibilityName(): String {
		if (isPackagePrivate()) {
			return "package-private"
		}
		if (isPublic()) {
			return "public"
		}
		if (isPrivate()) {
			return "private"
		}
		if (isProtected()) {
			return "protected"
		}
		throw JadxRuntimeException("Unknown visibility flags: ${getVisibility()}")
	}

	/** 原始访问标志位掩码。 */
	fun rawValue(): Int = accFlags

	override fun toString(): String = "AccessInfo: $type 0x${Integer.toHexString(accFlags)} (${makeString(true)})"
}
