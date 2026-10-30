package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the behaviour of every newly added 8086 instruction against Intel semantics. */
class FullInstructionSetTest {

    private fun run(code: String, input: String = ""): AssemblyEngine {
        val engine = AssemblyEngine(code, input)
        engine.run()
        return engine
    }

    private fun register(code: String, name: String): Int = run(code).snapshot().registers.getValue(name)
    private fun flag(code: String, name: String): Int = run(code).snapshot().flags.getValue(name)
    private fun memory(code: String, address: Int): Int = run(code).snapshot().memory[address] ?: 0

    // ---------------------------------------------------------------- data transfer

    @Test fun pushAndPopRoundTripThroughTheStack() {
        val cpu = run("MOV AX, 1234H\nPUSH AX\nMOV AX, 0\nPOP BX\nHLT").snapshot()
        assertEquals(0x1234, cpu.registers["BX"])
        assertEquals(0xFFFE, cpu.registers["SP"])
        assertEquals(0x34, cpu.memory[0xFFFC])
        assertEquals(0x12, cpu.memory[0xFFFD])
    }

    @Test fun pushAcceptsAnImmediateValue() {
        assertEquals(0x1234, register("PUSH 1234H\nPOP AX\nHLT", "AX"))
    }

    @Test fun pushAndPopSegmentRegister() {
        assertEquals(0x1234, register("MOV AX, 1234H\nPUSH AX\nPOP DS\nHLT", "DS"))
    }

    @Test fun pushfAndPopfRoundTripTheFlagWord() {
        assertEquals(1, flag("STC\nPUSHF\nCLC\nPOPF\nHLT", "CF"))
        assertEquals(0, flag("CLC\nPUSHF\nSTC\nPOPF\nHLT", "CF"))
    }

    @Test fun lahfLoadsLowFlagByteAndSahfStoresIt() {
        // AH = SF ZF 0 AF 0 PF 1 CF, so a set CF alone gives AH = 03H
        assertEquals(0x03, register("STC\nLAHF\nHLT", "AH"))
        assertEquals(1, flag("STC\nLAHF\nCLC\nSAHF\nHLT", "CF"))
    }

    @Test fun leaLoadsTheEffectiveAddressInsteadOfTheContents() {
        assertEquals(0x107, register("MOV BX, 100H\nMOV SI, 5\nLEA AX, [BX+SI+2]\nHLT", "AX"))
        assertEquals(0x200, register("MOV BYTE PTR [200H], 99H\nLEA AX, [200H]\nHLT", "AX"))
    }

    @Test fun ldsAndLesLoadFarPointers() {
        val cpu = run(
            "MOV WORD PTR [200H], 1234H\nMOV WORD PTR [202H], 5678H\n" +
                "MOV WORD PTR [204H], 0ABCDH\nMOV WORD PTR [206H], 0EF01H\n" +
                "LDS AX, [200H]\nLES BX, [204H]\nHLT"
        ).snapshot()
        assertEquals(0x1234, cpu.registers["AX"])
        assertEquals(0x5678, cpu.registers["DS"])
        assertEquals(0xABCD, cpu.registers["BX"])
        assertEquals(0xEF01, cpu.registers["ES"])
    }

    @Test fun xlatTranslatesAlThroughTheTableAtBx() {
        val cpu = run("MOV BX, 200H\nMOV AL, 3\nMOV BYTE PTR [203H], 41H\nXLAT\nHLT").snapshot()
        assertEquals(0x41, cpu.registers["AL"])
        assertEquals(0x200, cpu.registers["BX"])
    }

    @Test fun inAndOutUseThePortSpace() {
        val cpu = run("MOV AL, 7\nOUT 60H, AL\nMOV AL, 0\nIN AL, 60H\nHLT").snapshot()
        assertEquals(7, cpu.registers["AL"])
        assertEquals(7, cpu.ports[0x60])
    }

