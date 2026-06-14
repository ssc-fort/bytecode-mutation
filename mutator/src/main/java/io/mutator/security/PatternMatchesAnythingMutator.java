package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;

public enum PatternMatchesAnythingMutator implements MethodMutatorFactory {

    PATTERN_MATCHES_ANYTHING_MUTATOR;

    @Override
    public MethodVisitor create(final MutationContext context,
            final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
        return new PatternMatchesAnythingVisitor(context, methodVisitor, this);
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

class PatternMatchesAnythingVisitor extends MethodVisitor {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    PatternMatchesAnythingVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(Opcodes.ASM9, writer);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        if (opcode == Opcodes.INVOKESTATIC
                && owner.equals("java/util/regex/Pattern")
                && name.equals("compile")
                && desc.equals("(Ljava/lang/String;)Ljava/util/regex/Pattern;")
                && !itf) {

            final MutationIdentifier newId = context.registerMutation(factory,
                    "*SECURITY* Replaced Pattern.compile(String) by Pattern.compile(\"([^¤]*)\")");

            if (context.shouldMutate(newId)) {
                mv.visitInsn(Opcodes.POP);
                mv.visitLdcInsn("([^¤]*)"); // ([^¤]*)
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }
}
