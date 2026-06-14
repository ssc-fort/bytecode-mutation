package io.mutator.security;

import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.reloc.asm.MethodVisitor;
import org.pitest.reloc.asm.Opcodes;
import org.pitest.reloc.asm.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLInjectionOKWithJDBCVisitor extends AbstractVisitorSimplified {

    private final MethodMutatorFactory factory;
    private final MutationContext      context;

    private final int seen1stInstruction1ALOADConnection                  = 11;
    private final int seen1stInstruction2LDCINSNQuerry                    = 12;
    private final int seen1stInstruction3INVOKEINTERFACEPreparedStatement = 13;
    private final int seen1stInstruction4ASTOREPreparedStatement          = 14;
    private final int seen2dInstruction1ALOADPreparedStatement            = 21;
    private final int seen2dInstruction2ICONST                            = 22;
    private final int seen2dInstructionLOAD                               = 23;

    private int          connectionLocalVariableIndex    = 0;
    private int          preparedStatementVariableIndex  = 0;
    private String       querry                          = "";
    private int          numberOfVariablesToSet          = 0;
    private List<Runnable> listOfMethods                 = new ArrayList<>();
    private List<Type>   listOfTypes                     = new ArrayList<>();
    private String       connectionDescription           = "";

    SQLInjectionOKWithJDBCVisitor(final MutationContext context,
            final MethodVisitor writer, final MethodMutatorFactory factory) {
        super(context, writer, factory);
        this.factory = factory;
        this.context = context;
    }

    @Override
    public void visitInsn(int opcode) {
        if (state == seen2dInstruction1ALOADPreparedStatement
                && opcode >= Opcodes.ICONST_1 && opcode <= Opcodes.ICONST_5) {
            state = seen2dInstruction2ICONST;
            mv.visitInsn(opcode);
            return;
        }
        visitInsn();
        mv.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (state == seen2dInstruction1ALOADPreparedStatement
                && opcode == Opcodes.BIPUSH
                && operand == numberOfVariablesToSet + 1) {
            state = seen2dInstruction2ICONST;
            mv.visitIntInsn(opcode, operand);
            return;
        }
        visitInsn();
        mv.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (state == seennothing && opcode == Opcodes.ALOAD) {
            connectionLocalVariableIndex = var;
            state = seen1stInstruction1ALOADConnection;
            mv.visitVarInsn(opcode, var);
            return;
        }
        if (state == seen1stInstruction3INVOKEINTERFACEPreparedStatement
                && opcode == Opcodes.ASTORE) {
            preparedStatementVariableIndex = var;
            state = seen1stInstruction4ASTOREPreparedStatement;
            mv.visitVarInsn(opcode, var);
            return;
        }
        if (state == seen1stInstruction4ASTOREPreparedStatement
                && opcode == Opcodes.ALOAD
                && var == preparedStatementVariableIndex) {
            state = seen2dInstruction1ALOADPreparedStatement;
            mv.visitVarInsn(opcode, var);
            return;
        }
        if (state == seen2dInstruction2ICONST
                && opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) {
            final int opcodef = opcode;
            final int varf    = var;
            Runnable methodCall = (opcode == Opcodes.ALOAD)
                    ? new RunnableForSQLInjection(mv, opcodef, varf)
                    : () -> mv.visitVarInsn(opcodef, varf);
            listOfMethods.add(methodCall);
            mv.visitVarInsn(opcode, var);
            state = seen2dInstructionLOAD;
            return;
        }
        visitInsn();
        mv.visitVarInsn(opcode, var);
    }

    @Override
    public void visitLdcInsn(Object cst) {
        if (cst instanceof String && state == seen1stInstruction1ALOADConnection
                && containsValues((String) cst)) {
            state = seen1stInstruction2LDCINSNQuerry;
            querry = (String) cst;
            mv.visitLdcInsn(cst);
            return;
        }
        if (state == seen2dInstruction1ALOADPreparedStatement && cst instanceof Integer) {
            int operand = (Integer) cst;
            if (operand == numberOfVariablesToSet + 1) {
                state = seen2dInstruction2ICONST;
                mv.visitLdcInsn(cst);
                return;
            }
        }
        if (state == seen2dInstruction2ICONST) {
            final Object cstf = cst;
            listOfMethods.add(() -> mv.visitLdcInsn(cstf));
            mv.visitLdcInsn(cst);
            state = seen2dInstructionLOAD;
            return;
        }
        visitInsn();
        mv.visitLdcInsn(cst);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name,
            String desc, boolean itf) {
        // set* methods — track argument types
        if (opcode == Opcodes.INVOKEINTERFACE
                && owner.equals("java/sql/PreparedStatement")
                && itf && state == seen2dInstructionLOAD) {
            Type t = null;
            if      (name.equals("setFloat")   && desc.equals("(IF)V"))                   t = Type.FLOAT_TYPE;
            else if (name.equals("setDouble")  && desc.equals("(ID)V"))                   t = Type.DOUBLE_TYPE;
            else if (name.equals("setBoolean") && desc.equals("(IZ)V"))                   t = Type.BOOLEAN_TYPE;
            else if (name.equals("setLong")    && desc.equals("(IJ)V"))                   t = Type.LONG_TYPE;
            else if (name.equals("setString")  && desc.equals("(ILjava/lang/String;)V"))  t = Type.getType(String.class);
            else if (name.equals("setInt")     && desc.equals("(II)V"))                   t = Type.INT_TYPE;

            if (t != null) {
                numberOfVariablesToSet++;
                state = seen1stInstruction4ASTOREPreparedStatement;
                listOfTypes.add(t);
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
        }

        // prepareStatement — first block
        if (opcode == Opcodes.INVOKEINTERFACE
                && (owner.equals("com/mysql/jdbc/Connection") || owner.equals("java/sql/Connection"))
                && name.equals("prepareStatement")
                && desc.equals("(Ljava/lang/String;)Ljava/sql/PreparedStatement;")
                && itf
                && state == seen1stInstruction2LDCINSNQuerry) {
            state = seen1stInstruction3INVOKEINTERFACEPreparedStatement;
            connectionDescription = owner;
            mv.visitMethodInsn(opcode, owner, name, desc, itf);
            return;
        }

        // execute/executeQuery/executeUpdate — apply mutation if conditions met
        if (opcode == Opcodes.INVOKEINTERFACE
                && owner.equals("java/sql/PreparedStatement")
                && itf
                && state == seen2dInstruction1ALOADPreparedStatement
                && (name.equals("executeUpdate") || name.equals("executeQuery") || name.equals("execute"))) {

            if (numberOfVariablesToSet == numberOfVariablesToSetInQuerry(querry)
                    && aStringIsPushedByReference(listOfMethods, listOfTypes)) {

                String toDisplay = listOfTypes.stream()
                        .map(Type::toString).reduce("", String::concat);

                final MutationIdentifier newId = context.registerMutation(factory,
                        "*SECURITY* replaced PreparedStatement." + name + "()"
                                + " with Statement." + name + "() — arg types: " + toDisplay);

                if (context.shouldMutate(newId)) {
                    createStatementThenPrepareStackThenExecute(name, desc);
                } else {
                    mv.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            } else {
                mv.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            listOfMethods          = new ArrayList<>();
            listOfTypes            = new ArrayList<>();
            numberOfVariablesToSet = 0;
            state                  = seennothing;
            return;
        }

        visitInsn();
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    // -----------------------------------------------------------------------

    private void createStatementThenPrepareStackThenExecute(String method, String desc) {
        mv.visitInsn(Opcodes.POP); // remove PreparedStatement ref
        mv.visitVarInsn(Opcodes.ALOAD, connectionLocalVariableIndex);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, connectionDescription,
                "createStatement", "()Ljava/sql/Statement;", true);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder",
                "<init>", "(Ljava/lang/String;)V", false);
        appendQuerryInString(querry, listOfMethods, listOfTypes);
        String addStringDesc = "(Ljava/lang/String;)" + desc.substring(2);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/sql/Statement",
                method, addStringDesc, true);
    }

    private void appendQuerryInString(String querry, List<Runnable> methods, List<Type> types) {
        String[] parts = querry.split("[?]", -1);
        int index = 0;
        while (index < numberOfVariablesToSet) {
            if (index < parts.length)
                appendString(parts[index]);

            Type t = types.get(index);
            if (t.equals(Type.getType(String.class)))
                appendString("'");

            methods.get(index).run();

            String appendDesc;
            if      (t.equals(Type.FLOAT_TYPE))                appendDesc = "(F)Ljava/lang/StringBuilder;";
            else if (t.equals(Type.DOUBLE_TYPE))               appendDesc = "(D)Ljava/lang/StringBuilder;";
            else if (t.equals(Type.BOOLEAN_TYPE))              appendDesc = "(Z)Ljava/lang/StringBuilder;";
            else if (t.equals(Type.LONG_TYPE))                 appendDesc = "(J)Ljava/lang/StringBuilder;";
            else if (t.equals(Type.getType(String.class)))     appendDesc = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
            else                                               appendDesc = "(I)Ljava/lang/StringBuilder;";

            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                    "append", appendDesc, false);

            if (t.equals(Type.getType(String.class)))
                appendString("'");

            index++;
        }
        while (index < parts.length) {
            appendString(parts[index]);
            index++;
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "toString", "()Ljava/lang/String;", false);
    }

    private void appendString(String s) {
        mv.visitLdcInsn(s);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder",
                "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
    }

    private boolean containsValues(String argument) {
        try {
            return Pattern.compile("[?]").matcher(argument).find();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    private int numberOfVariablesToSetInQuerry(String q) {
        int count = 0, last = q.lastIndexOf('?');
        while (last != -1) { count++; q = q.substring(0, last); last = q.lastIndexOf('?'); }
        return count;
    }

    public boolean aStringIsPushedByReference(List<Runnable> pushed, List<Type> types) {
        if (pushed.size() != types.size()) return false;
        Iterator<Runnable> ri = pushed.iterator();
        Iterator<Type>     ti = types.iterator();
        while (ri.hasNext()) {
            Type    t = ti.next();
            Runnable r = ri.next();
            if (t.equals(Type.getType(String.class)) && r instanceof RunnableForSQLInjection)
                return true;
        }
        return false;
    }

    @Override
    protected void visitInsn() {
        listOfMethods          = new ArrayList<>();
        listOfTypes            = new ArrayList<>();
        state                  = seennothing;
    }
}
