package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum UseMD5ForEncryptionJAVAStandardMutator implements MethodMutatorFactory {

    USE_MD5_FOR_ENCRYPTION_JAVA_STANDARD_MUTATOR;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new UseMD5ForEncryptionJAVAStandardVisitor(context, methodVisitor, this);
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

class UseMD5ForEncryptionJAVAStandardVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    UseMD5ForEncryptionJAVAStandardVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKESTATIC
                && owner.equals("java/security/MessageDigest")
                && name.equals("getInstance")
                && desc.equals("(Ljava/lang/String;)Ljava/security/MessageDigest;")
                && !itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*SECURITY* Replaced MessageDigest.getInstance(String) with MessageDigest.getInstance(\"MD5\")");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP);   // pop original algorithm string
                mv.visitLdcInsn("MD5");
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
