package com.salikoon.emulator8086.engine

/**
 * A small, deterministic 16-bit 8086 teaching emulator.
 *
 * One call to [step] executes one source line. String instructions with a REP prefix are the one
 * exception: the whole repeat runs inside a single step (the 8086 treats REP as part of one
 * instruction). Memory is a flat 64 KiB offset space (segment registers are kept but not applied),
 * the stack is real (SS:SP is modelled by SP over the flat space), and "IP" is expressed as an index
 * into the parsed source lines so that CALL/RET/IRET can work without a byte-level assembler.
 */
data class ExecutionSnapshot(
    val sourceLine: Int,
    val instruction: String,
    val registers: Map<String, Int>,
    val flags: Map<String, Int>,
    val memory: Map<Int, Int>,
    val output: String,
    val halted: Boolean,
    val finished: Boolean,
    val ports: Map<Int, Int> = emptyMap()
)

data class ValidationIssue(val line: Int, val message: String)

class AssemblyEngine(val source: String, private val input: String = "") {
    private data class Line(val sourceLine: Int, val code: String, val prefix: String?, val op: String, val args: List<String>)

    private val registers = linkedMapOf(
        "AX" to 0, "BX" to 0, "CX" to 0, "DX" to 0,
        "SI" to 0, "DI" to 0, "BP" to 0, "SP" to 0xFFFE,
        "CS" to 0, "DS" to 0, "ES" to 0, "SS" to 0, "IP" to 0
    )
    private val flagNames = listOf("CF", "PF", "AF", "ZF", "SF", "TF", "IF", "DF", "OF")
    private val flags = flagNames.associateWithTo(linkedMapOf()) { 0 }
    private val memory = linkedMapOf<Int, Int>()
    private val ports = linkedMapOf<Int, Int>()
    private val output = StringBuilder()
    private val lines: List<Line>
    private val labels = linkedMapOf<String, Int>()
    private var cursor = 0
    private var inputCursor = 0
    private var halted = false
    private var done = false
    private var currentLine = 1

    init {
        val parsed = mutableListOf<Line>()
        source.lines().forEachIndexed { index, raw ->
            var text = raw.substringBefore(';').trim().uppercase()
            if (text.isEmpty()) return@forEachIndexed
            val head = text.substringBefore(' ')
            if (head.endsWith(":")) {
                val label = head.dropLast(1)
                require(labelPattern.matches(label)) { "Invalid label '$label' on line ${index + 1}" }
            }
            val match = labelAtStart.find(text)
            if (match != null) {
                val label = match.groupValues[1]
                require(!labels.containsKey(label)) { "Duplicate label '$label' on line ${index + 1}" }
                labels[label] = parsed.size
                text = text.substring(match.range.last + 1).trim()
                if (text.isEmpty()) return@forEachIndexed
            }
            parsed += splitPrefix(text, index + 1)
        }
        lines = parsed
        val unsupported = lines.firstOrNull { it.op !in supportedInstructions }
        require(unsupported == null) { "Unsupported instruction '${unsupported?.op}' on line ${unsupported?.sourceLine}" }
        lines.filter { it.op in labelTargetInstructions }
            .forEach { line ->
                val target = line.args.firstOrNull().orEmpty()
                require(target in labels) { "Unknown label: $target" }
            }
    }

    private fun splitPrefix(text: String, sourceLine: Int): Line {
        var prefix: String? = null
        var rest = text
        val first = rest.split(WHITESPACE, limit = 2)[0]
        if (first in prefixInstructions) {
            prefix = first
            rest = rest.split(WHITESPACE, limit = 2).getOrNull(1)?.trim().orEmpty()
            require(rest.isNotEmpty()) { "Missing instruction after $prefix on line $sourceLine" }
        }
        val parts = rest.split(WHITESPACE, limit = 2)
        val op = parts[0]
        val args = operands(parts.getOrNull(1).orEmpty())
        return Line(sourceLine, text, prefix, op, args)
    }

    fun reset() {
        registers.keys.toList().forEach { registers[it] = if (it == "SP") 0xFFFE else 0 }
        flags.keys.toList().forEach { flags[it] = 0 }
        memory.clear(); ports.clear(); output.clear()
        cursor = 0; inputCursor = 0; halted = false; done = lines.isEmpty(); currentLine = 1
    }

    fun snapshot(): ExecutionSnapshot = ExecutionSnapshot(
        sourceLine = if (done || lines.isEmpty()) currentLine else lines.getOrNull(cursor)?.sourceLine ?: currentLine,
        instruction = if (done || lines.isEmpty()) if (halted) "HLT" else "—" else lines.getOrNull(cursor)?.code ?: "—",
        registers = registers.toMutableMap().apply {
            byteAliases.forEach { (alias, baseAndHigh) ->
                val word = registers.getValue(baseAndHigh.first)
                put(alias, if (baseAndHigh.second) (word ushr 8) and 0xFF else word and 0xFF)
            }
        }.toMap(),
        flags = flags.toMap(), memory = memory.toMap(), output = output.toString(),
        halted = halted, finished = done, ports = ports.toMap()
    )

