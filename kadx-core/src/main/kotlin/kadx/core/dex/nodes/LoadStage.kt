package kadx.core.dex.nodes

enum class LoadStage {
	NONE,
	PROCESS_STAGE, // dependencies not yet loaded
	CODEGEN_STAGE, // all dependencies loaded
}
