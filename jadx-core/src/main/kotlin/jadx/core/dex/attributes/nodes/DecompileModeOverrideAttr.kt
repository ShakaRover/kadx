package jadx.core.dex.attributes.nodes

import jadx.api.DecompilationMode
import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType

/**
 * 反编译模式覆盖属性：为单个类指定不同于全局配置的反编译模式。
 *
 * **用途**：某些类用 RESTRUCTURE 模式会失败或结果很差，可以单独标记为 SIMPLE/FALLBACK，
 * 而不影响其它类的默认模式。
 */
class DecompileModeOverrideAttr(val mode: DecompilationMode) : IJadxAttribute {

	override fun getAttrType(): IJadxAttrType<DecompileModeOverrideAttr> = AType.DECOMPILE_MODE_OVERRIDE

	override fun toString(): String = "DECOMPILE_MODE_OVERRIDE: $mode"
}
