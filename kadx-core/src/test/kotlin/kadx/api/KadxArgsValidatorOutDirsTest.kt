package kadx.api

import kadx.core.utils.files.FileUtils.toFile
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class KadxArgsValidatorOutDirsTest {
	private lateinit var args: KadxArgs

	@field:TempDir
	private lateinit var testDir: Path

	@Test
	fun checkAllSet() {
		setOutDirs("r", "s", "r")
		checkOutDirs("r", "s", "r")
	}

	@Test
	fun checkRootOnly() {
		setOutDirs("out", null, null)
		checkOutDirs("out", "out/" + KadxArgs.DEFAULT_SRC_DIR, "out/" + KadxArgs.DEFAULT_RES_DIR)
	}

	@Test
	fun checkSrcOnly() {
		setOutDirs(null, "src", null)
		checkOutDirs("src", "src", "src/" + KadxArgs.DEFAULT_RES_DIR)
	}

	@Test
	fun checkResOnly() {
		setOutDirs(null, null, "res")
		checkOutDirs("res", "res/" + KadxArgs.DEFAULT_SRC_DIR, "res")
	}

	@Test
	fun checkNone() {
		setOutDirs(null, null, null)
		val inputFileBase = args.inputFiles[0].getName().replace(".apk", "")
		checkOutDirs(
			inputFileBase,
			inputFileBase + '/' + KadxArgs.DEFAULT_SRC_DIR,
			inputFileBase + '/' + KadxArgs.DEFAULT_RES_DIR,
		)
	}

	private fun setOutDirs(outDir: String?, srcDir: String?, resDir: String?) {
		args = makeArgs()
		args.outDir = toFile(outDir)
		args.outDirSrc = toFile(srcDir)
		args.outDirRes = toFile(resDir)
		LOG.debug("Set dirs: out={}, src={}, res={}", outDir, srcDir, resDir)
	}

	private fun checkOutDirs(outDir: String?, srcDir: String?, resDir: String?) {
		KadxArgsValidator.validate(KadxDecompiler(args))
		LOG.debug("Got dirs: out={}, src={}, res={}", args.outDir, args.outDirSrc, args.outDirRes)
		assertThat(args.outDir).isEqualTo(toFile(outDir))
		assertThat(args.outDirSrc).isEqualTo(toFile(srcDir))
		assertThat(args.outDirRes).isEqualTo(toFile(resDir))
	}

	private fun makeArgs(): KadxArgs = try {
		val args = KadxArgs()
		args.inputFiles.add(Files.createTempFile(testDir, "test-", ".apk").toFile())
		args
	} catch (e: Exception) {
		throw RuntimeException(e)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(KadxArgsValidatorOutDirsTest::class.java)
	}
}
