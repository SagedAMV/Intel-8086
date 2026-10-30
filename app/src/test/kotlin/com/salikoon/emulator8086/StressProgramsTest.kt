package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import com.salikoon.emulator8086.engine.ExecutionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4: every instruction group driven by a long, realistic program instead of a one-liner.
 * Each program is also pushed through validate(): a valid program must produce zero issues, so a
 * false alarm from the error logic shows up here as loudly as a wrong result.
 */
class StressProgramsTest {

    private fun run(code: String, input: String = ""): ExecutionSnapshot {
        val issues = AssemblyEngine.validate(code)
        assertTrue("valid program produced issues: $issues", issues.isEmpty())
        return AssemblyEngine(code, input).run(20_000)
    }

    // ---------------------------------------------------------------- data transfer

    @Test fun blockCopyThroughEveryAddressingForm() {
        val code = """
            MOV BX, 1000H
            MOV SI, 0
            MOV AL, 11H
            MOV [BX+SI], AL
            INC SI
            MOV AL, 22H
            MOV [BX+SI], AL
            INC SI
            MOV AL, 33H
            MOV [BX+SI], AL
            MOV DI, 0
            MOV AL, [BX+DI]
            MOV [2000H], AL
            MOV AL, [BX+DI+1]
            MOV [2001H], AL
            MOV AL, [BX+DI+2]
            MOV [2002H], AL
            MOV AX, [1000H]
            XCHG AX, BX
            XCHG BX, AX
            MOV CX, 0
            LEA DX, [BX+SI+4]
            PUSH DX
            POP CX
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x11, cpu.memory[0x2000])
        assertEquals(0x22, cpu.memory[0x2001])
        assertEquals(0x33, cpu.memory[0x2002])
        assertEquals(0x1006, cpu.registers["CX"])   // LEA keeps the whole address 1000H + SI + 4
        assertEquals(0x1000, cpu.registers["BX"])   // XCHG twice restored BX
        assertEquals(0xFFFE, cpu.registers["SP"])   // PUSH/POP balanced
    }

    @Test fun tableLookupWithXlatAndSegmentOverride() {
        val code = """
            MOV BX, 300H
            MOV AL, 0
            MOV [BX], AL
            MOV AL, 41H
            MOV [BX+1], AL
            MOV AL, 42H
            MOV [BX+2], AL
            MOV AL, 43H
            MOV [BX+3], AL
            MOV AL, 2
            XLAT
            MOV DL, AL
            MOV AL, 3
            MOV BX, 300H
            XLAT
            MOV DH, AL
            MOV BX, 300H
            MOV AL, DS:[BX+1]
            MOV DS, CX
            MOV AL, ES:[BX+2]
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals('B'.code, cpu.registers["DL"])   // XLAT read the byte at 300H + 2
        assertEquals('C'.code, cpu.registers["DH"])   // and the byte at 300H + 3
    }

    // ---------------------------------------------------------------- arithmetic

