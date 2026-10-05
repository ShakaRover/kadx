package kadx.core.dex.attributes.nodes

import kadx.api.DecompilationMode
import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType

/**
 * 反编译模式覆盖属性：为单个类指定不同于全局配置的反编译模式。
 *
 * **用途**：某些类用 RESTRUCTURE 模式会失败或结果很差，可以单独标记为 SIMPLE/FALLBACK，
 * 而不影响其它类的默认模式。
 */
class DecompileModeOverrideAttr(val mode: DecompilationMode) : IKadxAttribute {

	override val attrType: IKadxAttrType<DecompileModeOverrideAttr> get() = AType.DECOMPILE_MODE_OVERRIDE

	override fun toString(): String = "DECOMPILE_MODE_OVERRIDE: $mode"
}
