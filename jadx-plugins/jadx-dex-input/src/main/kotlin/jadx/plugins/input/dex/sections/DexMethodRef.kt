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

	private var uniqIdValue: Int = 0
	private var nameValue: String? = null
	private var parentClassTypeValue: String? = null
	private var returnTypeValue: String? = null
	private var argTypesValue: List<String>? = null

	// lazy loading info（延迟加载所需的索引与读取器）
	private var dexIdx: Int = 0
	private var sectionReader: SectionReader? = null

	public fun initUniqId(dexReader: DexReader, idx: Int) {
		this.uniqIdValue = (dexReader.uniqId and 0xFFFF) shl 16 or (idx and 0xFFFF)
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

	override val uniqId: Int get() = uniqIdValue

	public fun reset() {
		nameValue = null
		parentClassTypeValue = null
		returnTypeValue = null
		argTypesValue = null
	}

	override val parentClassType: String get() = checkNotNull(parentClassTypeValue) { "method ref not loaded" }

	public fun setParentClassType(parentClassTypeValue: String?) {
		this.parentClassTypeValue = parentClassTypeValue
	}

	override val name: String get() = checkNotNull(nameValue) { "method ref not loaded" }

	public fun setName(nameValue: String?) {
		this.nameValue = nameValue
	}

	override val returnType: String get() = checkNotNull(returnTypeValue) { "method ref not loaded" }

	public fun setReturnType(returnTypeValue: String?) {
		this.returnTypeValue = returnTypeValue
	}

	override val argTypes: List<String> get() = checkNotNull(argTypesValue) { "method ref not loaded" }

	public fun setArgTypes(argTypesValue: List<String>?) {
		this.argTypesValue = argTypesValue
	}

	override fun toString(): String {
		if (nameValue == null) {
			// 尚未 load：输出 uniqId 十六进制便于调试定位
			return Integer.toHexString(uniqIdValue)
		}
		return "$parentClassTypeValue->$nameValue(${Utils.listToStr(argTypes)})$returnTypeValue"
	}
}
