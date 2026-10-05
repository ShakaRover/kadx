package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.AttrNode

/**
 * 重命名原因属性：记录节点为什么被重命名（例如“不是合法 Java 名”“包含不可打印字符”）。
 *
 * **为什么要记录原因？** 反编译结果里会自动给类/方法/字段改名，保留原因可以让用户
 * 理解改名动机，并在 UI 的注释中展示。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 静态工厂 `forNode` → companion + `@JvmStatic`，Java 调用方写法不变；
 * - [description] 私有且只暴露 [getDescription]，故声明为 `private var` + 显式函数；
 * - 主构造器带默认值，Kotlin 会额外生成无参构造器，Java 的 `new RenameReasonAttr()` 仍可用；
 * - 原 Java 另外两个构造器保留为次构造器，JVM 构造器签名不变。
 */
class RenameReasonAttr(private var description: String = "") : IKadxAttribute {

	companion object {
		/** 获取节点上的重命名原因属性；不存在则创建并挂载一个 */
		fun forNode(node: AttrNode): RenameReasonAttr {
			val renameReasonAttr = node.get(AType.RENAME_REASON)
			if (renameReasonAttr != null) {
				return renameReasonAttr
			}
			val newAttr = RenameReasonAttr()
			node.addAttr(newAttr)
			return newAttr
		}
	}

	/** 从已有节点拷贝重命名原因（节点上没有该属性时使用空字符串） */
	constructor(node: AttrNode) : this(node.get(AType.RENAME_REASON)?.description ?: "")

	/** 从节点拷贝原因，并按需追加“非法名/不可打印”两种原因 */
	constructor(node: AttrNode, notValid: Boolean, notPrintable: Boolean) : this(node) {
		if (notValid) {
			notValid()
		}
		if (notPrintable) {
			notPrintable()
		}
	}

	/** 追加“不是合法 Java 名”的原因 */
	fun notValid(): RenameReasonAttr = append("not valid java name")

	/** 追加“包含不可打印字符”的原因 */
	fun notPrintable(): RenameReasonAttr = append("contains not printable characters")

	/** 追加一条重命名原因（多条原因用 " and " 连接） */
	fun append(reason: String): RenameReasonAttr {
		if (description.isEmpty()) {
			description += reason
		} else {
			description += " and " + reason
		}
		return this
	}

	fun getDescription(): String = description

	override val attrType: AType<RenameReasonAttr> get() = AType.RENAME_REASON

	override fun toString(): String = "RENAME_REASON:$description"
}
