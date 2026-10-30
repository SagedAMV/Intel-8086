package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the instruction-set contract: every mnemonic of the original Intel 8086/8088 instruction
 * set must be accepted by the validator and must execute at least one step without an
 * "Unsupported instruction" error.
 */
class InstructionCoverageTest {

    private val samples: Map<String, String> = linkedMapOf(
        // data transfer
        "MOV" to "MOV AX, 1", "PUSH" to "PUSH AX", "POP" to "POP AX", "XCHG" to "XCHG AX, BX",
        "IN" to "IN AL, 60H", "OUT" to "OUT 60H, AL", "XLAT" to "XLAT", "LEA" to "LEA AX, [BX]",
        "LDS" to "LDS AX, [BX]", "LES" to "LES AX, [BX]", "LAHF" to "LAHF", "SAHF" to "SAHF",
        "PUSHF" to "PUSHF", "POPF" to "POPF",
        // arithmetic
        "ADD" to "ADD AX, 1", "ADC" to "ADC AX, 1", "SUB" to "SUB AX, 1", "SBB" to "SBB AX, 1",
        "INC" to "INC AX", "DEC" to "DEC AX", "NEG" to "NEG AX", "CMP" to "CMP AX, 1",
        "MUL" to "MUL BX", "IMUL" to "IMUL BX", "DIV" to "MOV BX, 2\nDIV BX", "IDIV" to "MOV BX, 2\nIDIV BX",
        "AAA" to "AAA", "AAS" to "AAS", "AAM" to "AAM", "AAD" to "AAD", "DAA" to "DAA", "DAS" to "DAS",
        "CBW" to "CBW", "CWD" to "CWD",
        // logic
        "AND" to "AND AX, 1", "OR" to "OR AX, 1", "XOR" to "XOR AX, 1", "NOT" to "NOT AX", "TEST" to "TEST AX, 1",
        // shifts and rotates
        "SHL" to "SHL AX, 1", "SAL" to "SAL AX, 1", "SHR" to "SHR AX, 1", "SAR" to "SAR AX, 1",
        "ROL" to "ROL AX, 1", "ROR" to "ROR AX, 1", "RCL" to "RCL AX, 1", "RCR" to "RCR AX, 1",
        // strings
        "MOVSB" to "MOVSB", "MOVSW" to "MOVSW", "CMPSB" to "CMPSB", "CMPSW" to "CMPSW",
        "SCASB" to "SCASB", "SCASW" to "SCASW", "LODSB" to "LODSB", "LODSW" to "LODSW",
        "STOSB" to "STOSB", "STOSW" to "STOSW", "REP" to "REP MOVSB", "REPE" to "REPE CMPSB",
        "REPNE" to "REPNE SCASB",
        // control transfer
        "JMP" to "L1: NOP\nJMP L1", "CALL" to "CALL L1\nL1: RET", "RET" to "RET", "RETN" to "RETN",
        "RETF" to "RETF", "IRET" to "IRET", "INTO" to "INTO",
        "JA" to "L1: NOP\nJA L1", "JNBE" to "L1: NOP\nJNBE L1", "JAE" to "L1: NOP\nJAE L1",
        "JNB" to "L1: NOP\nJNB L1", "JNC" to "L1: NOP\nJNC L1", "JB" to "L1: NOP\nJB L1",
        "JNAE" to "L1: NOP\nJNAE L1", "JC" to "L1: NOP\nJC L1", "JBE" to "L1: NOP\nJBE L1",
        "JNA" to "L1: NOP\nJNA L1", "JE" to "L1: NOP\nJE L1", "JZ" to "L1: NOP\nJZ L1",
        "JG" to "L1: NOP\nJG L1", "JNLE" to "L1: NOP\nJNLE L1", "JGE" to "L1: NOP\nJGE L1",
        "JNL" to "L1: NOP\nJNL L1", "JL" to "L1: NOP\nJL L1", "JNGE" to "L1: NOP\nJNGE L1",
        "JLE" to "L1: NOP\nJLE L1", "JNG" to "L1: NOP\nJNG L1", "JNE" to "L1: NOP\nJNE L1",
        "JNZ" to "L1: NOP\nJNZ L1", "JNO" to "L1: NOP\nJNO L1", "JNP" to "L1: NOP\nJNP L1",
        "JPO" to "L1: NOP\nJPO L1", "JNS" to "L1: NOP\nJNS L1", "JO" to "L1: NOP\nJO L1",
        "JP" to "L1: NOP\nJP L1", "JPE" to "L1: NOP\nJPE L1", "JS" to "L1: NOP\nJS L1",
        "JCXZ" to "L1: NOP\nJCXZ L1", "LOOP" to "L1: NOP\nLOOP L1", "LOOPE" to "L1: NOP\nLOOPE L1",
        "LOOPZ" to "L1: NOP\nLOOPZ L1", "LOOPNE" to "L1: NOP\nLOOPNE L1", "LOOPNZ" to "L1: NOP\nLOOPNZ L1",
        // flags
        "STC" to "STC", "CLC" to "CLC", "CMC" to "CMC", "STD" to "STD", "CLD" to "CLD",
        "CLI" to "CLI", "STI" to "STI",
        // processor control
        "NOP" to "NOP", "HLT" to "HLT", "WAIT" to "WAIT", "LOCK" to "LOCK NOP", "ESC" to "ESC 6, [BX]",
        // interrupts
        "INT" to "MOV AH, 2\nMOV DL, 48\nINT 21H"
    )

