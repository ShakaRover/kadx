package kadx.api.plugins.input.data.attributes.types

import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 签名属性：存储类/方法/字段的泛型签名（Signature attribute）。
 *
 * **背景**：Java class file / Dex 中，涉及泛型的声明会把完整泛型信息
 * （如 `java.util.List<java.lang.String>`）存在 Signature 属性里，
 * 与擦除后的描述符分开存储。
 *
 * **示例**：类 `class Box<T extends Number>` 的签名是
 * `Lkadx/example/Box<TT;TT;Ljava/lang/Number;>;`（JVM 泛型语法）。
 *
 * @param signature 泛型签名字符串
 *
 * **Kotlin 转换说明**：原 Java 用私有 final 字段 + `getSignature()` getter，
 * 这里改用主构造器属性 `val signature`，字节码生成的 getter 完全相同，
 * Java 调用方（如 kadx-java-input 的 JavaSignatureAttr）零改动。

 * **open 说明**：原 Java 类非 final，kadx-java-input 的 JavaSignatureAttr 继承它，故声明为 `open`。
 */
public open class SignatureAttr(
	/** 泛型签名字符串 */
	public val signature: String,
) : PinnedAttribute() {

	/**
	 * 返回本属性的类型标识：[KadxAttrType.SIGNATURE]。
	 *
	 * 原 Java 声明为 `IKadxAttrType<? extends IKadxAttribute>`，这里用协变的具体类型
	 * `KadxAttrType<SignatureAttr>`（Kotlin 允许对 Java 通配符签名做协变覆写）。
	 */
	override val attrType: KadxAttrType<SignatureAttr> get() = KadxAttrType.SIGNATURE

	/** 调试字符串，格式与原 Java 一致：`SIGNATURE: <签名>` */
	override fun toString(): String = "SIGNATURE: $signature"
}
