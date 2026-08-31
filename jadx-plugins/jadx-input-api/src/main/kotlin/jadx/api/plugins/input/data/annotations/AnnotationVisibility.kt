package jadx.api.plugins.input.data.annotations

/**
 * 注解的可见性（保留策略）枚举。
 *
 * Java 注解有三种保留策略，决定了注解在哪个阶段可用：
 *
 * | 可见性 | 源码 | 编译后.class | 运行时反射 |
 * |--------|------|--------------|------------|
 * | BUILD  | ✅   | ❌           | ❌         |
 * | RUNTIME| ✅   | ✅           | ✅         |
 * | SYSTEM | ✅   | ✅（JADX 扩展）| ❌        |
 *
 * **BUILD**：编译时可见，用于编译器检查（如 @Override、@Deprecated）
 * **RUNTIME**：运行时可通过反射获取（如 Spring 的 @Autowired）
 * **SYSTEM**：JADX 自定义，表示 Dex 中的系统注解（如 Android 的 runtime-visible/hidden annotations）
 */
public enum class AnnotationVisibility(
	/** 人类可读的描述 */
	public val description: String,
) {
	/** 编译时可见（默认保留策略）*/
	BUILD("Build-time visibility (compile-only)"),

	/** 运行时可见（可通过反射获取）*/
	RUNTIME("Runtime visibility (reflectable)"),

	/** 系统注解（Dex 特有，JADX 扩展）*/
	SYSTEM("System annotation (Dex-specific)"),
}
