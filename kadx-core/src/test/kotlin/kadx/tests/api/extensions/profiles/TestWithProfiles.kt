package kadx.tests.api.extensions.profiles

import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 在多个 [TestProfile] 下重复运行同一个测试方法。
 *
 * 被标注的方法**不能**再有 `@Test`，由 [KadxTestProfilesExtension] 负责生成调用。
 */
@TestTemplate
@ExtendWith(KadxTestProfilesExtension::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class TestWithProfiles(vararg val value: TestProfile)