    @Test fun sumOfOneToHundredWithLoop() {
        val code = """
            MOV CX, 100
            MOV AX, 0
            MOV BX, 0
        ADD_LOOP:
            ADD AX, CX
            ADC BX, 0
            LOOP ADD_LOOP
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(5050, cpu.registers["AX"])
        assertEquals(0, cpu.registers["BX"])
        assertEquals(0, cpu.registers["CX"])
    }

    @Test fun thirtyTwoBitAdditionAndSubtractionChain() {
        val code = """
            MOV DX, 1
            MOV AX, 0FFFFH
            MOV CX, 0
            MOV BX, 1
            ADD AX, BX
            ADC DX, CX
            PUSH DX
            PUSH AX
            MOV DX, 0
            MOV AX, 5
            MOV CX, 0
            MOV BX, 6
            SUB AX, BX
            SBB DX, CX
            POP SI
            POP DI
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x0000, cpu.registers["SI"])   // low word of the sum
        assertEquals(0x0002, cpu.registers["DI"])   // high word of the sum
        assertEquals(0xFFFF, cpu.registers["AX"])   // 5 - 6 = -1
        assertEquals(0xFFFF, cpu.registers["DX"])   // borrow propagated through SBB
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun packedBcdAdditionOfTwoFourDigitNumbers() {
        val code = """
            MOV AL, 34H
            ADD AL, 78H
            DAA
            MOV BL, AL
            MOV AL, 12H
            ADC AL, 56H
            DAA
            MOV BH, AL
            MOV AL, 99H
            MOV AH, 0
            ADD AL, 1
            DAA
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x12, cpu.registers["BL"])     // 34 + 78 = 112 -> 12 carry 1
        assertEquals(0x69, cpu.registers["BH"])     // 12 + 56 + 1 = 69
        assertEquals(0x00, cpu.registers["AL"])     // 99 + 1 -> 00
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun unpackedBcdCorrectionWithAaaAasAndAam() {
        val code = """
            MOV AX, 0009H
            MOV BL, 08H
            ADD AL, BL
            AAA
            MOV CL, AL
            MOV CH, AH
            MOV AX, 0
            MOV AL, 17
            MOV BL, 5
            AAM
            MOV DX, AX
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x7, cpu.registers["CL"])      // 9 + 8 = 17 -> 7 with a carry nibble
        assertEquals(0x1, cpu.registers["CH"])      // AH counted the carry
        assertEquals(0x01, cpu.registers["AH"])     // 17 / 10 -> AH = 1, AL = 7
        assertEquals(0x07, cpu.registers["AL"])
    }

    @Test fun signedDivisionAndMultiplicationOfNegatives() {
        val code = """
            MOV AL, 0F6H
            CBW
            MOV BL, 3
            IDIV BL
            MOV CL, AL
            MOV CH, AH
            MOV AL, 0FEH
            CBW
            MOV BX, 7
            IMUL BX
            MOV DI, AX
            MOV SI, DX
            MOV AL, 10
            CBW
            CWD
            MOV BX, 4
            IDIV BX
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0xFD, cpu.registers["CL"])     // -10 / 3 = -3
        assertEquals(0xFF, cpu.registers["CH"])     // remainder -1
        assertEquals(0xFFF2, cpu.registers["DI"])   // -2 * 7 = -14
        assertEquals(0xFFFF, cpu.registers["SI"])   // sign extension in DX
        assertEquals(2, cpu.registers["AX"])        // 10 / 4 = 2
        assertEquals(2, cpu.registers["DX"])        // remainder
    }

    @Test fun negateAndCompareAcrossTheWholeWordRange() {
        val code = """
            MOV AX, 1
            NEG AX
            MOV BX, AX
            NEG BX
            MOV CX, 8000H
            NEG CX
            MOV DX, 0
            CMP AX, 1
            JL SIGNED
            MOV DX, 1
        SIGNED:
            CMP AX, 1
            JB UNSIGNED
            MOV DX, DX
        UNSIGNED:
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x0001, cpu.registers["BX"])   // NEG AX made -1, NEG BX turned it back into 1
        assertEquals(0x8000, cpu.registers["CX"])   // NEG of 8000H overflows to itself
        assertEquals(0, cpu.registers["DX"])        // -1 is signed less than 1, so JL was taken
    }

    // ---------------------------------------------------------------- logic

    @Test fun bitCountingWithShiftAndTestChain() {
        val code = """
            MOV AL, 0B7H
            MOV CL, 8
            MOV DL, 0
        COUNT:
            SHR AL, 1
            ADC DL, 0
            DEC CL
            JNZ COUNT
            MOV AL, 0FH
            OR AL, 0F0H
            AND AL, 0F0H
            XOR AL, 0FFH
            NOT AL
            MOV BL, 0FH
            TEST BL, 1
            MOV BH, 0
            JZ EVEN
            MOV BH, 1
        EVEN:
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(6, cpu.registers["DL"])        // 1011 0111 has six set bits
        assertEquals(0xF0, cpu.registers["AL"])     // OR/AND/XOR/NOT chain ends on NOT 0FH
        assertEquals(1, cpu.registers["BH"])        // the low bit of 0FH is set
    }

    // ---------------------------------------------------------------- shift and rotate

    @Test fun thirtyTwoBitShiftBuiltFromShlAndRcl() {
        val code = """
            MOV DX, 5678H
            MOV AX, 1234H
            MOV CX, 4
        SHIFT:
            SHL AX, 1
            RCL DX, 1
            LOOP SHIFT
            MOV BX, 8001H
            SAR BX, 1
            MOV SI, 8001H
            SHR SI, 1
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x2340, cpu.registers["AX"])
        assertEquals(0x6781, cpu.registers["DX"])
        assertEquals(0xC000, cpu.registers["BX"])   // arithmetic shift keeps the sign
        assertEquals(0x4000, cpu.registers["SI"])   // logical shift feeds in a zero
    }

    @Test fun rotateThroughCarryDuringAWholeWordRotation() {
        val code = """
            MOV AX, 1234H
            MOV CX, 17
            CLC
        TURN:
            RCL AX, 1
            LOOP TURN
            MOV BX, 8001H
            MOV CX, 4
        ROR_LOOP:
            ROR BX, 1
            LOOP ROR_LOOP
            MOV DX, 8001H
            ROL DX, 1
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x1234, cpu.registers["AX"])   // a 17-bit rotate needs 17 turns to return
        assertEquals(0x1800, cpu.registers["BX"])   // 8001H rotated right four times
        assertEquals(0x0003, cpu.registers["DX"])   // 8001H rotated left once
    }

    // ---------------------------------------------------------------- string instructions

    private fun fillStringBlock(): String = """
        MOV SI, 2000H
        MOV AL, 41H
        MOV [SI], AL
        INC SI
        MOV AL, 42H
        MOV [SI], AL
        INC SI
        MOV AL, 43H
        MOV [SI], AL
        INC SI
        MOV AL, 44H
        MOV [SI], AL
        INC SI
        MOV AL, 45H
        MOV [SI], AL
        INC SI
        MOV AL, 24H
        MOV [SI], AL
        MOV SI, 2000H
    """.trimIndent()

    @Test fun copyCompareAndScanWithRepPrefixes() {
        val code = """
            ${fillStringBlock()}
            MOV DI, 3000H
            MOV CX, 6
            CLD
            REP MOVSB
            MOV SI, 2000H
            MOV DI, 3000H
            MOV CX, 6
            REPE CMPSB
            MOV DX, CX
            MOV DI, 3000H
            MOV AL, 43H
            MOV CX, 6
            REPNE SCASB
            MOV BX, DI
            MOV DI, 3000H
            MOV AL, 41H
            MOV CX, 6
            REP STOSB
            MOV SI, 2000H
            MOV CX, 4
            MOV DX, 0
        SUM2:
            LODSB
            ADD DL, AL
            ADC DH, 0
            LOOP SUM2
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x41, cpu.memory[0x3000])      // REP STOSB filled the block with 'A'
        assertEquals(0x41, cpu.memory[0x3005])
        assertEquals(0x3003, cpu.registers["BX"])   // REPNE SCASB stopped just past the 'C' at 3002
        assertEquals(266, cpu.registers["DX"])      // LODSB summed the ASCII codes of A, B, C and D
    }

    @Test fun stringInstructionsRespectTheDirectionFlag() {
        val code = """
            MOV SI, 2000H
            MOV AL, 11H
            MOV [SI], AL
            MOV AL, 22H
            MOV [SI+1], AL
            MOV AL, 33H
            MOV [SI+2], AL
            MOV SI, 2002H
            MOV DI, 2102H
            MOV CX, 3
            STD
            REP MOVSB
            MOV SI, 2100H
            MOV CX, 3
            CLD
            MOV DX, 0
        SUM:
            LODSB
            ADD DL, AL
            ADC DH, 0
            LOOP SUM
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x11, cpu.memory[0x2100])      // STD walked SI and DI downwards
        assertEquals(0x22, cpu.memory[0x2101])
        assertEquals(0x33, cpu.memory[0x2102])
        assertEquals(0x66, cpu.registers["DX"])     // 11H + 22H + 33H
        assertEquals(0, cpu.flags["DF"])
    }

    // ---------------------------------------------------------------- control flow

    @Test fun bubbleSortOfEightBytesWithNestedLoops() {
        val code = """
            MOV BX, 1000H
            MOV AL, 5
            MOV [BX], AL
            MOV AL, 3
            MOV [BX+1], AL
            MOV AL, 9
            MOV [BX+2], AL
            MOV AL, 1
            MOV [BX+3], AL
            MOV AL, 7
            MOV [BX+4], AL
            MOV AL, 2
            MOV [BX+5], AL
            MOV AL, 8
            MOV [BX+6], AL
            MOV AL, 4
            MOV [BX+7], AL
            MOV CX, 7
        OUTER:
            PUSH CX
            MOV SI, 0
        INNER:
            MOV AL, [BX+SI]
            MOV DL, [BX+SI+1]
            CMP AL, DL
            JBE NOSWAP
            MOV [BX+SI], DL
            MOV [BX+SI+1], AL
        NOSWAP:
            INC SI
            CMP SI, 7
            JB INNER
            POP CX
            LOOP OUTER
            HLT
        """.trimIndent()
        val cpu = run(code)
        val sorted = (0 until 8).map { cpu.memory[0x1000 + it] }
        assertEquals(listOf(1, 2, 3, 4, 5, 7, 8, 9), sorted)
        assertEquals(0xFFFE, cpu.registers["SP"])   // every PUSH was matched by a POP
    }

    @Test fun threeLevelCallChainKeepsTheStackBalanced() {
        val code = """
            MOV AX, 0
            MOV BX, 0
            CALL FIRST
            MOV DX, AX
            CALL FIRST
            MOV CX, AX
            HLT
        FIRST:
            PUSH AX
            CALL SECOND
            POP BX
            ADD AX, BX
            RET
        SECOND:
            PUSH BX
            CALL THIRD
            POP BX
            ADD AX, 3
            RET
        THIRD:
            ADD AX, 4
            RET
        """.trimIndent()
        val cpu = run(code)
        assertEquals(7, cpu.registers["DX"])        // FIRST returns 3 + 4
        assertEquals(21, cpu.registers["CX"])       // and 7 + 7 + 7 the second time
        assertEquals(0xFFFE, cpu.registers["SP"])
        assertEquals(21, cpu.registers["AX"])
    }

    @Test fun conditionalJumpsCoverSignedAndUnsignedTests() {
        val code = """
            MOV AX, 0FFFFH
            MOV BX, 1
            MOV DX, 0
            CMP AX, BX
            JB BELOW
            MOV DX, 1
        BELOW:
            CMP AX, BX
            JL LESS
            MOV DX, 2
        LESS:
            CMP AX, 0
            JE ZERO
            MOV CX, 5
            JCXZ EMPTY
            MOV DX, 3
            JMP EMPTY
        ZERO:
            MOV DX, 4
        EMPTY:
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(3, cpu.registers["DX"])        // 0FFFFH is above 1 unsigned, below it signed
    }

    @Test fun stackJugglingWithPushaStyleSequenceAndIret() {
        val code = """
            MOV AX, 1
            MOV BX, 2
            MOV CX, 3
            MOV DX, 4
            PUSH AX
            PUSH BX
            PUSH CX
            PUSH DX
            POP SI
            POP DI
            POP BP
            POP DX
            STC
            PUSHF
            CLC
            POPF
            LAHF
            SAHF
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(4, cpu.registers["SI"])        // the last push is the first pop
        assertEquals(3, cpu.registers["DI"])
        assertEquals(2, cpu.registers["BP"])
        assertEquals(1, cpu.registers["DX"])
        assertEquals(0xFFFE, cpu.registers["SP"])   // every push was matched
        assertEquals(1, cpu.flags["CF"])            // POPF restored the carry saved by PUSHF
    }

    // ---------------------------------------------------------------- flags and machine control

    @Test fun flagsSurviveSaveRestoreAndIncrementLeavesCarryAlone() {
        val code = """
            MOV AL, 0FFH
            STC
            INC AL
            MOV BL, 0
            JNC NO_CARRY
            MOV BL, 1
        NO_CARRY:
            MOV CL, 0
            JNZ NO_ZERO
            MOV CL, 1
        NO_ZERO:
            PUSHF
            CLC
            POPF
            MOV CH, 0
            JC CARRY_SET
            MOV CH, 2
        CARRY_SET:
            NOP
            WAIT
            LOCK XCHG SI, DI
            ESC 6, [BX]
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(1, cpu.registers["BL"])        // INC leaves CF alone, so JNC was not taken
        assertEquals(1, cpu.registers["CL"])        // the wrapped zero set ZF, so JNZ was not taken either
        assertEquals(0, cpu.registers["CH"])        // POPF restored the carry that CLC had cleared
        assertTrue(cpu.halted)
    }

    // ---------------------------------------------------------------- input and output

    @Test fun dosCharacterAndStringOutputWithExitCode() {
        val code = """
            ${fillStringBlock()}
            MOV AH, 9
            MOV DX, 2000H
            INT 21H
            MOV AL, 0AH
            MOV DL, AL
            MOV AH, 2
            INT 21H
            MOV AH, 1
            INT 21H
            MOV DL, AL
            MOV AH, 2
            INT 21H
            MOV AX, 4C03H
            INT 21H
        """.trimIndent()
        val cpu = run(code, input = "Z")
        assertEquals("ABCDE\nZ", cpu.output)
        assertEquals(3, cpu.registers["AL"])        // exit code from AH=4Ch
        assertTrue(cpu.halted || cpu.finished)
    }

    // ---------------------------------------------------------------- error logic under load

    @Test fun longProgramWithOneMistakeReportsOnlyThatMistake() {
        val code = """
            MOV BX, 1000H
            MOV AL, 5
            MOV [BX], AL
            MOV AL, 3
            MOV [BX+1], AL
            MOV CX, 7
        OUTER:
            PUSH CX
            MOV SI, 0
        INNER:
            MOV AL, [BX+SI]
            MOV DL, [BX+SI+1]
            CMP AL, DL
            JBE NOSWAP
            MOV [BX+SI], DL
            MOV [BX+SI+1], AL
        NOSWAP:
            INC SI
            CMP SI, CL
            JB INNER
            POP CX
            LOOP OUTER
            HLT
        """.trimIndent()
        val issues = AssemblyEngine.validate(code)
        assertEquals("one mistake must give exactly one issue: $issues", 1, issues.size)
        assertEquals(19, issues.first().line)        // CMP SI, CL mixes a word register with a byte register
        assertTrue(issues.first().message.contains("same size"))
    }

    @Test fun everyStressProgramPassesTheValidator() {
        // a guard against the new size and range checks firing on legal code
        val legal = listOf(
            "MOV AL, 0FFH\nADD AL, 1\nHLT",
            "MOV AX, 0FFFFH\nADD AX, 1\nHLT",
            "MOV AL, 255\nMOV BL, 127\nCMP AL, BL\nHLT",
            "MOV AX, -1\nADD AX, 32767\nHLT",
            "MOV BYTE PTR [100H], 255\nMOV WORD PTR [200H], 65535\nHLT",
            "MOV AL, 5\nCBW\nMUL BL\nHLT"
        )
        legal.forEach { code ->
            val issues = AssemblyEngine.validate(code)
            assertTrue("false alarm on a legal program: $code -> $issues", issues.isEmpty())
        }
    }
}
