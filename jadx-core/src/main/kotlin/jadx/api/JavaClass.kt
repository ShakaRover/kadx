package jadx.api

import jadx.api.metadata.ICodeAnnotation
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.nodes.ClassNode
import jadx.core.utils.ListUtils
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

/**
 * 类的 Java 视图：把内部 [ClassNode] 包装成对插件/GUI 友好的对象。
 *
 * 公共 API，getter 全部保留显式函数形态（JVM 方法名与原来一致）。
 * 该类是 final 且使用自定义 equals/hashCode（基于 [ClassNode]），禁止改成 `data class`。
 *
 * 说明：`decompiler` / `parent` 都可能为 null：
 * - 顶层类持有 decompiler、没有 parent；
 * - 内部类持有 parent、没有 decompiler（通过 parent 逐级向上获取反编译器）。
 */
class JavaClass : JavaNode {
	private val decompiler: JadxDecompiler?
	private val cls: ClassNode
	private val parent: JavaClass?

	private var innerClasses: List<JavaClass> = emptyList()
	private var inlinedClasses: List<JavaClass> = emptyList()
	private var fields: List<JavaField> = emptyList()
	private var methods: List<JavaMethod> = emptyList()
	private var listsLoaded = false

	internal constructor(classNode: ClassNode, decompiler: JadxDecompiler) {
		this.decompiler = decompiler
		this.cls = classNode
		this.parent = null
	}

	/**
	 * 内部类构造器。
	 */
	internal constructor(classNode: ClassNode, parent: JavaClass) {
		this.decompiler = null
		this.cls = classNode
		this.parent = parent
	}

	/** 反编译后的代码字符串。 */
	fun getCode(): String = getCodeInfo().getCodeStr()

	/** 反编译后的代码信息（触发反编译并加载内部列表）。 */
	fun getCodeInfo(): ICodeInfo {
		val code = load()
		if (code != null) {
			return code
		}
		return cls.decompile()
	}

	/** 触发反编译（不关心结果时使用）。 */
	fun decompile() {
		load()
	}

	/**
	 * 判断调用 load() 是否可能触发昂贵的反编译操作。
	 */
	fun loadingWouldRequireDecompilation(): Boolean {
		if (listsLoaded) {
			// 列表已加载，无论类状态如何都是安全的
			return false
		}
		if (cls.state.isProcessComplete()) {
			// 反编译已完成
			return false
		}
		return true
	}

	/** 重新反编译并刷新缓存。 */
	@Synchronized
	fun reload(): ICodeInfo {
		listsLoaded = false
		return cls.reloadCode()
	}

	/** 卸载已加载的代码，释放内存。 */
	fun unload() {
		listsLoaded = false
		cls.unloadCode()
	}

	/** 该类是否被标记为“不生成代码”。 */
	fun isNoCode(): Boolean = cls.contains(AFlag.DONT_GENERATE)

	/** 是否为内部类。 */
	fun isInner(): Boolean = cls.isInner()

	/** 获取 smali 反汇编文本。 */
	@Synchronized
	fun getSmali(): String = cls.disassembledCode

	override fun isOwnCodeAnnotation(ann: ICodeAnnotation): Boolean {
		if (ann.getAnnType() == ICodeAnnotation.AnnType.CLASS) {
			return ann == cls
		}
		return false
	}

	override fun getCodeNodeRef(): ICodeNodeRef = cls

	/**
	 * 内部 API，非稳定。
	 */
	@ApiStatus.Internal
	fun getClassNode(): ClassNode = cls

	/** 获取指定位置的代码注解。 */
	fun getAnnotationAt(pos: Int): ICodeAnnotation? = getCodeInfo().getCodeMetadata().getAt(pos)

	/** 位置 -> Java 节点的使用映射。 */
	fun getUsageMap(): Map<Int, JavaNode> {
		val map = getCodeInfo().getCodeMetadata().getAsMap()
		if (map.isEmpty() || decompiler == null) {
			return emptyMap()
		}
		val resultMap = HashMap<Int, JavaNode>(map.size)
		for ((codePosition, obj) in map) {
			if (obj is ICodeNodeRef) {
				val node = getRootDecompiler().getJavaNodeByRef(obj)
				if (node != null) {
					resultMap[codePosition] = node
				}
			}
		}
		return resultMap
	}

	/** 查找 [javaNode] 在代码信息中的全部使用位置。 */
	fun getUsePlacesFor(codeInfo: ICodeInfo, javaNode: JavaNode): List<Int> {
		if (!codeInfo.hasMetadata()) {
			return emptyList()
		}
		val result = ArrayList<Int>()
		codeInfo.getCodeMetadata().searchDown<Any?>(0) { pos, ann ->
			if (javaNode.isOwnCodeAnnotation(ann)) {
				result.add(pos)
			}
			null
		}
		return result
	}

	override fun getUseIn(): List<JavaNode> = getRootDecompiler().convertNodes(cls.getUseIn())

	/** 反编译行号 -> 源码行号。 */
	fun getSourceLine(decompiledLine: Int): Int? = getCodeInfo().getCodeMetadata().getLineMapping()[decompiledLine]

