package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UseBLOWFISHWithShortKeyVisitor extends AbstractVisitorSimplified {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    private final int seenFirstInstruction1BlowfishDecl            = 11;
    private final int seenFirstInstruction2KeygeneratorGetinstance = 12;
    private final int seenFirstInstruction3KeygeneratorStore       = 13;
    private final int seenSecondInstruction1KeygeneratorLoad       = 21;
    private final int seenSecondInstruction2Bipush                 = 22;

    private int bipushOperand;
    private int keyGeneratorLocalVariableIndex = 0;

    UseBLOWFISHWithShortKeyVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(context, writer, factory);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (state == seenSecondInstruction1KeygeneratorLoad
                && opcode == Opcodes.SIPUSH && operand >= 128) {
            state = seenSecondInstruction2Bipush;
            bipushOperand = operand;
            mv.visitIntInsn(opcode, operand);
            return;
        }
        visitInsn();
        mv.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (state == seenFirstInstruction2KeygeneratorGetinstance
                && opcode == Opcodes.ASTORE) {
            keyGeneratorLocalVariableIndex = var;
            state = seenFirstInstruction3KeygeneratorStore;
            mv.visitVarInsn(opcode, var);
            return;
        }
        if (state == seenFirstInstruction3KeygeneratorStore
                && opcode == Opcodes.ALOAD
                && var == keyGeneratorLocalVariableIndex) {
            state = seenSecondInstruction1KeygeneratorLoad;
            mv.visitVarInsn(opcode, var);
            return;
        }
        visitInsn();
        mv.visitVarInsn(opcode, var);
    }

    @Override
    public void visitLdcInsn(Object cst) {
        if (cst instanceof Integer && state == seenSecondInstruction1KeygeneratorLoad) {
            int pushed = (Integer) cst;
            if (pushed >= 128) {
                state = seenSecondInstruction2Bipush;
                bipushOperand = pushed;
                mv.visitLdcInsn(cst);
                return;
            }
        }
        if (cst instanceof String && state == seennothing && isBlowFish((String) cst)) {
            state = seenFirstInstruction1BlowfishDecl;
            mv.visitLdcInsn(cst);
            return;
        }
        visitInsn();
        mv.visitLdcInsn(cst);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKESTATIC
                && owner.equals("javax/crypto/KeyGenerator")
                && name.equals("getInstance")
                && desc.equals("(Ljava/lang/String;)Ljavax/crypto/KeyGenerator;")
                && !itf
                && state == seenFirstInstruction1BlowfishDecl) {
            state = seenFirstInstruction2KeygeneratorGetinstance;
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
            return;
        }
        if (opcode == Opcodes.INVOKEVIRTUAL
                && owner.equals("javax/crypto/KeyGenerator")
                && name.equals("init")
                && desc.equals("(I)V")
                && !itf
                && state == seenSecondInstruction2Bipush) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*NEW* replaced KeyGenerator.init(" + bipushOperand + ") with KeyGenerator.init(64)");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP);
                mv.visitIntInsn(Opcodes.BIPUSH, 64);
            }
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
            state = seennothing;
            return;
        }
        visitInsn();
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    private boolean isBlowFish(String argument) {
        try {
            Matcher m = Pattern.compile("[bB][lL][oO][wW][fF][iI][sS][hH]")
                    .matcher(argument.substring(0, 8));
            return m.find();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    @Override
    protected void visitInsn() {
        if (state >= seenFirstInstruction3KeygeneratorStore) {
            state = seenFirstInstruction3KeygeneratorStore;
        } else {
            state = seennothing;
        }
    }
}
