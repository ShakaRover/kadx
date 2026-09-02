# Kotlin Conversion Plan: jadx-dex-input (Accurate)

**Module:** jadx-plugins/jadx-dex-input  
**Total Java Files:** 40  
**Batches:** 5 (each ≤10 files, SOP topology order)

---

## Execution Order

| # | Batch | Description | File Count | Dependencies |
|---|-------|-------------|------------|--------------|
| 1 | batch-1 | Core Reader & Config | **6** | none |
| 2 | batch-4 | Code & Debug Parsing | **7** | batch-1 (DexCodeReader) |
| 3 | batch-3 | Sections / Data Models | **8** | batch-4 |
| 4 | batch-2 | Insns & Opcodes | **6** | independent |
| 5 | batch-5 | Utils & Smali Output | **13** | leaf nodes |

---

## Batch #1: Core Reader & Config (6 files, no deps)

1. jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexFileLoader.java (194 lines)
2. jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexReader.java (1566 lines)
3. jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexInputOptions.java (490 lines)
4. jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexException.java (303 lines)
5. jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexLoadResult.java (917 lines)
6. jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexInputPlugin.java

---

## Batch #2: Code & Debug Parsing (7 files, depends on batch-1)

1. DexCodeReader.java — Core code stream reader
2. DebugInfoParser.java — Debug info extraction
3. AnnotationsParser.java — Annotation parsing core
4. DataReader.java — Binary data reading utility
5. MUtF8.java — UTF-8 decoding for Dex strings
6. DexConsts.java — Constants (standalone)
7. SectionReader.java — Abstract section reader interface

---

## Batch #3: Sections / Data Models (8 files, depends on batch-2)

1. DexClassData.java — Class data container (large file)
2. DexMethodData.java — Method data structure
3. DexFieldData.java — Field reference data
4. DexHeader.java + DexHeaderV41.java — DEX file header parsing (2 files)
5. DexMethodProto.java — Method proto reference
6. DexMethodRef.java — Method reference data
7. SimpleDexData.java — Simple data wrapper (standalone)
8. DexAnnotationsConvert.java — Annotation conversion utility

---

## Batch #4: Insns & Opcodes (6 files, independent)

1. DexInsnData.java — Instruction data structure
2. DexOpcodes.java — Opcode enumeration (155 values)
3. DexInsnFormat.java — Instruction format definitions
4. DexInsnMnemonics.java — Mnemonic mappings
5. SmaliCodeWriter.java — Smali code output generator
6. SmaliInsnFormat.java — Smali instruction format

---

## Batch #5: Utils & Smali Output (13 files, leaf nodes)

**Utils subgroup:**
- Leb128.java — LEB128 encoding helper
- DexCheckSum.java — DEX checksum verification
- IDexData.java — Data interface (standalone)
- MUtF8.java — UTF-8 decoding

**Smali Output subgroup:**
- InsnFormatterInfo.java — Formatter configuration info
- InsnFormatter.java — Instruction formatter core
- SmaliPrinter.java — Smali code pretty printer

---

## SOP Compliance Notes

1. Static methods → companion object + @JvmStatic (DexFileLoader.getNextUniqId, resetDexUniqId)
2. Field access patterns → @JvmField for public static final fields where applicable
3. Null safety → Convert @Nullable to Kotlin ? type; validate before construction
4. Generics → Use out T : X for covariance in visitor interfaces
5. Synthetic properties → If upstream .kt files use .propName, declare actual property or use explicit fun call

---

## Last Updated

2026-W38 (Session #7) — Plan validated: 6+7+8+6+13=40 files total
