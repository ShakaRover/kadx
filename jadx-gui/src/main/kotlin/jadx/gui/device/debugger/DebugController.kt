package jadx.gui.device.debugger

import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.utils.StringUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.device.debugger.BreakpointManager.FileBreakpoint
import jadx.gui.device.debugger.SmaliDebugger.Frame
import jadx.gui.device.debugger.SmaliDebugger.RuntimeBreakpoint
import jadx.gui.device.debugger.SmaliDebugger.RuntimeDebugInfo
import jadx.gui.device.debugger.SmaliDebugger.RuntimeField
import jadx.gui.device.debugger.SmaliDebugger.RuntimeRegister
import jadx.gui.device.debugger.SmaliDebugger.RuntimeValue
import jadx.gui.device.debugger.SmaliDebugger.RuntimeVarInfo
import jadx.gui.device.debugger.smali.Smali
import jadx.gui.device.debugger.smali.SmaliRegister
import jadx.gui.treemodel.JClass
import jadx.gui.ui.panel.IDebugController
import jadx.gui.ui.panel.JDebuggerPanel
import jadx.gui.ui.panel.JDebuggerPanel.IListElement
import jadx.gui.ui.panel.JDebuggerPanel.ValueTreeNode
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.JOptionPane
import javax.swing.tree.DefaultMutableTreeNode

/**
 * 调试器控制器：连接 [SmaliDebugger] 与 GUI。
 *
 * **做什么**：实现 [IDebugController]，负责启动/停止调试、单步、暂停/继续，
 * 并在收到挂起事件时刷新线程、栈帧、寄存器与字段。
 *
 * **线程模型**：保留原 Java 的 `updateQueue` / `lazyQueue` 单线程执行器与
 * Swing `UiUtils.uiRun`，不引入协程。
 */
