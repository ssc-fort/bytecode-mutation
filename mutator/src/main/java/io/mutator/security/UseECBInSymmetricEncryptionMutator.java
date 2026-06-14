package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum UseECBInSymmetricEncryptionMutator implements MethodMutatorFactory {

    USE_ECB_IN_SYMMETRIC_ENCRYPTION;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new UseECBInSymmetricEncryptionVisitor(context, methodVisitor, this);
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

class UseECBInSymmetricEncryptionVisitor extends AbstractVisitorSimplified {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;
    private final int                  seenPushString = 11;
    private       String               pushedString   = "";

    UseECBInSymmetricEncryptionVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(context, writer, factory);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitLdcInsn(Object cst) {
        if (cst instanceof String && state == seennothing && !containsECB((String) cst)) {
            state = seenPushString;
            pushedString = (String) cst;
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
                && owner.equals("javax/crypto/Cipher")
                && name.equals("getInstance")
                && desc.equals("(Ljava/lang/String;)Ljavax/crypto/Cipher;")
                && !itf
                && state == seenPushString) {

            int    slashIdx  = pushedString.indexOf('/');
            String toPush;
            if (slashIdx == -1) {
                toPush = pushedString + "/ECB/PKCS5Padding";
            } else {
                String rest        = pushedString.substring(slashIdx + 1);
                int    paddingIdx  = rest.indexOf('/');
                String beginning   = pushedString.substring(0, slashIdx);
                String paddingSuffix = rest.substring(paddingIdx);
                toPush = beginning + "/ECB" + paddingSuffix;
            }

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*SECURITY* Replaced Cipher.getInstance(" + pushedString
                            + ") with Cipher.getInstance(" + toPush + ")");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP);
                mv.visitLdcInsn(toPush);
            }
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
            state = seennothing;
            return;
        }
        visitInsn();
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    private boolean containsECB(String argument) {
        try {
            Matcher m = Pattern.compile("[/][eE][cC][bB][/]").matcher(argument);
            return m.find();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    @Override
    protected void visitInsn() {
        state = seennothing;
    }
}
