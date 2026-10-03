package jadx.cli.config

/**
 * 配置字段排除标记注解。
 *
 * **做什么**：标注在 [jadx.cli.JadxCLIArgs] 的某个字段上，告诉 Gson 在序列化/反序列化
 * 配置（`.json`）时跳过该字段。
 *
 * **为什么这样写**：原 Java 是 `@Target(FIELD)` 的注解，Gson 的 `ExclusionStrategy`
 * 通过 `Field.getAnnotation(JadxConfigExclude.class)` 判断。Kotlin 的 `annotation class`
 * 编译后同样是 Java 注解接口，保留 `FIELD` 目标即可让注解落在 Kotlin 属性的背后字段上，
 * 因此 `@JadxConfigExclude` 的用法与语义完全不变。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class JadxConfigExclude
