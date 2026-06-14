package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UseDESForSymmetricEncryptionVisitor extends AbstractVisitorForBigPatterns {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    private final int seen1stInstruction1PushString        = 11;
    private final int seen1stInstruction2CipherGetInstance = 12;
    private final int seen1stInstruction3ASTORE            = 13;
    private final int seen2dInstruction1ALOAD              = 21;
    private final int seen2dInstruction2ICONST             = 22;
    private final int seen2dInstruction3ALOADKeyReference  = 23;

    private MutationIdentifier newId                     = null;
    private int                cipherLocalVariableIndex;
    private int                keyReferenceLocalVariableIndex;

    UseDESForSymmetricEncryptionVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(context, writer, factory);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitLdcInsn(Object cst) {
        if (cst instanceof String && state == seennothing
                && containsAESorBLOWFISHorRC2orRC5((String) cst)) {
            state = seen1stInstruction1PushString;
            mv.visitLdcInsn(cst);
            return;
        }
        visitInsn();
        mv.visitLdcInsn(cst);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (opcode == Opcodes.ASTORE && state == seen1stInstruction2CipherGetInstance) {
            state = seen1stInstruction3ASTORE;
            cipherLocalVariableIndex = var;
            mv.visitVarInsn(opcode, var);
            return;
        }
        if (opcode == Opcodes.ALOAD && state == seen1stInstruction3ASTORE
                && var == cipherLocalVariableIndex) {
            state = seen2dInstruction1ALOAD;
            mv.visitVarInsn(opcode, var);
            return;
        }
        if (opcode == Opcodes.ALOAD && state == seen2dInstruction2ICONST) {
            state = seen2dInstruction3ALOADKeyReference;
            keyReferenceLocalVariableIndex = var;
            mv.visitVarInsn(opcode, var);
            return;
        }
        visitInsn();
        mv.visitVarInsn(opcode, var);
    }

    @Override
    public void visitInsn(int opcode) {
        if (state == seen2dInstruction1ALOAD
                && (opcode == Opcodes.ICONST_1 || opcode == Opcodes.ICONST_2)) {
            state = seen2dInstruction2ICONST;
            mv.visitInsn(opcode);
            return;
        }
        visitInsn();
        mv.visitInsn(opcode);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        // Second block: Cipher.init(int, Key) — apply mutation if registered
        if (opcode == Opcodes.INVOKEVIRTUAL
                && owner.equals("javax/crypto/Cipher")
                && name.equals("init")
                && desc.equals("(ILjava/security/Key;)V")
                && !itf
                && state == seen2dInstruction3ALOADKeyReference) {

            if (newId != null && context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP); // remove old key
                mv.visitTypeInsn(Opcodes.NEW, "javax/crypto/spec/SecretKeySpec");
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ALOAD, keyReferenceLocalVariableIndex);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/security/Key",
                        "getEncoded", "()[B", true);
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitIntInsn(Opcodes.BIPUSH, 8);
                mv.visitLdcInsn("DES");
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/crypto/spec/SecretKeySpec",
                        "<init>", "([BIILjava/lang/String;)V", false);
            }
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
            state = seennothing;
            return;
        }
        // First block: Cipher.getInstance(String) — register mutation
        if (opcode == Opcodes.INVOKESTATIC
                && owner.equals("javax/crypto/Cipher")
                && name.equals("getInstance")
                && desc.equals("(Ljava/lang/String;)Ljavax/crypto/Cipher;")
                && !itf
                && state == seen1stInstruction1PushString) {

            newId = context.registerMutation(factory,
                    "*NEW* replaced Cipher.init(int,key) with Cipher.init(int,new SecretKeySpec(key.getEncoded(),0,8,\"DES\")");

            if (newId != null && context.shouldMutate(newId)) {
                state = seen1stInstruction2CipherGetInstance;
                mv.visitInsn(Opcodes.POP);
                mv.visitLdcInsn("DES/ECB/PKCS5Padding");
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
            state = seen1stInstruction2CipherGetInstance;
            return;
        }
        visitInsn();
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    private boolean containsAESorBLOWFISHorRC2orRC5(String argument) {
        try {
            String prefix3 = argument.substring(0, 3);
            if (Pattern.compile("([aA][eE][sS]|[rR][cC][2]|[rR][cC][5])")
                    .matcher(prefix3).find()) return true;
            String prefix8 = argument.substring(0, 8);
            return Pattern.compile("[bB][lL][oO][wW][fF][iI][sS][hH]")
                    .matcher(prefix8).find();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    @Override
    protected void visitInsn() {
        if (state >= seen1stInstruction3ASTORE) {
            state = seen1stInstruction3ASTORE;
        } else {
            state = seennothing;
        }
    }
}
