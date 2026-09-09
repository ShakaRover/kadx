package jadx.plugins.input.dex.sections

import jadx.api.plugins.input.data.IMethodRef
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.dex.DexReader

/**
 * DEX 方法引用：定位某个类中的某个方法（轻量视图，支持延迟加载）。
 *
 * **背景**：
 * 1. [SectionReader.initMethodRef] 构造时只写入 uniqId / dexIdx / sectionReader，
 *    真正的父类/名字/签名要等调用方触发 [load] 时才从 method_ids + proto_ids section 读取；
 * 2. [DexClassData.readMethods] 复用同一实例：每轮先 [reset] 清空再重新填充。
 *
 * **可空语义**（与原 Java 一致）：[reset] 之后、load 之前各字段为 null，
 * 此时调用 getter 会抛 NPE（checkNotNull），与原 Java 返回 null 后由调用方解引用崩溃的行为等价；
 * [toString] 则直接读底层字段以支持"未加载"状态的十六进制 uniqId 输出。
 */
public class DexMethodRef : IMethodRef {

	private var uniqId: Int = 0
	private var name: String? = null
	private var parentClassType: String? = null
	private var returnType: String? = null
	private var argTypes: List<String>? = null

	// lazy loading info（延迟加载所需的索引与读取器）
	private var dexIdx: Int = 0
	private var sectionReader: SectionReader? = null

	public fun initUniqId(dexReader: DexReader, idx: Int) {
		this.uniqId = (dexReader.uniqId and 0xFFFF) shl 16 or (idx and 0xFFFF)
	}

	override fun load() {
		val reader = sectionReader ?: return
		reader.loadMethodRef(this, dexIdx)
		sectionReader = null
	}

	public fun setDexIdx(dexIdx: Int) {
		this.dexIdx = dexIdx
	}

	public fun setSectionReader(sectionReader: SectionReader?) {
		this.sectionReader = sectionReader
	}

	override fun getUniqId(): Int = uniqId

	public fun reset() {
		name = null
		parentClassType = null
		returnType = null
		argTypes = null
	}

	override fun getParentClassType(): String = checkNotNull(parentClassType) { "method ref not loaded" }

	public fun setParentClassType(parentClassType: String?) {
		this.parentClassType = parentClassType
	}

	override fun getName(): String = checkNotNull(name) { "method ref not loaded" }

	public fun setName(name: String?) {
		this.name = name
	}

	override fun getReturnType(): String = checkNotNull(returnType) { "method ref not loaded" }

	public fun setReturnType(returnType: String?) {
		this.returnType = returnType
	}

	override fun getArgTypes(): List<String> = checkNotNull(argTypes) { "method ref not loaded" }

	public fun setArgTypes(argTypes: List<String>?) {
		this.argTypes = argTypes
	}

	override fun toString(): String {
		if (name == null) {
			// 尚未 load：输出 uniqId 十六进制便于调试定位
			return Integer.toHexString(uniqId)
		}
		return "$parentClassType->$name(${Utils.listToStr(argTypes)})$returnType"
	}
}
