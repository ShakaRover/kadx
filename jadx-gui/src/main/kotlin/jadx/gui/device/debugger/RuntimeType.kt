package jadx.gui.device.debugger

import io.github.skylot.jdwp.JDWP

/**
 * JDWP 值类型（Tag）枚举。
 *
 * **做什么**：把 JDWP 协议里的类型标签与人类可读的类型名对应起来，
 * 并提供从 JDWP Tag 到枚举的反查（[fromJdwpTag]）。
 *
 * @param jdwpTag JDWP 协议中的 Tag 字节值
 * @param desc    展示用的类型描述
 */
enum class RuntimeType(private val jdwpTag: Int, private val desc: String) {
	ARRAY(91, "[]"),
	BYTE(66, "byte"),
	CHAR(67, "char"),
	OBJECT(76, "object"),
	FLOAT(70, "float"),
	DOUBLE(68, "double"),
	INT(73, "int"),
	LONG(74, "long"),
	SHORT(83, "short"),
	VOID(86, "void"),
	BOOLEAN(90, "boolean"),
	STRING(115, "string"),
	THREAD(116, "thread"),
	THREAD_GROUP(103, "thread_group"),
	CLASS_LOADER(108, "class_loader"),
	CLASS_OBJECT(99, "class_object"),
	;

	/** @return JDWP 协议中的 Tag 值 */
	val tag: Int get() = jdwpTag

	/** @return 展示用的类型描述 */
	fun getDesc(): String = desc

	companion object {
		/**
		 * 把 JDWP 的 `JDWP.Tag` 转换为 [RuntimeType]。
		 *
		 * @throws SmaliDebuggerException 当遇到未知 Tag 时抛出
		 */
		@Throws(SmaliDebuggerException::class)
		fun fromJdwpTag(tag: Int): RuntimeType = when (tag) {
			JDWP.Tag.ARRAY -> ARRAY
			JDWP.Tag.BYTE -> BYTE
			JDWP.Tag.CHAR -> CHAR
			JDWP.Tag.OBJECT -> OBJECT
			JDWP.Tag.FLOAT -> FLOAT
			JDWP.Tag.DOUBLE -> DOUBLE
			JDWP.Tag.INT -> INT
			JDWP.Tag.LONG -> LONG
			JDWP.Tag.SHORT -> SHORT
			JDWP.Tag.VOID -> VOID
			JDWP.Tag.BOOLEAN -> BOOLEAN
			JDWP.Tag.STRING -> STRING
			JDWP.Tag.THREAD -> THREAD
			JDWP.Tag.THREAD_GROUP -> THREAD_GROUP
			JDWP.Tag.CLASS_LOADER -> CLASS_LOADER
			JDWP.Tag.CLASS_OBJECT -> CLASS_OBJECT
			else -> throw SmaliDebuggerException("Unexpected value: $tag")
		}
	}
}
