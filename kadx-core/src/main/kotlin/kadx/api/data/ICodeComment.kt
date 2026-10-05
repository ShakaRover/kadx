package kadx.api.data

/**
 * 用户代码注释的公共接口。
 *
 * **做什么**：一条注释由“挂载节点引用（类/方法/字段/包）”[getNodeRef]、
 * 可选的“代码引用（方法内参数/变量/指令）”[getCodeRef]、注释文本 [getComment]
 * 与注释风格 [getStyle] 组成。
 *
 * **为什么是接口**：kadx-gui / 插件会实现或序列化它；保持 Java 可实现（显式 getter、
 * 继承 `Comparable` 以支持排序），签名与 JVM 名与原生 Java 完全一致。
 */
interface ICodeComment : Comparable<ICodeComment> {

	/** 注释挂载到的节点引用。 */
	fun getNodeRef(): IJavaNodeRef

	/** 可选的代码引用；为 null 表示注释挂在节点本身（类/方法/字段）。 */
	fun getCodeRef(): IJavaCodeRef?

	/** 注释文本。 */
	fun getComment(): String

	/** 注释风格（行注释 / 块注释等）。 */
	fun getStyle(): CommentStyle
}
