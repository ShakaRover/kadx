package jadx.plugins.input.java.data

import jadx.api.plugins.input.data.IMethodRef
import jadx.plugins.input.java.JavaClassReader
import jadx.plugins.input.java.utils.DescriptorParser

/**
 * class 文件中的方法引用（所属类 + 名字 + 描述符），可缓存唯一 id。
 *
 **做什么**：常量池/方法表里的方法条目都封装为本对象；[load] 惰性解析描述符填充原型，
 * [initUniqId] 把"读取器 id + 方法序号"打包进一个 int 供跨模块快速识别（超限时禁用缓存）。
 */
class JavaMethodRef :
	JavaMethodProto(),
	IMethodRef {

	private var uniqId = 0
	private var parentClassType: String? = null
	private var name: String? = null
	private var descr: String? = null

	override fun getUniqId(): Int = uniqId

	fun initUniqId(clsReader: JavaClassReader, id: Int, fromConstPool: Boolean) {
		val readerId = clsReader.getId()
		if (readerId > 0xFFFF || id > 0x7FFF) {
			// loaded more than 65535 classes or more than 32767 methods in this class -> disable caching
			uniqId = 0
		} else {
			val source = if (fromConstPool) 0 else 0x8000
			uniqId = ((readerId and 0xFFFF) shl 16) or source or (id and 0x7FFF)
		}
	}

	// 接口声明非空；实际调用前 setParentClassType/setName 必已执行，提前调用时原 Java 同样 NPE
	override fun getParentClassType(): String = parentClassType ?: throw NullPointerException("parentClassType is null")

	fun setParentClassType(parentClassType: String?) {
		this.parentClassType = parentClassType
	}

	override fun getName(): String = name ?: throw NullPointerException("name is null")

	fun setName(name: String?) {
		this.name = name
	}

	fun getDescriptor(): String? = descr

	fun setDescr(descr: String?) {
		this.descr = descr
	}

	fun reset() {
		setReturnType(null)
		setArgTypes(null)
	}

	override fun load() {
		// 直接查 backing 字段：getter 在未加载时会 NPE，而这里需要 null 判断实现惰性解析
		if (retType == null) {
			DescriptorParser.fillMethodProto(checkNotNull(descr), this)
		}
	}

	override fun toString(): String = parentClassType + "->" + name + descr
}
