package jadx.api.plugins.input.data.attributes

/**
 * Jadx 属性接口：可挂载到大多数 jadx 节点上的自定义数据容器。
 *
 **背景**：插件/解析器把附加信息（注解、调试信息、异常表等）封装为本接口的实现，
 * 通过节点的 `getAttributes()` 暴露给反编译管线；[getAttrType] 返回的类型常量用于
 * 按类型查找属性（同类型属性唯一）。
 *
 **Kotlin 转换说明**：原 Java 通配符签名 `IJadxAttrType<? extends IJadxAttribute>`
 * → Kotlin `IJadxAttrType<out IJadxAttribute>`；两个 default 方法保留为接口默认实现。
 */
public interface IJadxAttribute {

	/**
	 * @return 属性类型常量（用于按类型查找，同类型属性在节点上唯一）。
	 *
	 * **Kotlin 转换说明**：原 Java 签名 `IJadxAttrType<? extends IJadxAttribute>`。这里必须用星投影
	 * `IJadxAttrType<*>` 而非 `out IJadxAttribute`——Kotlin 的声明式协变（`out T`）不写入字节码，
	 * javac 看到的是不变型 `IJadxAttrType<T>`，jadx-core 大量 Java 覆写返回更窄类型参数
	 * （如 `AType<CatchAttr>`）会因非子类型而编译失败；星投影在两侧语言都接受任意实参。
	 */
	public fun getAttrType(): IJadxAttrType<*>

	/**
	 * 标记节点卸载事件时跳过本属性的 unload。
	 *
	 * @return 默认 false；[PinnedAttribute] 固定为 true
	 */
	public fun keepLoaded(): Boolean = false

	/** @return 属性的字符串表示（默认委托 [toString]）*/
	public fun toAttrString(): String = this.toString()
}
