package kadx.zip

import kadx.zip.security.IKadxZipSecurity
import kadx.zip.security.KadxZipSecurity
import java.util.Set

/**
 * Zip 解析器的配置：安全策略（[zipSecurity]）+ 行为标志集（[flags]）。
 *
 * Property names match the original Java field names, so Kotlin auto-generates getZipSecurity() / getFlags(),
 * and existing Java callers require no changes.
 */
class ZipReaderOptions(val zipSecurity: IKadxZipSecurity, val flags: Set<ZipReaderFlags>) {
	companion object {
		/** Returns default options (built-in KadxZipSecurity security strategy + empty flag set) */
		fun getDefault(): ZipReaderOptions = ZipReaderOptions(KadxZipSecurity(), ZipReaderFlags.none())
	}
}
