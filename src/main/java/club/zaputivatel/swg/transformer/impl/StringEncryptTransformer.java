package club.zaputivatel.swg.transformer.impl;

import club.zaputivatel.swg.transformer.Transformer;
import org.mapleir.asm.MethodNode;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Безумная Идея Двух Обычных Парней
 * dynamic constant
 * Uses jvm11+ dynamic constants - pseudocode provided - see https://www.benf.org/other/cfr/dynamic-constants.html
 * CFR dynamic constant pseudocode
 * As of now, there's no way (Someone correct me!?) to get javac to explicitly use dynamic constants, i.e. there's no java language support.
 * //dynamic constant comment
 * So we currently HINT as to use of dynamic constants with
 * Explicitly showing the result type of the dynamic constant (to handle duplicate methods in obfuscation, where the jvm has no problem disambiguating)
 * A warning at the top of the classfile that dynamic constants have been emitted.
 *
 * ток CFR воссоздает псево с констант динамиком так что + крашер
 */

public class StringEncryptTransformer implements Transformer {
    private final Map<String, ClassState> classStates = new HashMap<>();

    @Override
    public void transform(ControlFlowGraph cfg, MethodNode mapleMethod) {
        org.objectweb.asm.tree.MethodNode method = mapleMethod.node;
        ClassNode owner = mapleMethod.owner.node;

        if (method.instructions == null || method.instructions.size() == 0) return;
        ClassState state = ensureClassState(owner);
        if (method.name.equals(state.bootstrapName)) return;

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String value) {
                if (value.isEmpty()) continue;

                int key = ThreadLocalRandom.current().nextInt(100, 0x7FFF);
                String runtimeName = owner.name.replace('/', '.');

                String encrypted = morph(value, key, runtimeName);

                ConstantDynamic condy = new ConstantDynamic(
                        "c_" + randomIdent(5),
                        "Ljava/lang/String;",
                        state.bootstrapHandle,
                        encrypted,
                        key
                );

                method.instructions.set(insn, new LdcInsnNode(condy));
            }
        }
    }

    private String morph(String input, int key, String className) {
        int salt = className.hashCode() ^ key;
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int c = chars[i];
            c = (c + (salt + i)) ^ 0xFFFF;
            chars[i] = (char) (c & 0xFFFF);
        }
        return new StringBuilder(new String(chars)).reverse().toString();
    }

    private ClassState ensureClassState(ClassNode owner) {
        return classStates.computeIfAbsent(owner.name, k -> {
            if (owner.version < Opcodes.V11) owner.version = Opcodes.V11;
            ClassState state = new ClassState();
            state.bootstrapName = "d_" + randomIdent(8);
            state.bootstrapDesc = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;I)Ljava/lang/String;";
            injectBootstrap(owner, state);
            state.bootstrapHandle = new Handle(Opcodes.H_INVOKESTATIC, owner.name, state.bootstrapName, state.bootstrapDesc, false);
            return state;
        });
    }

    private void injectBootstrap(ClassNode owner, ClassState state) {
        org.objectweb.asm.tree.MethodNode md = new org.objectweb.asm.tree.MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                state.bootstrapName, state.bootstrapDesc, null, null
        );

        InsnList il = md.instructions;
        LabelNode loop = new LabelNode();
        LabelNode end = new LabelNode();

        /**
         INVOKEVIRTUAL java/lang/String.length()I
         LDC 948913785
         ICONST_M1
         IXOR
         ICONST_M1
         IXOR
         LDC 948913684
         ISUB
         IF_ICMPGE M
         I:
         LINE I 36
         NEW java/lang/StringBuilder
         DUP
         INVOKESPECIAL java/lang/StringBuilder.<init>()V
         ALOAD 0
         INVOKEVIRTUAL java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;
       -->  LDC c_HBQNd : Ljava/lang/String; pack/tests/bench/Calc.provider_rUZMixue(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;I)Ljava/lang/String; (6) [复夥, 2796]
         INVOKEVIRTUAL java/lang/StringBuilder.append(Ljava/lang/String;)Ljava/lang/StringBuilder;
         INVOKEVIRTUAL java/lang/StringBuilder.toString()Ljava/lang/String;
         ASTORE 0
         LDC 119183350
         LDC 407678422
         IXOR
         ICONST_2
         LDC 119183350
         LDC 407678422
         IAND
         IMUL
         IADD
         LDC 407678422
         ISUB
         LDC 1151230837
         LDC -1269496572
         IADD
         IADD
         NOP
         DUP
         POP
         */

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false));
        il.add(new VarInsnNode(Opcodes.ILOAD, 4));
        il.add(new InsnNode(Opcodes.IXOR));
        il.add(new VarInsnNode(Opcodes.ISTORE, 5));
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "reverse", "()Ljava/lang/StringBuilder;", false));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 6));
        il.add(new VarInsnNode(Opcodes.ALOAD, 6));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false));
        il.add(new VarInsnNode(Opcodes.ASTORE, 7));
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new VarInsnNode(Opcodes.ISTORE, 8));
        il.add(loop);
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new InsnNode(Opcodes.ARRAYLENGTH));
        il.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.CALOAD));
        il.add(new IntInsnNode(Opcodes.SIPUSH, 0xFFFF));
        il.add(new InsnNode(Opcodes.IXOR));
        il.add(new VarInsnNode(Opcodes.ILOAD, 5));
        il.add(new VarInsnNode(Opcodes.ILOAD, 8));
        il.add(new InsnNode(Opcodes.IADD));
        il.add(new InsnNode(Opcodes.ISUB));
        il.add(new InsnNode(Opcodes.I2C));
        il.add(new InsnNode(Opcodes.CASTORE));
        il.add(new IincInsnNode(8, 1));
        il.add(new JumpInsnNode(Opcodes.GOTO, loop));
        il.add(end);
        il.add(new TypeInsnNode(Opcodes.NEW, "java/lang/String"));
        il.add(new InsnNode(Opcodes.DUP));
        il.add(new VarInsnNode(Opcodes.ALOAD, 7));
        il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        md.maxStack = 5;
        md.maxLocals = 10;
        owner.methods.add(md);
    }

    private String randomIdent(int len) {
        String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<len; i++) sb.append(abc.charAt(ThreadLocalRandom.current().nextInt(abc.length())));
        return sb.toString();
    }

    private static class ClassState {
        String bootstrapName;
        String bootstrapDesc;
        Handle bootstrapHandle;
    }
}