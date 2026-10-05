package kadx.gui.cache.code.disk.adapters

import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeAnnotation.AnnType
import kadx.core.dex.nodes.RootNode
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.util.EnumMap

/**
 * [ICodeAnnotation] 的二进制适配器：按注解类型分派到具体子适配器。
 *
 * **做什么**：构造时按固定顺序为每种 [AnnType] 分配一个 1..N 的标签 [TypeInfo.tag]，
 * 写入时先写标签再委托给对应适配器；读取时用标签反查适配器。
 *
 * **格式契约（持久化格式，禁止变更标签顺序）**：标签从 1 开始，按 [registerAdapters]
 * 中的注册顺序（即 [AnnType] 的枚举顺序）递增；标签 0 保留表示 `null`。
 *
 * **可空语义**：`write(null)` 写标签 0；`read` 遇到标签 0 返回 `null`。
 */
class CodeAnnotationAdapter(root: RootNode) : DataAdapter<ICodeAnnotation?> {

	private val adaptersByCls: MutableMap<AnnType, TypeInfo>
	private val adaptersByTag: Array<TypeInfo?>

	init {
		val map = registerAdapters(root)
		val size = map.size
		adaptersByCls = EnumMap<AnnType, TypeInfo>(AnnType::class.java)
		adaptersByTag = arrayOfNulls(size + 1)
		var tag = 1
		for ((key, value) in map) {
			val typeInfo = TypeInfo(tag, value)
			adaptersByCls[key] = typeInfo
			adaptersByTag[tag] = typeInfo
			tag++
		}
	}

	/**
	 * 注册各类注解的适配器。
	 *
	 * 这里需要把具体 `DataAdapter<X>` 当作 `DataAdapter<ICodeAnnotation?>` 存进同一个 Map，
	 * 属于泛型擦除后的未检查强转（与 Java 原版的 raw type 用法等价）。
	 */
	@Suppress("UNCHECKED_CAST")
	private fun registerAdapters(root: RootNode): Map<AnnType, DataAdapter<ICodeAnnotation?>> {
		val map = EnumMap<AnnType, DataAdapter<ICodeAnnotation?>>(AnnType::class.java)
		val mthAdapter = MethodNodeAdapter(root)
		map[AnnType.CLASS] = ClassNodeAdapter(root) as DataAdapter<ICodeAnnotation?>
		map[AnnType.FIELD] = FieldNodeAdapter(root) as DataAdapter<ICodeAnnotation?>
		map[AnnType.METHOD] = mthAdapter as DataAdapter<ICodeAnnotation?>
		map[AnnType.DECLARATION] = NodeDeclareRefAdapter(this) as DataAdapter<ICodeAnnotation?>
		map[AnnType.VAR] = VarNodeAdapter(mthAdapter) as DataAdapter<ICodeAnnotation?>
		map[AnnType.VAR_REF] = VarRefAdapter.INSTANCE as DataAdapter<ICodeAnnotation?>
		map[AnnType.OFFSET] = InsnCodeOffsetAdapter.INSTANCE as DataAdapter<ICodeAnnotation?>
		map[AnnType.END] = NodeEndAdapter() as DataAdapter<ICodeAnnotation?>
		return map
	}

	@Throws(IOException::class)
	override fun write(out: DataOutput, value: ICodeAnnotation?) {
		if (value == null) {
			out.writeByte(0)
			return
		}
		val typeInfo = adaptersByCls[value.annType]
			?: throw RuntimeException("Unexpected code annotation type: ${value.javaClass.simpleName}")
		out.writeByte(typeInfo.tag)
		typeInfo.adapter.write(out, value)
	}

	@Throws(IOException::class)
	override fun read(input: DataInput): ICodeAnnotation? {
		val tag = input.readByte().toInt()
		if (tag == 0) {
			return null
		}
		val typeInfo = adaptersByTag[tag] ?: throw RuntimeException("Unknown type tag: $tag")
		return typeInfo.adapter.read(input)
	}

	/** 标签与适配器的绑定。 */
	private class TypeInfo(
		val tag: Int,
		val adapter: DataAdapter<ICodeAnnotation?>,
	)
}