    fun step(): ExecutionSnapshot {
        if (done) return snapshot()
        if (cursor !in lines.indices) { done = true; return snapshot() }
        val line = lines[cursor]
        currentLine = line.sourceLine
        val op = line.op
        val args = line.args
        val prefix = line.prefix
        var next = cursor + 1
        fun need(count: Int) { require(args.size == count) { "$op expects $count operand(s)" } }
        fun needOneOrTwo() { require(args.size in 1..2) { "$op expects 1 or 2 operand(s)" } }

        require(args.none { it.isEmpty() }) { "Empty operand in '$op'" }
        require(!(op in memoryPairForbidden && args.size == 2 && isMemoryOperand(args[0]) && isMemoryOperand(args[1]))) {
            "$op cannot use memory for both operands; the 8086 cannot encode it"
        }
        if (prefix != null && prefix != "LOCK" && op !in stringInstructions) {
            error("$prefix prefix is only valid with a string instruction")
        }

        when (op) {
            // ---- processor control -------------------------------------------------
            "NOP", "WAIT" -> need(0)
            "ESC" -> require(args.size <= 2) { "ESC expects at most 2 operands" }
            "HLT" -> { need(0); halted = true; done = true }

            // ---- data transfer -----------------------------------------------------
            "MOV" -> {
                need(2)
                val width = widthOf(args[0], args[1])
                write(args[0], read(args[1], width), width)
            }
            "XCHG" -> {
                need(2)
                val width = widthOf(args[0], args[1])
                val a = read(args[0], width); val b = read(args[1], width)
                write(args[0], b, width); write(args[1], a, width)
            }
            "PUSH" -> {
                need(1); require(widthOf(args[0]) == 16 || isImmediate(args[0])) { "PUSH requires a 16-bit operand" }
                // the 8086 pushes the already decremented SP (the 80286 and later push the original value)
                val value = if (normalize(args[0]) == "SP") (read("SP") - 2) and 0xFFFF else read(args[0], 16)
                pushWord(value)
            }
            "POP" -> {
                need(1)
                require(normalize(args[0]) != "CS") { "POP CS is not allowed on the 8086" }
                write(args[0], popWord(), 16)
            }
            "PUSHF" -> { need(0); pushWord(packFlags()) }
            "POPF" -> { need(0); unpackFlags(popWord()) }
            "LAHF" -> { need(0); write("AH", (packFlags() and 0xFF) or 0x02) }
            "SAHF" -> {
                need(0)
                val value = read("AH", 8)
                flags["CF"] = value and 0x01; flags["PF"] = (value ushr 2) and 0x01
                flags["AF"] = (value ushr 4) and 0x01; flags["ZF"] = (value ushr 6) and 0x01
                flags["SF"] = (value ushr 7) and 0x01
            }
            "LEA" -> {
                need(2)
                require(isMemoryOperand(args[1])) { "LEA requires a memory operand as its source" }
                require(widthOf(args[0]) == 16) { "LEA requires a 16-bit register" }
                write(args[0], addressOf(args[1]), 16)
            }
            "LDS", "LES" -> {
                need(2)
                require(isMemoryOperand(args[1])) { "$op requires a memory operand as its source" }
                val base = addressOf(args[1])
                val low = readWordAt(base); val high = readWordAt((base + 2) and 0xFFFF)
                write(args[0], low, 16)
                write(if (op == "LDS") "DS" else "ES", high, 16)
            }
            "XLAT", "XLATB" -> { need(0); write("AL", readByteAt((read("BX") + read("AL")) and 0xFFFF)) }
            "IN" -> {
                need(2)
                val width = widthOf(args[0])
                require(normalize(args[0]) == "AL" || normalize(args[0]) == "AX") { "IN must target AL or AX" }
                val port = portNumber(args[1])
                write(args[0], ports[port] ?: 0, width)
            }
            "OUT" -> {
                need(2)
                val source = normalize(args[1])
                require(source == "AL" || source == "AX") { "OUT must read from AL or AX" }
                val port = portNumber(args[0])
                ports[port] = read(source, widthOf(source))
            }

            // ---- arithmetic --------------------------------------------------------
            "ADD", "ADC", "SUB", "SBB", "CMP" -> {
                need(2)
                val width = widthOf(args[0], args[1])
                val a = read(args[0], width); val b = read(args[1], width)
                when (op) {
                    "ADD" -> write(args[0], addWithCarry(a, b, 0, width), width)
                    "ADC" -> write(args[0], addWithCarry(a, b, flags["CF"] ?: 0, width), width)
                    "SUB" -> write(args[0], subWithBorrow(a, b, 0, width), width)
                    "SBB" -> write(args[0], subWithBorrow(a, b, flags["CF"] ?: 0, width), width)
                    else -> subWithBorrow(a, b, 0, width) // CMP: flags only
                }
            }
            "INC", "DEC" -> {
                need(1)
                val width = widthOf(args[0]); val a = read(args[0], width)
                write(args[0], increment(a, if (op == "INC") 1 else -1, width), width)
            }
            "NEG" -> {
                need(1)
                val width = widthOf(args[0]); val a = read(args[0], width)
                val result = (0 - a) and mask(width)
                flags["CF"] = if (a != 0) 1 else 0
                flags["OF"] = if (a == signBit(width)) 1 else 0
                flags["AF"] = if ((a and 0x0F) != 0) 1 else 0
                setSZP(result, width)
                write(args[0], result, width)
            }
            "NOT" -> {
                need(1)
                val width = widthOf(args[0])
                write(args[0], read(args[0], width).inv() and mask(width), width)
            }
            "MUL" -> {
                need(1); require(!isImmediate(args[0])) { "MUL does not accept an immediate value" }
                val width = widthOf(args[0]); val factor = read(args[0], width)
                if (width == 8) {
                    val product = read("AL", 8) * factor
                    write("AX", product, 16)
                    flags["CF"] = if (product > 0xFF) 1 else 0
                } else {
                    val product = read("AX", 16).toLong() * factor
                    write("AX", (product and 0xFFFF).toInt(), 16)
                    write("DX", ((product ushr 16) and 0xFFFF).toInt(), 16)
                    flags["CF"] = if ((product ushr 16) != 0L) 1 else 0
                }
                flags["OF"] = flags["CF"] ?: 0
            }
            "IMUL" -> {
                need(1); require(!isImmediate(args[0])) { "IMUL does not accept an immediate value" }
                val width = widthOf(args[0]); val factor = signed(read(args[0], width), width)
                if (width == 8) {
                    val product = signed(read("AL", 8), 8) * factor
                    write("AX", product and 0xFFFF, 16)
                    flags["CF"] = if (product != product.toByte().toInt()) 1 else 0
                } else {
                    val product = signed(read("AX", 16), 16).toLong() * factor
                    write("AX", (product and 0xFFFF).toInt(), 16)
                    write("DX", ((product ushr 16) and 0xFFFF).toInt(), 16)
                    flags["CF"] = if (product != product.toShort().toLong()) 1 else 0
                }
                flags["OF"] = flags["CF"] ?: 0
            }
            "DIV" -> {
                need(1); require(!isImmediate(args[0])) { "DIV does not accept an immediate value" }
                val width = widthOf(args[0]); val divisor = read(args[0], width)
                require(divisor != 0) { "Division by zero" }
                if (width == 8) {
                    val dividend = read("AX", 16)
                    val quotient = dividend / divisor
                    require(quotient <= 0xFF) { "Byte quotient overflow" }
                    write("AL", quotient, 8); write("AH", dividend % divisor, 8)
                } else {
                    val dividend = (read("DX", 16).toLong() shl 16) or read("AX", 16).toLong()
                    val quotient = dividend / divisor
                    require(quotient <= 0xFFFF) { "Word quotient overflow" }
                    write("AX", quotient.toInt(), 16); write("DX", (dividend % divisor).toInt(), 16)
                }
            }
            "IDIV" -> {
                need(1); require(!isImmediate(args[0])) { "IDIV does not accept an immediate value" }
                val width = widthOf(args[0]); val divisor = signed(read(args[0], width), width)
                require(divisor != 0) { "Division by zero" }
                if (width == 8) {
                    val dividend = signed(read("AX", 16), 16)
                    val quotient = dividend / divisor
                    require(quotient in -128..127) { "Byte quotient overflow" }
                    write("AL", quotient and 0xFF, 8); write("AH", (dividend % divisor) and 0xFF, 8)
                } else {
                    val dividend = ((read("DX", 16) shl 16) or read("AX", 16))
                    val quotient = dividend / divisor
                    require(quotient in -32768..32767) { "Word quotient overflow" }
                    write("AX", quotient and 0xFFFF, 16); write("DX", (dividend % divisor) and 0xFFFF, 16)
                }
            }
            "CBW" -> { need(0); write("AX", signed(read("AL", 8), 8) and 0xFFFF, 16) }
            "CWD" -> { need(0); write("DX", if ((read("AX", 16) and 0x8000) != 0) 0xFFFF else 0, 16) }
            "AAA", "AAS", "AAM", "AAD", "DAA", "DAS" -> { executeDecimalAdjust(op, args) }

            // ---- logic -------------------------------------------------------------
            "AND", "OR", "XOR", "TEST" -> {
                need(2)
                val width = widthOf(args[0], args[1])
                val a = read(args[0], width); val b = read(args[1], width)
                val result = when (op) { "AND", "TEST" -> a and b; "OR" -> a or b; else -> a xor b } and mask(width)
                flags["CF"] = 0; flags["OF"] = 0
                setSZP(result, width)
                if (op != "TEST") write(args[0], result, width)
            }

            // ---- shifts and rotates ------------------------------------------------
            "SHL", "SAL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR" -> {
                needOneOrTwo()
                val width = widthOf(args[0])
                val value = read(args[0], width)
                val count = when {
                    args.size == 1 -> 1
                    normalize(args[1]) == "CL" -> read("CL", 8)
                    else -> {
                        require(isImmediate(args[1])) { "The shift count of $op must be 1, CL, or an immediate value" }
                        parseNumber(args[1])
                    }
                }
                require(count in 0..255) { "Shift count must be between 0 and 255" }
                write(args[0], shiftRotate(op, value, count, width), width)
            }

            // ---- strings -----------------------------------------------------------
            in stringInstructions -> runString(op, prefix)

            // ---- control transfer --------------------------------------------------
            "JMP", "JA", "JNBE", "JNC", "JAE", "JNB", "JC", "JB", "JNAE", "JBE", "JNA", "JCXZ", "JZ", "JE",
            "JG", "JNLE", "JGE", "JNL", "JL", "JNGE", "JLE", "JNG", "JNZ", "JNE", "JNO", "JNP", "JPO",
            "JNS", "JO", "JP", "JPE", "JS" -> {
                need(1)
                if (jumpCondition(op)) next = labels[args[0]] ?: error("Unknown label ${args[0]}")
            }
            "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ" -> {
                need(1)
                val cx = (read("CX") - 1) and 0xFFFF
                write("CX", cx, 16)
                val condition = cx != 0 && when (op) {
                    "LOOPE", "LOOPZ" -> flags["ZF"] == 1
                    "LOOPNE", "LOOPNZ" -> flags["ZF"] == 0
                    else -> true
                }
                if (condition) next = labels[args[0]] ?: error("Unknown label ${args[0]}")
            }
            "CALL" -> {
                need(1)
                require(args[0] in labels) { "CALL requires a label target; '${args[0]}' is not a label" }
                pushWord(next and 0xFFFF)
                next = labels.getValue(args[0])
            }
            "RET", "RETN", "RETF", "IRET" -> {
                require(args.size <= 1) { "$op expects at most 1 operand" }
                next = returnFrom(op, args.firstOrNull())
            }
            "INTO" -> { need(0); if (flags["OF"] == 1) error("INTO: interrupt 4 has no handler in this emulator") }

            // ---- flags -------------------------------------------------------------
            "STC" -> { need(0); flags["CF"] = 1 }
            "CLC" -> { need(0); flags["CF"] = 0 }
            "CMC" -> { need(0); flags["CF"] = 1 - (flags["CF"] ?: 0) }
            "STD" -> { need(0); flags["DF"] = 1 }
            "CLD" -> { need(0); flags["DF"] = 0 }
            "STI" -> { need(0); flags["IF"] = 1 }
            "CLI" -> { need(0); flags["IF"] = 0 }

            // ---- interrupts --------------------------------------------------------
            "INT" -> {
                need(1)
                require(isImmediate(args[0])) { "INT requires an immediate interrupt vector" }
                executeInterrupt(parseNumber(args[0]) and 0xFF)
            }

            else -> error("Unsupported instruction: $op")
        }
        registers["IP"] = next and 0xFFFF
        if (!done) {
            cursor = next
            if (cursor !in lines.indices) { done = true; currentLine = (lines.lastOrNull()?.sourceLine ?: 0) + 1 }
        }
        return snapshot()
    }

