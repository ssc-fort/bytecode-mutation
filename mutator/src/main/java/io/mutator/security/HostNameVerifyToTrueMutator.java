package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum HostNameVerifyToTrueMutator implements MethodMutatorFactory {

    HOST_NAME_VERIFY_TO_TRUE;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new HostNameVerifyToTrueVisitor(context, methodVisitor, this);
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

class HostNameVerifyToTrueVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    HostNameVerifyToTrueVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKEINTERFACE
                && owner.equals("javax/net/ssl/HostnameVerifier")
                && name.equals("verify")
                && desc.equals("(Ljava/lang/String;Ljavax/net/ssl/SSLSession;)Z")
                && itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*SECURITY* Replaced HostNameVerifier.verify(String, SSLSession) with \"true\"");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP); // pop SSLSession
                mv.visitInsn(Opcodes.POP); // pop String (hostname)
                mv.visitInsn(Opcodes.ICONST_1); // push true
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
