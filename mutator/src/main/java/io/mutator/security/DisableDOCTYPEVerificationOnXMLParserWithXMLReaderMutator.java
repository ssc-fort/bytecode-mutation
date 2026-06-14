package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum DisableDOCTYPEVerificationOnXMLParserWithXMLReaderMutator
        implements MethodMutatorFactory {

    XML_PARSER_VULNERABLE_TO_XXE_WITH_XMLREADER;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new DisableDOCTYPEVerificationOnXMLParserWithXMLReaderVisitor(
                context, methodVisitor, this);
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

class DisableDOCTYPEVerificationOnXMLParserWithXMLReaderVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    DisableDOCTYPEVerificationOnXMLParserWithXMLReaderVisitor(
            final MutationContext context, final MethodVisitor writer,
            final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKEINTERFACE
                && owner.equals("org/xml/sax/XMLReader")
                && name.equals("parse")
                && desc.equals("(Lorg/xml/sax/InputSource;)V")
                && itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*SECURITY* added XMLReader.setFeature("
                            + "http://apache.org/xml/features/disallow-doctype-decl, false)"
                            + " before this line");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.SWAP);
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn("http://apache.org/xml/features/disallow-doctype-decl");
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/xml/sax/XMLReader",
                        "setFeature", "(Ljava/lang/String;Z)V", true);
                mv.visitInsn(Opcodes.SWAP);
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
