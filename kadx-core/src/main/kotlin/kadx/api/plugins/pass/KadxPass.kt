package kadx.api.plugins.pass

import kadx.api.plugins.pass.types.KadxPassType

/**
 * 所有 pass 的公共基接口。
 *
 * **做什么**：提供 pass 的元信息（[getInfo]）与类型（[getPassType]）。
 * 具体类型的 pass（准备 / 反编译 / 加载后）都继承它。
 *
 * **为什么保留显式 getter**：Java 插件实现与调用方都按 `getInfo()` / `getPassType()`
 * 访问，保持 JVM 方法名不变。
 */
interface KadxPass {

	fun getInfo(): KadxPassInfo

	fun getPassType(): KadxPassType
}
