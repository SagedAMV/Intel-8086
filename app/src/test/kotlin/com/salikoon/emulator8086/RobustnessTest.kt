package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Second review pass: every mistake the emulator can meet must surface as one of its own explanatory
 * messages (never as a raw JVM exception), and the machine-state details have to match the 8086.
 */
class RobustnessTest {

    private fun failureOf(code: String): String {
        val error = runCatching { AssemblyEngine(code).run() }.exceptionOrNull() ?: return ""
        return error.message.orEmpty()
    }

    private fun issuesOf(code: String) = AssemblyEngine.validate(code).map { it.message }

    // ------------------------------------------------------------- no raw JVM exceptions

    @Test fun invalidOperandsReportTheOffendingToken() {
        assertTrue(failureOf("MOV AX, B").startsWith("Invalid register 'B'"))
        assertEquals("Invalid number 'FOO'", failureOf("MOV AX, [BX+FOO]"))
        assertTrue(failureOf("MOV AX, MYLABEL\nMYLABEL: NOP").startsWith("Label 'MYLABEL' cannot be used as a value"))
        assertTrue(failureOf("MOV AX, H").startsWith("Invalid register 'H'"))
        // the validator says the same thing and adds a suggestion
        assertTrue(issuesOf("MOV AXX, 1").any { it.contains("'AXX'") && it.contains("did you mean AX", ignoreCase = true) })
    }

    @Test fun wrongOperandKindsAreRejectedWithClearMessages() {
        assertTrue(failureOf("AAM CL").contains("base must be an immediate value"))
        assertTrue(failureOf("SHL AX, BX").contains("must be 1, CL, or an immediate value"))
        assertTrue(failureOf("INT AX").contains("immediate interrupt vector"))
        assertTrue(issuesOf("AAM CL").any { it.contains("immediate base only") })
        assertTrue(issuesOf("INT AX").any { it.contains("immediate interrupt vector") })
    }

    @Test fun strayCommasAreRejectedOnBothSides() {
        assertTrue(failureOf("MOV AX,, 1").contains("Empty operand"))
        assertTrue(issuesOf("MOV AX,, 1").any { it.contains("Empty operand") })
    }

    @Test fun memoryToMemoryIsRejectedEvenBehindPtrPrefixes() {
        assertTrue(failureOf("MOV [BX], BYTE PTR [SI]").contains("cannot use memory for both operands"))
        assertTrue(issuesOf("MOV [BX], BYTE PTR [SI]").any { it.contains("cannot use memory for both operands") })
        assertTrue(issuesOf("ADD WORD PTR [200H], [SI]").any { it.contains("cannot use memory for both operands") })
    }

    @Test fun csCannotBeADestination() {
        assertTrue(failureOf("MOV CS, AX").contains("CS cannot be written"))
        assertTrue(issuesOf("MOV CS, AX").any { it.contains("CS cannot be used as a destination") })
        assertTrue(issuesOf("XCHG AX, CS").any { it.contains("cannot exchange CS") })
        assertTrue(issuesOf("POP CS").any { it.contains("POP CS is not allowed") })
        // reading CS stays legal
        assertTrue(issuesOf("MOV AX, CS").isEmpty())
        assertTrue(issuesOf("PUSH CS").isEmpty())
    }

    @Test fun escOpcodeMustFitSixBits() {
        assertTrue(issuesOf("ESC 64, [BX]").any { it.contains("between 0 and 63") })
        assertTrue(issuesOf("ESC 6, [BX]").isEmpty())
    }

