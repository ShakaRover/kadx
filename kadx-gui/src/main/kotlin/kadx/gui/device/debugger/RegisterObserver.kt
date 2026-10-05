package kadx.gui.device.debugger

import kadx.core.dex.instructions.args.ArgType
import kadx.gui.device.debugger.SmaliDebugger.RuntimeVarInfo
import kadx.gui.device.debugger.smali.SmaliRegister

/**
 * smali 寄存器与运行时（JDWP）变量信息的合并视图。
 *
 * **做什么**：把 smali 反汇编得到的寄存器列表与远端 JVM 的调试变量表按运行时编号对齐，
 * 提供「某代码偏移处寄存器的别名/类型」「某运行时编号对应的变量信息」等查询。
 */
class RegisterObserver private constructor(
	private val art: ArtAdapter.IArtAdapter,
	private val mthFullID: String,
) {

	/** 代码偏移 -> 该处的寄存器加载/卸载信息列表。 */
	private var infoMap: MutableMap<Long, MutableList<Info>> = mutableMapOf()

	/** 运行时编号排序后的寄存器映射列表。 */
	private val regList: MutableList<SmaliRegisterMapping> = ArrayList()

	/** 是否存在远端调试信息。 */
	private var hasDbgInfo: Boolean = false

	companion object {
		/**
		 * 合并运行时变量表与 smali 寄存器列表。
		 *
		 * @param rtRegs     远端 JVM 返回的变量表
		 * @param smaliRegs  smali 反汇编得到的寄存器列表
		 * @param art        Android 版本适配器
		 * @param mthFullID  方法原始全名（用于错误提示）
		 */
		fun merge(
			rtRegs: List<RuntimeVarInfo>,
			smaliRegs: List<SmaliRegister>,
			art: ArtAdapter.IArtAdapter,
			mthFullID: String,
		): RegisterObserver {
			val adapter = RegisterObserver(art, mthFullID)
			adapter.hasDbgInfo = rtRegs.isNotEmpty()
			if (adapter.hasDbgInfo) {
				adapter.infoMap = HashMap()
			}
			for (sr in smaliRegs) {
				adapter.regList.add(SmaliRegisterMapping(sr))
			}
			adapter.regList.sortWith(compareBy { it.getSmaliRegister().runtimeRegNum })
			for (rt in rtRegs) {
				val smaliRegMapping = adapter.getRegListEntry(rt.regNum)
				val smaliReg = smaliRegMapping.getSmaliRegister()
				smaliRegMapping.addRuntimeVarInfo(rt)

				var type = rt.signature
				if (type.isEmpty()) {
					type = rt.type
				}
				val at = ArgType.parse(type)
				if (at != null) {
					type = at.toString()
				}
				val load = Info(smaliReg.regNum, true, rt.name, type)
				val unload = Info(smaliReg.regNum, false, null, null)
				adapter.infoMap.computeIfAbsent(rt.startOffset.toLong()) { ArrayList() }.add(load)
				adapter.infoMap.computeIfAbsent(rt.endOffset.toLong()) { ArrayList() }.add(unload)
			}
			return adapter
		}
	}

	/** @return 在 [codeOffset] 处已初始化的 smali 寄存器列表 */
	fun getInitializedList(codeOffset: Long): List<SmaliRegister> {
		var ret: MutableList<SmaliRegister>? = null
		for (smaliRegisterMapping in regList) {
			if (smaliRegisterMapping.getSmaliRegister().isInitialized(codeOffset)) {
				if (ret == null) {
					ret = ArrayList()
				}
				ret.add(smaliRegisterMapping.getSmaliRegister())
			}
		}
		return ret ?: emptyList()
	}

	/** 按运行时编号与代码偏移查询变量信息，不存在时返回 null。 */
	fun getInfo(runtimeNum: Int, codeOffset: Long): RuntimeVarInfo? {
		val list = getRegListEntry(runtimeNum)
		for (info in list.runtimeVarInfoList) {
			if (info.startOffset > codeOffset) {
				break
			}
			if (info.isInitialized(codeOffset)) {
				return info
			}
		}
		return null
	}

	private fun getRegListEntry(regNum: Int): SmaliRegisterMapping = try {
		regList[regNum]
	} catch (e: IndexOutOfBoundsException) {
		throw RuntimeException(
			String.format(
				"Register %d does not exist (size: %d).\n %s\n Method: %s",
				regNum,
				regList.size,
				buildDeviceInfo(),
				mthFullID,
			),
			e,
		)
	}

	private fun buildDeviceInfo(): String {
		val debugSettings = DebugSettings.INSTANCE
		return "Device: " + debugSettings.getDevice().deviceInfo +
			", Android: " + debugSettings.getVer() +
			", ArtAdapter: " + art.javaClass.simpleName
	}

	/** @return [codeOffset] 处的寄存器加载/卸载信息列表 */
	fun getInfoAt(codeOffset: Long): List<Info> {
		if (hasDbgInfo) {
			val list = infoMap[codeOffset]
			if (list != null) {
				return list
			}
		}
		return emptyList()
	}

	/** smali 寄存器与其运行时变量列表的绑定。 */
	class SmaliRegisterMapping(private val smaliRegister: SmaliRegister) {
		private var rtList: MutableList<RuntimeVarInfo> = mutableListOf()

		fun getSmaliRegister(): SmaliRegister = smaliRegister

		val runtimeVarInfoList: List<RuntimeVarInfo> get() = rtList

		fun addRuntimeVarInfo(rt: RuntimeVarInfo) {
			rtList.add(rt)
		}
	}

	/** 寄存器在某个代码偏移处的加载（load=true）或卸载（load=false）事件。 */
	class Info internal constructor(
		private val smaliRegNum: Int,
		private val load: Boolean,
		private val name: String?,
		private val type: String?,
	) {
		fun getSmaliRegNum(): Int = smaliRegNum

		val isLoad: Boolean get() = load

		fun getName(): String? = name

		fun getType(): String? = type
	}
}
