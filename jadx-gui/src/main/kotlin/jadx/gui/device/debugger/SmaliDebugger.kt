package jadx.gui.device.debugger

import io.github.skylot.jdwp.JDWP
import io.github.skylot.jdwp.JDWP.ArrayReference.Length.LengthReplyData
import io.github.skylot.jdwp.JDWP.ByteBuffer
import io.github.skylot.jdwp.JDWP.Event.Composite.BreakpointEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ClassPrepareEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ClassUnloadEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.EventData
import io.github.skylot.jdwp.JDWP.Event.Composite.ExceptionEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.FieldAccessEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.FieldModificationEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MethodEntryEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MethodExitEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MethodExitWithReturnValueEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorContendedEnterEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorContendedEnteredEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorWaitEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.MonitorWaitedEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.SingleStepEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ThreadDeathEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.ThreadStartEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.VMDeathEvent
import io.github.skylot.jdwp.JDWP.Event.Composite.VMStartEvent
import io.github.skylot.jdwp.JDWP.EventRequest.Set.ClassMatchRequest
import io.github.skylot.jdwp.JDWP.EventRequest.Set.CountRequest
import io.github.skylot.jdwp.JDWP.EventRequest.Set.LocationOnlyRequest
import io.github.skylot.jdwp.JDWP.EventRequest.Set.StepRequest
import io.github.skylot.jdwp.JDWP.Method.VariableTableWithGeneric.VarTableWithGenericData
import io.github.skylot.jdwp.JDWP.Method.VariableTableWithGeneric.VarWithGenericSlot
import io.github.skylot.jdwp.JDWP.ObjectReference
import io.github.skylot.jdwp.JDWP.ObjectReference.ReferenceType.ReferenceTypeReplyData
import io.github.skylot.jdwp.JDWP.ObjectReference.SetValues.FieldValueSetter
import io.github.skylot.jdwp.JDWP.Packet
import io.github.skylot.jdwp.JDWP.ReferenceType.FieldsWithGeneric.FieldsWithGenericData
import io.github.skylot.jdwp.JDWP.ReferenceType.FieldsWithGeneric.FieldsWithGenericReplyData
import io.github.skylot.jdwp.JDWP.ReferenceType.MethodsWithGeneric.MethodsWithGenericData
import io.github.skylot.jdwp.JDWP.ReferenceType.MethodsWithGeneric.MethodsWithGenericReplyData
import io.github.skylot.jdwp.JDWP.ReferenceType.Signature.SignatureReplyData
import io.github.skylot.jdwp.JDWP.StackFrame.GetValues.GetValuesReplyData
import io.github.skylot.jdwp.JDWP.StackFrame.GetValues.GetValuesSlots
import io.github.skylot.jdwp.JDWP.StackFrame.SetValues.SlotValueSetter
import io.github.skylot.jdwp.JDWP.StackFrame.ThisObject.ThisObjectReplyData
import io.github.skylot.jdwp.JDWP.StringReference.Value.ValueReplyData
import io.github.skylot.jdwp.JDWP.ThreadReference.Frames.FramesReplyData
import io.github.skylot.jdwp.JDWP.ThreadReference.Frames.FramesReplyDataFrames
import io.github.skylot.jdwp.JDWP.ThreadReference.Name.NameReplyData
import io.github.skylot.jdwp.JDWP.VirtualMachine.AllClassesWithGeneric.AllClassesWithGenericData
import io.github.skylot.jdwp.JDWP.VirtualMachine.AllClassesWithGeneric.AllClassesWithGenericReplyData
import io.github.skylot.jdwp.JDWP.VirtualMachine.AllThreads.AllThreadsReplyData
import io.github.skylot.jdwp.JDWP.VirtualMachine.AllThreads.AllThreadsReplyDataThreads
import io.github.skylot.jdwp.JDWP.VirtualMachine.CreateString.CreateStringReplyData
import jadx.api.plugins.input.data.AccessFlags
import jadx.gui.device.debugger.smali.RegisterInfo
import jadx.gui.utils.IOUtils
import jadx.gui.utils.ObjectPool
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.AbstractMap.SimpleEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicInteger

// TODO: Finish error notification, inner errors should be logged let user notice.

/**
 * 基于 JDWP 的 smali 调试器。
 *
 * **做什么**：与远端 ART/JVM 建立 JDWP 连接，收发命令与事件，
 * 支持断点、单步、查看/修改变量与字段。
 *
 * **线程模型**：阻塞 socket I/O、单线程事件队列与
 * `SynchronousQueue` 同步机制。
 */
