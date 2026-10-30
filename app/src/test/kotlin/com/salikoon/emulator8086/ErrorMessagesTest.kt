package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4: the error logic itself. Every entry here is a mistake a beginner really makes.
 * The rules verified are:
 *   1. validate() names the offending token and says how to fix it,
 *   2. the runtime refuses the same program with the same words (never a raw JVM exception),
 *   3. nothing legal is flagged, because the editor blocks Run whenever validate() complains.
 */
class ErrorMessagesTest {

    private fun issues(code: String) = AssemblyEngine.validate(code).map { it.message }

    private fun runtimeError(code: String): String =
        runCatching { AssemblyEngine(code).run(2000) }.exceptionOrNull()?.message.orEmpty()

    private fun assertRejected(code: String, expected: String) {
        assertTrue("validate() stayed silent on: $code", issues(code).any { it.contains(expected) })
        assertTrue(
            "runtime message for '$code' was '${runtimeError(code)}'",
            runtimeError(code).contains(expected)
        )
    }

    // ------------------------------------------------------------ operand size

    @Test fun bothOperandsMustHaveTheSameWidth() {
        listOf(
            "MOV AX, BL", "MOV AL, BX", "ADD AX, CL", "XCHG AX, BL", "CMP DX, AL",
            "ADC AL, BX", "SUB BX, CH", "AND AX, DL", "OR AL, SI", "XOR DX, BH",
            "TEST AX, CL", "MOV AL, DS"
        ).forEach { code ->
            val message = issues(code).firstOrNull { it.contains("same size") }
            assertTrue("no size complaint for '$code': ${issues(code)}", message != null)
            assertTrue("no runtime complaint for '$code'", runtimeError(code).contains("same size"))
        }
    }

    @Test fun explicitPointerPrefixesAlsoCarryAWidth() {
        assertRejected("MOV AX, BYTE PTR [100H]", "same size")
        assertRejected("MOV AL, WORD PTR [100H]", "same size")
        assertTrue(issues("MOV AL, BYTE PTR [100H]").isEmpty())
        assertTrue(issues("MOV AX, WORD PTR [100H]").isEmpty())
    }

    // ------------------------------------------------------------ immediate range

    @Test fun constantsMustFitTheDestination() {
        assertRejected("MOV AL, 300", "cannot store 300")
        assertRejected("MOV AL, 1FFH", "cannot store 511")
        assertRejected("MOV AX, 70000", "cannot store 70000")
        assertRejected("MOV BYTE PTR [200H], 300", "cannot store 300")
        assertRejected("ADD AL, -200", "cannot store -200")
        // the boundary values themselves are legal
        assertTrue(issues("MOV AL, 255").isEmpty())
        assertTrue(issues("MOV AL, -128").isEmpty())
        assertTrue(issues("MOV AX, 65535").isEmpty())
        assertTrue(issues("MOV BYTE PTR [200H], 255").isEmpty())
    }

    @Test fun characterConstantsHoldExactlyOneCharacter() {
        assertTrue(issues("MOV AL, 'AB'").any { it.contains("exactly one character") })
        assertTrue(issues("MOV AL, 'A").any { it.contains("Missing closing quote") })
        assertEquals('A'.code, AssemblyEngine("MOV AL, 'A'\nHLT").run().registers["AL"])
    }

    // ------------------------------------------------------------ hex notation

    @Test fun hexConstantsMustStartWithADigit() {
        assertTrue(issues("MOV AX, E53BH").any { it.contains("write 0E53BH") })
        assertTrue(issues("MOV AX, FDH").any { it.contains("write 0FDH") })
        assertEquals(0xE53B, AssemblyEngine("MOV AX, 0E53BH\nHLT").run().registers["AX"])
        // AH and friends stay registers even though they look like hex constants
        assertEquals(0x34, AssemblyEngine("MOV AH, 34H\nHLT").run().registers["AH"])
        assertTrue(issues("MOV AH, 34H").isEmpty())
    }

    // ------------------------------------------------------------ labels

    @Test fun labelsCannotBeUsedAsValues() {
        val code = "MOV AX, COUNT\nCOUNT: NOP"
        assertTrue(issues(code).any { it.contains("Label 'COUNT' cannot be used as a value") })
        assertTrue(runtimeError(code).contains("Label 'COUNT' cannot be used as a value"))
    }

    @Test fun labelsCannotShadowRegisters() {
        assertRejected("JMP CS\nCS: NOP", "same name as a register")
        assertRejected("MOV AX, AX\nJMP BX\nBX: NOP", "same name as a register")
    }