    @Test fun outOfRangeImmediatesAreRejectedNotTruncated() {
        // round 4: a value that does not fit the destination is an error, on both sides, not a silent truncation
        assertTrue(failureOf("ADD AL, 300").contains("cannot store 300 in a 8-bit operand"))
        assertTrue(issuesOf("ADD AL, 300").any { it.contains("cannot store 300 in a 8-bit operand") })
        // signed values inside the range are still stored as their two's complement
        val cpu = AssemblyEngine("MOV AL, -1\nADD AL, 1\nHLT").run()
        assertEquals(0, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun ipIsNotWritableButCallRetMoveIt() {
        assertTrue(failureOf("MOV IP, 5").contains("cannot be written directly"))
        assertTrue(issuesOf("MOV IP, 5").any { it.contains("cannot be used as a destination") })
        val cpu = AssemblyEngine("CALL SUB\nHLT\nSUB: NOP\nRET").run()
        assertEquals(2, cpu.registers["IP"])
    }

    @Test fun cmpAndTestRejectAnImmediateDestination() {
        assertTrue(issuesOf("CMP 5, AX").any { it.contains("destination of CMP cannot be an immediate value") })
        assertTrue(issuesOf("TEST 5, AX").any { it.contains("destination of TEST cannot be an immediate value") })
    }

    @Test fun pushAndPopRejectByteSizeOperands() {
        assertTrue(issuesOf("POP BYTE PTR [200H]").any { it.contains("needs a 16-bit operand") })
        assertTrue(issuesOf("PUSH BYTE PTR [200H]").any { it.contains("needs a 16-bit operand") })
        assertTrue(issuesOf("PUSH WORD PTR [200H]").isEmpty())
    }

    // ------------------------------------------------------------------- lexical details

    @Test fun characterLiteralsMayContainAComma() {
        assertEquals(','.code, AssemblyEngine("MOV AL, ','\nHLT").run().registers["AL"])
        assertEquals("AB", AssemblyEngine(
            "MOV AL, 'A'\nMOV AH, 2\nMOV DL, AL\nINT 21H\nMOV DL, 'B'\nINT 21H\nHLT"
        ).run().output)
    }

    @Test fun decimalSuffixDIsAccepted() {
        assertEquals(1234, AssemblyEngine("MOV AX, 1234D\nHLT").run().registers["AX"])
    }

    // ------------------------------------------------------------- machine-state fidelity

    @Test fun ipRegisterFollowsTheSourceLineIndex() {
        val cpu = AssemblyEngine("MOV AX, 1\nNOP\nNOP\nHLT").run()
        assertEquals(4, cpu.registers["IP"])
        val stepping = AssemblyEngine("MOV AX, 1\nNOP\nHLT")
        assertEquals(0, stepping.snapshot().registers["IP"])
        stepping.step()
        assertEquals(1, stepping.snapshot().registers["IP"])
    }

    @Test fun pushSpStoresTheDecrementedSpLikeThe8086() {
        // the 8086 stores SP-2 (the 80286 and later store the original SP)
        val cpu = AssemblyEngine("PUSH SP\nPOP AX\nHLT").run()
        assertEquals(0xFFFC, cpu.registers["AX"])
        assertEquals(0xFFFE, cpu.registers["SP"])
    }

    @Test fun pushfSetsTheReservedHighBits() {
        assertEquals(0xF003, AssemblyEngine("STC\nPUSHF\nPOP AX\nHLT").run().registers["AX"])
        assertEquals(0xF002, AssemblyEngine("CLC\nPUSHF\nPOP AX\nHLT").run().registers["AX"])
        // POPF still honours bit 0 of the popped word (and ignores the reserved bits)
        assertEquals(0, AssemblyEngine("MOV AX, 0FFFEH\nPUSH AX\nPOPF\nHLT").run().flags["CF"])
        assertEquals(1, AssemblyEngine("MOV AX, 0FFFFH\nPUSH AX\nPOPF\nHLT").run().flags["CF"])
    }

    @Test fun dasFollowsTheIntelAlgorithmForTheCarry() {
        // AL=03H with AF set but no carry: Intel only adjusts the low digit
        val cpu = AssemblyEngine("MOV AL, 03H\nADD AL, 0FH\nMOV AL, 03H\nDAS\nHLT").run()
        assertEquals(0xFD, cpu.registers["AL"])
        assertEquals(0, cpu.flags["CF"])
        assertEquals(1, cpu.flags["AF"])
        // and a real borrow still produces the packed BCD result with CF
        val borrow = AssemblyEngine("MOV AL, 00H\nSUB AL, 01H\nDAS\nHLT").run()
        assertEquals(0x99, borrow.registers["AL"])
        assertEquals(1, borrow.flags["CF"])
    }

    @Test fun daaCarryFollowsTheOriginalAccumulator() {
        val cpu = AssemblyEngine("MOV AL, 99H\nADD AL, 01H\nDAA\nHLT").run()
        assertEquals(0x00, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun rcrOverflowUsesTheTwoMostSignificantBits() {
        // Intel's description defines OF for right rotates as MSB(result) XOR MSB-1(result);
        // the RCR pseudo-code in the same manual uses MSB XOR CF instead, so this test pins the
        // documented behaviour we follow (the value is only defined for a 1-bit rotate anyway).
        val cpu = AssemblyEngine("MOV AL, 03H\nSTC\nRCR AL, 1\nHLT").run()
        assertEquals(0x81, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["OF"])
    }
}