    fun run(maxSteps: Int = 10_000): ExecutionSnapshot {
        var count = 0
        while (!done && count < maxSteps) { step(); count++ }
        require(done) { "Execution limit ($maxSteps steps) reached; possible infinite loop" }
        return snapshot()
    }

    // -------------------------------------------------------------------- strings

    private fun runString(op: String, prefix: String?) {
        val compares = op.startsWith("CMPS") || op.startsWith("SCAS")
        if (prefix == null) { stringStep(op); return }
        var guard = 0
        while (read("CX") != 0) {
            stringStep(op)
            write("CX", (read("CX") - 1) and 0xFFFF, 16)
            if (++guard > 0x10000) error("REP: iteration limit reached")
            when (prefix) {
                "REP", "REPE", "REPZ" -> if (compares && flags["ZF"] == 0) break
                "REPNE", "REPNZ" -> if (compares && flags["ZF"] == 1) break
            }
        }
    }

    private fun stringStep(op: String) {
        val step = if (flags["DF"] == 1) -1 else 1
        val si = read("SI"); val di = read("DI")
        when (op) {
            "MOVSB" -> { writeByteAt(di, readByteAt(si)); advance("SI", step); advance("DI", step) }
            "MOVSW" -> { writeWordAt(di, readWordAt(si)); advance("SI", step * 2); advance("DI", step * 2) }
            "CMPSB" -> { subWithBorrow(readByteAt(si), readByteAt(di), 0, 8); advance("SI", step); advance("DI", step) }
            "CMPSW" -> { subWithBorrow(readWordAt(si), readWordAt(di), 0, 16); advance("SI", step * 2); advance("DI", step * 2) }
            "SCASB" -> { subWithBorrow(read("AL", 8), readByteAt(di), 0, 8); advance("DI", step) }
            "SCASW" -> { subWithBorrow(read("AX", 16), readWordAt(di), 0, 16); advance("DI", step * 2) }
            "LODSB" -> { write("AL", readByteAt(si), 8); advance("SI", step) }
            "LODSW" -> { write("AX", readWordAt(si), 16); advance("SI", step * 2) }
            "STOSB" -> { writeByteAt(di, read("AL", 8)); advance("DI", step) }
            "STOSW" -> { writeWordAt(di, read("AX", 16)); advance("DI", step * 2) }
            else -> error("Not a string instruction: $op")
        }
    }

