package jadx.api

import jadx.api.impl.SimpleCodeInfo
import jadx.api.metadata.ICodeMetadata

/**
 * 反编译后单个类的代码信息（代码文本 + 元数据）。
 *
 * 这是公共 API：jadx-cli / jadx-gui / 插件都会读取它。接口属性在 JVM 上仍生成
 * `getCodeStr()` / `getCodeMetadata()`，Java 实现方与调用方零改动。
 */
interface ICodeInfo {

	/** 获取反编译后的代码字符串。 */
	val codeStr: String

	/** 获取代码元数据（用于定位类/方法/字段/变量的代码位置）。 */
	val codeMetadata: ICodeMetadata

	/** 是否包含元数据。 */
	fun hasMetadata(): Boolean

	companion object {
		/**
		 * 空代码信息单例。
		 *
		 * 用 `@JvmField` 暴露为接口上的静态字段，Java 侧 `ICodeInfo.EMPTY` 写法保持不变；
		 * Kotlin 侧 `ICodeInfo.EMPTY` 也直接可用。注意这是共享单例，比较时必须用 `===`。
		 */
		@JvmField
		val EMPTY: ICodeInfo = SimpleCodeInfo("")
	}
}
