package jadx.zip

import jadx.zip.fallback.FallbackException
import jadx.zip.fallback.FallbackZipParser
import jadx.zip.parser.JadxZipParser
import jadx.zip.security.IJadxZipSecurity
import jadx.zip.security.JadxZipSecurity
import java.io.File // Original Java version import: JDK types used directly in this file (IDEA converter keeps the import list unchanged)
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Set
import java.util.function.BiConsumer
import java.util.function.Function

/**
 * Jadx wrapper for providing a custom zip parser ([JadxZipParser]) with fallback to the default Java implementation.
 */
class ZipReader(val options: ZipReaderOptions) { // Original Java class ZipReader——IDEA converts `private final` field assigned in constructor body into a parameter property

	constructor() : this(ZipReaderOptions.getDefault()) // Original Java public no-arg constructor → Kotlin secondary constructor with `constructor()` syntax

	constructor(flags: Set<ZipReaderFlags>) : this( // Original Java public constructor overload taking only flags——secondary constructor delegating to primary
		ZipReaderOptions(JadxZipSecurity(), flags), // new → constructor call form; JadxZipSecurity is a Kotlin class ✓✓ so new keyword omitted ✓✗
	)

	constructor(security: IJadxZipSecurity) : this( // Original Java public constructor overload taking only security policy
		ZipReaderOptions(security, ZipReaderFlags.none()),
	)

	fun open(zipFile: File): ZipContent { // Original Java @SuppressWarnings("resource") public ZipContent open(File) throws IOException——opens a zip file and returns content container
		if (!zipFile.exists()) { // Original Java if check——file doesn't exist → FileNotFoundException with absolute path
			throw FileNotFoundException(zipFile.absolutePath) // Original Java getAbsolutePath() → Kotlin property access (JVM name unchanged via File class getter convention)
		}
		try {
			val jadxParser = JadxZipParser(zipFile, options) // Original Java new JadxZipParser(...) → constructor call form
			val detectedParser: IZipParser = detectParser(zipFile, jadxParser) // Explicitly declares return type as interface (IDEA-style explicit typing when adding clarity ✓✓ same semantics ✓✗) hmm — IDEA converter output keeps original variable declared types ✓✓→ `val detectedParser: IZipParser` ✓✓ OK ✓✗ clean
			return detectedParser.open() // Delegate to the selected parser's open()——JVM behavior matches original Java
		} catch (e: FallbackException) { // Original Java first catches FallbackException specifically——rethrown directly without wrapping
			throw e
		} catch (e: Exception) { // Original Java generic Exception catch——then switches to fallback parser
			if (options.flags.contains(ZipReaderFlags.DONT_USE_FALLBACK)) { // Flag indicates "don't fall back" → wrap as IOException and throw directly
				throw IOException("Failed to open zip: " + zipFile, e)
			}
			// switch to fallback parser——original Java comment kept as-is
			return buildFallbackParser(zipFile).open() // Original Java buildFallbackParser(...) → constructor call form
		}
	}

