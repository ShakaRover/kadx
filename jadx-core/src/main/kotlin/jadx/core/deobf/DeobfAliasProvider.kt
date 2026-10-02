package jadx.core.deobf

import jadx.api.deobf.IAliasProvider
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.StringUtils

/**
 * jadx 默认的反混淆别名生成器。
 *
 * **用途**：当某个节点需要被重命名时，本类负责生成一个“可读且有辨识度”的新名字：
 * - 包：`p000`；
 * - 类：`[前缀]C0001`，前缀会根据类是否为接口 / 抽象类 / 枚举以及它继承的父类、
 *   实现的接口自动推导（如 `InterfaceRunnable`、`Enum`）；
 * - 字段：`f0`；
 * - 方法：`m0`（覆写方法用 `mo` 前缀，便于区分）。
 *
 * 每个名字后面会附带原名的“可打印片段”，这样即使反混淆后仍能看出一些线索。
 * 名字长度由 `--deobf-max` 控制，超长时退化为 `x` + 原名哈希的十六进制。
 */
class DeobfAliasProvider : IAliasProvider {

	private var pkgIndex = 0
	private var clsIndex = 0
	private var fldIndex = 0
	private var mthIndex = 0

	private var maxLength = 0

	override fun init(root: RootNode) {
		this.maxLength = root.args.deobfuscationMaxLength
	}

	override fun initIndexes(pkg: Int, cls: Int, fld: Int, mth: Int) {
		pkgIndex = pkg
		clsIndex = cls
		fldIndex = fld
		mthIndex = mth
	}

	override fun forPackage(pkg: PackageNode): String = String.format("p%03d%s", pkgIndex++, prepareNamePart(pkg.getPkgInfo().name))

	override fun forClass(cls: ClassNode): String {
		val prefix = makeClsPrefix(cls)
		return String.format("%sC%04d%s", prefix, clsIndex++, prepareNamePart(cls.name))
	}

	override fun forField(fld: FieldNode): String = String.format("f%d%s", fldIndex++, prepareNamePart(fld.getName()))

	override fun forMethod(mth: MethodNode): String {
		val prefix = if (mth.contains(AType.METHOD_OVERRIDE)) "mo" else "m"
		return String.format("%s%d%s", prefix, mthIndex++, prepareNamePart(mth.getName()))
	}

	private fun prepareNamePart(name: String): String {
		if (name.length > maxLength) {
			return "x" + Integer.toHexString(name.hashCode())
		}
		return NameMapper.removeInvalidCharsMiddle(name)
	}

	/**
	 * 根据类的一些特征（枚举 / 接口 / 抽象类 / 父类 / 实现的接口）生成类名前缀，
	 * 让反混淆后的类名带有可读的语义提示。
	 */
	private fun makeClsPrefix(cls: ClassNode): String {
		if (cls.isEnum()) {
			return "Enum"
		}
		val result = StringBuilder()
		if (cls.accessFlags.isInterface()) {
			result.append("Interface")
		} else if (cls.accessFlags.isAbstract()) {
			result.append("Abstract")
		}
		result.append(getBaseName(cls))
		return result.toString()
	}

	companion object {
		/**
		 * 沿继承链向上查找，从父类或实现的接口中推导一个“有意义的父类型名”。
		 *
		 * 例如继承自 `android.app.Activity` 的类会得到 `Activity` 前缀；
		 * 实现 `java.lang.Runnable` 会得到 `Runnable` 前缀。
		 */
		private fun getBaseName(cls: ClassNode): String {
			var currentCls: ClassNode? = cls
			while (currentCls != null) {
				val superCls = currentCls.superClass
				if (superCls != null) {
					val superClsName = superCls.getObject()
					if (superClsName.startsWith("android.app.") || // 例如 Activity 或 Fragment
						superClsName.startsWith("android.os.") // 例如 AsyncTask
					) {
						return getClsName(superClsName)
					}
				}
				// 注意：原实现遍历的是入参 cls 的接口（而非 currentCls），为保持语义一致原样保留
				for (interfaceType in cls.interfaces) {
					val name = interfaceType.getObject()
					if (name == "java.lang.Runnable") {
						return "Runnable"
					}
					if (name.startsWith("java.util.concurrent.") || // 例如 Callable
						name.startsWith("android.view.") || // 例如 View.OnClickListener
						name.startsWith("android.content.") // 例如 DialogInterface.OnClickListener
					) {
						return getClsName(name)
					}
				}
				if (superCls == null) {
					break
				}
				currentCls = cls.root().resolveClass(superCls)
			}
			return ""
		}

		/** 取全限定名的最后一段作为类名，并去掉内部类分隔符 `$`。 */
		private fun getClsName(name: String): String {
			val pgkEnd = name.lastIndexOf('.')
			val clsName = name.substring(pgkEnd + 1)
			return StringUtils.removeChar(clsName, '$')
		}
	}
}
