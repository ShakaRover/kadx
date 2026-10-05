package kadx.api.security

import java.util.EnumSet

/**
 * 安全特性开关：控制 kadx 在处理不可信输入时启用哪些防护措施。
 *
 * **做什么**：
 * - [VERIFY_APP_PACKAGE]：校验应用包名格式是否合法；
 * - [SECURE_XML_PARSER]：使用禁止 DTD / 外部实体的安全 XML 解析器；
 * - [SECURE_ZIP_READER]：启用 zip 路径穿越、zip 炸弹等防护；
 * - [SANITIZE_STRINGS]：对导出脚本里的字符串做转义。
 *
 * **为什么这样写**：这是公共 API，枚举常量名必须与原 Java 一致。原 Java 的静态方法
 * `all()` / `none()` 放入 `companion object` 并加 `@JvmStatic`，Java 调用方写法
 * `KadxSecurityFlag.all()` 保持不变。
 */
enum class KadxSecurityFlag {
	VERIFY_APP_PACKAGE,
	SECURE_XML_PARSER,
	SECURE_ZIP_READER,
	SANITIZE_STRINGS,
	;

	companion object {
		/** 返回包含全部开关的可变集合（对应原 Java 的 `EnumSet.allOf(...)`）。 */
		@JvmStatic
		fun all(): Set<KadxSecurityFlag> = EnumSet.allOf(KadxSecurityFlag::class.java)

		/** 返回不包含任何开关的可变集合（对应原 Java 的 `EnumSet.noneOf(...)`）。 */
		@JvmStatic
		fun none(): Set<KadxSecurityFlag> = EnumSet.noneOf(KadxSecurityFlag::class.java)
	}
}
