package kadx.plugins.input.java

import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.api.plugins.input.ICodeLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CustomLoadTest {

	private lateinit var kadx: KadxDecompiler

	@BeforeEach
	fun init() {
		kadx = KadxDecompiler(KadxArgs())
	}

	@AfterEach
	fun close() {
		kadx.close()
	}

	@Test
	fun loadFiles() {
		val files = listOf("HelloWorld.class", "HelloWorld\$HelloInner.class").map { getSample(it) }
		val loadResult: ICodeLoader = JavaInputPlugin.loadClassFiles(files)
		loadDecompiler(loadResult)
		assertThat(kadx.getClassesWithInners())
			.hasSize(2)
			.satisfiesOnlyOnce { cls -> assertThat(cls.getName()).isEqualTo("HelloWorld") }
			.satisfiesOnlyOnce { cls -> assertThat(cls.getName()).isEqualTo("HelloInner") }
	}

	@Test
	fun loadFromInputStream() {
		val fileName = "HelloWorld\$HelloInner.class"
		Files.newInputStream(getSample(fileName)).use { input: InputStream ->
			val loadResult: ICodeLoader = JavaInputPlugin.loadFromInputStream(input, fileName)
			loadDecompiler(loadResult)
			assertThat(kadx.getClassesWithInners())
				.hasSize(1)
				.satisfiesOnlyOnce { cls -> assertThat(cls.getName()).isEqualTo("HelloWorld\$HelloInner") }

			println(kadx.getClassesWithInners()[0].getCode())
		}
	}

	@Test
	fun loadSingleClass() {
		val fileName = "HelloWorld.class"
		val content = Files.readAllBytes(getSample(fileName))
		val loadResult: ICodeLoader = JavaInputPlugin.loadSingleClass(content, fileName)
		loadDecompiler(loadResult)
		assertThat(kadx.getClassesWithInners())
			.hasSize(1)
			.satisfiesOnlyOnce { cls -> assertThat(cls.getName()).isEqualTo("HelloWorld") }

		println(kadx.getClassesWithInners()[0].getCode())
	}

	@Test
	fun load() {
		val loadResult: ICodeLoader = JavaInputPlugin.load { loader ->
			val inputs = ArrayList<JavaClassReader>(2)
			try {
				val hello = "HelloWorld.class"
				val content = Files.readAllBytes(getSample(hello))
				inputs.add(loader.loadClass(content, hello))

				val helloInner = "HelloWorld\$HelloInner.class"
				Files.newInputStream(getSample(helloInner)).use { input ->
					inputs.addAll(loader.loadInputStream(input, helloInner))
				}
			} catch (e: Exception) {
				fail(e)
			}
			inputs
		}
		loadDecompiler(loadResult)
		assertThat(kadx.getClassesWithInners())
			.hasSize(2)
			.satisfiesOnlyOnce { cls -> assertThat(cls.getName()).isEqualTo("HelloWorld") }
			.satisfiesOnlyOnce { cls ->
				assertThat(cls.getName()).isEqualTo("HelloInner")
				assertThat(cls.getCode()).isEmpty() // no code for moved inner class
			}

		assertThat(kadx.getClasses())
			.hasSize(1)
			.satisfiesOnlyOnce { cls -> assertThat(cls.getName()).isEqualTo("HelloWorld") }
			.satisfiesOnlyOnce { cls ->
				assertThat(cls.getInnerClasses()).hasSize(1)
					.satisfiesOnlyOnce { inner -> assertThat(inner.getName()).isEqualTo("HelloInner") }
			}

		kadx.getClassesWithInners().forEach { cls -> println(cls.getCode()) }
	}

	fun loadDecompiler(codeLoader: ICodeLoader) {
		try {
			kadx.addCustomCodeLoader(codeLoader)
			kadx.load()
		} catch (e: Exception) {
			fail("Failed to load sample", e)
		}
	}

	fun getSample(name: String): Path = try {
		Paths.get(checkNotNull(ClassLoader.getSystemResource("samples/" + name)).toURI())
	} catch (e: Exception) {
		throw AssertionError("Failed to load sample", e) // 原 Java 用 assertj fail(...)，效果同为抛 AssertionError
	}
}
