package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum UseMD5ForEncryptionWithBouncyCastleMutator implements MethodMutatorFactory {

    USE_MD5_FOR_ENCRYPTION_WITH_BOUNCY_CASTLE;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new UseMD5ForEncryptionWithBouncyCastleVisitor(context, methodVisitor, this);
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

class UseMD5ForEncryptionWithBouncyCastleVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    UseMD5ForEncryptionWithBouncyCastleVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKESPECIAL
                && owner.equals("org/bouncycastle/crypto/generators/PKCS5S2ParametersGenerator")
                && name.equals("<init>")
                && desc.equals("(Lorg/bouncycastle/crypto/Digest;)V")
                && !itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*Security* Replaced digest in new PKCS5S2ParametersGenerator(Digest) with MD5Digest");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP); // discard original Digest
                mv.visitTypeInsn(Opcodes.NEW, "org/bouncycastle/crypto/digests/MD5Digest");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "org/bouncycastle/crypto/digests/MD5Digest",
                        "<init>", "()V", false);
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
