package com.salikoon.emulator8086

import com.salikoon.emulator8086.engine.AssemblyEngine
import org.junit.Test

class OriginalTemplatesTest {
    @Test fun everyBundledTemplateRunsWithinLimit() {
        val examples = listOf(
        "Area of Rectangle" to """
mov ax,6 ; length of rectangle
mov bx,5 ; width of rectangle
mul bx; calculate area=ax*bx
mov cx,6; now we will calculate the perimeter in sha Allah
mov dx,5; 
add dx,dx; dx=dx+dx
add cx,cx; cx=cx+cx
add cx,dx; perimeter=cx+dx

""",
        "Average of three numbers" to """
MOV AX,3
MOV BX,6
MOV CX,5
ADD AX,BX
ADD AX,CX
MOV DX,0
MOV BX,3
DIV BX
""",
        "Bitwise Instructions Demo" to """
MOV AX,6
MOV BX,4
AND AX,BX
XOR AX,BX
TEST AX,BX
OR AX,BX
NOT BX
NOP
""",
        "Jumps" to """
MOV AX,4
CMP AX,1
JE ONE
CMP AX,2
JE TWO
CMP AX,3
JE THREE
CMP AX,4
JE FOUR
ONE: MOV DX,1
JMP END
TWO: MOV DX,2
JMP END
THREE: MOV DX,3
JMP END
FOUR: MOV DX,4
END: NOP
""",
        "Factorial Calculation" to """
NOP; TASK: calculate 4!=24 in AX
MOV CX,4
MOV AX,1
MULTIPLY:MUL CL
LOOP MULTIPLY
""",
        "I/O Demo" to """
MOV AH,1
INT 21H
MOV AH,2
MOV DL,35H
INT 21H
NOP
""",
        "Flag Instructions Demo" to """
STC
CLC
CMC
CLI
STD
CLD
HLT
""",
        "String Copy (REP MOVSB)" to """
MOV BYTE PTR [200H],48H ; 'H'
MOV BYTE PTR [201H],49H ; 'I'
MOV BYTE PTR [202H],21H ; '!'
MOV SI,200H ; source
MOV DI,300H ; destination
MOV CX,3 ; byte count
CLD ; forward direction
REP MOVSB ; copy CX bytes
HLT
""",
        "Subroutine & Stack (CALL/RET)" to """
MOV AX,6
CALL SQUARE ; AX = AX*AX
HLT
SQUARE: PUSH BX ; save BX
MUL AX ; DX:AX = AX*AX
POP BX ; restore BX
RET ; return to caller
""",
        "BCD Addition (DAA)" to """
MOV AL,47H ; packed BCD 47
MOV BL,38H ; packed BCD 38
ADD AL,BL ; 47+38 = 7FH
DAA ; adjust to BCD 85
HLT
""",
        "Bit Rotation & Shifts" to """
MOV AL,81H
SHR AL,1 ; logical shift -> 40H, CF=1
MOV BL,81H
SAR BL,1 ; arithmetic shift -> C0H
MOV CL,4
ROL AL,CL ; rotate by CL
RCR AL,1 ; rotate through carry
HLT
""",
        )
        examples.forEach { (name, code) ->
            try { AssemblyEngine(code).run(3_000) }
            catch (e: Exception) { throw AssertionError("Template '$name' did not run: ${e.message}", e) }
        }
    }
}