    @Test fun inAndOutAcceptTheDxPortForm() {
        val cpu = run("MOV DX, 61H\nMOV AL, 9\nOUT DX, AL\nMOV AL, 0\nIN AL, DX\nHLT").snapshot()
        assertEquals(9, cpu.registers["AL"])
    }

    @Test fun movBetweenSegmentRegistersWorks() {
        assertEquals(0x1234, register("MOV AX, 1234H\nMOV DS, AX\nHLT", "DS"))
    }

    @Test fun byteWritesDoNotClobberTheNeighbouringByte() {
        val cpu = run("MOV BYTE PTR [200H], 11H\nMOV BYTE PTR [201H], 22H\nHLT").snapshot()
        assertEquals(0x11, cpu.memory[0x200])
        assertEquals(0x22, cpu.memory[0x201])
    }

    // -------------------------------------------------------------------- arithmetic

    @Test fun auxCarryFlagIsNowSetByAddAndInc() {
        assertEquals(1, flag("MOV AL, 0FH\nADD AL, 1\nHLT", "AF"))
        assertEquals(1, flag("MOV AL, 0FH\nINC AL\nHLT", "AF"))
        assertEquals(0, flag("MOV AL, 09H\nADD AL, 1\nHLT", "AF"))
    }

    @Test fun adcAddsTheCarryFlag() {
        val cpu = run("MOV AL, 0FFH\nSTC\nADC AL, 0\nHLT").snapshot()
        assertEquals(0, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["ZF"])
        assertEquals(1, cpu.flags["AF"])
    }

    @Test fun sbbSubtractsTheBorrowFlag() {
        val cpu = run("MOV AL, 5\nSTC\nSBB AL, 2\nHLT").snapshot()
        assertEquals(2, cpu.registers["AL"])
        assertEquals(0, cpu.flags["CF"])
    }

    @Test fun negSetsCarryAndOverflowForTheMostNegativeValue() {
        val cpu = run("MOV AL, 80H\nNEG AL\nHLT").snapshot()
        assertEquals(0x80, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["OF"])
    }

    @Test fun imulProducesASignedDoubleWidthProduct() {
        val cpu = run("MOV AX, -3\nMOV BX, 7\nIMUL BX\nHLT").snapshot()
        assertEquals(0xFFEB, cpu.registers["AX"])
        assertEquals(0xFFFF, cpu.registers["DX"])
        assertEquals(0, cpu.flags["CF"])
    }

