package kadx.gui.device.debugger

import kadx.api.KadxDecompiler
import kadx.api.JavaClass
import kadx.core.deobf.NameMapper
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.nodes.ClassNode
import kadx.core.utils.android.AndroidManifestParser
import kadx.core.utils.android.AppAttribute
import kadx.core.utils.android.ApplicationParams
import kadx.gui.device.debugger.smali.Smali
import kadx.gui.device.debugger.smali.SmaliMethodNode
import kadx.gui.treemodel.JClass
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.EnumSet

/**
 * 调试相关的通用工具方法集合。
 *
 * **做什么**：
 * - 缓存并获取 smali 反汇编结果（[getSmali]）；
 * - 在「smali 行号」「代码偏移」「方法签名」之间做转换；
 * - 解析 AndroidManifest 得到应用包名与主 Activity（[parseAppData]）。
 *
 * **为什么全部是静态方法**：原 Java 全是 `static`，Kotlin 用 `object` + `@JvmStatic`
 * 保持调用方式不变。
 */
object DbgUtils {
	private val LOG: Logger = LoggerFactory.getLogger(DbgUtils::class.java)

	/** 类信息 -> smali 反汇编结果缓存。 */
	private val smaliCache: MutableMap<ClassInfo, Smali> = HashMap()

	/** 获取（必要时生成）指定类的 smali 反汇编结果。 */
	internal fun getSmali(topCls: ClassNode): Smali = smaliCache.computeIfAbsent(topCls.topParentClass.classInfo) { Smali.disassemble(topCls) }

	/** @return 指定类的 smali 代码文本 */
	fun getSmaliCode(topCls: ClassNode): String = getSmali(topCls).getCode()

	/** 按 smali 行号定位到方法全名与代码偏移。 */
	fun getCodeOffsetInfoByLine(cls: JClass, line: Int): Map.Entry<String, Int>? {
		val smali = getSmali(cls.getCls().getClassNode().topParentClass)
		return smali.getMthFullIDAndCodeOffsetByLine(line)
	}

	/** 按方法全名取 smali 方法调试元数据。 */
	fun getSmaliMethodNode(cls: JClass, mthRawFullID: String): SmaliMethodNode? {
		val smali = getSmali(cls.getCls().getClassNode().topParentClass)
		return smali.getMethodNode(mthRawFullID)
	}

	/** 打印「smali 行号 -> 代码偏移」映射（调试用）。 */
	fun printSmaliLineMapping(smn: SmaliMethodNode) {
		for ((line, codeOffset) in smn.getLineMapping()) {
			LOG.debug("line={} -> codeOffset={}", line, codeOffset)
		}
	}

	/** 把 `类签名.方法签名` 拆成 `[类签名, 方法签名]`；无法拆分时返回 null。 */
	fun sepClassAndMthSig(fullSig: String): Array<String>? {
		var pos = fullSig.indexOf('(')
		if (pos != -1) {
			pos = fullSig.lastIndexOf('.', pos)
			if (pos != -1) {
				return arrayOf(fullSig.substring(0, pos), fullSig.substring(pos + 1))
			}
		}
		return null
	}

	/** 把 `Lfoo/Bar;` 形式的类签名转为 `foo.Bar`（不替换 `$`）。 */
	fun classSigToRawFullName(clsSig: String?): String? {
		var sig = clsSig
		if (sig != null && sig.startsWith("L") && sig.endsWith(";")) {
			sig = sig.substring(1, sig.length - 1).replace("/", ".")
		}
		return sig
	}

	/** 把 `Lfoo/Bar$Inner;` 形式的类签名转为 `foo.Bar.Inner`（替换 `$`）。 */
	fun classSigToFullName(clsSig: String?): String? {
		var sig = clsSig
		if (sig != null && sig.startsWith("L") && sig.endsWith(";")) {
			sig = sig.substring(1, sig.length - 1)
				.replace("/", ".")
				.replace("$", ".")
		}
		return sig
	}

	/** @return 顶层类的原始全名 */
	fun getRawFullName(topCls: JClass): String = topCls.getCls().getClassNode().classInfo.makeRawFullName()

	/** @return 该对象签名是否表示 `java.lang.String` */
	fun isStringObjectSig(objectSig: String): Boolean = objectSig == "Ljava/lang/String;"

	/** 按类签名查找顶层类节点。 */
	fun getTopClassBySig(clsSig: String, mainWindow: MainWindow): JClass? {
		val fullName = classSigToFullName(clsSig) ?: return null
		val cls = mainWindow.getWrapper().getDecompiler()
			.searchJavaClassOrItsParentByOrigFullName(fullName)
		if (cls != null) {
			val jc = mainWindow.getCacheObject().nodeCache.makeFrom(cls)
			return jc?.getRootClass()
		}
		return null
	}

	/** 由 Java 类构造界面节点。 */
	fun getJClass(cls: JavaClass, mainWindow: MainWindow): JClass = checkNotNull(mainWindow.getCacheObject().nodeCache.makeFrom(cls))

	/** 按类签名查找类节点。 */
	fun getClassNodeBySig(clsSig: String, mainWindow: MainWindow): ClassNode? {
		val fullName = classSigToFullName(clsSig) ?: return null
		return mainWindow.getWrapper().getDecompiler().searchClassNodeByOrigFullName(fullName)
	}

	/** @return 该字符是否是可打印 ASCII 字符 */
	fun isPrintableChar(c: Int): Boolean = 32 <= c && c <= 126

	/** 应用包名与主 Activity 的组合信息。 */
	class AppData(private val appPackage: String, private val mainActivityCls: JavaClass) {
		fun getAppPackage(): String = appPackage

		fun getMainActivityCls(): JavaClass = mainActivityCls

		val processName: String get() = appPackage + '/' + mainActivityCls.getClassNode().classInfo.fullName
	}

	/** 从 AndroidManifest 解析应用包名与主 Activity；失败时弹窗并返回 null。 */
	fun parseAppData(mw: MainWindow): AppData? {
		val decompiler: KadxDecompiler = mw.getWrapper().getDecompiler()
		val appPkg = decompiler.getRoot()?.getAppPackage()
		if (appPkg == null) {
			UiUtils.errorMessage(mw, NLS.str("error_dialog.not_found", "App package"))
			return null
		}
		val parser = AndroidManifestParser(
			AndroidManifestParser.getAndroidManifest(decompiler.getResources()),
			EnumSet.of(AppAttribute.MAIN_ACTIVITY),
			decompiler.getArgs().security,
		)
		if (!parser.isManifestFound()) {
			UiUtils.errorMessage(mw, NLS.str("error_dialog.not_found", "AndroidManifest.xml"))
			return null
		}
		val results: ApplicationParams = parser.parse()
		val mainActivityName = results.mainActivity
		if (mainActivityName == null) {
			UiUtils.errorMessage(mw, NLS.str("adb_dialog.msg_read_mani_fail"))
			return null
		}
		if (!NameMapper.isValidFullIdentifier(mainActivityName)) {
			UiUtils.errorMessage(mw, "Invalid main activity name")
			return null
		}
		val mainActivityClass = results.getMainActivityJavaClass(decompiler)
		if (mainActivityClass == null) {
			UiUtils.errorMessage(mw, NLS.str("error_dialog.not_found", "Main activity class"))
			return null
		}
		return AppData(appPkg, mainActivityClass)
	}
}
