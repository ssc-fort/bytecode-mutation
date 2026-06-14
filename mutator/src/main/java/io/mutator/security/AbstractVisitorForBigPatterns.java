package io.mutator.security;

import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.Handle;
import org.pitest.reloc.asm.Label;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

/**
 * Like {@link AbstractVisitorSimplified} but also resets state on {@code visitFrame}
 * and {@code visitLabel}, which is needed for multi-instruction pattern matching
 * that must not be confused by control-flow boundaries.
 */
public abstract class AbstractVisitorForBigPatterns extends MethodVisitor {

    protected final int seennothing = 0;
    protected int state = seennothing;

    AbstractVisitorForBigPatterns(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        visitInsn();
        mv.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        visitInsn();
        mv.visitIincInsn(var, increment);
    }

    @Override
    public void visitInsn(int opcode) {
        visitInsn();
        mv.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        visitInsn();
        mv.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm,
            Object... bsmArgs) {
        visitInsn();
        mv.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        visitInsn();
        mv.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLdcInsn(Object cst) {
        visitInsn();
        mv.visitLdcInsn(cst);
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        visitInsn();
        mv.visitLookupSwitchInsn(dflt, keys, labels);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        visitInsn();
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        visitInsn();
        mv.visitMultiANewArrayInsn(desc, dims);
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt,
            Label... labels) {
        visitInsn();
        mv.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        visitInsn();
        mv.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        visitInsn();
        mv.visitVarInsn(opcode, var);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        visitInsn();
        mv.visitMaxs(maxStack, maxLocals);
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local,
            int nStack, Object[] stack) {
        visitInsn();
        mv.visitFrame(type, nLocal, local, nStack, stack);
    }

    @Override
    public void visitLabel(Label label) {
        visitInsn();
        mv.visitLabel(label);
    }

    /** Called before each instruction to let subclasses update/reset state. */
    protected abstract void visitInsn();
}
