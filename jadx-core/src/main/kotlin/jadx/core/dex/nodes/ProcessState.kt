package jadx.core.dex.nodes

enum class ProcessState {
	NOT_LOADED,
	LOADED,
	PROCESS_STARTED,
	PROCESS_COMPLETE,
	GENERATED_AND_UNLOADED,
	;

	fun isProcessComplete(): Boolean = this == PROCESS_COMPLETE || this == GENERATED_AND_UNLOADED
}