    private fun advance(register: String, delta: Int) = write(register, (read(register) + delta) and 0xFFFF, 16)

    // ---------------------------------------------------------------- decimal adjust

    private fun executeDecimalAdjust(op: String, args: List<String>) {
        require(args.size <= 1) { "$op expects at most 1 operand" }
        val base = args.firstOrNull()?.let {
            require(isImmediate(it)) { "$op base must be an immediate value" }
            parseNumber(it)
        } ?: 10
        require(base in 2..255) { "$op base must be between 2 and 255" }
        val al = read("AL", 8); val ah = read("AH", 8)
        when (op) {
            "AAA" -> {
                if (((al and 0x0F) > 9) || flags["AF"] == 1) {
                    write("AL", (al + 6) and 0x0F, 8); write("AH", (ah + 1) and 0xFF, 8)
                    flags["AF"] = 1; flags["CF"] = 1
                } else { flags["AF"] = 0; flags["CF"] = 0 }
            }
            "AAS" -> {
                if (((al and 0x0F) > 9) || flags["AF"] == 1) {
                    write("AL", (al - 6) and 0x0F, 8); write("AH", (ah - 1) and 0xFF, 8)
                    flags["AF"] = 1; flags["CF"] = 1
                } else { flags["AF"] = 0; flags["CF"] = 0 }
            }
            "AAM" -> {
                val value = al
                write("AH", value / base, 8); val low = value % base; write("AL", low, 8)
                setSZP(low, 8)
            }
            "AAD" -> {
                val value = ((ah * base) + al) and 0xFF
                write("AL", value, 8); write("AH", 0, 8)
                setSZP(value, 8)
            }
            "DAA" -> {
                // Intel: adjust the low digit first, then the high digit; CF depends only on the old AL/CF
                val oldAL = al; val oldCF = flags["CF"] ?: 0
                var result = al
                if (((al and 0x0F) > 9) || flags["AF"] == 1) { result += 6; flags["AF"] = 1 } else flags["AF"] = 0
                if (oldAL > 0x99 || oldCF == 1) { result += 0x60; flags["CF"] = 1 } else flags["CF"] = 0
                write("AL", result and 0xFF, 8)
                setSZP(result and 0xFF, 8)
            }
            "DAS" -> {
                val oldAL = al; val oldCF = flags["CF"] ?: 0
                var result = al
                if (((al and 0x0F) > 9) || flags["AF"] == 1) { result -= 6; flags["AF"] = 1 } else flags["AF"] = 0
                if (oldAL > 0x99 || oldCF == 1) { result -= 0x60; flags["CF"] = 1 } else flags["CF"] = 0
                write("AL", result and 0xFF, 8)
                setSZP(result and 0xFF, 8)
            }
        }
    }

    // -------------------------------------------------------------- shifts / rotates

    private fun shiftRotate(op: String, input: Int, rawCount: Int, width: Int): Int {
        val mask = mask(width)
        val count = rawCount and 0x1F          // the 8086 masks the count to 5 bits
        if (count == 0) return input and mask
        var value = input and mask
        val bits = width
        when (op) {
            "SHL", "SAL" -> {
                var cf = 0
                repeat(count) { cf = (value ushr (bits - 1)) and 1; value = (value shl 1) and mask }
                flags["CF"] = cf
                if (count == 1) flags["OF"] = if ((((value ushr (bits - 1)) and 1) xor cf) != 0) 1 else 0
                setSZP(value, width)
            }
            "SHR" -> {
                var cf = 0
                repeat(count) { cf = value and 1; value = value ushr 1 }
                flags["CF"] = cf
                if (count == 1) flags["OF"] = (input ushr (bits - 1)) and 1
                setSZP(value, width)
            }
            "SAR" -> {
                val negative = (input and signBit(bits)) != 0
                var cf = 0
                repeat(count) {
                    cf = value and 1
                    value = (value ushr 1) or (if (negative) signBit(bits) else 0)
                }
                flags["CF"] = cf
                if (count == 1) flags["OF"] = 0
                setSZP(value, width)
            }
            "ROL" -> {
                var cf = 0
                repeat(count) { cf = (value ushr (bits - 1)) and 1; value = ((value shl 1) or cf) and mask }
                flags["CF"] = cf
                if (count == 1) flags["OF"] = if ((((value ushr (bits - 1)) and 1) xor cf) != 0) 1 else 0
            }
            "ROR" -> {
                var cf = 0
                repeat(count) { cf = value and 1; value = (value ushr 1) or (cf shl (bits - 1)) }
                flags["CF"] = cf
                if (count == 1) {
                    val msb = (value ushr (bits - 1)) and 1
                    val next = (value ushr (bits - 2)) and 1
                    flags["OF"] = if ((msb xor next) != 0) 1 else 0
                }
            }
            "RCL" -> {
                repeat(count) {
                    val msb = (value ushr (bits - 1)) and 1
                    value = ((value shl 1) or (flags["CF"] ?: 0)) and mask
                    flags["CF"] = msb
                }
                if (count == 1) flags["OF"] = if ((((value ushr (bits - 1)) and 1) xor (flags["CF"] ?: 0)) != 0) 1 else 0
            }
            "RCR" -> {
                repeat(count) {
                    val lsb = value and 1
                    value = ((value ushr 1) or ((flags["CF"] ?: 0) shl (bits - 1))) and mask
                    flags["CF"] = lsb
                }
                if (count == 1) {
                    val msb = (value ushr (bits - 1)) and 1
                    val next = (value ushr (bits - 2)) and 1
                    flags["OF"] = if ((msb xor next) != 0) 1 else 0
                }
            }
        }
        return value and mask
    }

