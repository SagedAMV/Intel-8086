# Intel 8086 Emulator for Android (Kotlin port)

A native Android learning app for entering short 16-bit Assembly programs, running them one instruction at a time, and inspecting registers, flags, memory, and DOS character I/O. This port replaces the active Java app code with Kotlin and keeps the original Java source under `legacy_java_reference/` for comparison.

## Features
- Kotlin 16-bit teaching emulator with 8/16-bit general-register aliases, flags, and little-endian data memory.
- Editor with validation, file open/save through Android's Storage Access Framework, and a recent-program shortcut.
- Step, run, and reset controls; register/flag/output inspection.
- Original sample templates and instruction-reference JSON retained and displayed in Kotlin screens.
- Adjustable editor font size and About screen.

## Supported instruction subset
`MOV`, `XCHG`, `ADD`, `SUB`, `CMP`, `AND`, `OR`, `XOR`, `TEST`, `INC`, `DEC`, `NEG`, `NOT`, `MUL`, `DIV`, `JMP`, conditional jumps, `LOOP` variants, `STC`, `CLC`, `CMC`, `STD`, `CLD`, `CLI`, `NOP`, `HLT`, and `INT 21h` character input/output (AH=1/AH=2). Labels, `;` comments, decimal, `...H`/`0x...` hexadecimal, and `...B` binary literals are accepted. This is a teaching subset, not a cycle-accurate or complete 8086 implementation; unsupported assembler directives and opcodes are rejected.

## Build
Requirements: JDK 17, Android SDK Platform 35, and Android Build Tools 35.0.1.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
