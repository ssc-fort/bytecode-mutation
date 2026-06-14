package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum CookieSecureFlagDisableMutator implements MethodMutatorFactory {

    REMOVE_SECURE_FLAG_MUTATOR;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new CookieSecureFlagDisableVisitor(context, methodVisitor, this);
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

class CookieSecureFlagDisableVisitor extends AbstractVisitorSimplified {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;
    private final int                  seenlcst1 = 1;

    CookieSecureFlagDisableVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(context, writer, factory);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == Opcodes.ICONST_1 && state == seennothing) {
            state = seenlcst1;
            mv.visitInsn(opcode);
            return;
        }
        visitInsn();
        mv.visitInsn(opcode);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKEVIRTUAL
                && owner.equals("javax/servlet/http/Cookie")
                && name.equals("setSecure")
                && desc.equals("(Z)V")
                && !itf
                && state == seenlcst1) {

            final MutationIdentifier newId = context.registerMutation(
                    factory, "*SECURITY* removed Cookie.setSecure(true)");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP); // pop the boolean
                mv.visitInsn(Opcodes.POP); // pop the Cookie reference
                state = seennothing;
                return;
            }
        }
        visitInsn();
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    protected void visitInsn() {
        state = seennothing;
    }
}
