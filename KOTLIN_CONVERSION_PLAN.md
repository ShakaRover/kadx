# Kotlin Conversion Plan: jadx-dex-input

Module: jadx-plugins/jadx-dex-input
Total Java files: 40

## Execution Order (SOP Topology)

1. batch-1 — Core Reader & Options (5 files, no deps)
2. batch-4 (DexCodeReader first) — Code & Debug Parsing (7 files, depends on batch-1)
3. batch-3 (Sections) — Core Data Models (8 files, depends on batch-4)
4. batch-2 (Insns) — Instruction System (6 files, independent)
5. batch-5 (Utils/Smali) — Utilities & Output (7 files, leaf nodes)

## Batches

### batch-1: Core Reader & Options (5 files)
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexFileLoader.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexReader.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexInputOptions.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexException.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexLoadResult.java

### batch-4: Code & Debug Parsing (7 files)
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexCodeReader.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/annotations/AnnotationsParser.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/debuginfo/DebugInfoParser.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexConsts.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/SectionReader.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/utils/DataReader.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/utils/MUtf8.java

### batch-3: Sections (Core Data Models) (8 files)
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexClassData.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexMethodData.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexFieldData.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexHeader.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexMethodProto.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexMethodRef.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexAnnotationsConvert.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/utils/IDexData.java

### batch-2: Insns & Opcodes (6 files)
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnData.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexOpcodes.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnFormat.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnMnemonics.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/smali/SmaliCodeWriter.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/smali/SmaliInsnFormat.java

### batch-5: Utils & Smali (7 files)
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/utils/Leb128.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/utils/SimpleDexData.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/smali/SmaliPrinter.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexInputPlugin.java
- jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexAnnotationsConvert.java

---
Last updated: 2026-W38 (Session #7) — Plan ready, waiting for batch-1 execution