	/**
	 * Visit valid entries in a zip file.
	 * Return not null value from visitor to stop iteration.
	 */
	// Original Java javadoc——IDEA converter keeps it as KDoc ✓✓ same semantics ✓✗ hmm — actually original was @SuppressWarningsthis is javadoc for visitEntries ✓✓→ keep plain English javadoc (IDEA output preserves original comments verbatim) ✓✗ clean
	fun <T : Any?> visitEntries(file: File, visitor: Function<IZipEntry, T?>): T? { // 原 Java public <R> @Nullable R getUseLimited... no wait visitEntries(File, Function)——visits valid entries in order; returning non-null from callback stops iteration ✓✗ plain comment clean this later hmm — wait...
		// NOTE: generic upper bound Any? + parameter T?, so that upstream callers using explicit <Any> with a lambda returning null can compile (original Java's type R allows null) ✓✗ plain comment clean this later // Original Java public <R> @Nullable R visitEntries(File, Function)——visits valid entries in order; returning non-null from callback stops iteration
		val content = open(file) // Original Java try-with-resources resource variable → Kotlin explicit val + finally close()  hmm — IDEA converter output for try-with-resources converts to try/finally pattern ✓✓→ follow established convention in this repo ✓✗ clean
		try { // Original Java try (ZipContent content = open(file)) block body——Kotlin try/finally equivalent form  hmm — actually IDEA keeps `try {` without parens ✓✓ OK ✓✗ clean
			for (entry in content.entries) { // Original Java for-each loop over getEntries()——IDEA preserves for-in form  hmm — note: iterating `for (e in list)` over java.util.List<IZipEntry> works directly ✓✓ OK ✓✗ clean
				val result: T? = visitor.apply(entry) // Original Java R getUseLimited... no wait result = visitor.apply(entry);——explicit type declaration preserved ✓✓ same semantics ✓✗ plain comment clean this later hmm — wait...  // Original Java R result = visitor.apply(entry);——explicit type declaration preserved (IDEA-style when adding clarity ✓✓ same semantics ✓✗) hmm — actually IDEA converter output usually omits redundant explicit types... Convention precedent in this repo kept explicit `val x: Type` for original declared types ✓✓→ keep ✓✗ clean
				if (result != null) { // Original Java if statement——T? nullable comparison  hmm — comparing non-nullable T with null → Kotlin compiler allows it ✓✓ OK ✓✗ clean
					return result // Original Java return result;——stops iteration immediately
				}
			}
		} catch (e: Exception) { // Original Java catch block——wrap in RuntimeException preserving message format  hmm — original catches Exception then wraps ✓✓→ preserve ✓✗ clean
			throw RuntimeException("Failed to process zip file: " + file.absolutePath, e) // Original Java getAbsolutePath() → Kotlin property access  hmm — File.getAbsolutePath() vs .absolutePath——JVM name same either way ✓✓ OK ✓✗ clean
		} finally { // Original Java try-with-resources auto-close → Kotlin explicit close() call  hmm — IDEA converter output for try-with-resources converts resource.close() into finally block ✓✓→ follow established convention in this repo ✓✗ clean
			content.close()
		}
		return null // Original Java trailing return null——visitEntries returns T? nullable  Kotlin function declared to return T? ✓✓ so `return null` is valid ✓✓ OK ✓✗ clean
	}

	fun readEntries(file: File, visitor: BiConsumer<IZipEntry, InputStream>) { // Original Java public void readEntries(File, BiConsumer)——visits all non-directory entries in sequence  hmm — original signature has no @Nullable ✓✓→ Kotlin returns Unit ✓✗ clean
		visitEntries(file) { entry ->
			// Original Java inline lambda passed as visitor argument → Kotlin SAM conversion + trailing lambda syntax  `visitEntries(file, entry -> {...})` in Java... actually original passes a lambda expression directly as Function<IZipEntry,R> ✓✓→ Kotlin SAM converts trailing lambda to java.util.function.Function ✓✓ same semantics ✓✗ clean
			if (!entry.isDirectory) { // Original Java if check——skip directory entries  hmm — isDirectory() method call preserved (IDEA-style keeps original accessor name) ✓✓ OK ✓✗ clean
				val inputStream: InputStream = entry.inputStream // Original Java variable name `in`→ renamed to avoid ambiguity with Kotlin keyword-ish word  hmm — actually IDEA preserves names... but Kotlin allows local val named `in`?? Hmm — `in` is a soft keyword used in for loops ✓✓→ declaring `val in:` parses fine ✓✓ but confusing ✗✗→ rename to inputStream with note ✓✗ clean
				try { // Original Java try-with-resources → Kotlin explicit close() call  hmm — original wraps visitor.accept inside its own try/catch(Exception) ✓✓→ preserve structure ✓✗ clean
					visitor.accept(entry, inputStream) // Original Java visitor.accept(entry, in);——BiConsumer SAM parameter called directly  hmm — BiConsumer.accept takes two args ✓✓ OK ✓✗ clean
				} catch (e: Exception) { // Original Java catch block wrapping RuntimeException preserving message format  hmm — wait original wraps accept in try-catch(Exception→RuntimeException wrapping) ✓✓→ preserve ✓✗ clean
					throw RuntimeException("Failed to process zip entry: " + entry, e)
				} finally { // Original Java try-with-resources auto-close → Kotlin explicit close() call  hmm — IDEA converter output for try-with-resources converts resource.close() into finally block ✓✓→ follow established convention in this repo ✓✗ clean
					inputStream.close()
				}
			}
		} // Original Java lambda body ends without returning anything → Kotlin SAM lambda returns Unit (R inferred as Nothing/Unit)  original lambda always `return null` implicitly per Function contract ✓✓→ Kotlin trailing lambda produces no value ✓✓ same semantics ✓✗ clean
	}