	/**
	 * 反编译类并加载字段/方法等内部列表；已加载则直接返回 null。
	 *
	 * @return 本次实际执行了反编译时返回代码信息，否则返回 null
	 */
	@Synchronized
	private fun load(): ICodeInfo? {
		if (listsLoaded) {
			return null
		}
		val code: ICodeInfo? = if (cls.state.isProcessComplete()) {
			// 已经反编译过 -> 类内部信息已加载
			null
		} else {
			cls.decompile()
		}
		loadLists()
		return code
	}

	private fun loadLists() {
		listsLoaded = true
		val rootDecompiler = getRootDecompiler()
		val inClsCount = cls.innerClasses.size
		if (inClsCount != 0) {
			val list = ArrayList<JavaClass>(inClsCount)
			for (inner in cls.innerClasses) {
				if (!inner.contains(AFlag.DONT_GENERATE)) {
					val javaClass = rootDecompiler.convertClassNode(inner)
					javaClass.loadLists()
					list.add(javaClass)
				}
			}
			this.innerClasses = Collections.unmodifiableList(list)
		}
		val inlinedClsCount = cls.getInlinedClasses().size
		if (inlinedClsCount != 0) {
			val list = ArrayList<JavaClass>(inlinedClsCount)
			for (inner in cls.getInlinedClasses()) {
				val javaClass = rootDecompiler.convertClassNode(inner)
				javaClass.loadLists()
				list.add(javaClass)
			}
			this.inlinedClasses = Collections.unmodifiableList(list)
		}

		val fieldsCount = cls.fields.size
		if (fieldsCount != 0) {
			val flds = ArrayList<JavaField>(fieldsCount)
			for (f in cls.fields) {
				if (!f.contains(AFlag.DONT_GENERATE)) {
					flds.add(rootDecompiler.convertFieldNode(f))
				}
			}
			this.fields = Collections.unmodifiableList(flds)
		}

		val methodsCount = cls.methods.size
		if (methodsCount != 0) {
			val mths = ArrayList<JavaMethod>(methodsCount)
			for (m in cls.methods) {
				if (!m.contains(AFlag.DONT_GENERATE)) {
					mths.add(rootDecompiler.convertMethodNode(m))
				}
			}
			mths.sortWith(compareBy { it.getName() })
			this.methods = Collections.unmodifiableList(mths)
		}
	}

	internal fun getRootDecompiler(): JadxDecompiler {
		if (parent != null) {
			return parent.getRootDecompiler()
		}
		return checkNotNull(decompiler)
	}

	fun getInnerClasses(): List<JavaClass> {
		load()
		return innerClasses
	}

	fun getInlinedClasses(): List<JavaClass> {
		load()
		return inlinedClasses
	}

	fun getFields(): List<JavaField> {
		load()
		return fields
	}

	fun getMethods(): List<JavaMethod> {
		load()
		return methods
	}

	/** 按短签名（名称+参数）查找方法。 */
	fun searchMethodByShortId(shortId: String): JavaMethod? {
		val methodNode = cls.searchMethodByShortId(shortId) ?: return null
		return getRootDecompiler().convertMethodNode(methodNode)
	}

	/** 本类依赖的其它类。 */
	fun getDependencies(): List<JavaClass> {
		val d = getRootDecompiler()
		return ListUtils.map(cls.dependencies) { node -> d.convertClassNode(node) }
	}

	/** 依赖数量（含递归依赖计数）。 */
	fun getTotalDepsCount(): Int = cls.totalDepsCount

	override fun removeAlias() {
		cls.removeAlias()
	}

	override fun getDefPos(): Int = cls.getDefPosition()

	override fun getName(): String = cls.shortName

	override fun getFullName(): String = cls.fullName

	/** 原始（未去混淆）类名。 */
	fun getRawName(): String = cls.rawName

	/** 类所在包名。 */
	fun getPackage(): String = cls.`package`

	/** 类所在包的 Java 视图；包尚未转换时可能为 null。 */
	fun getJavaPackage(): JavaPackage? = cls.packageNode.javaNode

	override fun getDeclaringClass(): JavaClass? = parent

	/** 原始顶层父类（不随内联/移动而变化）。 */
	fun getOriginalTopParentClass(): JavaClass = if (parent == null) this else parent.getOriginalTopParentClass()

	/**
	 * 返回包含本类代码的顶层父类。
	 * 代码父类可能与原始父类不同（例如移动或内联之后）。
	 *
	 * @return 若自身已是顶层类则返回 this
	 */
	override fun getTopParentClass(): JavaClass {
		val codeParent = getCodeParent()
		return codeParent?.getTopParentClass() ?: this
	}

	/**
	 * 返回包含本类代码的父类。
	 * 代码父类可能与原始父类不同（例如移动或内联之后）。
	 */
	fun getCodeParent(): JavaClass? {
		val anonymousClsAttr = cls.get(AType.ANONYMOUS_CLASS)
		if (anonymousClsAttr != null) {
			// 已移动到使用它的类中
			return getRootDecompiler().convertClassNode(anonymousClsAttr.outerCls)
		}
		val inlinedAttr = cls.get(AType.INLINED)
		if (inlinedAttr != null) {
			return getRootDecompiler().convertClassNode(inlinedAttr.inlineCls)
		}
		return parent
	}

	/** 访问修饰符信息。 */
	fun getAccessInfo(): AccessInfo = cls.accessFlags

	override fun equals(other: Any?): Boolean = this === other || (other is JavaClass && cls == other.cls)

	override fun hashCode(): Int = cls.hashCode()

	override fun toString(): String = getFullName()
}
