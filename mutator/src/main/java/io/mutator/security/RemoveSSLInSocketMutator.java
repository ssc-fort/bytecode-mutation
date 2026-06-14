package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum RemoveSSLInSocketMutator implements MethodMutatorFactory {

    REMOVE_SECURE_SOCKET_MUTATOR;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new RemoveSSLInSocketVisitor(context, methodVisitor, this);
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

class RemoveSSLInSocketVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    RemoveSSLInSocketVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKEVIRTUAL
                && owner.equals("javax/net/SocketFactory")
                && name.equals("createSocket")
                && desc.equals("(Ljava/lang/String;I)Ljava/net/Socket;")
                && !itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*Security* Replaced SSLSocketFactory.createSocket(host,port) with SocketFactory.getDefault().createSocket(host,port)");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP);   // pop port
                mv.visitInsn(Opcodes.SWAP);  // bring factory to top
                mv.visitInsn(Opcodes.POP);   // pop the SSLSocketFactory
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "javax/net/SocketFactory",
                        "getDefault", "()Ljavax/net/SocketFactory;", false);
                mv.visitInsn(Opcodes.SWAP);  // (factory, host) → (host, factory)
                mv.visitIntInsn(Opcodes.SIPUSH, 80);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "javax/net/SocketFactory",
                        "createSocket", "(Ljava/lang/String;I)Ljava/net/Socket;", false);
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
