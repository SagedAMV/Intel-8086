package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end programs that combine many instructions and check the final machine state. */
class ProgramsTest {

    private fun run(code: String, input: String = ""): com.salikoon.emulator8086.engine.ExecutionSnapshot =
        AssemblyEngine(code, input).run()

    @Test fun recursiveFactorialUsesTheRealStack() {
        val code = """
            MOV CX, 5
            CALL FACT
            HLT
            FACT: CMP CX, 1
                  JLE BASE
                  PUSH CX
                  DEC CX
                  CALL FACT
                  POP CX
                  MUL CX
                  RET
            BASE: MOV AX, 1
                  RET
        """.trimIndent()
        val cpu = run(code)
        assertEquals(120, cpu.registers["AX"])
        assertEquals(0xFFFE, cpu.registers["SP"])
    }

    @Test fun thirtyTwoBitAdditionUsesAdcForTheUpperWord() {
        val code = "MOV DX, 1\nMOV AX, 0FFFFH\nADD AX, 1\nADC DX, 0\nHLT"
        val cpu = run(code)
        assertEquals(0x0000, cpu.registers["AX"])
        assertEquals(0x0002, cpu.registers["DX"])
        assertEquals(0, cpu.flags["CF"])   // the ADC that finished the 32-bit add produced no carry
    }

    @Test fun packedBcdAdditionOfTwoFourDigitNumbers() {
        // 1234 + 5678 = 6912 in packed BCD
        val code = "MOV AX, 1234H\nMOV BX, 5678H\nADD AL, BL\nDAA\nADC AH, BH\nDAA\nHLT"
        val cpu = run(code)
        assertEquals(0x6912, cpu.registers["AX"])
    }

    @Test fun unpackedBitwiseNotProducesOnesComplement() {
        assertEquals(0xF0, run("MOV AL, 0FH\nNOT AL\nHLT").registers["AL"])
        assertEquals(0xF1, run("MOV AL, 0FH\nNOT AL\nINC AL\nHLT").registers["AL"])
    }

    @Test fun reversingAnArrayInPlaceWithPointerArithmetic() {
        val code = """
            MOV BYTE PTR [200H], 41H
            MOV BYTE PTR [201H], 42H
            MOV BYTE PTR [202H], 43H
            MOV BYTE PTR [203H], 44H
            MOV SI, 200H
            MOV DI, 203H
            MOV CX, 2
            REV: MOV AL, [SI]
                 MOV BL, [DI]
                 MOV [SI], BL
                 MOV [DI], AL
                 INC SI
                 DEC DI
                 LOOP REV
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals('D'.code, cpu.memory[0x200])
        assertEquals('C'.code, cpu.memory[0x201])
        assertEquals('B'.code, cpu.memory[0x202])
        assertEquals('A'.code, cpu.memory[0x203])
    }

    @Test fun summingAByteArrayWithAddAndAdc() {
        val code = """
            MOV BYTE PTR [200H], 10
            MOV BYTE PTR [201H], 20
            MOV BYTE PTR [202H], 30
            MOV BYTE PTR [203H], 40
            MOV BYTE PTR [204H], 50
            MOV SI, 200H
            MOV CX, 5
            MOV AX, 0
            TOTAL: ADD AL, [SI]
                   ADC AH, 0
                   INC SI
                   LOOP TOTAL
            HLT
        """.trimIndent()
        assertEquals(150, run(code).registers["AX"])
    }

    @Test fun blockCopyWithRepMovsbThenCompareWithRepeCmpsb() {
        val code = """
            MOV BYTE PTR [200H], 11H
            MOV BYTE PTR [201H], 22H
            MOV BYTE PTR [202H], 33H
            MOV SI, 200H
            MOV DI, 300H
            MOV CX, 3
            REP MOVSB
            MOV SI, 200H
            MOV DI, 300H
            MOV CX, 3
            REPE CMPSB
            HLT
        """.trimIndent()
        val cpu = run(code)
        assertEquals(0x11, cpu.memory[0x300])
        assertEquals(0x22, cpu.memory[0x301])
        assertEquals(0x33, cpu.memory[0x302])
        assertEquals(1, cpu.flags["ZF"])
        assertEquals(0, cpu.registers["CX"])
    }

    @Test fun printAStringWithInt21hAh9AfterFillingMemory() {
        val code = """
            MOV BYTE PTR [200H], 48H
            MOV BYTE PTR [201H], 65H
            MOV BYTE PTR [202H], 6CH
            MOV BYTE PTR [203H], 6CH
            MOV BYTE PTR [204H], 6FH
            MOV BYTE PTR [205H], 24H
            MOV DX, 200H
            MOV AH, 9
            INT 21H
            MOV AH, 4CH
            INT 21H
        """.trimIndent()
        val cpu = run(code)
        assertEquals("Hello", cpu.output)
        assertEquals(true, cpu.halted)
    }

    @Test fun echoInputCharactersWithAH1AndAH2() {
        val code = "MOV AH, 1\nINT 21H\nMOV DL, AL\nMOV AH, 2\nINT 21H\nHLT"
        assertEquals("Z", run(code, input = "Z").output)
    }

    @Test fun shiftLeftMultipliesAndShiftRightDividesByTwo() {
        val cpu = run("MOV AX, 7\nSHL AX, 3\nMOV BX, AX\nSHR BX, 2\nHLT")
        assertEquals(56, cpu.registers["AX"])
        assertEquals(14, cpu.registers["BX"])
    }

    @Test fun loopWithStringStoreFillsMemoryWithTheSameByte() {
        val code = "MOV DI, 400H\nMOV AL, 0AAH\nMOV CX, 4\nCLD\nFILL: STOSB\nLOOP FILL\nHLT"
        val cpu = run(code)
        assertEquals(0xAA, cpu.memory[0x400])
        assertEquals(0xAA, cpu.memory[0x401])
        assertEquals(0xAA, cpu.memory[0x402])
        assertEquals(0xAA, cpu.memory[0x403])
        assertEquals(0x404, cpu.registers["DI"])
    }
}
