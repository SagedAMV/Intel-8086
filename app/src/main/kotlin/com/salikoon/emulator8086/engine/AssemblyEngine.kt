package com.salikoon.emulator8086.engine

/** A small, deterministic 16-bit 8086 teaching emulator. One call to [step] executes one source line. */
data class ExecutionSnapshot(
    val sourceLine: Int,
    val instruction: String,
    val registers: Map<String, Int>,
    val flags: Map<String, Int>,
    val memory: Map<Int, Int>,
    val output: String,
    val halted: Boolean,
    val finished: Boolean
)

data class ValidationIssue(val line: Int, val message: String)

class AssemblyEngine(val source: String, private val input: String = "") {
    private data class Line(val sourceLine: Int, val code: String)
    private val registers = linkedMapOf(
        "AX" to 0, "BX" to 0, "CX" to 0, "DX" to 0,
        "SI" to 0, "DI" to 0, "BP" to 0, "SP" to 0xFFFE,
        "CS" to 0, "DS" to 0, "ES" to 0, "SS" to 0, "IP" to 0
    )
    private val flagNames = listOf("CF", "PF", "AF", "ZF", "SF", "TF", "IF", "DF", "OF")
    private val flags = flagNames.associateWithTo(linkedMapOf()) { 0 }
    private val memory = linkedMapOf<Int, Int>()
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
            val labelEnd = text.indexOf(':')
            if (labelEnd >= 0) {
                val label = text.substring(0, labelEnd).trim()
                require(label.matches(Regex("[A-Z_.$?][A-Z0-9_.$?]*"))) { "Invalid label '$label' on line ${index + 1}" }
                require(!labels.containsKey(label)) { "Duplicate label '$label' on line ${index + 1}" }
                labels[label] = parsed.size
                text = text.substring(labelEnd + 1).trim()
                if (text.isEmpty()) return@forEachIndexed
            }
            parsed += Line(index + 1, text)
        }
        lines = parsed
        val unsupported = lines.firstOrNull { it.code.substringBefore(' ') !in supportedInstructions }
        require(unsupported == null) { "Unsupported instruction '${unsupported?.code?.substringBefore(' ')}' on line ${unsupported?.sourceLine}" }
        val missing = lines.flatMap { line ->
            val op = line.code.substringBefore(' ')
            val rest = line.code.substringAfter(' ', "")
            if (isJump(op)) operands(rest).lastOrNull()?.let(::listOf).orEmpty() else emptyList()
        }.filter { isJumpReference(it) && it !in labels }
        require(missing.isEmpty()) { "Unknown label: ${missing.first()}" }
    }

    fun reset() {
        registers.keys.toList().forEach { registers[it] = if (it == "SP") 0xFFFE else 0 }
        flags.keys.toList().forEach { flags[it] = 0 }
        memory.clear(); output.clear(); cursor = 0; inputCursor = 0; halted = false; done = lines.isEmpty(); currentLine = 1
    }

    fun snapshot(): ExecutionSnapshot = ExecutionSnapshot(
        sourceLine = if (done || lines.isEmpty()) currentLine else lines.getOrNull(cursor)?.sourceLine ?: currentLine,
        instruction = if (done || lines.isEmpty()) if (halted) "HLT" else "—" else lines.getOrNull(cursor)?.code ?: "—",
        registers = registers.toMutableMap().apply {
            byteAliases.forEach { (alias, baseAndHigh) ->
                val word = registers.getValue(baseAndHigh.first)
                put(alias, if (baseAndHigh.second) (word ushr 8) and 0xFF else word and 0xFF)
            }
        }.toMap(), flags = flags.toMap(), memory = memory.toMap(),
        output = output.toString(), halted = halted, finished = done
    )

    fun step(): ExecutionSnapshot {
        if (done) return snapshot()
        if (cursor !in lines.indices) { done = true; return snapshot() }
        val line = lines[cursor]
        currentLine = line.sourceLine
        val parts = line.code.split(Regex("\\s+"), limit = 2)
        val op = parts[0]
        val args = parts.getOrNull(1)?.let(::operands).orEmpty()
        var next = cursor + 1
        fun need(count: Int) { require(args.size == count) { "$op expects $count operand(s)" } }
        when (op) {
            "NOP" -> need(0)
            "HLT" -> { need(0); halted = true; done = true }
            "MOV" -> { need(2); write(args[0], read(args[1], widthOf(args[0], args[1]))) }
            "XCHG" -> { need(2); val a = read(args[0], widthOf(args[0], args[1])); val b = read(args[1], widthOf(args[0], args[1])); write(args[0], b); write(args[1], a) }
            "ADD", "SUB", "CMP", "AND", "OR", "XOR", "TEST" -> {
                need(2); val width = widthOf(args[0], args[1]); val a = read(args[0], width); val b = read(args[1], width); val mask = mask(width)
                val result = when (op) { "ADD" -> a + b; "SUB", "CMP" -> a - b; "AND", "TEST" -> a and b; "OR" -> a or b; else -> a xor b } and mask
                setResultFlags(result, width)
                if (op == "ADD") { flags["CF"] = if (a + b > mask) 1 else 0; flags["OF"] = if (((a xor result) and (b xor result) and signBit(width)) != 0) 1 else 0; write(args[0], result) }
                if (op == "SUB" || op == "CMP") { flags["CF"] = if (a < b) 1 else 0; flags["OF"] = if (((a xor b) and (a xor result) and signBit(width)) != 0) 1 else 0; if (op == "SUB") write(args[0], result) }
                if (op == "AND" || op == "OR" || op == "XOR") { flags["CF"] = 0; flags["OF"] = 0; write(args[0], result) }
            }
            "INC", "DEC", "NEG", "NOT" -> {
                need(1); val width = widthOf(args[0]); val a = read(args[0], width); val mask = mask(width)
                val result = when (op) { "INC" -> (a + 1) and mask; "DEC" -> (a - 1) and mask; "NEG" -> (-a) and mask; else -> a.inv() and mask }
                if (op != "NOT") setResultFlags(result, width)
                if (op == "NEG") flags["CF"] = if (a == 0) 0 else 1
                write(args[0], result)
            }
            "MUL" -> {
                need(1); val width = widthOf(args[0]); val factor = read(args[0], width)
                if (width == 8) { val product = read("AL", 8) * factor; write("AX", product); flags["CF"] = if (product > 255) 1 else 0 }
                else { val product = read("AX", 16).toLong() * factor; write("AX", product.toInt()); write("DX", (product ushr 16).toInt()); flags["CF"] = if ((product ushr 16) != 0L) 1 else 0 }
                flags["OF"] = flags["CF"] ?: 0
            }
            "DIV" -> {
                need(1); val width = widthOf(args[0]); val divisor = read(args[0], width); require(divisor != 0) { "Division by zero" }
                if (width == 8) { val value = read("AX", 16); require(value / divisor <= 255) { "Byte quotient overflow" }; write("AL", value / divisor); write("AH", value % divisor) }
                else { val value = (read("DX", 16).toLong() shl 16) or read("AX", 16).toLong(); require(value / divisor <= 0xFFFF) { "Word quotient overflow" }; write("AX", (value / divisor).toInt()); write("DX", (value % divisor).toInt()) }
            }
            "JMP", "JA", "JNBE", "JNC", "JAE", "JNB", "JC", "JB", "JNAE", "JBE", "JNA", "JCXZ", "JZ", "JE", "JG", "JNLE", "JGE", "JNL", "JL", "JNGE", "JLE", "JNZ", "JNE", "JNO", "JNP", "JPO", "JNS", "JO", "JP", "JPE", "JS" -> {
                need(1); if (jumpCondition(op)) next = labels[args[0]] ?: error("Unknown label ${args[0]}")
            }
            "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ" -> {
                need(1); val cx = (read("CX") - 1) and 0xFFFF; write("CX", cx)
                val cond = cx != 0 && when (op) { "LOOPE", "LOOPZ" -> flags["ZF"] == 1; "LOOPNE", "LOOPNZ" -> flags["ZF"] == 0; else -> true }
                if (cond) next = labels[args[0]] ?: error("Unknown label ${args[0]}")
            }
            "STC" -> { need(0); flags["CF"] = 1 }
            "CLC" -> { need(0); flags["CF"] = 0 }
            "CMC" -> { need(0); flags["CF"] = 1 - (flags["CF"] ?: 0) }
            "STD" -> { need(0); flags["DF"] = 1 }
            "CLD" -> { need(0); flags["DF"] = 0 }
            "CLI" -> { need(0); flags["IF"] = 0 }
            "INT" -> { need(1); require(read(args[0]) == 0x21) { "Only DOS interrupt 21h is supported" }; when (read("AH", 8)) { 1 -> { val ch = input.getOrNull(inputCursor++) ?: '?'; write("AL", ch.code) }; 2 -> { val ch = read("DL", 8).toChar(); output.append(ch); write("AL", ch.code) }; else -> error("INT 21h supports AH=1 (read) and AH=2 (write)") } }
            else -> error("Unsupported instruction: $op")
        }
        if (!done) { cursor = next; if (cursor !in lines.indices) { done = true; currentLine = (lines.lastOrNull()?.sourceLine ?: 0) + 1 } }
        return snapshot()
    }

    fun run(maxSteps: Int = 10_000): ExecutionSnapshot {
        var count = 0
        while (!done && count < maxSteps) { step(); count++ }
        require(done) { "Execution limit ($maxSteps steps) reached; possible infinite loop" }
        return snapshot()
    }

    private fun jumpCondition(op: String): Boolean {
        val cf = flags["CF"] == 1; val zf = flags["ZF"] == 1; val sf = flags["SF"] == 1; val of = flags["OF"] == 1; val pf = flags["PF"] == 1
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
            "JLE" -> zf || sf != of
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

    private fun setResultFlags(result: Int, width: Int) {
        val value = result and mask(width)
        flags["ZF"] = if (value == 0) 1 else 0
        flags["SF"] = if ((value and signBit(width)) != 0) 1 else 0
        flags["PF"] = if (Integer.bitCount(value and 0xFF) % 2 == 0) 1 else 0
    }
    private fun widthOf(a: String, b: String? = null): Int = when {
        a in byteRegisters || b in byteRegisters -> 8
        else -> 16
    }
    private fun mask(width: Int) = if (width == 8) 0xFF else 0xFFFF
    private fun signBit(width: Int) = if (width == 8) 0x80 else 0x8000
    private fun read(raw: String, width: Int = widthOf(raw)): Int {
        val operand = raw.uppercase().replace("BYTE PTR", "").replace("WORD PTR", "").trim()
        if (operand in registers) return registers.getValue(operand)
        val alias = byteAliases[operand]
        if (alias != null) { val base = registers.getValue(alias.first); return if (alias.second) (base ushr 8) and 0xFF else base and 0xFF }
        if (operand.startsWith("[") && operand.endsWith("]")) { val address = address(operand.substring(1, operand.length - 1)); val low = memory[address] ?: 0; return (low or ((memory[(address + 1) and 0xFFFF] ?: 0) shl 8)) and mask(width) }
        return parseNumber(operand)
    }
    private fun write(raw: String, value: Int) {
        val operand = raw.uppercase().replace("BYTE PTR", "").replace("WORD PTR", "").trim()
        val v = value and mask(widthOf(operand))
        if (operand in registers) { registers[operand] = value and 0xFFFF; return }
        val alias = byteAliases[operand]
        if (alias != null) { val old = registers.getValue(alias.first); registers[alias.first] = if (alias.second) ((old and 0x00FF) or ((value and 0xFF) shl 8)) else ((old and 0xFF00) or (value and 0xFF)); return }
        if (operand.startsWith("[") && operand.endsWith("]")) { val address = address(operand.substring(1, operand.length - 1)); memory[address] = value and 0xFF; memory[(address + 1) and 0xFFFF] = (value ushr 8) and 0xFF; return }
        error("Cannot write to immediate operand '$raw'")
    }
    private fun address(expr: String): Int {
        val normalized = expr.replace("-", "+-")
        return normalized.split('+').map { it.trim() }.filter { it.isNotEmpty() }.sumOf { token ->
            when (token) { "BX", "BP", "SI", "DI" -> read(token); else -> parseNumber(token) }
        } and 0xFFFF
    }
    private fun parseNumber(token: String): Int {
        val t = token.trim().uppercase()
        if (t.startsWith("'") && t.endsWith("'") && t.length >= 3) return t[1].code
        return when {
            t.startsWith("0X") -> t.substring(2).toInt(16)
            t.endsWith("H") -> t.dropLast(1).toInt(16)
            t.endsWith("B") && t.dropLast(1).all { it == '0' || it == '1' } -> t.dropLast(1).toInt(2)
            else -> t.toInt()
        }
    }
    private fun operands(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    private fun isJump(op: String) = op in jumpInstructions
    private fun isJumpReference(token: String) = token.matches(Regex("[A-Z_.$?][A-Z0-9_.$?]*")) && token !in registers && token !in byteRegisters

    companion object {
        private val byteRegisters = setOf("AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL")
        private val byteAliases = mapOf("AH" to ("AX" to true), "AL" to ("AX" to false), "BH" to ("BX" to true), "BL" to ("BX" to false), "CH" to ("CX" to true), "CL" to ("CX" to false), "DH" to ("DX" to true), "DL" to ("DX" to false))
        private val supportedInstructions = setOf("NOP", "HLT", "MOV", "XCHG", "ADD", "SUB", "CMP", "AND", "OR", "XOR", "TEST", "INC", "DEC", "NEG", "NOT", "MUL", "DIV", "INT", "STC", "CLC", "CMC", "STD", "CLD", "CLI", "JMP", "JA", "JNBE", "JNC", "JAE", "JNB", "JC", "JB", "JNAE", "JBE", "JNA", "JCXZ", "JZ", "JE", "JG", "JNLE", "JGE", "JNL", "JL", "JNGE", "JLE", "JNZ", "JNE", "JNO", "JNP", "JPO", "JNS", "JO", "JP", "JPE", "JS", "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ")
        private val jumpInstructions = setOf("JMP", "JA", "JNBE", "JNC", "JAE", "JNB", "JC", "JB", "JNAE", "JBE", "JNA", "JCXZ", "JZ", "JE", "JG", "JNLE", "JGE", "JNL", "JL", "JNGE", "JLE", "JNZ", "JNE", "JNO", "JNP", "JPO", "JNS", "JO", "JP", "JPE", "JS", "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ")
        fun validate(source: String): List<ValidationIssue> = try { AssemblyEngine(source); emptyList() } catch (e: Exception) { listOf(ValidationIssue(0, e.message ?: "Invalid assembly program")) }
    }
}