    @Test fun everyOriginal8086MnemonicIsAcceptedAndExecutes() {
        val failures = mutableListOf<String>()
        for ((mnemonic, program) in samples) {
            val issues = AssemblyEngine.validate(program).filter { it.message.contains("Unknown opcode") }
            if (issues.isNotEmpty()) { failures += "$mnemonic: ${issues.first().message}"; continue }
            val error = runCatching { AssemblyEngine(program).step() }.exceptionOrNull()
            val message = error?.message.orEmpty()
            if (message.contains("Unsupported instruction")) failures += "$mnemonic: $message"
        }
        assertTrue("Unsupported mnemonics: $failures", failures.isEmpty())
    }

    @Test fun coverageListContainsTheWholeReferenceSet() {
        // 81 instruction types of the original 8086/8088 (aliases collapsed by the reference list)
        val expected = listOf(
            "MOV", "PUSH", "POP", "XCHG", "IN", "OUT", "XLAT", "LEA", "LDS", "LES", "LAHF", "SAHF",
            "PUSHF", "POPF", "ADD", "ADC", "SUB", "SBB", "INC", "DEC", "NEG", "CMP", "MUL", "IMUL",
            "DIV", "IDIV", "AAA", "AAS", "AAM", "AAD", "DAA", "DAS", "CBW", "CWD", "AND", "OR", "XOR",
            "NOT", "TEST", "SHL", "SAL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR", "MOVSB", "MOVSW",
            "CMPSB", "CMPSW", "SCASB", "SCASW", "LODSB", "LODSW", "STOSB", "STOSW", "REP", "REPE",
            "REPNE", "JMP", "CALL", "RET", "RETN", "RETF", "IRET", "INTO", "JA", "JAE", "JB", "JBE",
            "JE", "JG", "JGE", "JL", "JLE", "JNE", "JNO", "JNP", "JNS", "JO", "JP", "JS", "JCXZ",
            "LOOP", "LOOPE", "LOOPNE", "STC", "CLC", "CMC", "STD", "CLD", "CLI", "STI", "NOP", "HLT",
            "WAIT", "LOCK", "ESC", "INT"
        )
        val missing = expected.filter { it !in samples.keys }
        assertTrue("Reference mnemonics without a coverage sample: $missing", missing.isEmpty())
    }
}