class SmaliDebugger private constructor(
	private val suspendListener: SuspendListener,
	private val localTcpPort: Int,
	private val jdwp: JDWP,
	private val inputStream: InputStream,
	private val outputStream: OutputStream,
) {

	private val callbackMap: MutableMap<Int, ICommandResult> = ConcurrentHashMap()
	private val eventListenerMap: MutableMap<Int, EventListenerAdapter> = ConcurrentHashMap()

	private val classMap: MutableMap<String, AllClassesWithGenericData> = ConcurrentHashMap()
	private val classIDMap: MutableMap<Long, AllClassesWithGenericData> = ConcurrentHashMap()
	private val clsMethodMap: MutableMap<Long, List<MethodsWithGenericData>> = ConcurrentHashMap()
	private val clsFieldMap: MutableMap<Long, List<FieldsWithGenericData>> = ConcurrentHashMap()
	private var varMap: MutableMap<Long, MutableMap<Long, RuntimeDebugInfo>> = mutableMapOf() // 类 id: <方法 id: 变量表>

	private val oneOffEventReq: CountRequest = jdwp.eventRequest().cmdSet().newCountRequest()

	private val idGenerator = AtomicInteger(1)

	private val suspendInfo = SuspendInfo()

	private lateinit var slotsPool: ObjectPool<MutableList<GetValuesSlots>>
	private lateinit var stepReqPool: ObjectPool<MutableList<JDWP.EventRequestEncoder>>
	private lateinit var syncQueuePool: ObjectPool<SynchronousQueue<Packet>>
	private lateinit var fieldIdPool: ObjectPool<MutableList<Long>>
	private val syncQueueMap: MutableMap<Int, Thread> = ConcurrentHashMap()
	private val syncQueueID = AtomicInteger(0)

	private var clsListener: ClassListenerInfo? = null

	init {
		oneOffEventReq.count = 1
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SmaliDebugger::class.java)

		// 所有事件回调都在此队列执行，例如类加载/卸载
		private val EVENT_LISTENER_QUEUE: Executor = Executors.newSingleThreadExecutor()

		// 处理单步、断点与监视点的回调
		private val SUSPEND_LISTENER_QUEUE: Executor = Executors.newSingleThreadExecutor()

		private val SKIP_RESULT: ICommandResult = ICommandResult { }

		/**
		 * 连接远端进程。连接成功后远端会挂起，调用方需在设置完断点后调用 `resume()`。
		 */
		@Throws(SmaliDebuggerException::class)
		fun attach(host: String, port: Int, suspendListener: SuspendListener): SmaliDebugger {
			try {
				val bytes = JDWP.IDSizes.encode().getBytes()
				JDWP.setPacketID(bytes, 1)
				LOG.debug("Connecting to ADB {}:{}", host, port)
				val socket = Socket(host, port)
				val inputStream = socket.getInputStream()
				val outputStream = socket.getOutputStream()

				socket.soTimeout = 5000
				val jdwp = initJDWP(outputStream, inputStream)
				socket.soTimeout = 0 // 设回 0，避免解码循环因超时退出

				val debugger = SmaliDebugger(suspendListener, port, jdwp, inputStream, outputStream)

				debugger.decodingLoop()
				debugger.listenClassUnloadEvent()
				debugger.initPools()
				return debugger
			} catch (e: IOException) {
				throw SmaliDebuggerException("Attach failed", e)
			}
		}

		@Throws(SmaliDebuggerException::class)
		private fun initJDWP(outputStream: OutputStream, inputStream: InputStream): JDWP {
			try {
				handShake(outputStream, inputStream)
				outputStream.write(JDWP.Suspend.encode().setPacketID(1).getBytes()) // 挂起所有线程
				var res: Packet? = readPacket(inputStream)
				tryThrowError(res)
				if (checkNotNull(res).isReplyPacket() && res.getID() == 1) {
					// 获取 id 尺寸，用于解码/编码 JDWP 数据包
					outputStream.write(JDWP.IDSizes.encode().setPacketID(1).getBytes())
					res = readPacket(inputStream)
					tryThrowError(res)
					if (checkNotNull(res).isReplyPacket() && res.getID() == 1) {
						val sizes = JDWP.IDSizes.decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
						return JDWP(sizes)
					}
				}
			} catch (e: IOException) {
				throw SmaliDebuggerException(e)
			}
			throw SmaliDebuggerException("Failed to init JDWP.")
		}

		@Throws(SmaliDebuggerException::class)
		private fun handShake(outputStream: OutputStream, inputStream: InputStream) {
			var buf: ByteArray? = null
			try {
				outputStream.write(JDWP.encodeHandShakePacket())
				buf = IOUtils.readNBytes(inputStream, 14)
			} catch (e: Exception) {
				throw SmaliDebuggerException("jdwp handshake failed", e)
			}
			if (buf == null || !JDWP.decodeHandShakePacket(buf)) {
				throw SmaliDebuggerException("jdwp handshake bad reply")
			}
		}

		/** 读取一个 JDWP 数据包。 */
		@Throws(SmaliDebuggerException::class)
		private fun readPacket(inputStream: InputStream): Packet? {
			try {
				val header = IOUtils.readNBytes(inputStream, JDWP.PACKET_HEADER_SIZE) ?: return null // 流已结束
				val bodyLength = JDWP.getPacketLength(header, 0) - JDWP.PACKET_HEADER_SIZE
				if (bodyLength <= 0) {
					return Packet.make(header)
				}
				val body = IOUtils.readNBytes(inputStream, bodyLength)
					?: throw SmaliDebuggerException("Stream truncated")
				return Packet.make(concatBytes(header, body))
			} catch (e: IOException) {
				throw SmaliDebuggerException("Read packer error", e)
			}
		}

		private fun concatBytes(buf1: ByteArray, buf2: ByteArray): ByteArray {
			val tempBuf = ByteArray(buf1.size + buf2.size)
			System.arraycopy(buf1, 0, tempBuf, 0, buf1.size)
			System.arraycopy(buf2, 0, tempBuf, buf1.size, buf2.size)
			return tempBuf
		}

		@Throws(SmaliDebuggerException::class)
		private fun tryThrowError(res: Packet?) {
			if (res == null) {
				throw SmaliDebuggerException("Stream ended")
			}
			if (res.isError()) {
				throw SmaliDebuggerException(
					"(JDWP Error Code:" + res.getErrorCode() + ") " + res.getErrorText(),
					res.getErrorCode().toInt(),
				)
			}
		}

		/**
		 * 反射读取字段：JDWP 库中部分公开嵌套类的外层类是包级私有，
		 * Kotlin 无法直接引用，只能通过反射访问。
		 */
		private fun readField(obj: Any?, name: String): Any? {
			if (obj == null) {
				return null
			}
			return obj.javaClass.getField(name).get(obj)
		}

		private fun readLongField(obj: Any?, name: String): Long = (readField(obj, name) as? Long) ?: 0L

		private fun writeLongField(obj: Any?, name: String, value: Long) {
			if (obj != null) {
				obj.javaClass.getField(name).setLong(obj, value)
			}
		}

		private fun writeIntField(obj: Any?, name: String, value: Int) {
			if (obj != null) {
				obj.javaClass.getField(name).setInt(obj, value)
			}
		}
	}

	private fun onSuspended(thread: Long, clazz: Long, mth: Long, offset: Long) {
		suspendInfo.update()
			.updateThread(thread)
			.updateClass(clazz)
			.updateMethod(mth)
			.updateOffset(offset)
		if (suspendInfo.isAnythingChanged) {
			SUSPEND_LISTENER_QUEUE.execute { suspendListener.onSuspendEvent(suspendInfo) }
		}
	}

	@Throws(SmaliDebuggerException::class)
	fun stepInto() {
		sendStepRequest(suspendInfo.threadID, JDWP.StepDepth.INTO)
	}

	@Throws(SmaliDebuggerException::class)
	fun stepOver() {
		sendStepRequest(suspendInfo.threadID, JDWP.StepDepth.OVER)
	}

	@Throws(SmaliDebuggerException::class)
	fun stepOut() {
		sendStepRequest(suspendInfo.threadID, JDWP.StepDepth.OUT)
	}

	@Throws(SmaliDebuggerException::class)
	fun exit() {
		val res = sendCommandSync(jdwp.virtualMachine().cmdExit().encode(-1))
		tryThrowError(res)
	}

	@Throws(SmaliDebuggerException::class)
	fun detach() {
		val res = sendCommandSync(jdwp.virtualMachine().cmdDispose().encode())
		tryThrowError(res)
	}

	private fun initPools() {
		slotsPool = ObjectPool {
			val slots = ArrayList<GetValuesSlots>(1)
			val slot = jdwp.stackFrame().cmdGetValues().newValuesSlots()
			slot.slot = 0
			slot.sigbyte = JDWP.Tag.OBJECT.toByte()
			slots.add(slot)
			slots
		}
		stepReqPool = ObjectPool {
			val eventEncoders = ArrayList<JDWP.EventRequestEncoder>(2)
			eventEncoders.add(jdwp.eventRequest().cmdSet().newStepRequest())
			eventEncoders.add(oneOffEventReq)
			eventEncoders
		}
		syncQueuePool = ObjectPool { SynchronousQueue<Packet>() }
		fieldIdPool = ObjectPool {
			val ids = ArrayList<Long>(1)
			ids.add(-1L)
			ids
		}
	}

	/**
	 * 同步读取一个寄存器的值。
	 *
	 * @param regNum 若为参数则传其索引（非静态方法需 +1）；若为局部变量则传
	 *               寄存器号 + 参数个数（非静态方法再 +1）
	 */
	@Throws(SmaliDebuggerException::class)
	fun getRegisterSync(threadID: Long, frameID: Long, regNum: Int, type: RuntimeType): RuntimeRegister {
		val slots = slotsPool.get()
		val slot = slots[0]
		slot.slot = regNum
		slot.sigbyte = type.tag.toByte()
		val res = sendCommandSync(jdwp.stackFrame().cmdGetValues().encode(threadID, frameID, slots))
		tryThrowError(res)
		slotsPool.put(slots)
		val value = jdwp.stackFrame().cmdGetValues().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return buildRegister(regNum, value.values[0].slotValue.tag, value.values[0].slotValue.idOrValue)
	}

	@Throws(SmaliDebuggerException::class)
	fun getThisID(threadID: Long, frameID: Long): Long {
		val res = sendCommandSync(jdwp.stackFrame().cmdThisObject().encode(threadID, frameID))
		tryThrowError(res)
		val data = jdwp.stackFrame().cmdThisObject().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return readLongField(data.objectThis, "objectID")
	}

	@Throws(SmaliDebuggerException::class)
	fun getAllFieldsSync(clsID: Long): List<RuntimeField> = getAllFields(clsID)

	@Throws(SmaliDebuggerException::class)
	fun getFieldValueSync(clsID: Long, fld: RuntimeField) {
		val list = ArrayList<RuntimeField>(1)
		list.add(fld)
		getAllFieldValuesSync(clsID, list)
	}

	@Throws(SmaliDebuggerException::class)
	fun getAllFieldValuesSync(thisID: Long, flds: List<RuntimeField>) {
		val ids = ArrayList<Long>(flds.size)
		flds.forEach { f -> ids.add(f.getFieldID()) }
		val res = sendCommandSync(jdwp.objectReference().cmdGetValues().encode(thisID, ids))
		tryThrowError(res)
		val data = jdwp.objectReference().cmdGetValues().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		val values = data.values
		for (i in values.indices) {
			val value = values[i]
			flds[i].setValue(value.value.idOrValue)
				.setType(RuntimeType.fromJdwpTag(value.value.tag))
		}
	}

	@Throws(SmaliDebuggerException::class)
	fun getCurrentFrame(threadID: Long): Frame = getCurrentFrameInternal(threadID)

	@Throws(SmaliDebuggerException::class)
	fun getFramesSync(threadID: Long): List<Frame> = getAllFrames(threadID)

	@get:Throws(SmaliDebuggerException::class)
	val allThreadsSync: List<Long> get() = allThreads

	@Throws(SmaliDebuggerException::class)
	fun getThreadNameSync(threadID: Long): String? = sendThreadNameReq(threadID)

	@Throws(SmaliDebuggerException::class)
	fun getClassSignatureSync(classID: Long): String? = getClassSignatureInternal(classID)

	@Throws(SmaliDebuggerException::class)
	fun getMethodSignatureSync(classID: Long, methodID: Long): String? = getMethodSignatureInternal(classID, methodID)

	fun errIsTypeMismatched(errCode: Int): Boolean = errCode == JDWP.Error.TYPE_MISMATCH

	fun errIsInvalidSlot(errCode: Int): Boolean = errCode == JDWP.Error.INVALID_SLOT

	fun errIsInvalidObject(errCode: Int): Boolean = errCode == JDWP.Error.INVALID_OBJECT

	private class ClassListenerInfo {
		var prepareReqID: Int = -1
		var unloadReqID: Int = -1
		lateinit var listener: ClassListener

		fun reset(l: ClassListener) {
			this.listener = l
			this.prepareReqID = -1
			this.unloadReqID = -1
		}
	}

	/** 监听类的加载与卸载事件。 */
	@Throws(SmaliDebuggerException::class)
	fun setClassListener(listener: ClassListener) {
		val current = clsListener
		if (current != null) {
			if (listener !== current.listener) {
				unregisterEventSync(JDWP.EventKind.CLASS_PREPARE, current.prepareReqID)
				unregisterEventSync(JDWP.EventKind.CLASS_UNLOAD, current.unloadReqID)
			}
		} else {
			clsListener = ClassListenerInfo()
		}
		checkNotNull(clsListener).reset(listener)
		regClassPrepareEvent(checkNotNull(clsListener))
		regClassUnloadEvent(checkNotNull(clsListener))
	}

	@Throws(SmaliDebuggerException::class)
	private fun regClassUnloadEvent(info: ClassListenerInfo) {
		val res = sendCommandSync(
			jdwp.eventRequest().cmdSet().newClassExcludeRequest(
				JDWP.EventKind.CLASS_UNLOAD.toByte(),
				JDWP.SuspendPolicy.NONE.toByte(),
				"java.*",
			),
		)
		tryThrowError(res)
		info.unloadReqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		eventListenerMap[info.unloadReqID] = object : EventListenerAdapter() {
			override fun onClassUnload(event: ClassUnloadEvent) {
				info.listener.onUnloaded(checkNotNull(DbgUtils.classSigToRawFullName(event.signature)))
			}
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun regClassPrepareEvent(info: ClassListenerInfo) {
		val res = sendCommandSync(
			jdwp.eventRequest().cmdSet().newClassExcludeRequest(
				JDWP.EventKind.CLASS_PREPARE.toByte(),
				JDWP.SuspendPolicy.NONE.toByte(),
				"java.*",
			),
		)
		tryThrowError(res)
		info.prepareReqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		eventListenerMap[info.prepareReqID] = object : EventListenerAdapter() {
			override fun onClassPrepare(event: ClassPrepareEvent) {
				info.listener.onPrepared(checkNotNull(DbgUtils.classSigToRawFullName(event.signature)), event.typeID)
			}
		}
	}

	@Throws(SmaliDebuggerException::class)
	fun regClassPrepareEventForBreakpoint(clsSig: String, l: ClassPrepareListener) {
		val res = sendCommandSync(buildClassMatchReqForBreakpoint(clsSig, JDWP.EventKind.CLASS_PREPARE))
		tryThrowError(res)
		val reqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		eventListenerMap[reqID] = object : EventListenerAdapter() {
			override fun onClassPrepare(event: ClassPrepareEvent) {
				EVENT_LISTENER_QUEUE.execute {
					try {
						l.onPrepared(event.typeID)
					} finally {
						eventListenerMap.remove(reqID)
						try {
							resume()
						} catch (e: SmaliDebuggerException) {
							LOG.error("Resume failed", e)
						}
					}
				}
			}
		}
	}

	/** 方法进入事件监听。 */
	fun interface MethodEntryListener {
		/** @return true 表示移除监听 */
		fun entry(mthSig: String): Boolean
	}

	@Throws(SmaliDebuggerException::class)
	fun regMethodEntryEventSync(clsSig: String, l: MethodEntryListener) {
		val res = sendCommandSync(
			jdwp.eventRequest().cmdSet().newClassMatchRequest(
				JDWP.EventKind.METHOD_ENTRY.toByte(),
				JDWP.SuspendPolicy.ALL.toByte(),
				clsSig,
			),
		)
		tryThrowError(res)
		val reqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		eventListenerMap[reqID] = object : EventListenerAdapter() {
			override fun onMethodEntry(event: MethodEntryEvent) {
				EVENT_LISTENER_QUEUE.execute {
					var removeListener = false
					try {
						val loc = readField(event, "location")
						val sig = getMethodSignatureInternal(readLongField(loc, "classID"), readLongField(loc, "methodID"))
						removeListener = l.entry(checkNotNull(sig))
						if (removeListener) {
							sendCommand(
								jdwp.eventRequest().cmdClear().encode(JDWP.EventKind.METHOD_ENTRY.toByte(), reqID),
								SKIP_RESULT,
							)
							onSuspended(event.thread, readLongField(loc, "classID"), readLongField(loc, "methodID"), -1)
							eventListenerMap.remove(reqID)
						}
					} catch (e: SmaliDebuggerException) {
						LOG.error("Method entry failed", e)
					} finally {
						if (!removeListener) {
							try {
								resume()
							} catch (e: SmaliDebuggerException) {
								LOG.error("Resume failed", e)
							}
						}
					}
				}
			}
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun unregisterEventSync(eventKind: Int, reqID: Int) {
		eventListenerMap.remove(reqID)
		val rst = sendCommandSync(jdwp.eventRequest().cmdClear().encode(eventKind.toByte(), reqID))
		tryThrowError(rst)
	}

	@Throws(SmaliDebuggerException::class)
	fun readObjectSignatureSync(value: RuntimeValue): String {
		val objID = readID(value)
		// 通过对象 id 取得类型引用
		var res = sendCommandSync(jdwp.objectReference().cmdReferenceType().encode(objID))
		tryThrowError(res)
		val data: ReferenceTypeReplyData =
			jdwp.objectReference().cmdReferenceType().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)

		// 通过类型引用 id 取得签名
		res = sendCommandSync(jdwp.referenceType().cmdSignature().encode(data.typeID))
		tryThrowError(res)
		val sigData: SignatureReplyData = jdwp.referenceType().cmdSignature().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return sigData.signature
	}

	@Throws(SmaliDebuggerException::class)
	fun readStringSync(value: RuntimeValue): String = readStringSync(readID(value))

	@Throws(SmaliDebuggerException::class)
	fun readStringSync(id: Long): String {
		val res = sendCommandSync(jdwp.stringReference().cmdValue().encode(id))
		tryThrowError(res)
		val strData: ValueReplyData = jdwp.stringReference().cmdValue().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return strData.stringValue
	}

	@Throws(SmaliDebuggerException::class)
	fun setValueSync(runtimeRegNum: Int, type: RuntimeType, value: Any?, threadID: Long, frameID: Long): Boolean {
		var valToSet = value
		var typeToSet = type
		if (typeToSet == RuntimeType.STRING) {
			val newID = createString(valToSet as String)
			if (newID == -1L) {
				return false
			}
			valToSet = newID
			typeToSet = RuntimeType.OBJECT
		}
		val setters = buildRegValueSetter(typeToSet.tag, runtimeRegNum)
		JDWP.encodeAny(setters[0].slotValue.idOrValue, valToSet)
		val res = sendCommandSync(jdwp.stackFrame().cmdSetValues().encode(threadID, frameID, setters))
		tryThrowError(res)
		return jdwp.stackFrame().cmdSetValues().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
	}

	@Throws(SmaliDebuggerException::class)
	fun setValueSync(objID: Long, fldID: Long, type: RuntimeType, value: Any?): Boolean {
		var valToSet = value
		if (type == RuntimeType.STRING) {
			val newID = createString(valToSet as String)
			if (newID == -1L) {
				return false
			}
			valToSet = newID
		}
		val setters = buildFieldValueSetter()
		val setter = setters[0]
		setter.fieldID = fldID
		JDWP.encodeAny(setter.value.idOrValue, valToSet)
		val res = sendCommandSync(jdwp.objectReference().cmdSetValues().encode(objID, setters))
		tryThrowError(res)
		return jdwp.objectReference().cmdSetValues().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
	}

	@Throws(SmaliDebuggerException::class)
	fun getValueSync(objID: Long, fld: RuntimeField) {
		val ids = fieldIdPool.get()
		ids[0] = fld.getFieldID()
		val res = sendCommandSync(jdwp.objectReference().cmdGetValues().encode(objID, ids))
		tryThrowError(res)
		val data = jdwp.objectReference().cmdGetValues().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		fld.setValue(data.values[0].value.idOrValue)
			.setType(RuntimeType.fromJdwpTag(data.values[0].value.tag))
	}

	@Throws(SmaliDebuggerException::class)
	private fun createString(localStr: String): Long {
		val res = sendCommandSync(jdwp.virtualMachine().cmdCreateString().encode(localStr))
		tryThrowError(res)
		val id: CreateStringReplyData = jdwp.virtualMachine().cmdCreateString().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return id.stringObject
	}

	fun readID(value: RuntimeValue): Long = JDWP.decodeBySize(value.getRawVal().getBytes(), 0, value.getRawVal().size())

	@Throws(SmaliDebuggerException::class)
	fun readArraySignature(value: RuntimeValue): String = readObjectSignatureSync(value)

	@Throws(SmaliDebuggerException::class)
	fun readArrayLength(value: RuntimeValue): Int {
		val res = sendCommandSync(jdwp.arrayReference().cmdLength().encode(readID(value)))
		tryThrowError(res)
		val data: LengthReplyData = jdwp.arrayReference().cmdLength().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return data.arrayLength
	}

	/**
	 * 读取数组内容。
	 *
	 * @param startIndex 小于 0 视为 0
	 * @param len        小于等于 0 表示最多 99 个或剩余全部
	 * @return key 为数组总长（len &lt;= 0 时），value 为对象 id 列表
	 */
	@Throws(SmaliDebuggerException::class)
	fun readArray(reg: RuntimeValue, startIndex: Int, len: Int): Map.Entry<Int, List<Long>?> {
		val id = readID(reg)
		var length = len
		val ret: Map.Entry<Int, List<Long>?>
		if (length <= 0) {
			val res = sendCommandSync(jdwp.arrayReference().cmdLength().encode(id))
			tryThrowError(res)
			val data: LengthReplyData = jdwp.arrayReference().cmdLength().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			length = minOf(99, data.arrayLength)
			ret = SimpleEntry(data.arrayLength, null)
		} else {
			ret = SimpleEntry(0, null)
		}
		val start = maxOf(0, startIndex)
		val res = sendCommandSync(jdwp.arrayReference().cmdGetValues().encode(id, start, length))
		tryThrowError(res)
		val valData = jdwp.arrayReference().cmdGetValues().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		ret.setValue(readField(valData.values, "idOrValues") as List<Long>)
		return ret
	}

	fun readByte(value: RuntimeValue): Byte = JDWP.decodeByte(value.getRawVal().getBytes(), 0)

	fun readChar(value: RuntimeValue): Char = JDWP.decodeChar(value.getRawVal().getBytes(), 0)

	fun readShort(value: RuntimeValue): Short = JDWP.decodeShort(value.getRawVal().getBytes(), 0)

	fun readInt(value: RuntimeValue): Int = JDWP.decodeInt(value.getRawVal().getBytes(), 0)

	fun readFloat(value: RuntimeValue): Float = JDWP.decodeFloat(value.getRawVal().getBytes(), 0)

	/** @param value 一般为 8 字节 */
	fun readAll(value: RuntimeValue): Long = JDWP.decodeBySize(value.getRawVal().getBytes(), 0, minOf(value.getRawVal().size(), 8))

	fun readDouble(value: RuntimeValue): Double = JDWP.decodeDouble(value.getRawVal().getBytes(), 0)

	@Throws(SmaliDebuggerException::class)
	fun getRuntimeDebugInfo(clsID: Long, mthID: Long): RuntimeDebugInfo? {
		val secMap = varMap[clsID]
		var info = secMap?.get(mthID)
		if (info == null) {
			info = initDebugInfo(clsID, mthID)
		}
		return info
	}

	@Throws(SmaliDebuggerException::class)
	private fun initDebugInfo(clsID: Long, mthID: Long): RuntimeDebugInfo {
		val res = sendCommandSync(jdwp.method().cmdVariableTableWithGeneric.encode(clsID, mthID))
		tryThrowError(res)
		val data: VarTableWithGenericData =
			jdwp.method().cmdVariableTableWithGeneric.decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		val info = RuntimeDebugInfo(data)
		varMap.computeIfAbsent(clsID) { HashMap() }[mthID] = info
		return info
	}

	@Throws(SmaliDebuggerException::class)
	private fun getMethodBySig(classID: Long, sig: String): MethodsWithGenericData? {
		val methods = clsMethodMap[classID]
		if (methods != null) {
			for (method in methods) {
				if (sig.startsWith(method.name + "(") && sig.endsWith(method.signature)) {
					return method
				}
			}
		}
		return null
	}

	private fun genID(): Int = idGenerator.getAndAdd(1)

	/** 从 socket 连接读取并解码数据包。 */
	private fun decodingLoop() {
		Executors.newSingleThreadExecutor().execute {
			var errFromCallback: Boolean
			while (true) {
				errFromCallback = false
				try {
					val res = readPacket(inputStream) ?: break
					suspendInfo.nextRound()
					val callback = callbackMap.remove(res.getID())
					if (callback != null) {
						if (callback !== SKIP_RESULT) {
							errFromCallback = true
							callback.onCommandReply(res)
						}
						continue
					}
					if (res.getCommandSetID() == 64 && res.getCommandID() == 100) { // 来自 JVM 的命令
						errFromCallback = true
						decodeCompositeEvents(res)
					} else {
						printUnexpectedID(res.getID())
					}
				} catch (e: SmaliDebuggerException) {
					LOG.error("Error in debugger decoding loop", e)
					if (!errFromCallback) { // 致命错误
						break
					}
				}
			}
			suspendInfo.setTerminated()
			clearWaitingSyncQueue()
			suspendListener.onSuspendEvent(suspendInfo)
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun sendCommand(buf: ByteBuffer, callback: ICommandResult) {
		val id = genID()
		callbackMap[id] = callback
		try {
			outputStream.write(buf.setPacketID(id).getBytes())
		} catch (e: IOException) {
			throw SmaliDebuggerException(e)
		}
	}

	/**
	 * 不要在本方法的回调里使用它，否则会死锁。应在线程中使用。
	 */
	@Throws(SmaliDebuggerException::class)
	private fun sendCommandSync(buf: ByteBuffer): Packet {
		val store = syncQueuePool.get()
		sendCommand(buf) { res ->
			try {
				store.put(res)
			} catch (e: Exception) {
				LOG.error("Command send failed", e)
			}
		}
		val id = syncQueueID.getAndAdd(1)
		try {
			syncQueueMap[id] = Thread.currentThread()
			return store.take()
		} catch (e: InterruptedException) {
			throw SmaliDebuggerException(e)
		} finally {
			syncQueueMap.remove(id)
			syncQueuePool.put(store)
		}
	}

	// 由 decodingLoop() 在发生致命错误时调用，否则 store.take() 可能永久阻塞。
	private fun clearWaitingSyncQueue() {
		syncQueueMap.keys.forEach { k ->
			val t = syncQueueMap.remove(k)
			if (t != null) {
				t.interrupt()
			}
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun printUnexpectedID(id: Int): Unit = throw SmaliDebuggerException("Missing handler for this id: $id")

	@Throws(SmaliDebuggerException::class)
	private fun decodeCompositeEvents(res: Packet) {
		val data: EventData = jdwp.event().cmdComposite().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		for (event in data.events) {
			val listener = eventListenerMap[event.getRequestID()]
			if (listener == null) {
				LOG.error("Missing handler for id: {}", event.getRequestID())
				continue
			}
			if (event is VMStartEvent) {
				listener.onVMStart(event)
				return
			}
			if (event is VMDeathEvent) {
				listener.onVMDeath(event)
				return
			}
			if (event is SingleStepEvent) {
				listener.onSingleStep(event)
				return
			}
			if (event is BreakpointEvent) {
				listener.onBreakpoint(event)
				return
			}
			if (event is MethodEntryEvent) {
				listener.onMethodEntry(event)
				return
			}
			if (event is MethodExitEvent) {
				listener.onMethodExit(event)
				return
			}
			if (event is MethodExitWithReturnValueEvent) {
				listener.onMethodExitWithReturnValue(event)
				return
			}
			if (event is MonitorContendedEnterEvent) {
				listener.onMonitorContendedEnter(event)
				return
			}
			if (event is MonitorContendedEnteredEvent) {
				listener.onMonitorContendedEntered(event)
				return
			}
			if (event is MonitorWaitEvent) {
				listener.onMonitorWait(event)
				return
			}
			if (event is MonitorWaitedEvent) {
				listener.onMonitorWaited(event)
				return
			}
			if (event is ExceptionEvent) {
				listener.onException(event)
				return
			}
			if (event is ThreadStartEvent) {
				listener.onThreadStart(event)
				return
			}
			if (event is ThreadDeathEvent) {
				listener.onThreadDeath(event)
				return
			}
			if (event is ClassPrepareEvent) {
				listener.onClassPrepare(event)
				return
			}
			if (event is ClassUnloadEvent) {
				listener.onClassUnload(event)
				return
			}
			if (event is FieldAccessEvent) {
				listener.onFieldAccess(event)
				return
			}
			if (event is FieldModificationEvent) {
				listener.onFieldModification(event)
				return
			}
			throw SmaliDebuggerException("Unexpected event: $event")
		}
	}

	private val stepListener: EventListenerAdapter = object : EventListenerAdapter() {
		override fun onSingleStep(event: SingleStepEvent) {
			val loc = readField(event, "location")
			onSuspended(event.thread, readLongField(loc, "classID"), readLongField(loc, "methodID"), readLongField(loc, "index"))
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun sendStepRequest(threadID: Long, depth: Int) {
		val stepReq = buildStepRequest(threadID, JDWP.StepSize.MIN, depth)
		val stepEncodedBuf = jdwp.eventRequest().cmdSet().encode(
			JDWP.EventKind.SINGLE_STEP.toByte(),
			JDWP.SuspendPolicy.ALL.toByte(),
			stepReq,
		)
		stepReqPool.put(stepReq)
		sendCommand(stepEncodedBuf) { res ->
			tryThrowError(res)
			val reqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			eventListenerMap[reqID] = stepListener
		}
		resume()
	}

	@Throws(SmaliDebuggerException::class)
	fun resume() {
		sendCommand(JDWP.Resume.encode(), SKIP_RESULT)
	}

	@Throws(SmaliDebuggerException::class)
	fun suspend() {
		sendCommand(JDWP.Suspend.encode(), SKIP_RESULT)
	}

	@Throws(SmaliDebuggerException::class)
	fun setBreakpoint(bp: RuntimeBreakpoint) {
		sendCommand(buildBreakpointRequest(bp)) { res ->
			tryThrowError(res)
			bp.reqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			eventListenerMap[bp.reqID] = object : EventListenerAdapter() {
				override fun onBreakpoint(event: BreakpointEvent) {
					val loc = readField(event, "location")
					onSuspended(
						event.thread,
						readLongField(loc, "classID"),
						readLongField(loc, "methodID"),
						readLongField(loc, "index"),
					)
				}
			}
		}
	}

	@Throws(SmaliDebuggerException::class)
	fun getClassID(clsSig: String, fetch: Boolean): Long {
		var shouldFetch = fetch
		do {
			val data = classMap[clsSig]
			if (data == null) {
				if (shouldFetch) {
					getAllClasses()
					shouldFetch = false
					continue
				}
				break
			} else {
				return data.typeID
			}
		} while (true)
		return -1
	}

	@Throws(SmaliDebuggerException::class)
	fun getMethodID(cid: Long, mthSig: String): Long {
		initClassCache(cid)
		val data = getMethodBySig(cid, mthSig)
		if (data != null) {
			return data.methodID
		}
		return -1
	}

	@Throws(SmaliDebuggerException::class)
	fun initClassCache(clsID: Long) {
		initFields(clsID)
		initMethods(clsID)
	}

	@Throws(SmaliDebuggerException::class)
	fun removeBreakpoint(bp: RuntimeBreakpoint) {
		sendCommand(
			jdwp.eventRequest().cmdClear().encode(JDWP.EventKind.BREAKPOINT.toByte(), bp.reqID),
			SKIP_RESULT,
		)
	}

	private fun buildBreakpointRequest(bp: RuntimeBreakpoint): ByteBuffer {
		val req = jdwp.eventRequest().cmdSet().newLocationOnlyRequest()
		val loc = readField(req, "loc")
		writeLongField(loc, "classID", bp.clsID)
		writeLongField(loc, "methodID", bp.mthID)
		writeLongField(loc, "index", bp.offset)
		writeIntField(loc, "tag", JDWP.TypeTag.CLASS)
		val list = ArrayList<JDWP.EventRequestEncoder>(1)
		list.add(req)
		return jdwp.eventRequest().cmdSet().encode(
			JDWP.EventKind.BREAKPOINT.toByte(),
			JDWP.SuspendPolicy.ALL.toByte(),
			list,
		)
	}

	/** 为设置断点构建一次性的类准备事件。 */
	private fun buildClassMatchReqForBreakpoint(cls: String, eventKind: Int): ByteBuffer {
		val encoders = ArrayList<JDWP.EventRequestEncoder>(2)
		val match: ClassMatchRequest = jdwp.eventRequest().cmdSet().newClassMatchRequest()
		encoders.add(match)
		encoders.add(oneOffEventReq)
		match.classPattern = cls
		return jdwp.eventRequest().cmdSet().encode(
			eventKind.toByte(),
			JDWP.SuspendPolicy.ALL.toByte(),
			encoders,
		)
	}

	private fun buildStepRequest(threadID: Long, stepSize: Int, stepDepth: Int): MutableList<JDWP.EventRequestEncoder> {
		val eventEncoders = stepReqPool.get()
		val req = eventEncoders[0] as StepRequest
		req.size = stepSize
		req.depth = stepDepth
		req.thread = threadID
		return eventEncoders
	}

	private fun buildFieldValueSetter(): MutableList<FieldValueSetter> {
		val setter = jdwp.objectReference().cmdSetValues().FieldValueSetter()
		setter.value = jdwp.UntaggedValuePacket()
		setter.value.idOrValue = ByteBuffer()
		val setters = ArrayList<FieldValueSetter>(1)
		setters.add(setter)
		return setters
	}

	private fun buildRegValueSetter(tag: Int, regNum: Int): MutableList<SlotValueSetter> {
		val setters = ArrayList<SlotValueSetter>(1)
		val setter = jdwp.stackFrame().cmdSetValues().SlotValueSetter()
		setters.add(setter)
		setter.slot = regNum
		setter.slotValue = jdwp.ValuePacket()
		setter.slotValue.tag = tag
		setter.slotValue.idOrValue = ByteBuffer()
		return setters
	}

	@Throws(SmaliDebuggerException::class)
	private fun getClassSignatureInternal(id: Long): String? {
		var data = classIDMap[id]
		if (data == null) {
			getAllClasses()
		}
		data = classIDMap[id]
		return data?.signature
	}

	@Throws(SmaliDebuggerException::class)
	private fun getMethodSignatureInternal(clsID: Long, mthID: Long): String? {
		var mthData = clsMethodMap[clsID]
		if (mthData == null) {
			val res = sendCommandSync(jdwp.referenceType().cmdMethodsWithGeneric().encode(clsID))
			tryThrowError(res)
			val data: MethodsWithGenericReplyData =
				jdwp.referenceType().cmdMethodsWithGeneric().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			mthData = data.declared
			clsMethodMap[clsID] = mthData
		}
		if (mthData != null) {
			for (data in mthData) {
				if (data.methodID == mthID) {
					return data.name + data.signature
				}
			}
		}
		return null
	}

	@Throws(SmaliDebuggerException::class)
	private fun sendThreadNameReq(id: Long): String {
		val res = sendCommandSync(jdwp.threadReference().cmdName().encode(id))
		tryThrowError(res)
		val nameData: NameReplyData = jdwp.threadReference().cmdName().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		return nameData.threadName
	}

	@Throws(SmaliDebuggerException::class)
	private fun getAllFields(clsID: Long): List<RuntimeField> {
		initFields(clsID)
		val flds = clsFieldMap[clsID]
		if (flds != null && flds.isNotEmpty()) {
			val rfs = ArrayList<RuntimeField>(flds.size)
			for (fld in flds) {
				var type = fld.signature
				if (fld.genericSignature != null && !fld.genericSignature.trim().isEmpty()) {
					type += "<" + fld.genericSignature + ">"
				}
				rfs.add(RuntimeField(fld.name, type, fld.fieldID, fld.modBits))
			}
			return rfs
		}
		return emptyList()
	}

	@Throws(SmaliDebuggerException::class)
	fun getCurrentFrameInternal(threadID: Long): Frame {
		val res = sendCommandSync(jdwp.threadReference().cmdFrames().encode(threadID, 0, 1))
		tryThrowError(res)
		val frameData: FramesReplyData =
			jdwp.threadReference().cmdFrames().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		val frame: FramesReplyDataFrames = frameData.frames[0]
		val loc = readField(frame, "location")
		return Frame(
			frame.frameID,
			readLongField(loc, "classID"),
			readLongField(loc, "methodID"),
			readLongField(loc, "index"),
		)
	}

	@Throws(SmaliDebuggerException::class)
	private fun getAllFrames(threadID: Long): List<Frame> {
		val res = sendCommandSync(jdwp.threadReference().cmdFrames().encode(threadID, 0, -1))
		tryThrowError(res)
		val frameData: FramesReplyData =
			jdwp.threadReference().cmdFrames().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		val frames = ArrayList<Frame>()
		for (frame in frameData.frames) {
			val loc = readField(frame, "location")
			frames.add(
				Frame(
					frame.frameID,
					readLongField(loc, "classID"),
					readLongField(loc, "methodID"),
					readLongField(loc, "index"),
				),
			)
		}
		return frames
	}

	@get:Throws(SmaliDebuggerException::class)
	private val allThreads: List<Long> get() {
		val res = sendCommandSync(jdwp.virtualMachine().cmdAllThreads().encode())
		tryThrowError(res)
		val data: AllThreadsReplyData =
			jdwp.virtualMachine().cmdAllThreads().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		val threads = ArrayList<Long>(data.threads.size)
		for (thread in data.threads) {
			threads.add(thread.thread)
		}
		return threads
	}

	@Throws(SmaliDebuggerException::class)
	private fun getAllClasses() {
		val res = sendCommandSync(jdwp.virtualMachine().cmdAllClassesWithGeneric().encode())
		tryThrowError(res)
		val classData: AllClassesWithGenericReplyData =
			jdwp.virtualMachine().cmdAllClassesWithGeneric().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
		for (aClass in classData.classes) {
			classMap[checkNotNull(DbgUtils.classSigToRawFullName(aClass.signature))] = aClass
			classIDMap[aClass.typeID] = aClass
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun initFields(clsID: Long) {
		if (clsFieldMap[clsID] == null) {
			val res = sendCommandSync(jdwp.referenceType().cmdFieldsWithGeneric().encode(clsID))
			tryThrowError(res)
			val data: FieldsWithGenericReplyData =
				jdwp.referenceType().cmdFieldsWithGeneric().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			clsFieldMap[clsID] = data.declared
		}
	}

	@Throws(SmaliDebuggerException::class)
	private fun initMethods(clsID: Long) {
		if (clsMethodMap[clsID] == null) {
			val res = sendCommandSync(jdwp.referenceType().cmdMethodsWithGeneric().encode(clsID))
			tryThrowError(res)
			val data: MethodsWithGenericReplyData =
				jdwp.referenceType().cmdMethodsWithGeneric().decode(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			clsMethodMap[clsID] = data.declared
		}
	}

	/** 当类从 JVM 卸载时移除类缓存。 */
	@Throws(SmaliDebuggerException::class)
	private fun listenClassUnloadEvent() {
		sendCommand(
			jdwp.eventRequest().cmdSet().encode(
				JDWP.EventKind.CLASS_UNLOAD.toByte(),
				JDWP.SuspendPolicy.NONE.toByte(),
				emptyList(),
			),
		) { res ->
			val reqID = jdwp.eventRequest().cmdSet().decodeRequestID(res.getBuf(), JDWP.PACKET_HEADER_SIZE)
			eventListenerMap[reqID] = object : EventListenerAdapter() {
				override fun onClassUnload(event: ClassUnloadEvent) {
					EVENT_LISTENER_QUEUE.execute {
						System.out.printf("ClassUnloaded: %s%n", event.signature)
						val clsData = classMap.remove(event.signature)
						if (clsData != null) {
							classIDMap.remove(clsData.typeID)
							clsFieldMap.remove(clsData.typeID)
							clsMethodMap.remove(clsData.typeID)
							varMap.remove(clsData.typeID)
						}
					}
				}
			}
		}
	}

	private fun interface ICommandResult {
		@Throws(SmaliDebuggerException::class)
		fun onCommandReply(res: Packet)
	}

	/** 远端字段。 */
	class RuntimeField internal constructor(
		private val name: String,
		private val fldType: String,
		private val fieldID: Long,
		private val modBits: Int,
	) : RuntimeValue(null, null) {
		val fieldType: String get() = fldType

		fun getName(): String = name

		fun getFieldID(): Long = fieldID

		internal fun setValue(rawVal: ByteBuffer): RuntimeField {
			this.rawVal = rawVal
			return this
		}

		val isBelongToThis: Boolean get() = !AccessFlags.hasFlag(modBits, AccessFlags.STATIC) &&
			!AccessFlags.hasFlag(modBits, AccessFlags.SYNTHETIC)
	}

	/** 已下发的断点（含 JDWP 请求 id）。 */
	class RuntimeBreakpoint internal constructor() {
		internal var clsID: Long = 0
		internal var mthID: Long = 0
		internal var offset: Long = 0
		internal var reqID: Int = 0

		val codeOffset: Long get() = offset
	}

	fun makeBreakpoint(cid: Long, mid: Long, offset: Long): RuntimeBreakpoint {
		val bp = RuntimeBreakpoint()
		bp.clsID = cid
		bp.mthID = mid
		bp.offset = offset
		return bp
	}

	@Throws(SmaliDebuggerException::class)
	private fun buildRegister(num: Int, tag: Int, buf: ByteBuffer): RuntimeRegister = RuntimeRegister(num, RuntimeType.fromJdwpTag(tag), buf)

	/** 带类型的运行时值（寄存器或字段）。 */
	open class RuntimeValue internal constructor(
		protected var rawVal: ByteBuffer?,
		protected var valueType: RuntimeType?,
	) {
		val type: RuntimeType get() = checkNotNull(valueType)

		fun setType(type: RuntimeType) {
			this.valueType = type
		}

		internal fun getRawVal(): ByteBuffer = checkNotNull(rawVal)
	}

	/** 运行时寄存器。 */
	class RuntimeRegister internal constructor(
		private val num: Int,
		type: RuntimeType,
		rawVal: ByteBuffer,
	) : RuntimeValue(rawVal, type) {
		val regNum: Int get() = num
	}

	/** 远端调试变量表中的一个变量。 */
	class RuntimeVarInfo internal constructor(private val slot: VarWithGenericSlot) : RegisterInfo() {
		override val name: String get() = slot.name

		override val regNum: Int get() = slot.slot

		override val type: String get() {
			val gen = signature
			if (gen.isEmpty()) {
				return this.slot.signature
			}
			return gen
		}

		override val signature: String get() = this.slot.genericSignature.trim()

		override val startOffset: Int get() = slot.codeIndex.toInt()

		override val endOffset: Int get() = (slot.codeIndex + slot.length).toInt()

		override val isMarkedAsParameter: Boolean get() = false
	}

	/** 一个方法的远端调试信息。 */
	class RuntimeDebugInfo internal constructor(data: VarTableWithGenericData) {
		private val infoList: MutableList<RuntimeVarInfo> = ArrayList(data.slots.size)

		init {
			for (slot in data.slots) {
				infoList.add(RuntimeVarInfo(slot))
			}
		}

		fun getInfoList(): List<RuntimeVarInfo> = infoList
	}

	/** 一个栈帧。 */
	class Frame internal constructor(
		private val id: Long,
		private val clsID: Long,
		private val mthID: Long,
		private val index: Long,
	) {
		val iD: Long get() = id

		val classID: Long get() = clsID

		val methodID: Long get() = mthID

		val codeIndex: Long get() = index
	}

	fun interface ClassPrepareListener {
		fun onPrepared(id: Long)
	}

	interface ClassListener {
		fun onPrepared(cls: String, id: Long)

		fun onUnloaded(cls: String)
	}

	/** 断点、监视、单步等事件的监听器。 */
	interface SuspendListener {
		/**
		 * 单步、断点、监视点等事件挂起 JVM 时回调。
		 * 本方法在 stateListenQueue 中执行。
		 */
		fun onSuspendEvent(current: SuspendInfo)
	}
}
