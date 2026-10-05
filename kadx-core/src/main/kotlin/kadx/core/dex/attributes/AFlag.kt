package kadx.core.dex.attributes

/**
 * 布尔型属性标记（Flag）枚举。
 *
 * **什么是 AFlag？**
 * 反编译过程中，节点（类/方法/字段/指令/基本块等）常常只需要记录“有/没有”某条信息，
 * 这类信息就用枚举常量表示，比新建一个属性对象更轻量。它们被存放在
 * [AttributeStorage] 的 `EnumSet` 中（见 [AttributeStorage.flags]）。
 *
 * **为什么用 EnumSet？**
 * `EnumSet` 内部用位向量（bit vector）实现，判断 `contains` 极快且内存占用小。
 * [AttributeStorage] 的静态初始化块会检查常量数量必须小于 64，
 * 以保证一个 `long` 就能装下所有标记（这正是 `EnumSet` 的优化前提）。
 *
 * **Kotlin 转换说明**：枚举直接平替为 Kotlin `enum class`，Java 调用方仍写
 * `AFlag.XXX` / `AFlag.values()`，JVM 表面完全不变。
 */
enum class AFlag {
	MTH_ENTER_BLOCK,
	MTH_EXIT_BLOCK,

	TRY_ENTER,
	TRY_LEAVE,

	LOOP_START,
	LOOP_END,

	SYNTHETIC,

	/** 该基本块只包含 return 指令 */
	RETURN,
	ORIG_RETURN,

	DONT_WRAP,
	DONT_INLINE,
	DONT_INLINE_CONST,

	/** 不要反转这个 if 语句 */
	DONT_INVERT,

	/** 照常处理，但不要输出到生成代码中 */
	DONT_GENERATE,

	/** 照常处理，但在生成代码中把该指令注释掉 */
	COMMENT_OUT,

	/** 可以被完全移除 */
	REMOVE,

	/** 不要添加父类 */
	REMOVE_SUPER_CLASS,

	/** 该指令被其它指令内部使用，但不列在参数列表中 */
	HIDDEN,

	/** 枚举类已成功还原为原始形式 */
	CONVERTED_ENUM,

	/** 反混淆时不要重命名 */
	DONT_RENAME,

	/** 强制使用原始名称而不是别名 */
	FORCE_RAW_NAME,

	ADDED_TO_REGION,
	DUPLICATED,

	/** 该循环条件已被合并，或者不应再受“单指令限制”约束 */
	ALLOW_MULTIPLE_INSNS_LOOP_COND,

	EXC_TOP_SPLITTER,
	EXC_BOTTOM_SPLITTER,
	FINALLY_INSNS,
	IGNORE_THROW_SPLIT,

	SKIP_FIRST_ARG,

	/** invoke 调用中跳过该参数 */
	SKIP_ARG,
	NO_SKIP_ARGS,

	ANONYMOUS_CONSTRUCTOR,
	INLINE_INSTANCE_FIELD,

	THIS,
	SUPER,

	PACKAGE_INFO,

	/** 标记 Android 资源类（R 类） */
	ANDROID_R_CLASS,

	/** 方法参数的 RegisterArg 标记 */
	METHOD_ARGUMENT,

	/** RegisterArg 或 SSAVar 的类型不允许再被改变 */
	IMMUTABLE_TYPE,

	/** 强制内联带赋值的内联指令 */
	FORCE_ASSIGN_INLINE,

	/** 该寄存器的变量不需要声明 */
	CUSTOM_DECLARE,
	DECLARE_VAR,

	ELSE_IF_CHAIN,

	WRAPPED,
	ARITH_ONEARG,

	FALL_THROUGH,

	VARARG_CALL,

	/** 使用显式类型的常量：强制转换 `(byte) 1` 或类型后缀 `7L` */
	EXPLICIT_PRIMITIVE_TYPE,
	EXPLICIT_CAST,

	/** 合成转换，用于辅助类型推断（允许泛型未检查转换） */
	SOFT_CAST,

	/** 警告：反编译结果可能不正确 */
	INCONSISTENT_CODE,

	/** 请求再次运行 if 区域优化 */
	REQUEST_IF_REGION_OPTIMIZE,
	REQUEST_CODE_SHRINK,

	METHOD_CANDIDATE_FOR_INLINE,

	/** 方法中的源码行信息可信 */
	USE_LINES_HINTS,

	DISABLE_BLOCKS_LOCK,

	// ===== 类处理相关标记 =====

	/** 需要重新执行代码生成 */
	RESTART_CODEGEN,

	/** 类无法在 process 阶段分析 => 在 codegen 阶段前卸载 */
	RELOAD_AT_CODEGEN_STAGE,

	/** 在 process 之前执行深度类卸载（重载） */
	CLASS_DEEP_RELOAD,

	/** 类已被完全卸载 */
	CLASS_UNLOADED,

	/** 代码生成后不要卸载类（仅用于测试和调试！） */
	DONT_UNLOAD_CLASS,

	RESOLVE_JAVA_JSR,
	COMPUTE_POST_DOM,
}
