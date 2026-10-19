package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblyEngineTest {
    @Test fun arithmeticAndByteAliasesWrapAtEightBits() {
        val cpu = AssemblyEngine("MOV AL, 255\nADD AL, 1\nHLT").run()
        assertEquals(0, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["ZF"])
    }

    @Test fun labelsAndLoopProduceFactorial() {
        val cpu = AssemblyEngine("MOV CX, 4\nMOV AX, 1\nFACTORIAL: MUL CX\nLOOP FACTORIAL\nHLT").run()
        assertEquals(24, cpu.registers["AX"])
        assertEquals(0, cpu.registers["CX"])
    }

    @Test fun memoryUsesLittleEndianWordAccess() {
        val cpu = AssemblyEngine("MOV AX, 1234H\nMOV [100H], AX\nMOV BX, [100H]\nHLT").run()
        assertEquals(0x1234, cpu.registers["BX"])
        assertEquals(0x34, cpu.memory[0x100])
        assertEquals(0x12, cpu.memory[0x101])
    }

    @Test fun conditionalJumpAndDosCharacterOutput() {
        val code = "MOV AX, 5\nCMP AX, 5\nJNE BAD\nMOV AH, 2\nMOV DL, 35H\nINT 21H\nJMP END\nBAD: MOV DL, 58H\nINT 21H\nEND: HLT"
        val cpu = AssemblyEngine(code).run()
        assertEquals("5", cpu.output)
        assertEquals(1, cpu.flags["ZF"])
    }

    @Test fun validationRejectsUnknownLabels() {
        assertTrue(AssemblyEngine.validate("JMP MISSING").isNotEmpty())
        assertTrue(AssemblyEngine.validate("MOV AX, 7\nHLT").isEmpty())
    }

    @Test fun validationCollectsAllErrorsWithLineNumbersAndReasons() {
        val issues = AssemblyEngine.validate("MOV AX\nMVO BX, 3\nADD QZ, 1\nJMP MISSING\nMOV ZX, 99Z")
        assertEquals(6, issues.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 5), issues.map { it.line })
        assertTrue(issues[0].message.contains("requires exactly 2 operand(s)"))
        assertTrue(issues[1].message.contains("Unknown opcode 'MVO'"))
        assertTrue(issues[2].message.contains("'QZ'"))
        assertTrue(issues[3].message.contains("Label 'MISSING' is not defined"))
        assertTrue(issues[4].message.contains("'ZX'"))
        assertTrue(issues[5].message.contains("'99Z'"))
    }
}
