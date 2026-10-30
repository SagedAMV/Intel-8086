# Intel 8086 Emulator for Android (Kotlin port)

A native Android learning app for entering short 16-bit Assembly programs, running them one instruction at a time, and inspecting registers, flags, memory, and DOS character I/O. This port replaces the active Java app code with Kotlin and keeps the original Java source under `legacy_java_reference/` for comparison.

## Features
- Kotlin 16-bit teaching emulator with 8/16-bit general-register aliases, flags, and little-endian data memory.
- Editor with validation, file open/save through Android's Storage Access Framework, and a recent-program shortcut.
- Step, run, and reset controls; register/flag/output inspection.
- Original sample templates and instruction-reference JSON retained and displayed in Kotlin screens.
- Adjustable editor font size and About screen.

## Supported instruction set
The engine implements the original Intel 8086/8088 instruction set (81 instruction types / 76 entries once
male/female variants and aliases are collapsed):

- **Data transfer:** `MOV`, `PUSH`, `POP`, `PUSHF`, `POPF`, `XCHG`, `LAHF`, `SAHF`, `LEA`, `LDS`, `LES`, `IN`, `OUT`, `XLAT`
- **Arithmetic:** `ADD`, `ADC`, `SUB`, `SBB`, `CMP`, `INC`, `DEC`, `NEG`, `MUL`, `IMUL`, `DIV`, `IDIV`, `CBW`, `CWD`, `AAA`, `AAS`, `AAM`, `AAD`, `DAA`, `DAS`
- **Logic:** `AND`, `OR`, `XOR`, `NOT`, `TEST`
- **Shifts and rotates:** `SHL`/`SAL`, `SHR`, `SAR`, `ROL`, `ROR`, `RCL`, `RCR` (by 1, by `CL`, or by an immediate count masked to 5 bits)
- **Strings:** `MOVSB`/`MOVSW`, `CMPSB`/`CMPSW`, `SCASB`/`SCASW`, `LODSB`/`LODSW`, `STOSB`/`STOSW` with the `REP`, `REPE`/`REPZ` and `REPNE`/`REPNZ` prefixes
- **Control transfer:** `JMP`, all 16 conditional jumps (including the `JNG` alias), `JCXZ`, `LOOP`/`LOOPE`/`LOOPZ`/`LOOPNE`/`LOOPNZ`, `CALL`, `RET`/`RETN`/`RETF`, `IRET`, `INTO`, `INT 21h`
- **Flags and machine control:** `STC`, `CLC`, `CMC`, `STD`, `CLD`, `STI`, `CLI`, `NOP`, `HLT`, `WAIT`, `LOCK`, `ESC`

Notes on the model:

- The stack is real: `SP` starts at `FFFEH`, `PUSH`/`POP`, `CALL`/`RET`, `PUSHF`/`POPF` and `IRET` all use it.
- Memory is a flat 64 KiB offset space; the segment registers are stored and can be loaded, but addressing does not
  apply them, so `[BX+SI]` and `ES:[BX]` reach the same byte. String destinations use `DI` and sources use `SI`.
- `IP` is modelled as an index into the parsed source lines, which is what `CALL`, `RET` and `IRET` push and pop.
- `INT 21h` implements AH=1 (read a character), AH=2 (write `DL`), AH=9 (print the `'$'`-terminated string at
  `DS:DX`) and AH=4Ch (terminate). Other vectors are rejected.
- `IN`/`OUT` use a small port space that starts at zero; `WAIT`, `LOCK` and `ESC` behave as no-ops (there is no 8087).
- A `REP` prefix completes all its iterations inside a single `step()` call, because on real hardware `REP` is part
  of one instruction.
- Labels, `;` comments, decimal (also with the MASM `...D` suffix), `...H`/`0x...` hexadecimal, `...B` binary
  literals and character literals such as `'A'` or `','` are accepted. Assembler directives (`DB`, `DW`, `ORG`,
  `EQU`, `SEGMENT`) are not assembled.
- `PUSH SP` stores the already decremented `SP` (the 8086 quirk; the 80286 changed it), `PUSHF` sets the reserved
  high bits, `CS` and `IP` cannot be written directly, and immediates are truncated to the operand width.
- Every malformed program stops with a message that names the offending token (`Invalid number 'FOO'`) instead of a
  runtime exception; the validator reports the same problems before the program is ever run.

### What the validator checks

`AssemblyEngine.validate()` runs the same rules as the engine, so a program can never execute in a state the editor
would have refused. Each message names the token and the fix:

| Rule | Example message |
| --- | --- |
| both operands have one width | `MOV operands must be the same size: AX is 16-bit but BL is 8-bit.` |
| a constant fits its destination | `MOV cannot store 300 in a 8-bit operand (allowed range -128..255)` |
| one character per literal | `Character constant 'AB' must contain exactly one character, for example 'A'.` |
| labels are not values | `Label 'COUNT' cannot be used as a value; labels are only valid as jump and call targets.` |
| labels do not shadow registers | `Label 'CS' has the same name as a register; choose another label name.` |
| labels end with a colon, no space | `Labels must be written like 'START:' with no space before the colon.` |
| the last label marks an instruction | `Label 'L1' is not followed by any instruction, so it points past the end of the program` |
| hex constants start with a digit | `a hex constant must start with a digit, so write 0E53BH instead.` |
| a typo gets a suggestion | `Unknown opcode 'MVO': this instruction is invalid or not implemented. Did you mean MOV?` |
| directives are named as such | `Assembler directives are not supported: use only 8086 instructions` |
| jumps take a label | `A jump must target a label, but 'AX' is a register.` |
| `RET` needs its `CALL` | `RET returns to an address that was never pushed, because the program contains no CALL` |
| `INT` only supports 21h | `Only INT 21h is supported by this emulator; 10H has no handler here.` |
| segment registers travel only with `MOV`/`PUSH`/`POP` | `ADD cannot use the segment register DS` |

Everything the runtime can object to is an `IllegalArgumentException` with the same wording, so the message in the
editor and the message during execution never contradict each other.

## Build
Requirements: JDK 17, Android SDK Platform 35, and Android Build Tools 35.0.1.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
