package jadx.zip

import java.util.Set
import jadx.zip.security.IJadxZipSecurity
import jadx.zip.security.JadxZipSecurity

/**
 * Zip 解析器的配置：安全策略（[zipSecurity]）+ 行为标志集（[flags]）。
 *
 * Property names match the original Java field names, so Kotlin auto-generates getZipSecurity() / getFlags(),
 * and existing Java callers require no changes.
 */
class ZipReaderOptions(val zipSecurity: IJadxZipSecurity, val flags: Set<ZipReaderFlags>) {
	companion object {
		/** Returns default options (built-in JadxZipSecurity security strategy + empty flag set) */
		@JvmStatic
		fun getDefault(): ZipReaderOptions = ZipReaderOptions(JadxZipSecurity(), ZipReaderFlags.none())
	}
}
