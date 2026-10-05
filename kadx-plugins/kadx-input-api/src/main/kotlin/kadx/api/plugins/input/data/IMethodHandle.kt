package kadx.api.plugins.input.data

/**
 * 方法句柄（method handle）接口：invoke-custom / const-method-handle 指令引用的目标。
 *
 * **背景**：Dex 的 method_handle 结构指向一个字段或方法，并带有类型标签
 * （见 [MethodHandleType]），用于 lambda metafactory、varargs 等场景。
 */
public interface IMethodHandle {

	/** @return 句柄类型（如 REF_invokeVirtual）*/
	public val type: MethodHandleType

	/** @return 指向的字段引用；仅当类型为字段类句柄时非 null */
	public val fieldRef: IFieldRef?

	/** @return 指向的方法引用；仅当类型为方法类句柄时非 null */
	public val methodRef: IMethodRef?

	/**
	 * 惰性加载：首次访问前调用，填充 [getFieldRef] / [getMethodRef]。
	 * **注意**：load() 之前只能依赖构造时已知的信息（如 [getType]）。
	 */
	public fun load()
}