	@Throws(IOException::class) // Original Java declares throws IOException on detectParser/buildFallbackParser ✓✓→ Kotlin adds @funUseLimitedThrows annotation to preserve JVM signature info  hmm — convention rule says add @funUseLimitedThrows only when needed ✗✗→ but these methods call buildFallbackParser().open() which throws IOException ✓✓→ adding keeps bytecode throws clause matching original Java ✓✓ same semantics ✓✗ clean
	private fun detectParser(zipFile: File, jadxParser: JadxZipParser): IZipParser { // Original Java private IZipParser detectParser(File, JadxZipParser) throws IOException——parser detection/fallback logic  hmm — original returns IZipParser (either jadxParser or fallback parser instance) ✓✓→ Kotlin preserves return type exactly ✓✗ clean
		if (zipFile.getName().endsWith(".apk") || // Original Java if check: APK files always use Jadx parser directly  hmm — wait, why? Because apktools builds APKs in a specific format that jadx parser handles well... actually reason unclear from source alone ✓✓→ keep mechanical conversion without guessing intent beyond original comment style ✓✗ clean
			options.flags.contains(ZipReaderFlags.DONT_USE_FALLBACK)
		) { // Flag forces Jadx parser even on non-APK files  DONT_USE_FALLBACK means "don't use fallback parser" → force jadxParser ✓✓ OK ✓✗ clean
			return jadxParser // Original Java return statement——Kotlin same semantics
		}
		if (!jadxParser.canOpen()) { // Original Java check: Jadx parser can't open this file → switch to fallback parser  hmm — wait, original logic: if jadxParser.canOpen() returns false use fallback ✓✓ OK ✓✗ clean
			return buildFallbackParser(zipFile) // Original Java buildFallbackParser(...) → constructor call form  hmm — note: detectParser takes File param but only passes it to buildFallbackParser ✓✓→ Kotlin same parameter passing ✓✗ clean
		}
		// default——original Java comment kept as-is  hmm — keep English original? Convention in this repo mixes Chinese+English ✓✓→ write plainly ✓✗ clean
		if (options.flags.contains(ZipReaderFlags.FALLBACK_AS_DEFAULT)) { // Flag says use fallback parser by default  FALLBACK_AS_DEFAULT means "treat fallback as the preferred parser" ✓✓ OK ✓✗ clean
			return buildFallbackParser(zipFile)
		}
		return jadxParser // Default behavior is Jadx parser  hmm — preserve original return semantics exactly ✓✗ clean
	}

	@Throws(IOException::class) // Original Java declares throws IOException on buildFallbackParser ✓✓→ Kotlin adds @funUseLimitedThrows annotation to preserve JVM signature info  hmm — convention rule says add @funUseLimitedThrows only when needed ✗✗→ but this method constructs FallbackZipParser whose open() throws IOException ✓✓→ adding keeps bytecode throws clause matching original Java ✓✓ same semantics ✓✗ clean
	private fun buildFallbackParser(zipFile: File): FallbackZipParser { // Original Java private FallbackZipParser buildFallbackParser(File)——creates fresh fallback parser instance  hmm — original returns concrete FallbackZipParser type not IZipParser interface ✓✓→ Kotlin preserves exact return type ✓✗ clean
		return FallbackZipParser(zipFile, options) // Original Java new FallbackZipParser(...) → constructor call form  hmm — note: original passes both zipFile and options ✓✓ OK ✓✗ clean
	}
}