    // ------------------------------------------------------------------- interrupts

    private fun executeInterrupt(vector: Int) {
        require(vector == 0x21) { "Only DOS interrupt 21h is supported" }
        when (read("AH", 8)) {
            0x01 -> {
                val ch = input.getOrNull(inputCursor++) ?: '?'
                write("AL", ch.code, 8)
            }
            0x02 -> { val ch = read("DL", 8).toChar(); output.append(ch); write("AL", ch.code, 8) }
            0x09 -> {
                var address = read("DX", 16)
                var guard = 0
                while (true) {
                    val ch = readByteAt(address)
                    if (ch == '$'.code) break
                    output.append(ch.toChar())
                    address = (address + 1) and 0xFFFF
                    if (++guard > 0x10000) error("INT 21h AH=9: string is not terminated with '\$'")
                }
            }
            0x4C -> { halted = true; done = true }
            else -> error("INT 21h supports AH=1 (read), AH=2 (write), AH=9 (print string) and AH=4Ch (exit)")
        }
    }

    // -------------------------------------------------------------------- returns

    private fun returnFrom(op: String, operand: String?): Int {
        val extra = operand?.let(::parseNumber) ?: 0
        val target: Int
        when (op) {
            "IRET" -> {
                target = popWord()
                popWord()          // CS (flat model: discarded)
                unpackFlags(popWord())
            }
            "RETF" -> { target = popWord(); popWord() }
            else -> target = popWord()
        }
        if (extra != 0) write("SP", (read("SP") + extra) and 0xFFFF, 16)
        if (target == lines.size) { done = true; return lines.size }
        require(target in lines.indices) { "$op: the stack does not contain a valid return address (SP=${read("SP")})" }
        return target
    }

    // ------------------------------------------------------------------- conditionals

    private fun jumpCondition(op: String): Boolean {
        val cf = flags["CF"] == 1; val zf = flags["ZF"] == 1; val sf = flags["SF"] == 1
        val of = flags["OF"] == 1; val pf = flags["PF"] == 1
        return when (op) {
            "JMP" -> true
            "JA", "JNBE" -> !cf && !zf
            "JNC", "JAE", "JNB" -> !cf
            "JC", "JB", "JNAE" -> cf
            "JBE", "JNA" -> cf || zf
            "JCXZ" -> read("CX") == 0
            "JZ", "JE" -> zf
            "JG", "JNLE" -> !zf && sf == of
            "JGE", "JNL" -> sf == of
            "JL", "JNGE" -> sf != of
            "JLE", "JNG" -> zf || sf != of
            "JNZ", "JNE" -> !zf
            "JNO" -> !of
            "JNP", "JPO" -> !pf
            "JNS" -> !sf
            "JO" -> of
            "JP", "JPE" -> pf
            "JS" -> sf
            else -> false
        }
    }

    // ----------------------------------------------------------------------- flags

    private fun setSZP(result: Int, width: Int) {
        val value = result and mask(width)
        flags["ZF"] = if (value == 0) 1 else 0
        flags["SF"] = if ((value and signBit(width)) != 0) 1 else 0
        flags["PF"] = if (Integer.bitCount(value and 0xFF) % 2 == 0) 1 else 0
    }

    private fun addWithCarry(a: Int, b: Int, carry: Int, width: Int): Int {
        val mask = mask(width)
        val sum = a + b + carry
        val result = sum and mask
        flags["CF"] = if (sum > mask) 1 else 0
        flags["OF"] = if (((a xor result) and (b xor result) and signBit(width)) != 0) 1 else 0
        flags["AF"] = if (((a and 0x0F) + (b and 0x0F) + carry) > 0x0F) 1 else 0
        setSZP(result, width)
        return result
    }

    private fun subWithBorrow(a: Int, b: Int, borrow: Int, width: Int): Int {
        val mask = mask(width)
        val difference = a - b - borrow
        val result = difference and mask
        flags["CF"] = if (difference < 0) 1 else 0
        flags["OF"] = if (((a xor b) and (a xor result) and signBit(width)) != 0) 1 else 0
        flags["AF"] = if (((a and 0x0F) - (b and 0x0F) - borrow) < 0) 1 else 0
        setSZP(result, width)
        return result
    }

    private fun increment(a: Int, delta: Int, width: Int): Int {
        val result = (a + delta) and mask(width)
        setSZP(result, width)
        flags["AF"] = if (delta > 0) { if ((a and 0x0F) == 0x0F) 1 else 0 } else { if ((a and 0x0F) == 0x00) 1 else 0 }
        flags["OF"] = if (a == (if (delta > 0) signBit(width) - 1 else signBit(width))) 1 else 0
        return result
    }

    private fun packFlags(): Int =
        (flags["CF"] ?: 0) or ((flags["PF"] ?: 0) shl 2) or ((flags["AF"] ?: 0) shl 4) or
            ((flags["ZF"] ?: 0) shl 6) or ((flags["SF"] ?: 0) shl 7) or ((flags["TF"] ?: 0) shl 8) or
            ((flags["IF"] ?: 0) shl 9) or ((flags["DF"] ?: 0) shl 10) or ((flags["OF"] ?: 0) shl 11) or 0xF002
        // bits 12-15 read as ones on the 8086, bit 1 is always one

    private fun unpackFlags(word: Int) {
        flags["CF"] = word and 1
        flags["PF"] = (word ushr 2) and 1
        flags["AF"] = (word ushr 4) and 1
        flags["ZF"] = (word ushr 6) and 1
        flags["SF"] = (word ushr 7) and 1
        flags["TF"] = (word ushr 8) and 1
        flags["IF"] = (word ushr 9) and 1
        flags["DF"] = (word ushr 10) and 1
        flags["OF"] = (word ushr 11) and 1
    }

    // --------------------------------------------------------------------- operands

    private fun widthOf(a: String, b: String? = null): Int {
        if (declaresByte(a) || (b != null && declaresByte(b))) return 8
        if (declaresWord(a) || (b != null && declaresWord(b))) return 16
        if (normalize(a) in byteRegisters || (b != null && normalize(b) in byteRegisters)) return 8
        return 16
    }

    private fun declaresByte(operand: String) = operand.contains("BYTE PTR")
    private fun declaresWord(operand: String) = operand.contains("WORD PTR")
    private fun mask(width: Int) = if (width == 8) 0xFF else 0xFFFF
    private fun signBit(width: Int) = if (width == 8) 0x80 else 0x8000
    private fun signed(value: Int, width: Int): Int = if (width == 8) (value shl 24) shr 24 else (value shl 16) shr 16

