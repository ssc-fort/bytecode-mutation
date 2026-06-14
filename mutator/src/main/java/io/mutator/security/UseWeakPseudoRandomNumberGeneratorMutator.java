package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum UseWeakPseudoRandomNumberGeneratorMutator implements MethodMutatorFactory {

    USE_WEAK_PSEUDO_RANDOM_NUMBER_GENERATOR_MUTATOR;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new UseWeakPseudoRandomNumberGeneratorVisitor(context, methodVisitor, this);
    }

    @Override
    public String getGloballyUniqueId() {
        return this.getClass().getName();
    }

    @Override
    public String getName() {
        return name();
    }
}

class UseWeakPseudoRandomNumberGeneratorVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    UseWeakPseudoRandomNumberGeneratorVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKEVIRTUAL
                && owner.equals("java/security/SecureRandom")
                && name.equals("nextBytes")
                && desc.equals("([B)V")
                && !itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*SECURITY* Replaced SecureRandom.nextBytes(byte[]) with Random.nextBytes(byte[])");

            if (context.shouldMutate(newId)) {
                mv.visitTypeInsn(Opcodes.NEW, "java/util/Random");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Random",
                        "<init>", "()V", false);
                mv.visitInsn(Opcodes.SWAP); // bring byte[] back to top
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Random",
                        "nextBytes", "([B)V", false);
                mv.visitInsn(Opcodes.POP); // discard the SecureRandom reference
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
