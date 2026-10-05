package kadx.core.dex.instructions

/**
 * 方法调用类型枚举，对应 Dex 的 invoke-* 系列指令。
 *
 * - [STATIC]：静态方法调用（invoke-static）
 * - [DIRECT]：私有方法 / 构造器直调（invoke-direct）
 * - [VIRTUAL]：普通虚方法调用（invoke-virtual）
 * - [INTERFACE]：接口方法调用（invoke-interface）
 * - [SUPER]：父类方法调用（invoke-super）
 * - [POLYMORPHIC]：MethodHandle 多态调用（invoke-polymorphic）
 * - [CUSTOM]：invoke-custom（lambda 等）
 * - [CUSTOM_RAW]：无法完全解析、按原始形式输出的 invoke-custom
 *
 * Kotlin 转换说明：枚举常量名与原 Java 一致，Java 调用方无需改动。
 */
enum class InvokeType {
	STATIC,
	DIRECT,
	VIRTUAL,
	INTERFACE,
	SUPER,
	POLYMORPHIC,
	CUSTOM,
	CUSTOM_RAW,
}
