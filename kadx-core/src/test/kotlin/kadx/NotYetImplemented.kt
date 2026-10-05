package kadx

/**
 * 标记一个已知会失败的测试。
 *
 * 被标记的测试若失败则视为成功；反之若通过则视为失败，
 * 这样可以在相关 issue 被意外修复时及时得到提醒。
 *
 * 需要配合测试类上的扩展使用：
 *
 * ```
 * @ExtendWith(NotYetImplementedExtension::class)
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class NotYetImplemented(val value: String = "")
