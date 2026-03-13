package club.zaputivatel.swg.transformer.impl;


import club.zaputivatel.swg.transformer.Transformer;
import org.mapleir.asm.MethodNode;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.security.SecureRandom;
import java.util.*;

public class MutateInstrTransformer implements Transformer, Opcodes {

    private static final SecureRandom rand = new SecureRandom();

    private static final int OP_LOAD = 10;
    private static final int OP_JUNK = 20;
    private static final int OP_FINAL = 30;

    @Override
    public void transform(ControlFlowGraph cfg, MethodNode mapleMethod) {
        org.objectweb.asm.tree.MethodNode method = mapleMethod.node;
        if (method.instructions == null || method.instructions.size() == 0) return;

        float chance = (method.instructions.size() > 500) ? 0.15f : 0.45f;

        List<AbstractInsnNode> toVirtualize = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (isIntegerInsn(insn)) {
                int val = getIntegerValue(insn);

                if (val >= -20 && val <= 20) continue;

                if (rand.nextFloat() > chance) continue;

                toVirtualize.add(insn);
            }
        }

        for (AbstractInsnNode insn : toVirtualize) {
            if (method.instructions.size() > 2000) break;

            int value = getIntegerValue(insn);
            int vReg = method.maxLocals++;
            int vOp = method.maxLocals++;

            method.instructions.insertBefore(insn, runner(value, vReg, vOp));
            method.instructions.remove(insn);
        }
    }

    private InsnList runner(int value, int vReg, int vOp) {
        InsnList list = new InsnList();
        LabelNode end = new LabelNode();
        LabelNode lLoad = new LabelNode();
        LabelNode lJunk = new LabelNode();
        LabelNode lFinal = new LabelNode();

        list.add(new LdcInsnNode(OP_FINAL));
        list.add(new VarInsnNode(ISTORE, vOp));

        TreeMap<Integer, LabelNode> cases = new TreeMap<>();
        cases.put(OP_LOAD, lLoad);
        cases.put(OP_JUNK, lJunk);
        cases.put(OP_FINAL, lFinal);

        int[] keys = new int[3];
        LabelNode[] labels = new LabelNode[3];
        int i = 0;
        for (Map.Entry<Integer, LabelNode> entry : cases.entrySet()) {
            keys[i] = entry.getKey();
            labels[i] = entry.getValue();
            keys[i] = entry.getKey();
            labels[i] = entry.getValue();
            i++;
        }

        list.add(new VarInsnNode(ILOAD, vOp));
        list.add(new LookupSwitchInsnNode(lFinal, keys, labels));

        list.add(lLoad);
        list.add(new LdcInsnNode(value ^ 0x55));
        list.add(new VarInsnNode(ISTORE, vReg));
        list.add(new JumpInsnNode(GOTO, end));

        list.add(lJunk);
        list.add(new LdcInsnNode(rand.nextInt()));
        list.add(new VarInsnNode(ISTORE, vReg));
        list.add(new JumpInsnNode(GOTO, end));

        list.add(lFinal);
        list.add(new LdcInsnNode(value));
        list.add(new VarInsnNode(ISTORE, vReg));

        list.add(end);
        list.add(new VarInsnNode(ILOAD, vReg));

        return list;
    }

    private boolean isIntegerInsn(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        return (op >= ICONST_M1 && op <= ICONST_5) || op == BIPUSH || op == SIPUSH ||
                (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer);
    }

    private int getIntegerValue(AbstractInsnNode insn) {
        if (insn instanceof IntInsnNode ii) return ii.operand;
        if (insn instanceof LdcInsnNode ldc) return (Integer) ldc.cst;
        return switch (insn.getOpcode()) {
            case ICONST_M1 -> -1;
            case ICONST_0 -> 0;
            case ICONST_1 -> 1;
            case ICONST_2 -> 2;
            case ICONST_3 -> 3;
            case ICONST_4 -> 4;
            case ICONST_5 -> 5;
            default -> 0;
        };
    }
}