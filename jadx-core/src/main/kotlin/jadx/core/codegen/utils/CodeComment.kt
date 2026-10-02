package jadx.core.codegen.utils

import jadx.api.data.CommentStyle
import jadx.api.data.ICodeComment

/**
 * 代码注释值对象：保存注释文本与注释风格（行注释 / 块注释等）。
 *
 * **为什么用普通 class**：原 Java 未覆写 equals/hashCode，保持身份语义；
 * `getComment()` / `getStyle()` 通过主构造器属性生成，JVM 方法名与原 Java 完全一致。
 */
class CodeComment(val comment: String, val style: CommentStyle) {

	/** 从 API 层注释对象复制（ICodeComment 为 Java 接口，使用其 getter）。 */
	constructor(comment: ICodeComment) : this(comment.getComment(), comment.getStyle())

	override fun toString(): String = "CodeComment{$style: '$comment'}"
}