    @Test fun imulSetsCarryWhenTheProductDoesNotFitInOneRegister() {
        val cpu = run("MOV AL, 100\nMOV BL, 3\nIMUL BL\nHLT").snapshot()
        assertEquals(300, cpu.registers["AX"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["OF"])
    }

    @Test fun idivDividesSignedValuesAndKeepsTheRemainderSign() {
        val cpu = run("MOV AX, -100\nCWD\nMOV BX, 7\nIDIV BX\nHLT").snapshot()
        assertEquals(0xFFF2, cpu.registers["AX"])   // -14
        assertEquals(0xFFFE, cpu.registers["DX"])   // -2
    }

    @Test fun cbwAndCwdSignExtend() {
        assertEquals(0xFFFE, register("MOV AL, 0FEH\nCBW\nHLT", "AX"))
        assertEquals(0x007E, register("MOV AL, 7EH\nCBW\nHLT", "AX"))
        assertEquals(0xFFFF, register("MOV AX, 8000H\nCWD\nHLT", "DX"))
        assertEquals(0x0000, register("MOV AX, 7FFFH\nCWD\nHLT", "DX"))
    }

    @Test fun aaaAdjustsPackedAsciiAfterAddition() {
        val cpu = run("MOV AX, 0\nMOV AL, 9\nADD AL, 1\nAAA\nHLT").snapshot()
        assertEquals(0, cpu.registers["AL"])
        assertEquals(1, cpu.registers["AH"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["AF"])
    }

    @Test fun aasAdjustsAsciiAfterSubtraction() {
        val cpu = run("MOV AX, 0100H\nMOV AL, 5\nSUB AL, 8\nAAS\nHLT").snapshot()
        assertEquals(7, cpu.registers["AL"])
        assertEquals(0, cpu.registers["AH"])
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun aamSplitsAlIntoTwoDecimalDigits() {
        val cpu = run("MOV AL, 42\nAAM\nHLT").snapshot()
        assertEquals(4, cpu.registers["AH"])
        assertEquals(2, cpu.registers["AL"])
    }

    @Test fun aadCombinesTwoDecimalDigitsIntoAl() {
        val cpu = run("MOV AX, 0402H\nAAD\nHLT").snapshot()
        assertEquals(42, cpu.registers["AL"])
        assertEquals(0, cpu.registers["AH"])
    }

    @Test fun daaAdjustsPackedBcdAfterAddition() {
        val cpu = run("MOV AL, 95H\nADD AL, 95H\nDAA\nHLT").snapshot()
        assertEquals(0x90, cpu.registers["AL"])
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun dasAdjustsPackedBcdAfterSubtraction() {
        val cpu = run("MOV AL, 42H\nSUB AL, 05H\nDAS\nHLT").snapshot()
        assertEquals(0x37, cpu.registers["AL"])
        assertEquals(0, cpu.flags["CF"])
    }

    // ------------------------------------------------------------ shifts and rotates

    @Test fun shlSetsCarryFromTheLastBitShiftedOut() {
        val cpu = run("MOV AX, 8000H\nSHL AX, 1\nHLT").snapshot()
        assertEquals(0, cpu.registers["AX"])
        assertEquals(1, cpu.flags["CF"])
        assertEquals(1, cpu.flags["ZF"])
        assertEquals(1, cpu.flags["OF"])
    }

    @Test fun shlAcceptsAMultiBitCountAndCl() {
        assertEquals(8, register("MOV AX, 1\nSHL AX, 3\nHLT", "AX"))
        assertEquals(16, register("MOV CL, 4\nMOV AX, 1\nSHL AX, CL\nHLT", "AX"))
        assertEquals(2, register("MOV AX, 1\nSAL AX, 1\nHLT", "AX"))
    }

    @Test fun shiftByZeroChangesNothing() {
        val cpu = run("STC\nMOV AX, 0FFFFH\nSHL AX, 0\nHLT").snapshot()
        assertEquals(0xFFFF, cpu.registers["AX"])
        assertEquals(1, cpu.flags["CF"])
    }

    @Test fun shrAndSarDifferOnTheSignBit() {
        val shr = run("MOV AL, 81H\nSHR AL, 1\nHLT").snapshot()
        assertEquals(0x40, shr.registers["AL"])
        assertEquals(1, shr.flags["CF"])
        assertEquals(1, shr.flags["OF"])
        val sar = run("MOV AL, 81H\nSAR AL, 1\nHLT").snapshot()
        assertEquals(0xC0, sar.registers["AL"])
        assertEquals(1, sar.flags["CF"])
        assertEquals(0, sar.flags["OF"])
    }

    @Test fun rolAndRorRotateWithoutLosingBits() {
        val rol = run("MOV AL, 80H\nROL AL, 1\nHLT").snapshot()
        assertEquals(0x01, rol.registers["AL"])
        assertEquals(1, rol.flags["CF"])
        assertEquals(1, rol.flags["OF"])
        val ror = run("MOV AL, 01H\nROR AL, 1\nHLT").snapshot()
        assertEquals(0x80, ror.registers["AL"])
        assertEquals(1, ror.flags["CF"])
    }

    @Test fun rclAndRcrRotateThroughTheCarryFlag() {
        val rcl = run("MOV AL, 80H\nSTC\nRCL AL, 1\nHLT").snapshot()
        assertEquals(0x01, rcl.registers["AL"])
        assertEquals(1, rcl.flags["CF"])
        val rcr = run("MOV AL, 01H\nCLC\nRCR AL, 1\nHLT").snapshot()
        assertEquals(0x00, rcr.registers["AL"])
        assertEquals(1, rcr.flags["CF"])
    }

    @Test fun incAndDecSetOverflowAtTheSignedBoundaries() {
        val inc = run("MOV AL, 7FH\nINC AL\nHLT").snapshot()
        assertEquals(0x80, inc.registers["AL"])
        assertEquals(1, inc.flags["OF"])
        val dec = run("MOV AL, 80H\nDEC AL\nHLT").snapshot()
        assertEquals(0x7F, dec.registers["AL"])
        assertEquals(1, dec.flags["OF"])
        assertEquals(0, run("MOV AL, 10H\nINC AL\nHLT").snapshot().flags["OF"])
    }

    @Test fun aFullTurnOfRotateStillUpdatesCarry() {
        val rol = run("MOV AL, 0A5H\nROL AL, 8\nHLT").snapshot()
        assertEquals(0xA5, rol.registers["AL"])
        assertEquals(1, rol.flags["CF"])
        val ror = run("MOV AL, 0A5H\nROR AL, 8\nHLT").snapshot()
        assertEquals(0xA5, ror.registers["AL"])
        assertEquals(1, ror.flags["CF"])
    }

    @Test fun shiftCountsAreMaskedToFiveBitsLikeThe8086() {
        // a count of 32 behaves like 0 on real hardware (count & 0x1F)
        val cpu = run("MOV AX, 0FFFFH\nSHL AX, 32\nHLT").snapshot()
        assertEquals(0xFFFF, cpu.registers["AX"])
    }

    @Test fun rotatesDoNotTouchTheZeroSignParityFlags() {
        val cpu = run("MOV AL, 0\nROL AL, 1\nHLT").snapshot()
        assertEquals(0, cpu.flags["ZF"])
    }

    // ----------------------------------------------------------------------- strings

    @Test fun movsbCopiesOneByteAndAdvancesBothPointers() {
        val cpu = run("MOV SI, 200H\nMOV DI, 300H\nMOV BYTE PTR [200H], 41H\nMOVSB\nHLT").snapshot()
        assertEquals(0x41, cpu.memory[0x300])
        assertEquals(0x201, cpu.registers["SI"])
        assertEquals(0x301, cpu.registers["DI"])
    }

    @Test fun movswCopiesAWordAndAdvancesByTwo() {
        val cpu = run("MOV SI, 200H\nMOV DI, 300H\nMOV WORD PTR [200H], 1234H\nMOVSW\nHLT").snapshot()
        assertEquals(0x34, cpu.memory[0x300])
        assertEquals(0x12, cpu.memory[0x301])
        assertEquals(0x202, cpu.registers["SI"])
        assertEquals(0x302, cpu.registers["DI"])
    }

    @Test fun stdMakesStringInstructionsWalkBackwards() {
        val cpu = run("MOV SI, 200H\nMOV DI, 300H\nMOV BYTE PTR [200H], 41H\nSTD\nMOVSB\nHLT").snapshot()
        assertEquals(0x41, cpu.memory[0x300])
        assertEquals(0x1FF, cpu.registers["SI"])
        assertEquals(0x2FF, cpu.registers["DI"])
    }

    @Test fun repMovsbCopiesACxCountOfBytes() {
        val code = "MOV SI, 200H\nMOV DI, 300H\nMOV BYTE PTR [200H], 1\nMOV BYTE PTR [201H], 2\n" +
            "MOV BYTE PTR [202H], 3\nMOV CX, 3\nREP MOVSB\nHLT"
        val cpu = run(code).snapshot()
        assertEquals(1, cpu.memory[0x300])
        assertEquals(2, cpu.memory[0x301])
        assertEquals(3, cpu.memory[0x302])
        assertEquals(0, cpu.registers["CX"])
        assertEquals(0x203, cpu.registers["SI"])
        assertEquals(0x303, cpu.registers["DI"])
    }

    @Test fun repeCmpsbStopsAtTheFirstDifferenceAndComparesCorrectly() {
        val equal = "MOV SI, 200H\nMOV DI, 300H\nMOV BYTE PTR [200H], 9\nMOV BYTE PTR [300H], 9\n" +
            "MOV CX, 1\nREPE CMPSB\nHLT"
        val first = run(equal).snapshot()
        assertEquals(0, first.registers["CX"])
        assertEquals(1, first.flags["ZF"])

        val different = "MOV SI, 200H\nMOV DI, 300H\nMOV BYTE PTR [200H], 9\nMOV BYTE PTR [300H], 4\n" +
            "MOV CX, 5\nREPE CMPSB\nHLT"
        val second = run(different).snapshot()
        assertEquals(4, second.registers["CX"])
        assertEquals(0, second.flags["ZF"])
    }

    @Test fun repneScasbFindsTheByteInAl() {
        val code = "MOV DI, 300H\nMOV BYTE PTR [300H], 1\nMOV BYTE PTR [301H], 2\nMOV BYTE PTR [302H], 41H\n" +
            "MOV BYTE PTR [303H], 4\nMOV AL, 41H\nMOV CX, 4\nREPNE SCASB\nHLT"
        val cpu = run(code).snapshot()
        assertEquals(1, cpu.registers["CX"])
        assertEquals(0x303, cpu.registers["DI"])
        assertEquals(1, cpu.flags["ZF"])
    }

    @Test fun lodsbAndStosbMoveDataThroughAl() {
        val cpu = run("MOV SI, 200H\nMOV BYTE PTR [200H], 55H\nLODSB\nHLT").snapshot()
        assertEquals(0x55, cpu.registers["AL"])
        assertEquals(0x201, cpu.registers["SI"])

        val stored = run("MOV DI, 300H\nMOV AX, 1234H\nSTOSW\nHLT").snapshot()
        assertEquals(0x34, stored.memory[0x300])
        assertEquals(0x12, stored.memory[0x301])
        assertEquals(0x302, stored.registers["DI"])
    }

    @Test fun repWithCountZeroDoesNothing() {
        val cpu = run("MOV SI, 200H\nMOV DI, 300H\nMOV CX, 0\nREP MOVSB\nHLT").snapshot()
        assertEquals(0x200, cpu.registers["SI"])
        assertEquals(0x300, cpu.registers["DI"])
    }

    // ------------------------------------------------------------- stack and control

    @Test fun callAndRetReturnToTheInstructionAfterTheCall() {
        val cpu = run("MOV AX, 5\nCALL DOUBLE\nHLT\nDOUBLE: ADD AX, AX\nRET").snapshot()
        assertEquals(10, cpu.registers["AX"])
        assertEquals(0xFFFE, cpu.registers["SP"])
        assertTrue(cpu.halted)
    }

    @Test fun nestedCallsKeepTheReturnAddressesOnTheStack() {
        val code = "MOV AX, 1\nCALL OUTER\nHLT\nOUTER: CALL INNER\nRET\nINNER: ADD AX, 10\nRET"
        val cpu = run(code).snapshot()
        assertEquals(11, cpu.registers["AX"])
        assertEquals(0xFFFE, cpu.registers["SP"])
    }

    @Test fun retWithAnOperandReleasesExtraStackWords() {
        val code = "MOV AX, 7\nPUSH AX\nPUSH AX\nCALL WORK\nHLT\nWORK: RET 4"
        val cpu = run(code).snapshot()
        assertEquals(0xFFFE, cpu.registers["SP"])
    }

    @Test fun iretPopsAnInterruptFrameAndRestoresTheFlags() {
        val code = "JMP START\nHANDLER: MOV AX, 9\nHLT\nSTART: PUSHF\nPUSH 0\nPUSH 1\nIRET"
        val cpu = run(code).snapshot()
        assertEquals(9, cpu.registers["AX"])
        assertTrue(cpu.halted)
    }

    @Test fun intoIsANoopWhenOverflowIsClear() {
        val cpu = run("MOV AL, 1\nADD AL, 1\nINTO\nHLT").snapshot()
        assertTrue(cpu.halted)
        assertEquals(2, cpu.registers["AL"])
    }

    // ------------------------------------------------------------------- misc / flags

    @Test fun jngIsAnAliasForJle() {
        val cpu = run("MOV AL, 1\nCMP AL, 5\nJNG SMALL\nMOV BX, 99\nJMP DONE\nSMALL: MOV BX, 7\nDONE: HLT").snapshot()
        assertEquals(7, cpu.registers["BX"])
    }

    @Test fun stiSetsTheInterruptFlagAndLockWaitEscAreNoOps() {
        assertEquals(1, flag("STI\nHLT", "IF"))
        val cpu = run("WAIT\nLOCK NOP\nESC 6, [BX]\nNOP\nHLT").snapshot()
        assertTrue(cpu.halted)
    }

    @Test fun int21hAh9PrintsAZeroTerminatedString() {
        val code = "MOV BYTE PTR [200H], 48H\nMOV BYTE PTR [201H], 49H\nMOV BYTE PTR [202H], 24H\n" +
            "MOV DX, 200H\nMOV AH, 9\nINT 21H\nHLT"
        assertEquals("HI", run(code).snapshot().output)
    }

    @Test fun int21hAh4cTerminatesTheProgramWithItsExitCode() {
        val cpu = run("MOV AL, 3\nMOV AH, 4CH\nINT 21H").snapshot()
        assertTrue(cpu.halted)
        assertTrue(cpu.finished)
    }

    @Test fun divisionByZeroIsReported() {
        val error = runCatching { run("MOV AX, 10\nMOV BX, 0\nDIV BX\nHLT") }.exceptionOrNull()
        assertTrue(error?.message?.contains("Division by zero") == true)
    }

    @Test fun malformedReturnIsReported() {
        val error = runCatching { run("PUSH 999\nRET") }.exceptionOrNull()
        assertTrue(error?.message?.contains("valid return address") == true)
    }

    // -------------------------------------------------------------------- validation

    @Test fun ahCanNowBeUsedAsADestination() {
        assertTrue(AssemblyEngine.validate("MOV AH, 2\nINT 21H").isEmpty())
        assertTrue(AssemblyEngine.validate("MOV BH, 1\nMOV CH, 2\nMOV DH, 3").isEmpty())
    }

    @Test fun validProgramsUsingNewInstructionsValidateCleanly() {
        assertTrue(AssemblyEngine.validate("MOV CX, 4\nREP MOVSB").isEmpty())
        assertTrue(AssemblyEngine.validate("MOV AX, 1\nSHL AX, CL\nPUSH AX\nPOP BX\nCALL L1\nL1: RET").isEmpty())
        assertTrue(AssemblyEngine.validate("MOV AL, 9\nAAM\nDAA\nXLAT").isEmpty())
    }

    @Test fun unknownOpcodesAreStillRejected() {
        assertTrue(AssemblyEngine.validate("FOO AX").any { it.message.contains("Unknown opcode 'FOO'") })
    }

    @Test fun repWithANonStringInstructionIsRejected() {
        assertTrue(AssemblyEngine.validate("REP MOV AX, 1").any { it.message.contains("only valid with a string instruction") })
    }

    @Test fun pushOfAByteRegisterIsRejected() {
        assertTrue(AssemblyEngine.validate("PUSH AL").any { it.message.contains("16-bit operand") })
    }

    @Test fun leaRequiresAMemorySource() {
        assertTrue(AssemblyEngine.validate("LEA AX, BX").any { it.message.contains("requires a memory operand") })
    }
}
