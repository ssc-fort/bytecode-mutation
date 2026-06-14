package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum TrustUserInputInFilesRetrievementMutator implements MethodMutatorFactory {

    TRUST_USER_INPUT_IN_FILES_RETRIEVEMENT;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new TrustUserInputInFilesRetrievementVisitor(context, methodVisitor, this);
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

class TrustUserInputInFilesRetrievementVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    TrustUserInputInFilesRetrievementVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKESTATIC
                && owner.equals("org/apache/commons/io/FilenameUtils")
                && name.equals("getName")
                && desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
                && !itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*Security* removed FilenameUtils.getName(String)");

            if (context.shouldMutate(newId)) {
                // remove the call entirely — the raw (untrusted) input stays on the stack
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