    @Test fun aSpaceBeforeTheColonIsExplained() {
        assertRejected("START : NOP\nJMP START", "no space before the colon")
        assertTrue(issues("START: NOP\nJMP START").isEmpty())
    }

    @Test fun aLabelWithoutAnInstructionAtTheEndIsReported() {
        assertTrue(issues("L1: NOP\nHALT_LOOP:").any { it.contains("not followed by any instruction") })
        // a label on its own line in the middle of a program is perfectly normal
        assertTrue(issues("L1:\nNOP\nHLT").isEmpty())
    }

    @Test fun misspelledLabelsGetASuggestion() {
        assertTrue(issues("JMP LABLE\nLABEL: NOP").any { it.contains("Did you mean 'LABEL'") })
        assertTrue(issues("JMP AX\nAX: NOP").any { it.contains("same name as a register") })
    }

    // ------------------------------------------------------------ mnemonics

    @Test fun typosSuggestTheRealInstruction() {
        assertTrue(issues("MVO AX, 1").any { it.contains("Did you mean MOV") })
        assertTrue(issues("JNEQ L1\nL1: NOP").any { it.contains("Did you mean JNE") })
        assertTrue(issues("ADDD AX, 1").any { it.contains("Did you mean ADD") })
    }

    @Test fun assemblerDirectivesAreExplained() {
        listOf("DB 5", "DW 100H", "ORG 100H", "EQU 5", "SEGMENT").forEach { code ->
            assertTrue("no directive hint for '$code': ${issues(code)}",
                issues(code).any { it.contains("Assembler directives are not supported") })
        }
    }

    // ------------------------------------------------------------ operand shapes

    @Test fun segmentRegistersOnlyTravelWithMovPushAndPop() {
        assertRejected("ADD AX, DS", "segment register DS")
        assertRejected("CMP ES, AX", "segment register ES")
        assertTrue(issues("MOV AX, DS").isEmpty())
        assertTrue(issues("PUSH DS").isEmpty())
    }

    @Test fun theOperandCountOfEveryTwoOperandInstructionIsRight() {
        // LEA/LDS/LES used to be listed with a single operand, which made validate() reject them
        assertTrue(issues("LEA DX, [BX+SI+4]").isEmpty())
        assertTrue(issues("LDS SI, [1000H]").isEmpty())
        assertTrue(issues("LES DI, [1000H]").isEmpty())
        listOf("LEA DX, [BX]", "LDS SI, [BX]", "LES DI, [BX]").forEach { code ->
            assertTrue("valid program flagged: $code -> ${issues(code)}", issues(code).isEmpty())
        }
    }

    @Test fun onlyInt21hIsSupported() {
        assertRejected("MOV AH, 2\nINT 10H", "Only INT 21h is supported")
        assertTrue(issues("MOV AH, 2\nINT 21H").isEmpty())
    }

    @Test fun retWithoutAnyCallIsPointedOut() {
        assertTrue(issues("RET").any { it.contains("the program contains no CALL") })
        assertTrue(runtimeError("RET").contains("every RET has its CALL"))
        assertTrue(issues("CALL SUB\nHLT\nSUB: RET").isEmpty())
        // pushing a return address by hand is a legitimate trick, so it is not flagged
        assertTrue(issues("PUSH 3\nRET").isEmpty())
    }

    // ------------------------------------------------------------ agreement

    @Test fun validateAndTheRuntimeSpeakWithOneVoice() {
        val bad = listOf(
            "MOV AX, BL", "ADD AL, 300", "MOV QX, 1", "MOV 5, AX", "MOV AX, [BX", "MOV AL, 'AB'",
            "MOV AX, COUNT\nCOUNT: NOP", "MVO AX, 1", "INT 10H", "PUSH AL", "POP CS", "SHL AX, BX",
            "REP MOV AX, 1", "JMP LABLE\nLABEL: NOP", "START : NOP\nJMP START", "MOV [100H], [200H]"
        )
        bad.forEach { code ->
            val messages = issues(code)
            assertTrue("validate() said nothing about: $code", messages.isNotEmpty())
            val runtime = runtimeError(code)
            assertTrue("the runtime accepted: $code", runtime.isNotEmpty())
            val agreed = messages.any { message ->
                val a = message.substringAfter(": ").trimEnd('.')
                val b = runtime.trimEnd('.')
                a.contains(b) || b.contains(a) || a == b
            }
            assertTrue("validate() and the runtime disagree on '$code'\n  validate: $messages\n  runtime: $runtime", agreed)
            assertTrue("raw exception text for '$code': $runtime", runtime.isNotBlank() && !runtime.contains("null"))
        }
    }
}
