package io.mutator.security;

import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

class RunnableForSQLInjection implements Runnable {

    private final MethodVisitor mv;
    private final int           opcode;
    private final int           var;

    RunnableForSQLInjection(MethodVisitor mv, int opcode, int var) {
        this.mv     = mv;
        this.opcode = opcode;
        this.var    = var;
    }

    @Override
    public void run() {
        mv.visitVarInsn(opcode, var);
    }
}