    private fun normalize(raw: String): String = raw.uppercase()
        .replace("BYTE PTR", "").replace("WORD PTR", "").replace("FAR PTR", "").replace("NEAR PTR", "")
        .trim()

    private fun stripSegment(operand: String): String = operand.replace(SEGMENT_PREFIX, "")

    private fun read(raw: String, width: Int = widthOf(raw)): Int {
        val operand = stripSegment(normalize(raw))
        registers[operand]?.let { return it }
        byteAliases[operand]?.let { alias ->
            val base = registers.getValue(alias.first)
            return if (alias.second) (base ushr 8) and 0xFF else base and 0xFF
        }
        if (isMemoryOperand(operand)) {
            val address = addressOf(operand)
            return if (width == 8) readByteAt(address) else readWordAt(address)
        }
        return parseNumber(operand) and mask(width)
    }

    private fun write(raw: String, value: Int, width: Int = widthOf(raw)) {
        val operand = stripSegment(normalize(raw))
        require(operand != "CS" && operand != "IP") {
            "$operand cannot be written directly on the 8086; only CALL/RET/IRET change the instruction pointer"
        }
        if (operand in registers) { registers[operand] = value and 0xFFFF; return }
        byteAliases[operand]?.let { alias ->
            val old = registers.getValue(alias.first)
            registers[alias.first] = if (alias.second) ((old and 0x00FF) or ((value and 0xFF) shl 8))
            else ((old and 0xFF00) or (value and 0xFF))
            return
        }
        if (isMemoryOperand(operand)) {
            val address = addressOf(operand)
            if (width == 8) writeByteAt(address, value) else writeWordAt(address, value)
            return
        }
        error("Cannot write to immediate operand '$raw'")
    }

    private fun readByteAt(address: Int): Int = memory[address and 0xFFFF] ?: 0
    private fun writeByteAt(address: Int, value: Int) { memory[address and 0xFFFF] = value and 0xFF }
    private fun readWordAt(address: Int): Int =
        (memory[address and 0xFFFF] ?: 0) or ((memory[(address + 1) and 0xFFFF] ?: 0) shl 8)
    private fun writeWordAt(address: Int, value: Int) {
        memory[address and 0xFFFF] = value and 0xFF
        memory[(address + 1) and 0xFFFF] = (value ushr 8) and 0xFF
    }

    private fun pushWord(value: Int) {
        val sp = (read("SP") - 2) and 0xFFFF
        write("SP", sp, 16)
        writeWordAt(sp, value)
    }

    private fun popWord(): Int {
        val sp = read("SP")
        val value = readWordAt(sp)
        write("SP", (sp + 2) and 0xFFFF, 16)
        return value
    }

    private fun addressOf(operand: String): Int = address(stripSegment(normalize(operand)).removeSurrounding("[", "]"))

    private fun address(expression: String): Int {
        val normalized = expression.replace("-", "+-")
        return normalized.split('+').map { it.trim() }.filter { it.isNotEmpty() }.sumOf { token ->
            when (token) { "BX", "BP", "SI", "DI" -> read(token); else -> parseNumber(token) }
        } and 0xFFFF
    }

    private fun portNumber(token: String): Int {
        val port = if (normalize(token) == "DX") read("DX", 16) else parseNumber(token)
        return port and 0xFFFF
    }

    private fun parseNumber(token: String): Int {
        val t = token.trim().uppercase()
        require(t.isNotEmpty()) { "Missing number" }
        if (t.startsWith("'") && t.endsWith("'") && t.length >= 3) return t[1].code
        fun digits(text: String, radix: Int): Int = try {
            text.toInt(radix)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid number '$token'")
        }
        return when {
            t.startsWith("0X") && t.length > 2 -> digits(t.substring(2), 16)
            t.endsWith("H") && t.length > 1 -> digits(t.dropLast(1), 16)
            t.endsWith("B") && t.length > 1 && t.dropLast(1).all { it == '0' || it == '1' } -> digits(t.dropLast(1), 2)
            t.endsWith("D") && t.length > 1 && t.dropLast(1).all { it.isDigit() } -> digits(t.dropLast(1), 10)
            t.matches(Regex("-?\\d+")) -> t.toInt()
            else -> throw IllegalArgumentException("Invalid number '$token'")
        }
    }

    private fun operands(text: String): List<String> = operandsOf(text)

    private fun isMemoryOperand(operand: String): Boolean {
        val value = stripSegment(normalize(operand))
        return value.startsWith('[') && value.endsWith(']')
    }