class DebugController :
	SmaliDebugger.SuspendListener,
	IDebugController {

	private lateinit var debuggerPanel: JDebuggerPanel
	private var debugger: SmaliDebugger? = null
	private lateinit var art: ArtAdapter.IArtAdapter
	private val cur = CurrentInfo()

	private var bpStore: BreakpointStore? = null
	private var updateAllFldAndReg: Boolean = false // 是否更新全部字段与寄存器
	private var toBeUpdatedTreeNode: ValueTreeNode? = null

	// 字段或寄存器节点
	@Volatile
	private var isSuspended: Boolean = true
	private var hasResumed: Boolean = false
	private var run: ResumeCmd? = null
	private var stepOver: ResumeCmd? = null
	private var stepInto: ResumeCmd? = null
	private var stepOut: ResumeCmd? = null
	private var stateListener: IDebugController.StateListener? = null

	private val regAdaMap: MutableMap<String, RegisterObserver> = ConcurrentHashMap()

	private val updateQueue: ExecutorService = Executors.newSingleThreadExecutor()
	private val lazyQueue: ExecutorService = Executors.newSingleThreadExecutor()

	override fun startDebugger(debuggerPanel: JDebuggerPanel, adbHost: String, adbPort: Int, androidVer: Int): Boolean {
		if (TYPE_MAP.isEmpty()) {
			initTypeMap()
		}
		this.debuggerPanel = debuggerPanel
		UiUtils.uiRunAndWait(debuggerPanel::resetUI)
		val dbg: SmaliDebugger
		try {
			dbg = SmaliDebugger.attach(adbHost, adbPort, this)
		} catch (e: SmaliDebuggerException) {
			JOptionPane.showMessageDialog(
				debuggerPanel.getMainWindow(),
				e.message,
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
			logErr(e)
			return false
		}
		debugger = dbg
		art = ArtAdapter.getAdapter(androidVer)
		resetAllInfo()
		hasResumed = false
		run = ResumeCmd { dbg.resume() }
		stepOver = ResumeCmd { dbg.stepOver() }
		stepInto = ResumeCmd { dbg.stepInto() }
		stepOut = ResumeCmd { dbg.stepOut() }
		stopAtOnCreate()
		val store = bpStore
		if (store == null) {
			bpStore = BreakpointStore()
		} else {
			store.reset()
		}
		BreakpointManager.setDebugController(this)
		initBreakpoints(BreakpointManager.getAllBreakpoints())
		return true
	}

	private fun openMainActivityTab(mainActivity: JClass) {
		val fullID = DbgUtils.getRawFullName(mainActivity) + "." + ONCREATE_SIGNATURE
		val smali = DbgUtils.getSmali(mainActivity.getCls().getClassNode())
		val pos = smali.getMethodDefPos(fullID)
		val finalPos = maxOf(1, pos)
		debuggerPanel.scrollToSmaliLine(mainActivity, finalPos, true)
	}

	private fun stopAtOnCreate() {
		val appData = DbgUtils.parseAppData(debuggerPanel.getMainWindow())
		if (appData == null) {
			debuggerPanel.log("Failed to set breakpoint at onCreate, you have to do it yourself.")
			return
		}
		val mainActivity = DbgUtils.getJClass(appData.getMainActivityCls(), debuggerPanel.getMainWindow())
		lazyQueue.execute { openMainActivityTab(mainActivity) }
		val clsSig = DbgUtils.getRawFullName(mainActivity)
		try {
			val id = checkNotNull(debugger).getClassID(clsSig, true)
			if (id != -1L) {
				return // 应用已在运行，无法再停在 onCreate
			}
			debuggerPanel.log(String.format("Breakpoint will set at %s.%s", clsSig, ONCREATE_SIGNATURE))
			checkNotNull(debugger).regMethodEntryEventSync(clsSig, ONCREATE_SIGNATURE::equals)
		} catch (e: SmaliDebuggerException) {
			logErr(e, String.format("Failed set breakpoint at %s.%s", clsSig, ONCREATE_SIGNATURE))
		}
	}

	override fun isSuspended(): Boolean = isSuspended

	override fun isDebugging(): Boolean = debugger != null

	override fun run(): Boolean = execResumeCmd(run)

	override fun stepInto(): Boolean = execResumeCmd(stepInto)

	override fun stepOver(): Boolean = execResumeCmd(stepOver)

	override fun stepOut(): Boolean = execResumeCmd(stepOut)

	override fun pause(): Boolean {
		if (isDebugging()) {
			try {
				checkNotNull(debugger).suspend()
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				return false
			}
			setDebuggerState(true, false)
			resetAllInfo()
		}
		return true
	}

	override fun stop(): Boolean {
		if (isDebugging()) {
			try {
				checkNotNull(debugger).exit()
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				return false
			}
		}
		return true
	}

	override fun exit(): Boolean {
		if (isDebugging()) {
			setDebuggerState(true, true)
			stop()
			debugger = null
		}
		BreakpointManager.setDebugController(null)
		debuggerPanel.getMainWindow().destroyDebuggerPanel()
		return true
	}

	/**
	 * @param type 必须是 int、long、float、double、string 或 object 之一
	 */
	override fun modifyRegValue(valNode: ValueTreeNode, type: ArgType, value: Any?): Boolean {
		checkType(type, value)
		if (isDebugging() && isSuspended()) {
			return modifyValueInternal(valNode, castType(type), value)
		}
		return false
	}

	override fun getProcessName(): String {
		val appData = DbgUtils.parseAppData(debuggerPanel.getMainWindow())
		if (appData == null) {
			return ""
		}
		return appData.getProcessName()
	}

	private fun castType(type: ArgType): RuntimeType {
		if (type == ArgType.INT) {
			return RuntimeType.INT
		}
		if (type == ArgType.STRING) {
			return RuntimeType.STRING
		}
		if (type == ArgType.LONG) {
			return RuntimeType.LONG
		}
		if (type == ArgType.FLOAT) {
			return RuntimeType.FLOAT
		}
		if (type == ArgType.DOUBLE) {
			return RuntimeType.DOUBLE
		}
		if (type == ArgType.OBJECT) {
			return RuntimeType.OBJECT
		}
		throw JadxRuntimeException("Unexpected type: $type")
	}

	private fun checkType(type: ArgType, value: Any?) {
		if (!(type == ArgType.INT && value is Int) &&
			!(type == ArgType.STRING && value is String) &&
			!(type == ArgType.LONG && value is Long) &&
			!(type == ArgType.FLOAT && value is Float) &&
			!(type == ArgType.DOUBLE && value is Double) &&
			!(type == ArgType.OBJECT && value is Long)
		) {
			throw JadxRuntimeException("Type must be one of int, long, float, double, String or Object.")
		}
	}

	private fun modifyValueInternal(valNode: ValueTreeNode, type: RuntimeType, value: Any?): Boolean {
		if (valNode is RegTreeNode) {
			try {
				val frame = checkNotNull(cur.frame)
				checkNotNull(debugger).setValueSync(
					valNode.getRuntimeRegNum(),
					type,
					value,
					frame.getThreadID(),
					frame.getFrame().getID(),
				)
				lazyQueue.execute {
					setRegsNotUpdated()
					updateRegister(valNode, type, true)
				}
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				return false
			}
		} else if (valNode is FieldTreeNode) {
			// TODO: 检查类型
			try {
				checkNotNull(debugger).setValueSync(
					valNode.getObjectID(),
					(valNode.getRuntimeValue() as RuntimeField).getFieldID(),
					valNode.getRuntimeField().getType(),
					value,
				)
				lazyQueue.execute {
					updateField(valNode)
				}
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				return false
			}
		}
		return true
	}

	private fun interface ResumeCmd {
		@Throws(SmaliDebuggerException::class)
		fun exec()
	}

	private fun execResumeCmd(cmd: ResumeCmd?): Boolean {
		if (!hasResumed) {
			if (cmd !== run) {
				return false
			}
			hasResumed = true
		}
		if (isDebugging() && isSuspended()) {
			updateAllFldAndReg = cmd === run
			setDebuggerState(false, false)
			try {
				cmd?.exec()
				return true
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				setDebuggerState(true, false)
			}
		}
		return false
	}

	/**
	 * @param suspended 由单步/断点等事件挂起
	 * @param stopped   远端应用已终止（仅用于切换图标，判断是否运行请用 [isDebugging]）
	 */
	private fun setDebuggerState(suspended: Boolean, stopped: Boolean) {
		isSuspended = suspended
		if (stopped) {
			hasResumed = false
		}
		stateListener?.onStateChanged(suspended, stopped)
	}

	override fun setStateListener(listener: IDebugController.StateListener) {
		stateListener = listener
	}

	override fun onSuspendEvent(info: SuspendInfo) {
		if (!isDebugging()) {
			return
		}
		if (info.isTerminated()) {
			debuggerPanel.log("Debugger exited.")
			setDebuggerState(true, true)
			debugger = null
			return
		}
		setDebuggerState(true, false)
		val threadID = info.getThreadID()
		var refreshLevel = 2 // 更新全部线程、栈帧、寄存器与字段
		val frame = cur.frame
		if (frame != null) {
			if (threadID == frame.getThreadID() &&
				info.getClassID() == frame.getClsID() &&
				info.getMethodID() == frame.getMthID()
			) {
				refreshLevel = 1 // 只更新相关寄存器或字段
			}
			setRegsNotUpdated()
		}
		if (refreshLevel == 2) {
			updateAllInfo(threadID, info.getOffset())
		} else {
			if (cur.smali != null && frame != null) {
				refreshRegInfo(info.getOffset())
				refreshCurFrame(threadID, info.getOffset())
				if (updateAllFldAndReg) {
					debuggerPanel.resetRegTreeNodes()
					updateAllRegisters(frame)
				} else if (toBeUpdatedTreeNode != null) {
					val node = toBeUpdatedTreeNode
					lazyQueue.execute { node?.let { updateRegOrField(it) } }
				}
				markCodeOffset(info.getOffset())
			} else {
				debuggerPanel.resetRegTreeNodes()
			}
			if (frame != null) {
				// 更新当前栈帧中的代码偏移
				frame.updateCodeOffset(info.getOffset())
				debuggerPanel.refreshStackFrameList(emptyList())
			}
		}
	}

	private fun refreshRegInfo(codeOffset: Long) {
		val list = checkNotNull(cur.regAdapter).getInfoAt(codeOffset)
		for (info in list) {
			val reg = checkNotNull(cur.frame).getRegNodes()[info.getSmaliRegNum()]
			if (info.isLoad()) {
				applyDbgInfo(reg, info.getName(), info.getType())
			} else {
				reg.setAlias("")
				reg.setAbsoluteType(false)
			}
		}
		if (list.isNotEmpty()) {
			debuggerPanel.refreshRegisterTree()
		}
	}

	private fun updateRegOrField(valTreeNode: ValueTreeNode) {
		if (valTreeNode is RegTreeNode) {
			updateRegister(valTreeNode, null, true)
			return
		}
		if (valTreeNode is FieldTreeNode) {
			updateField(valTreeNode)
		}
	}

	private fun updateField(node: FieldTreeNode) {
		try {
			setFieldsNotUpdated()
			checkNotNull(debugger).getValueSync(node.getObjectID(), node.getRuntimeField())
			decodeRuntimeValue(node)
			debuggerPanel.updateThisTree(node)
		} catch (e: SmaliDebuggerException) {
			logErr(e)
		}
	}

	private fun updateRegister(regNode: RegTreeNode, type: RuntimeType?, retry: Boolean): Boolean {
		var typeToUse = type
		if (typeToUse == null) {
			typeToUse = if (regNode.isAbsoluteType()) castType(regNode.getType()) else POSSIBLE_TYPES[0]
		}
		var ok = false
		var register: RuntimeRegister? = null
		try {
			val frame = checkNotNull(cur.frame)
			register = checkNotNull(debugger).getRegisterSync(
				frame.getThreadID(),
				frame.getFrame().getID(),
				regNode.getRuntimeRegNum(),
				typeToUse,
			)
		} catch (e: SmaliDebuggerException) {
			if (retry) {
				if (checkNotNull(debugger).errIsTypeMismatched(e.getErrCode())) {
					val types = getPossibleTypes(typeToUse)
					for (nextType in types) {
						ok = updateRegister(regNode, nextType, false)
						if (ok) {
							regNode.updateType(nextType.getDesc())
							break
						}
					}
				} else {
					logErr(e.message + " for " + regNode.getName())
					regNode.updateType(null)
					regNode.updateValue(null)
				}
			}
		}
		if (register != null) {
			regNode.updateReg(register)
			decodeRuntimeValue(regNode)
		}
		debuggerPanel.updateRegTree(regNode)
		return ok
	}

	private fun getPossibleTypes(cur: RuntimeType): Array<RuntimeType> {
		val types = arrayOfNulls<RuntimeType>(2)
		var j = 0
		for (i in POSSIBLE_TYPES.indices) {
			if (cur != POSSIBLE_TYPES[i]) {
				types[j++] = POSSIBLE_TYPES[i]
			}
		}
		return types.requireNoNulls()
	}

	// 单步时检测哪些寄存器需要更新
	private fun markNextToBeUpdated(codeOffset: Long) {
		if (codeOffset != -1L) {
			val rst = checkNotNull(cur.smali).getResultRegOrField(cur.mthFullID, codeOffset)
			toBeUpdatedTreeNode = null
			val frame = cur.frame
			if (frame != null) {
				if (rst is Int) {
					if (frame.getRegNodes().size > rst) {
						toBeUpdatedTreeNode = frame.getRegNodes()[rst]
					}
					return
				}
				if (rst is FieldInfo) {
					toBeUpdatedTreeNode = frame.getFieldNodes()
						.firstOrNull { f -> f.getName() == rst.name }
				}
			}
		}
	}

	private fun updateAllThreads() {
		val threads: List<Long>
		try {
			threads = checkNotNull(debugger).getAllThreadsSync()
		} catch (e: SmaliDebuggerException) {
			logErr(e)
			return
		}
		val threadEleList = ArrayList<ThreadBoxElement>(threads.size)
		for (thread in threads) {
			threadEleList.add(ThreadBoxElement(thread))
		}
		debuggerPanel.refreshThreadBox(threadEleList)
		lazyQueue.execute {
			for (ele in threadEleList) { // 获取线程名
				try {
					ele.setName(checkNotNull(debugger).getThreadNameSync(ele.getThreadID()))
				} catch (e: SmaliDebuggerException) {
					logErr(e)
				}
			}
			debuggerPanel.refreshThreadBox(emptyList())
		}
	}

	private fun updateAllStackFrames(threadID: Long): FrameNode? {
		var frames: List<SmaliDebugger.Frame> = emptyList()
		try {
			frames = checkNotNull(debugger).getFramesSync(threadID)
		} catch (e: SmaliDebuggerException) {
			logErr(e)
		}
		if (frames.isEmpty()) {
			return null
		}
		val frameEleList = ArrayList<FrameNode>(frames.size)
		for (frame in frames) {
			frameEleList.add(FrameNode(threadID, frame))
		}
		val curEle = frameEleList[0]
		fetchStackFrameNames(curEle)

		debuggerPanel.refreshStackFrameList(frameEleList)
		lazyQueue.execute {
			// 获取各栈帧的类名与方法名
			for (i in 1 until frameEleList.size) {
				fetchStackFrameNames(frameEleList[i])
			}
			debuggerPanel.refreshStackFrameList(emptyList())
		}
		return frameEleList[0]
	}

	private fun fetchStackFrameNames(ele: FrameNode) {
		try {
			val clsID = ele.getFrame().getClassID()
			val clsSig = checkNotNull(debugger).getClassSignatureSync(clsID)
			val mthSig = checkNotNull(debugger).getMethodSignatureSync(clsID, ele.getFrame().getMethodID())
			ele.setSignatures(clsSig, mthSig)
		} catch (e: SmaliDebuggerException) {
			logErr(e)
		}
	}

	private fun decodeSmali(frame: FrameNode): Smali? {
		val clsSig = frame.getClsSig()
		if (clsSig != null) {
			val jClass = DbgUtils.getTopClassBySig(clsSig, debuggerPanel.getMainWindow())
			if (jClass != null) {
				val cNode = jClass.getCls().getClassNode()
				cur.clsNode = jClass
				cur.mthFullID = checkNotNull(DbgUtils.classSigToRawFullName(clsSig)) + "." + frame.getMthSig()
				return DbgUtils.getSmali(cNode)
			}
		}
		return null
	}

	private fun refreshCurFrame(threadID: Long, codeOffset: Long) {
		try {
			val frame = checkNotNull(debugger).getCurrentFrame(threadID)
			cur.frame?.setFrame(frame)
			cur.frame?.updateCodeOffset(codeOffset)
		} catch (e: SmaliDebuggerException) {
			logErr(e)
		}
	}

	private fun updateAllFields(frame: FrameNode) {
		var fldNodes: List<FieldNode> = emptyList()
		val clsSig = frame.getClsSig()
		if (clsSig != null) {
			val clsNode = DbgUtils.getClassNodeBySig(clsSig, debuggerPanel.getMainWindow())
			if (clsNode != null) {
				fldNodes = clsNode.fields
			}
		}
		try {
			val thisID = checkNotNull(debugger).getThisID(frame.getThreadID(), frame.getFrame().getID())
			val flds = checkNotNull(debugger).getAllFieldsSync(frame.getClsID())
			val nodes = ArrayList<FieldTreeNode>(flds.size)
			for (fld in flds) {
				val fldNode = FieldTreeNode(fld, thisID)
				fldNodes.firstOrNull { f -> f.getName() == fldNode.getName() }
					?.let { smaliFld -> fldNode.setAlias(smaliFld.getAlias()) }
				nodes.add(fldNode)
			}
			debuggerPanel.updateThisFieldNodes(nodes)
			frame.setFieldNodes(nodes)
			if (thisID > 0 && nodes.isNotEmpty()) {
				lazyQueue.execute { updateAllFieldValues(thisID, frame) }
			}
		} catch (e: SmaliDebuggerException) {
			logErr(e)
		}
	}

	private fun updateAllFieldValues(thisID: Long, frame: FrameNode) {
		val nodes = frame.getFieldNodes()
		if (nodes.isNotEmpty()) {
			val flds = ArrayList<FieldTreeNode>(nodes.size)
			val rts = ArrayList<RuntimeField>(nodes.size)
			nodes.forEach { n ->
				val f = n.getRuntimeField()
				if (f.isBelongToThis()) {
					flds.add(n)
					rts.add(f)
				}
			}
			try {
				checkNotNull(debugger).getAllFieldValuesSync(thisID, rts)
				flds.forEach { n -> decodeRuntimeValue(n) }
				debuggerPanel.refreshThisFieldTree()
			} catch (e: SmaliDebuggerException) {
				logErr(e)
			}
		}
	}

	private fun updateAllRegisters(frame: FrameNode) {
		UiUtils.uiRun {
			if (buildRegTreeNodes(frame).isNotEmpty()) {
				fetchAllRegisters(frame)
			}
		}
	}

	private fun fetchAllRegisters(frame: FrameNode) {
		val regs = checkNotNull(cur.regAdapter).getInitializedList(frame.getCodeOffset())
		for (reg in regs) {
			val info = checkNotNull(cur.regAdapter).getInfo(reg.getRuntimeRegNum(), frame.getCodeOffset())
			val regNode = frame.getRegNodes()[reg.getRegNum()]
			if (info != null) {
				applyDbgInfo(regNode, info)
			}
			updateRegister(regNode, null, true)
		}
	}

	private fun applyDbgInfo(rn: RegTreeNode, info: RuntimeVarInfo) {
		applyDbgInfo(rn, info.getName(), info.getType())
	}

	private fun applyDbgInfo(rn: RegTreeNode, alias: String?, type: String?) {
		rn.setAlias(alias)
		rn.updateType(type)
		rn.setAbsoluteType(true)
	}

	private fun setRegsNotUpdated() {
		val frame = cur.frame
		if (frame != null) {
			for (regNode in frame.getRegNodes()) {
				regNode.setUpdated(false)
			}
		}
	}

	private fun setFieldsNotUpdated() {
		val frame = cur.frame
		if (frame != null) {
			for (node in frame.getFieldNodes()) {
				node.setUpdated(false)
			}
		}
	}

	private fun buildRegTreeNodes(frame: FrameNode): List<RegTreeNode> {
		val regs = checkNotNull(cur.smali).getRegisterList(cur.mthFullID)
		val regNodes = ArrayList<RegTreeNode>(regs.size)
		val inRtOrder = ArrayList<RegTreeNode>(regs.size)

		regs.forEach { r ->
			val rn = RegTreeNode(r)
			regNodes.add(rn)
			inRtOrder.add(rn)
		}
		inRtOrder.sortWith(compareBy { it.getRuntimeRegNum() })
		frame.setRegNodes(regNodes)
		debuggerPanel.updateRegTreeNodes(inRtOrder)
		debuggerPanel.refreshRegisterTree()
		return regNodes
	}

	private fun decodeRuntimeValue(valNode: RuntimeValueTreeNode): Boolean {
		val rValue = valNode.getRuntimeValue()
		val type = rValue.getType()
		if (!valNode.isAbsoluteType()) {
			valNode.updateType(null)
		}
		try {
			when (type) {
				RuntimeType.OBJECT -> return decodeObject(valNode)

				RuntimeType.STRING -> {
					val str = "\"" + checkNotNull(debugger).readStringSync(rValue) + "\""
					valNode.updateType("java.lang.String")
						.updateTypeID(checkNotNull(debugger).readID(rValue))
						.updateValue(str)
				}

				RuntimeType.INT -> valNode.updateValue(checkNotNull(debugger).readInt(rValue).toString())

				RuntimeType.LONG -> valNode.updateValue(checkNotNull(debugger).readAll(rValue).toString())

				RuntimeType.ARRAY -> decodeArrayVal(valNode)

				RuntimeType.BOOLEAN -> {
					val b = checkNotNull(debugger).readByte(rValue).toInt()
					valNode.updateValue(if (b == 1) "true" else "false")
				}

				RuntimeType.SHORT -> valNode.updateValue(checkNotNull(debugger).readShort(rValue).toString())

				RuntimeType.CHAR, RuntimeType.BYTE -> {
					val b = checkNotNull(debugger).readAll(rValue).toInt()
					if (DbgUtils.isPrintableChar(b)) {
						valNode.updateValue(
							if (type == RuntimeType.CHAR) b.toChar().toString() else b.toByte().toString(),
						)
					} else {
						valNode.updateValue(b.toString())
					}
				}

				RuntimeType.DOUBLE -> {
					val d = checkNotNull(debugger).readDouble(rValue)
					valNode.updateValue(d.toString())
				}

				RuntimeType.FLOAT -> {
					val f = checkNotNull(debugger).readFloat(rValue)
					valNode.updateValue(f.toString())
				}

				RuntimeType.VOID -> valNode.updateType("void")

				RuntimeType.THREAD -> valNode.updateType("thread").updateTypeID(checkNotNull(debugger).readID(rValue))

				RuntimeType.THREAD_GROUP ->
					valNode.updateType("thread_group").updateTypeID(checkNotNull(debugger).readID(rValue))

				RuntimeType.CLASS_LOADER ->
					valNode.updateType("class_loader").updateTypeID(checkNotNull(debugger).readID(rValue))

				RuntimeType.CLASS_OBJECT ->
					valNode.updateType("class_object").updateTypeID(checkNotNull(debugger).readID(rValue))
			}
		} catch (e: SmaliDebuggerException) {
			logErr(e)
			return false
		}
		return true
	}

	private fun decodeObject(valNode: RuntimeValueTreeNode): Boolean {
		var rValue = valNode.getRuntimeValue()
		var ok = true
		if (checkNotNull(debugger).readID(rValue) == 0L) {
			if (valNode.isAbsoluteType()) {
				valNode.updateValue("null")
				return ok
			} else if (!art.readNullObject()) {
				valNode.updateType(art.typeForNull())
				valNode.updateValue("0")
				return ok
			}
		}
		val sig: String
		try {
			sig = checkNotNull(debugger).readObjectSignatureSync(rValue)
			valNode.updateType(
				String.format("%s@%d", DbgUtils.classSigToRawFullName(sig), checkNotNull(debugger).readID(rValue)),
			)
		} catch (e: SmaliDebuggerException) {
			ok = checkNotNull(debugger).errIsInvalidObject(e.getErrCode()) && valNode is RegTreeNode
			if (ok) {
				try {
					val reg = valNode as RegTreeNode
					val frame = checkNotNull(cur.frame)
					val rr = checkNotNull(debugger).getRegisterSync(
						frame.getThreadID(),
						frame.getFrame().getID(),
						reg.getRuntimeRegNum(),
						RuntimeType.INT,
					)
					reg.updateReg(rr)
					rValue = rr
					valNode.updateType(RuntimeType.INT.getDesc())
					valNode.updateValue(checkNotNull(debugger).readAll(rValue).toInt().toString())
				} catch (except: SmaliDebuggerException) {
					logErr(except, String.format("Update %s failed, %s", valNode.getName(), except.message))
					valNode.updateValue(except.message)
					ok = false
				}
			} else {
				logErr(e)
			}
		}
		return ok
	}

	@Throws(SmaliDebuggerException::class)
	private fun decodeArrayVal(valNode: RuntimeValueTreeNode) {
		val type = checkNotNull(debugger).readObjectSignatureSync(valNode.getRuntimeValue())
		val argType = checkNotNull(ArgType.parse(type))
		var javaType = argType.toString()
		val ret = checkNotNull(debugger).readArray(valNode.getRuntimeValue(), 0, 0)
		javaType = javaType.substring(0, javaType.length - 1) + ret.key + "]"
		valNode.updateType(javaType + "@" + checkNotNull(debugger).readID(valNode.getRuntimeValue()))

		if (checkNotNull(argType.getArrayElement()).isPrimitive()) {
			for (aLong in ret.value.orEmpty()) {
				valNode.add(DefaultMutableTreeNode(aLong.toString()))
			}
			return
		}
		var typeSig = type.substring(1)
		if (DbgUtils.isStringObjectSig(typeSig)) {
			for (aLong in ret.value.orEmpty()) {
				valNode.add(DefaultMutableTreeNode(checkNotNull(debugger).readStringSync(aLong)))
			}
			return
		}
		typeSig = checkNotNull(DbgUtils.classSigToRawFullName(typeSig))
		for (aLong in ret.value.orEmpty()) {
			valNode.add(DefaultMutableTreeNode(String.format("%s@%d", typeSig, aLong)))
		}
	}

	private fun updateAllInfo(threadID: Long, codeOffset: Long) {
		updateQueue.execute {
			resetAllInfo()
			cur.frame = updateAllStackFrames(threadID)
			val frame = cur.frame
			if (frame != null) {
				lazyQueue.execute { updateAllFields(frame) }
				if (frame.getClsSig() == null || frame.getMthSig() == null) {
					fetchStackFrameNames(frame)
				}
				val smali = decodeSmali(frame)
				cur.smali = smali
				if (smali != null) {
					cur.regAdapter = regAdaMap.computeIfAbsent(cur.mthFullID) {
						RegisterObserver.merge(
							getRuntimeDebugInfo(frame),
							getSmaliRegisterList(),
							art,
							cur.mthFullID,
						)
					}

					if (smali.getRegCount(cur.mthFullID) > 0) {
						updateAllRegisters(frame)
					}
					markCodeOffset(codeOffset)
				}
			}
			updateAllThreads()
		}
	}

	private fun getSmaliRegisterList(): List<SmaliRegister> {
		val smali = cur.smali ?: return emptyList()
		val regCount = smali.getRegCount(cur.mthFullID)
		val paramStart = smali.getParamRegStart(cur.mthFullID)
		val srs = smali.getRegisterList(cur.mthFullID)
		for (sr in srs) {
			sr.setRuntimeRegNum(art.getRuntimeRegNum(sr.getRegNum(), regCount, paramStart))
		}
		return srs
	}

	private fun resetAllInfo() {
		isSuspended = true
		toBeUpdatedTreeNode = null
		cur.reset()
		UiUtils.uiRun(debuggerPanel::resetAllDebuggingInfo)
	}

	private fun getRuntimeDebugInfo(frame: FrameNode): List<RuntimeVarInfo> {
		try {
			val dbgInfo = checkNotNull(debugger).getRuntimeDebugInfo(frame.getClsID(), frame.getMthID())
			if (dbgInfo != null) {
				return dbgInfo.getInfoList()
			}
		} catch (e: SmaliDebuggerException) {
			// logErr(e)
		}
		return emptyList()
	}

	private fun markCodeOffset(codeOffset: Long) {
		scrollToPos(codeOffset)
		markNextToBeUpdated(codeOffset)
	}

	private fun logErr(e: Exception, extra: String) {
		debuggerPanel.log(e.message ?: "")
		debuggerPanel.log(extra)
		LOG.error(extra, e)
	}

	private fun logErr(e: Exception) {
		debuggerPanel.log(e.message ?: "")
		LOG.error("Debug error", e)
	}

	private fun logErr(e: String) {
		debuggerPanel.log(e)
		LOG.error("Debug error: {}", e)
	}

	private fun scrollToPos(codeOffset: Long) {
		var pos = -1
		if (codeOffset > -1) {
			pos = cur.smali?.getInsnPosByCodeOffset(cur.mthFullID, codeOffset) ?: -1
		}
		if (pos == -1) {
			pos = cur.smali?.getMethodDefPos(cur.mthFullID) ?: -1
			if (pos == -1) {
				debuggerPanel.log("Can't scroll to " + cur.mthFullID)
				return
			}
		}
		debuggerPanel.scrollToSmaliLine(checkNotNull(cur.clsNode), pos, true)
	}

	private fun initBreakpoints(fbps: List<FileBreakpoint>) {
		if (fbps.isEmpty()) {
			return
		}
		var fetch = true
		for (fbp in fbps) {
			try {
				val id = checkNotNull(debugger).getClassID(fbp.cls, fetch)
				// 只从 JVM 拉取一次类列表，之后 JVM 会被冻结，不再变化
				fetch = false
				if (id > -1) {
					setBreakpoint(id, fbp)
				} else {
					setDelayBreakpoint(fbp)
				}
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				failBreakpoint(fbp, e.message ?: "")
			}
		}
	}

	internal fun setBreakpoint(bp: FileBreakpoint): Boolean {
		if (!isDebugging()) {
			return true
		}
		try {
			val cid = checkNotNull(debugger).getClassID(bp.cls, true)
			if (cid > -1) {
				setBreakpoint(cid, bp)
			} else {
				setDelayBreakpoint(bp)
			}
		} catch (e: SmaliDebuggerException) {
			logErr(e)
			BreakpointManager.failBreakpoint(bp)
			return false
		}
		return true
	}

	private fun setDelayBreakpoint(bp: FileBreakpoint) {
		val store = checkNotNull(bpStore)
		val hasSet = store.hasSetDelaied(bp.cls)
		store.add(bp, null)
		if (!hasSet) {
			updateQueue.execute {
				try {
					checkNotNull(debugger).regClassPrepareEventForBreakpoint(bp.cls) { id ->
						val list = store.get(bp.cls)
						for (fbp in list) {
							setBreakpoint(id, fbp)
						}
					}
				} catch (e: SmaliDebuggerException) {
					logErr(e)
					failBreakpoint(bp, "")
				}
			}
		}
	}

	internal fun setBreakpoint(cid: Long, fbp: FileBreakpoint) {
		try {
			val mid = checkNotNull(debugger).getMethodID(cid, fbp.mth)
			if (mid > -1) {
				val rbp = checkNotNull(debugger).makeBreakpoint(cid, mid, fbp.codeOffset)
				checkNotNull(debugger).setBreakpoint(rbp)
				checkNotNull(bpStore).add(fbp, rbp)
				return
			}
		} catch (e: SmaliDebuggerException) {
			logErr(e)
		}
		failBreakpoint(fbp, "Failed to get method for breakpoint, " + fbp.mth + ":" + fbp.codeOffset)
	}

	private fun failBreakpoint(fbp: FileBreakpoint, msg: String) {
		if (msg.isNotEmpty()) {
			debuggerPanel.log(msg)
		}
		checkNotNull(bpStore).removeBreakpoint(fbp)
		BreakpointManager.failBreakpoint(fbp)
	}

	internal fun removeBreakpoint(fbp: FileBreakpoint): Boolean {
		if (!isDebugging()) {
			return true
		}
		val rbp = checkNotNull(bpStore).removeBreakpoint(fbp)
		if (rbp != null) {
			try {
				checkNotNull(debugger).removeBreakpoint(rbp)
			} catch (e: SmaliDebuggerException) {
				logErr(e)
				return false
			}
		}
		return true
	}

	private inner class BreakpointStore {
		val bpm: MutableMap<FileBreakpoint, RuntimeBreakpoint> = ConcurrentHashMap()

		init {
			if (delayBP == null) {
				delayBP = checkNotNull(debugger).makeBreakpoint(-1, -1, -1)
			}
		}

		fun reset() {
			bpm.clear()
		}

		fun hasSetDelaied(cls: String): Boolean {
			for (entry in bpm.entries) {
				if (entry.value === delayBP && entry.key.cls == cls) {
					return true
				}
			}
			return false
		}

		fun get(cls: String): List<FileBreakpoint> {
			val fbps = ArrayList<FileBreakpoint>()
			bpm.forEach { (k, v) ->
				if (v === delayBP && k.cls == cls) {
					fbps.add(k)
					bpm.remove(k)
				}
			}
			return fbps
		}

		fun add(fbp: FileBreakpoint, rbp: RuntimeBreakpoint?) {
			bpm[fbp] = rbp ?: checkNotNull(delayBP)
		}

		fun removeBreakpoint(fbp: FileBreakpoint): RuntimeBreakpoint? = bpm.remove(fbp)
	}

	private inner class FrameNode(private val threadID: Long, frame: SmaliDebugger.Frame) : IListElement {
		private var frame: SmaliDebugger.Frame = frame
		private var clsSig: String? = null
		private var mthSig: String? = null
		private var cache = StringBuilder(DEFAULT_CACHE_SIZE)
		private var codeOffset: Long = -1
		private var regNodes: List<RegTreeNode> = emptyList()
		private var thisNodes: List<FieldTreeNode> = emptyList()
		private var thisID: Long = 0

		fun getFrame(): SmaliDebugger.Frame = frame

		fun setFrame(frame: SmaliDebugger.Frame) {
			this.frame = frame
		}

		fun getClsID(): Long = frame.getClassID()

		fun getMthID(): Long = frame.getMethodID()

		fun getThreadID(): Long = threadID

		fun getThisID(): Long = thisID

		fun setThisID(thisID: Long) {
			this.thisID = thisID
		}

		fun setSignatures(clsSig: String?, mthSig: String?) {
			this.clsSig = clsSig
			this.mthSig = mthSig
			resetCache()
		}

		fun getClsSig(): String? = clsSig

		fun getMthSig(): String? = mthSig

		fun updateCodeOffset(codeOffset: Long) {
			this.codeOffset = codeOffset
			if (this.codeOffset > -1) {
				resetCache()
			}
		}

		fun getCodeOffset(): Long = if (codeOffset == -1L) frame.getCodeIndex() else codeOffset

		fun setRegNodes(regNodes: List<RegTreeNode>) {
			this.regNodes = regNodes
		}

		fun getRegNodes(): List<RegTreeNode> = regNodes

		fun getFieldNodes(): List<FieldTreeNode> = thisNodes

		fun setFieldNodes(thisNodes: List<FieldTreeNode>) {
			this.thisNodes = thisNodes
		}

		override fun onSelected() {
			val sig = clsSig
			if (sig != null) {
				val cls = DbgUtils.getTopClassBySig(sig, debuggerPanel.getMainWindow())
				if (cls != null) {
					val smali = DbgUtils.getSmali(cls.getCls().getClassNode())
					val pos = smali.getInsnPosByCodeOffset(
						checkNotNull(DbgUtils.classSigToRawFullName(sig)) + "." + mthSig,
						getCodeOffset(),
					)
					debuggerPanel.scrollToSmaliLine(cls, maxOf(0, pos), true)
					return
				}
				debuggerPanel.log("Can't open smali panel for " + sig + "->" + mthSig)
			}
		}

		private fun resetCache() {
			// 不复用旧缓存实例，避免 toString() 正在执行时的多线程访问问题
			this.cache = StringBuilder(DEFAULT_CACHE_SIZE)
		}

		override fun toString(): String {
			val sbCache = cache
			if (sbCache.isEmpty()) {
				val off = getCodeOffset()
				if (off < 0) {
					sbCache.append(String.format("index: %-4d ", off))
				} else {
					sbCache.append(String.format("index: %04x ", off))
				}
				if (clsSig == null) {
					sbCache.append("clsID: ").append(frame.getClassID())
				} else {
					sbCache.append(clsSig).append("->")
				}
				if (mthSig == null) {
					sbCache.append(" mthID: ").append(frame.getMethodID())
				} else {
					sbCache.append(mthSig)
				}
			}
			return sbCache.toString()
		}
	}

	private class ThreadBoxElement(private val threadID: Long) : IListElement {
		private var name: String? = null

		fun setName(name: String?) {
			this.name = name
		}

		fun getThreadID(): Long = threadID

		override fun toString(): String = if (name == null) "thread id: $threadID" else "thread id: $threadID name:$name"

		override fun onSelected() {
		}
	}

	private class RegTreeNode(private val smaliReg: SmaliRegister) : RuntimeValueTreeNode() {
		private var runtimeReg: RuntimeRegister? = null
		private var value: String? = null
		private var type: String? = null
		private var alias: String? = null
		private var absType: Boolean = false

		fun updateReg(reg: RuntimeRegister) {
			runtimeReg = reg
		}

		fun setAlias(alias: String?) {
			this.alias = alias
		}

		override fun updateValue(value: String?): RegTreeNode {
			setUpdated(true)
			this.value = value
			removeAllChildren()
			return this
		}

		override fun updateType(type: String?): RegTreeNode {
			if (this.type == null || this.type != type) {
				this.type = type
				reset()
			}
			return this
		}

		private fun reset() {
			value = null
			removeAllChildren()
			setUpdated(true)
			this.absType = false
			updateTypeID(0)
		}

		override fun getName(): String = if (!StringUtils.isEmpty(alias)) {
			String.format("%s (%s)", smaliReg.getName(), alias)
		} else {
			String.format("%-3s", smaliReg.getName())
		}

		override fun getValue(): String? = value

		fun getRuntimeReg(): RuntimeRegister? = runtimeReg

		fun getRuntimeRegNum(): Int = smaliReg.getRuntimeRegNum()

		override fun getType(): String? {
			if (type != null) {
				return type
			}
			return runtimeReg?.getType()?.getDesc()
		}

		override fun getRuntimeValue(): RuntimeValue = checkNotNull(runtimeReg)

		override fun isAbsoluteType(): Boolean = absType

		fun setAbsoluteType(abs: Boolean) {
			absType = abs
		}

		companion object {
			private const val serialVersionUID: Long = -1111111202103122234L
		}
	}

	private class FieldTreeNode internal constructor(
		private val field: RuntimeField,
		id: Long,
	) : RuntimeValueTreeNode() {
		private var value: String? = null
		private var alias: String? = null
		private var objectID: Long = id

		fun getObjectID(): Long = objectID

		fun setObjectID(id: Long) {
			objectID = id
		}

		fun getRuntimeField(): RuntimeField = field

		fun setAlias(alias: String?) {
			this.alias = alias
		}

		override fun updateValue(value: String?): FieldTreeNode {
			setUpdated(true)
			this.value = value
			removeAllChildren()
			return this
		}

		override fun updateType(value: String?): FieldTreeNode = this

		override fun getName(): String = if (StringUtils.isEmpty(alias) || alias == field.getName()) {
			field.getName()
		} else {
			field.getName() + " (" + alias + ")"
		}

		override fun getValue(): String? = value

		override fun getType(): String = checkNotNull(ArgType.parse(field.getFieldType())).toString()

		override fun getRuntimeValue(): RuntimeValue = field

		override fun isAbsoluteType(): Boolean = true

		companion object {
			private const val serialVersionUID: Long = -1111111202103122235L
		}
	}

	private abstract class RuntimeValueTreeNode : ValueTreeNode() {
		private var typeID: Long = 0

		override fun updateTypeID(id: Long): ValueTreeNode {
			this.typeID = id
			return this
		}

		override fun getTypeID(): Long = this.typeID

		abstract fun getRuntimeValue(): RuntimeValue

		abstract fun isAbsoluteType(): Boolean

		companion object {
			private const val serialVersionUID: Long = -1111111202103260222L
		}
	}

	private inner class CurrentInfo {
		var clsNode: JClass? = null
		var mthFullID: String = ""
		var smali: Smali? = null
		var frame: FrameNode? = null
		var regAdapter: RegisterObserver? = null

		fun reset() {
			frame = null
			smali = null
			clsNode = null
			regAdapter = null
			mthFullID = ""
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DebugController::class.java)

		private const val ONCREATE_SIGNATURE = "onCreate(Landroid/os/Bundle;)V"

		private val TYPE_MAP: MutableMap<String, RuntimeType> = HashMap()

		private val POSSIBLE_TYPES = arrayOf(RuntimeType.OBJECT, RuntimeType.INT, RuntimeType.LONG)

		private const val DEFAULT_CACHE_SIZE = 512

		private var delayBP: RuntimeBreakpoint? = null

		fun castType(type: String?): RuntimeType {
			var rt: RuntimeType? = null
			if (!StringUtils.isEmpty(type)) {
				rt = TYPE_MAP[type]
			}
			return rt ?: POSSIBLE_TYPES[0]
		}

		private fun initTypeMap() {
			TYPE_MAP["I"] = RuntimeType.INT
			TYPE_MAP["Z"] = RuntimeType.INT
			TYPE_MAP["B"] = RuntimeType.INT
			TYPE_MAP["C"] = RuntimeType.INT
			TYPE_MAP["F"] = RuntimeType.INT
			TYPE_MAP["S"] = RuntimeType.INT
			TYPE_MAP["V"] = RuntimeType.INT
			TYPE_MAP["int"] = RuntimeType.INT
			TYPE_MAP["boolean"] = RuntimeType.INT
			TYPE_MAP["byte"] = RuntimeType.INT
			TYPE_MAP["short"] = RuntimeType.INT
			TYPE_MAP["char"] = RuntimeType.INT
			TYPE_MAP["float"] = RuntimeType.INT
			TYPE_MAP["void"] = RuntimeType.INT
			TYPE_MAP["L"] = RuntimeType.LONG
			TYPE_MAP["D"] = RuntimeType.LONG
			TYPE_MAP["long"] = RuntimeType.LONG
			TYPE_MAP["double"] = RuntimeType.LONG
			TYPE_MAP["java.lang.String"] = RuntimeType.STRING
			TYPE_MAP["Ljava/lang/String;"] = RuntimeType.STRING
		}
	}
}