    private fun isImmediate(operand: String): Boolean {
        val value = normalize(operand)
        return value !in registers && value !in byteRegisters && isImmediateLiteral(value)
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** Splits an operand list on commas, ignoring commas inside character literals such as ','. */
        private fun operandsOf(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            text.forEach { ch ->
                when {
                    ch == '\'' -> { quoted = !quoted; current.append(ch) }
                    ch == ',' && !quoted -> { parts += current.toString().trim(); current.clear() }
                    else -> current.append(ch)
                }
            }
            parts += current.toString().trim()
            return parts
        }
        private val labelAtStart = Regex("^([A-Z_.$?][A-Z0-9_.$?]*)\\s*:")
        private val labelPattern = Regex("[A-Z_.$?][A-Z0-9_.$?]*")
        private val SEGMENT_PREFIX = Regex("^(CS|DS|ES|SS)\\s*:")

        private val byteRegisters = setOf("AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL")
        private val byteAliases = mapOf(
            "AH" to ("AX" to true), "AL" to ("AX" to false),
            "BH" to ("BX" to true), "BL" to ("BX" to false),
            "CH" to ("CX" to true), "CL" to ("CX" to false),
            "DH" to ("DX" to true), "DL" to ("DX" to false)
        )
        private val addressRegisters = setOf("BX", "BP", "SI", "DI")
        private val allRegisters = setOf("AX", "BX", "CX", "DX", "SI", "DI", "BP", "SP", "CS", "DS", "ES", "SS", "IP") + byteRegisters

        private val prefixInstructions = setOf("REP", "REPE", "REPZ", "REPNE", "REPNZ", "LOCK")
        private val stringInstructions = setOf(
            "MOVSB", "MOVSW", "CMPSB", "CMPSW", "SCASB", "SCASW", "LODSB", "LODSW", "STOSB", "STOSW"
        )
        private val jumpInstructions = setOf(
            "JMP", "JA", "JNBE", "JNC", "JAE", "JNB", "JC", "JB", "JNAE", "JBE", "JNA", "JCXZ", "JZ", "JE",
            "JG", "JNLE", "JGE", "JNL", "JL", "JNGE", "JLE", "JNG", "JNZ", "JNE", "JNO", "JNP", "JPO",
            "JNS", "JO", "JP", "JPE", "JS",
            "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ", "JCXZ"
        )
        private val labelTargetInstructions = jumpInstructions + "CALL"

        private val zeroOperand = setOf(
            "NOP", "WAIT", "HLT", "STC", "CLC", "CMC", "STD", "CLD", "STI", "CLI",
            "LAHF", "SAHF", "PUSHF", "POPF", "CBW", "CWD", "AAA", "AAS", "DAA", "DAS",
            "XLAT", "XLATB", "INTO", "IRET"
        ) + stringInstructions
        private val oneOperand = setOf(
            "INC", "DEC", "NEG", "NOT", "MUL", "DIV", "IMUL", "IDIV", "INT",
            "PUSH", "POP", "LEA", "LDS", "LES", "CALL"
        ) + jumpInstructions
        private val twoOperand = setOf(
            "MOV", "XCHG", "ADD", "ADC", "SUB", "SBB", "CMP", "AND", "OR", "XOR", "TEST", "IN", "OUT"
        )
        private val variableOperand = mapOf(
            "SHL" to (1..2), "SAL" to (1..2), "SHR" to (1..2), "SAR" to (1..2),
            "ROL" to (1..2), "ROR" to (1..2), "RCL" to (1..2), "RCR" to (1..2),
            "RET" to (0..1), "RETN" to (0..1), "RETF" to (0..1), "IRET" to (0..0),
            "AAM" to (0..1), "AAD" to (0..1), "ESC" to (0..2)
        )

        private val operandCounts: Map<String, IntRange> = buildMap {
            zeroOperand.forEach { put(it, 0..0) }
            oneOperand.forEach { put(it, 1..1) }
            twoOperand.forEach { put(it, 2..2) }
            putAll(variableOperand)
        }

        private val supportedInstructions = operandCounts.keys

        private val immediateDestination = setOf(
            "MOV", "XCHG", "ADD", "ADC", "SUB", "SBB", "AND", "OR", "XOR", "CMP", "TEST",
            "INC", "DEC", "NEG", "NOT", "SHL", "SAL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR",
            "POP", "LEA", "LDS", "LES"
        )
        private val memoryPairForbidden = setOf("MOV", "XCHG", "ADD", "ADC", "SUB", "SBB", "AND", "OR", "XOR", "CMP", "TEST")
        private val memoryOnlySource = setOf("LEA", "LDS", "LES")
        private val csDestinationForbidden = setOf(
            "MOV", "ADD", "ADC", "SUB", "SBB", "AND", "OR", "XOR", "CMP", "TEST", "INC", "DEC", "NEG", "NOT",
            "SHL", "SAL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR", "LEA", "LDS", "LES"
        )
        private val unwritableRegisters = setOf("CS", "IP")
        private val registerOnlySource = setOf("MUL", "DIV", "IMUL", "IDIV")

        fun validate(source: String): List<ValidationIssue> {
            val issues = mutableListOf<ValidationIssue>()
            val labels = linkedMapOf<String, Int>()
            val instructions = mutableListOf<Triple<Int, String, List<String>>>()
            val prefixes = mutableMapOf<Int, String>()

            source.lineSequence().forEachIndexed { index, raw ->
                var code = raw.substringBefore(';').trim().uppercase()
                if (code.isEmpty()) return@forEachIndexed
                val head = code.split(WHITESPACE, limit = 2)
                var first = head[0]
                val colon = first.indexOf(':')
                if (colon > 0) {
                    val label = first.substring(0, colon)
                    if (!labelPattern.matches(label)) {
                        issues += ValidationIssue(
                            index + 1,
                            "Invalid label '$label': labels must start with a letter or underscore and contain only letters, digits, _, ., $, or ?."
                        )
                    } else if (labels.containsKey(label)) {
                        issues += ValidationIssue(
                            index + 1,
                            "Duplicate label '$label'; it was already declared on line ${labels.getValue(label)}."
                        )
                    } else {
                        labels[label] = index + 1
                    }
                    code = code.substring(colon + 1).trim()
                    if (code.isEmpty()) return@forEachIndexed
                }
                var prefix: String? = null
                val tokens = code.split(WHITESPACE, limit = 2)
                if (tokens[0] in prefixInstructions) {
                    prefix = tokens[0]
                    code = tokens.getOrNull(1)?.trim().orEmpty()
                    if (code.isEmpty()) {
                        issues += ValidationIssue(index + 1, "Prefix ${prefix} must be followed by an instruction.")
                        return@forEachIndexed
                    }
                }
                val splitAt = code.indexOfFirst { it.isWhitespace() }
                val opcode = if (splitAt < 0) code else code.substring(0, splitAt)
                val tail = if (splitAt < 0) "" else code.substring(splitAt).trim()
                val args = operandsOf(tail)
                if (prefix != null) prefixes[index + 1] = prefix
                instructions += Triple(index + 1, opcode, args)
            }

            val unresolvedLabels = mutableListOf<Pair<Int, String>>()
            for ((line, opcode, args) in instructions) {
                val expected = operandCounts[opcode]
                if (expected == null) {
                    issues += ValidationIssue(line, "Unknown opcode '$opcode': this instruction is invalid or not implemented.")
                    continue
                }
                if (args.size !in expected) {
                    issues += ValidationIssue(
                        line,
                        if (expected.first == expected.last)
                            "Opcode $opcode requires exactly ${expected.first} operand(s), but ${args.size} were supplied."
                        else
                            "Opcode $opcode requires ${expected.first} to ${expected.last} operand(s), but ${args.size} were supplied."
                    )
                }
                val prefix = prefixes[line]
                if (prefix != null && opcode !in stringInstructions && prefix != "LOCK") {
                    issues += ValidationIssue(line, "Prefix $prefix is only valid with a string instruction (MOVS/CMPS/SCAS/LODS/STOS).")
                }
                if (args.any { it.isEmpty() }) {
                    issues += ValidationIssue(line, "Empty operand in '$opcode': remove the stray comma.")
                }
                if (opcode in labelTargetInstructions) {
                    val target = args.firstOrNull().orEmpty()
                    if (target.isNotEmpty()) {
                        if (!labelPattern.matches(target)) {
                            issues += ValidationIssue(line, "Invalid jump target '$target': a jump must reference a label.")
                        } else {
                            unresolvedLabels += line to target
                        }
                    }
                    args.drop(1).forEachIndexed { index, operand ->
                        if (operand.isNotEmpty() && !isValidOperand(operand)) {
                            issues += ValidationIssue(line, "Operand ${index + 2} '$operand' is not a valid register, immediate value, or memory reference.")
                        }
                    }
                } else {
                    args.forEachIndexed { index, operand ->
                        if (operand.isNotEmpty() && !isValidOperand(operand)) {
                            issues += ValidationIssue(line, "Operand ${index + 1} '$operand' is not a valid register, immediate value, or memory reference.")
                        }
                    }
                }
                if (opcode in registerOnlySource && args.firstOrNull()?.let(::isImmediateOperand) == true) {
                    issues += ValidationIssue(line, "$opcode does not accept an immediate value; use a register or memory operand.")
                }
                if (opcode in memoryPairForbidden && args.size >= 2 && isMemoryOperand(args[0]) && isMemoryOperand(args[1])) {
                    issues += ValidationIssue(line, "$opcode cannot use memory for both operands; at least one operand must be a register or an immediate value.")
                }
                if (opcode in memoryOnlySource && args.size >= 2 && !isMemoryOperand(args[1])) {
                    issues += ValidationIssue(line, "$opcode requires a memory operand as its source, for example $opcode AX, [BX].")
                }
                if (opcode in immediateDestination && args.firstOrNull()?.let(::isImmediateOperand) == true) {
                    issues += ValidationIssue(line, "The destination of $opcode cannot be an immediate value.")
                }
                if (opcode == "IN" && args.size == 2) {
                    val destination = args[0].uppercase()
                    if (destination != "AL" && destination != "AX") {
                        issues += ValidationIssue(line, "IN must target AL or AX, not '${args[0]}'.")
                    }
                }
                if (opcode == "OUT" && args.size == 2) {
                    val outRegister = args[1].uppercase()
                    if (outRegister != "AL" && outRegister != "AX") {
                        issues += ValidationIssue(line, "OUT must read from AL or AX, not '${args[1]}'.")
                    }
                }
                if (opcode in setOf("SHR", "SHL", "SAL", "SAR", "ROL", "ROR", "RCL", "RCR") && args.size == 2) {
                    val count = args[1].uppercase()
                    if (count != "CL" && !isImmediateLiteral(count)) {
                        issues += ValidationIssue(line, "The shift count of $opcode must be 1, CL, or an immediate value, not '${args[1]}'.")
                    }
                }
                if (opcode == "PUSH" && args.size == 1) {
                    val operand = args[0].uppercase()
                    if (operand in byteRegisters) {
                        issues += ValidationIssue(line, "PUSH requires a 16-bit operand; '${args[0]}' is a byte register.")
                    }
                }
                if (opcode == "POP" && args.firstOrNull()?.uppercase() == "CS") {
                    issues += ValidationIssue(line, "POP CS is not allowed on the 8086.")
                }
                if (opcode in csDestinationForbidden && args.firstOrNull()?.let { it.uppercase() in unwritableRegisters } == true) {
                    issues += ValidationIssue(line, "${args[0].uppercase()} cannot be used as a destination of $opcode on the 8086.")
                }
                if (opcode in setOf("PUSH", "POP") && args.firstOrNull()?.uppercase()?.contains("BYTE PTR") == true) {
                    issues += ValidationIssue(line, "$opcode needs a 16-bit operand; drop the BYTE PTR.")
                }
                if ((opcode == "XCHG") && args.size == 2 && args[1].uppercase() == "CS") {
                    issues += ValidationIssue(line, "XCHG cannot exchange CS on the 8086.")
                }
                if (opcode in setOf("AAM", "AAD") && args.firstOrNull()?.let(::isImmediateOperand) == false) {
                    issues += ValidationIssue(line, "$opcode accepts an immediate base only, not '${args[0]}'.")
                }
                if (opcode == "INT" && args.firstOrNull()?.let(::isImmediateOperand) == false) {
                    issues += ValidationIssue(line, "INT requires an immediate interrupt vector, not '${args[0]}'.")
                }
                if (opcode == "ESC" && args.isNotEmpty()) {
                    val spec = args[0].uppercase()
                    val value = spec.toIntOrNull() ?: spec.removeSuffix("H").toIntOrNull(16)
                    if (value == null || value !in 0..63) {
                        issues += ValidationIssue(line, "The ESC opcode must be an immediate value between 0 and 63.")
                    }
                }
            }
            unresolvedLabels.forEach { (line, target) ->
                if (target !in labels) issues += ValidationIssue(line, "Label '$target' is not defined; add '$target:' or correct the jump target.")
            }
            return issues.sortedBy { it.line }
        }

        private fun isValidOperand(raw: String): Boolean {
            val operand = raw.replace("BYTE PTR", "").replace("WORD PTR", "").replace("FAR PTR", "")
                .replace("NEAR PTR", "").trim()
            if (operand in allRegisters || isImmediateLiteral(operand)) return true
            val stripped = operand.replace(SEGMENT_PREFIX, "").trim()
            if (!isMemoryOperand(stripped)) return false
            val expression = stripped.substring(1, stripped.length - 1).replace("-", "+-")
            if (expression.isBlank()) return false
            return expression.split('+').map { it.trim() }.filter { it.isNotEmpty() }.all { term ->
                val value = term.removePrefix("-").trim()
                value in addressRegisters || isImmediateLiteral(value)
            }
        }

        private fun isMemoryOperand(operand: String): Boolean {
            val value = operand
                .replace("BYTE PTR", "").replace("WORD PTR", "").replace("FAR PTR", "").replace("NEAR PTR", "")
                .replace(SEGMENT_PREFIX, "").trim()
            return value.startsWith('[') && value.endsWith(']')
        }

        private fun isImmediateOperand(raw: String): Boolean {
            val operand = raw.replace("BYTE PTR", "").replace("WORD PTR", "").trim()
            return operand !in allRegisters && isImmediateLiteral(operand)
        }

        private fun isImmediateLiteral(raw: String): Boolean {
            val value = raw.trim().uppercase()
            return value.matches(Regex("-?\\d+")) ||
                value.matches(Regex("0X[0-9A-F]+")) ||
                value.matches(Regex("[0-9A-F]+H")) ||
                value.matches(Regex("[01]+B")) ||
                (value.startsWith("'") && value.endsWith("'") && value.length == 3)
        }
    }
}
