package risc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Rv32ima {
    public static final long RV32_RAM_IMAGE_OFFSET = 0x80000000L;
    public static final int ram_amt = 64*1024*1024;
    public static final TTY tty = new TTY();

    public State state = new State();
    public byte[] image = new byte[ram_amt];
    public int fail_on_all_faults = 0;

    public static BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

    public void dumpMem(String path) throws Exception {
        Files.write(Path.of(path), image);
    }

    public Rv32ima() {
        tty.setVisible(true);
    }

    private void rv32_store(long ofs, long val, int words) { //TODO: This might not be little/big indian
        int mask = 0xff;

        for (int i = 0; i < words; i++) {
            byte word = (byte) ((val & mask) >> (i * 8));
            mask <<= 8;
            image[(int) ofs + i] = word;
        }
    }

    private long rv32_load(long ofs, int words) { //TODO: This might not be little/big indian
        long ret = 0;

        for (int i = 0; i < words; i++) {
            ret |= Byte.toUnsignedLong(image[(int) ofs + i]) << (i * 8);
            //ret <<= 8;
        }
        return ret;
    }

    private void rv32_store4(long ofs, long val) {rv32_store(ofs, val, 4);}
    private void rv32_store2(long ofs, long val) {rv32_store(ofs, val, 2);}
    private void rv32_store1(long ofs, long val) {rv32_store(ofs, val, 1);}
    private long rv32_load4(long ofs) {return rv32_load(ofs, 4);}
    private int rv32_load2(long ofs) {return (int) rv32_load(ofs, 2);}
    private short rv32_load1(long ofs) {return (short) rv32_load(ofs, 1);}

    private void regDump() {
        long[] regs = state.regs;
        System.out.printf( "Z:%08x ra:%08x sp:%08x gp:%08x tp:%08x t0:%08x t1:%08x t2:%08x s0:%08x s1:%08x a0:%08x a1:%08x a2:%08x a3:%08x a4:%08x a5:%08x ",
                regs[0], regs[1], regs[2], regs[3], regs[4], regs[5], regs[6], regs[7],
                regs[8], regs[9], regs[10], regs[11], regs[12], regs[13], regs[14], regs[15] );
        System.out.printf( "a6:%08x a7:%08x s2:%08x s3:%08x s4:%08x s5:%08x s6:%08x s7:%08x s8:%08x s9:%08x s10:%08x s11:%08x t3:%08x t4:%08x t5:%08x t6:%08x\n",
                regs[16], regs[17], regs[18], regs[19], regs[20], regs[21], regs[22], regs[23],
                regs[24], regs[25], regs[26], regs[27], regs[28], regs[29], regs[30], regs[31] );
    }

    private String extra() {
        if (useRegistersInDebug) {
            long[] regs = state.regs;
            return String.format("; REG: %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\n",
                    regs[0], regs[1], regs[2], regs[3], regs[4], regs[5], regs[6], regs[7], regs[8], regs[9], regs[10], regs[11], regs[12], regs[13], regs[14], regs[15], regs[16], regs[17], regs[18], regs[19], regs[20], regs[21], regs[22], regs[23], regs[24], regs[25], regs[26], regs[27], regs[28], regs[29], regs[30], regs[31]);
        } else if (useIRInDebug)
            return String.format("; %08x", ir);
        return "";
    }

    private static final String timepath = "/run/media/jacobm/Backup Plus/ARCH Backup/jacob/IdeaProjects/BytesToListForScratch/lists/time.txt";
    private static java.util.List<Long> times;
    private static int timeIndex = 0;

    static {
        try {
            //times = Files.readAllLines(Path.of(timepath)).stream().map(Long::parseLong).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static long getTimeMicroseconds() {
        //long ret = times.get(timeIndex++);
        //System.out.println(ret);
        //return ret;

        return System.nanoTime() / 1000;
        //return 0;
        //return (((int) System.currentTimeMillis() / 10) * 10) * 1000;
        //long ret = System.currentTimeMillis() * 1000;
        //times.add(ret);
        //if (times.size() >= 200000) {
        //    try {
        //        Files.writeString(Path.of(timepath), times.stream().map(Object::toString).collect(Collectors.joining("\n")));
        //    } catch (Exception e) {
        //        throw new RuntimeException(e);
        //    }
        //    System.exit(0);
        //}
        //return ret;
    }

    static long totalExecuted = 0;
    final boolean debug = false;
    final boolean useRegistersInDebug = false;
    final boolean useIRInDebug = false;
    final boolean showTraps = false;
    final long dumpStart = 2962953;//2962953;
    final long debugStart = 92962953;//2962953;
    final long debugLength = 199000;

    long ir;
    long rval;

    private static final StringBuilder debugInfo = new StringBuilder();

    public long rv32IMAStep(long vProcAddress, long elapsedUs, int count) {
        long new_timer = state.timerl + elapsedUs;
        if (new_timer < state.timerl) state.timerh++;
        state.timerl = new_timer;

        if ((state.timerh > state.timermatchh || (state.timerh == state.timermatchh && state.timerl > state.timermatchl)) && (state.timermatchh > 0 || state.timermatchl > 0)) {
            state.extraflags &= ~4;
            state.mip |= 1<<7;
        } else
            state.mip &= ~(1<<7);
        if ((state.extraflags & 4) > 0)
            return 1;

        //if (totalExecuted >= debugStart && debug) System.out.printf("   ELAPSED: %d, %d\n", elapsedUs, state.timerl);

        int icount;
        for (icount = 0; icount < count; icount++) {

            ir = 0;
            long trap = 0;
            rval = 0;

            state.cyclel++;
            if (state.cyclel == 0) state.cycleh++;

            long pc = state.pc;
            long ofs_pc = pc - RV32_RAM_IMAGE_OFFSET;

            if (ofs_pc >= image.length)
                trap = 1 + 1;
            else if ((pc & 3) > 0)
                trap = 1 + 0;
            else {
                //String extra = String.valueOf(rv32_load4(2933624));
                //String extra = state.mepc + " " + state.mtval;
                String extra = String.valueOf(state.timerl);
                //String extra = null;

                if (debug && totalExecuted > (dumpStart - 1))
                    debugInfo.append(Arrays.toString(state.regs).replaceAll(", ", " ").replaceAll("\\[", "").replaceAll("]", "")).append(" ").append(rval).append(extra != null ? " " + extra : "").append("\n");
                ir = rv32_load4(ofs_pc);
                long rdid = (ir >> 7) & 0x1f;
                //if (debug && totalExecuted > debugStart + debugLength)
                 if (totalExecuted > debugLength + Math.min(dumpStart, debugStart) && debug) {
                     if (debug) {
                         try {
                             Files.writeString(Path.of("/run/media/jacobm/Backup Plus/ARCH Backup/jacob/IdeaProjects/BytesToListForScratch/lists/debugInfo.txt"), debugInfo.toString());
                         } catch (IOException e) {
                             throw new RuntimeException(e);
                         }
                     }
                     System.exit(0);
                 }

                totalExecuted++;
                if (totalExecuted >= debugStart && debug)
                    System.out.println(ofs_pc + " " + ir + " " + rdid + " " + rval);
                //if (totalExecuted > 13000000) System.exit(0);

                switch ((int) (ir & 0x7f)) {
                    case 0b0110111 -> {
                        rval = (ir & 0xfffff000L); // LUI
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: LUI %d%s\n", pc, rval, extra());
                    }
                    case 0b0010111 -> {
                        rval = (pc + (ir & 0xfffff000L)) & 0xFFFFFFFFL; // AUIPC
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: AUIPC %d%s\n", pc, rval, extra());
                    }
                    case 0b1101111 -> { // JAL
                        int reladdy = (int) (((ir & 0x80000000L)>>11) | ((ir & 0x7fe00000)>>20) | ((ir & 0x00100000)>>9) | ((ir&0x000ff000)));
                        if((reladdy & 0x00100000) > 0) reladdy |= 0xffe00000L; // Sign extension.

                        rval = pc + 4;
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: JAL %d %d%s\n", pc, reladdy, rval, extra());
                        pc = pc + reladdy - 4;
                    }
                    case 0b1100111 -> { // JALR
                        long imm = ir >> 20;
                        int imm_se = (int) (imm | (((imm & 0x800) > 0) ? 0xfffff000 : 0));
                        rval = pc + 4;

                        //if (totalExecuted >= debugStart && debug) System.out.printf("%08x: JALR %d, %d%s\n", pc, imm, imm_se, extra());

                        pc = ( (state.regs[(int) ((ir >> 15) & 0x1f)] + imm_se) & ~1) - 4;
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: JALR %d %d %d %d %d%s\n", pc, pc, imm, imm_se, (int) ((ir >> 15) & 0x1f), state.regs[(int) ((ir >> 15) & 0x1f)], extra());
                    }
                    case 0b1100011 -> { // BRANCH
                        long immm4 = ((ir & 0xf00) >> 7) | ((ir & 0x7e000000) >> 20) | ((ir & 0x80) << 4) | ((ir >> 31) << 12);
                        if ((immm4 & 0x1000) > 0) immm4 |= 0xffffe000L;

                        int rs1 = (int) state.regs[(int) (ir >> 15) & 0x1f];
                        int rs2 = (int) state.regs[(int) (ir >> 20) & 0x1f];

                        immm4 = (((pc + immm4) & 0xFFFFFFFFL) - 4) & 0xFFFFFFFFL;
                        rdid = 0;
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: BRANCH %d, %d, %d%s\n", pc, rs1, rs2, immm4, extra());

                        switch ((int) ((ir >> 12) & 0x7)) {
                            case 0b000 -> {if(rs1 == rs2) pc = immm4;}
                            case 0b001 -> {if(rs1 != rs2) pc = immm4;}
                            case 0b100 -> {if(rs1 < rs2) pc = immm4;}
                            case 0b101 -> {if(rs1 >= rs2) pc = immm4;}
                            case 0b110 -> {if(Integer.toUnsignedLong(rs1) < Integer.toUnsignedLong(rs2)) pc = immm4;}
                            case 0b111 -> {if(Integer.toUnsignedLong(rs1) >= Integer.toUnsignedLong(rs2)) pc = immm4;}
                            default -> trap = (2+1);
                        }
                    }
                    case 0b0000011 -> { // Load
                        long rs1 = state.regs[(int) ((ir >> 15) & 0x1f)];
                        long imm = (ir >> 20) & 0xFFFFFFFFL;
                        int imm_se = (int) (imm | (((imm & 0x800) > 0) ? 0xfffff000 : 0));
                        long rsval = (rs1 + Integer.toUnsignedLong(imm_se)) & 0xFFFFFFFFL;

                        rsval -= RV32_RAM_IMAGE_OFFSET;
                        rsval &= 0xFFFFFFFFL;

                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: LOAD %d, %d, %d, %d%s\n", pc, rs1, imm, imm_se, rsval, extra());
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: LOAD %d, %d, %d, %d%s\n", pc, state.timerl, state.timerh, state.cyclel, state.cycleh, extra());

                        if (rsval >= ram_amt - 3  ) {

                            rsval -= RV32_RAM_IMAGE_OFFSET;
                            rsval &= 0xFFFFFFFFL;
                            if (rsval >= 0x10000000L && rsval < 0x12000000L) {
                                if (rsval == 0x1100bffcL)
                                    rval = state.timerh;
                                else if (rsval == 0x1100bff8L)
                                    rval = state.timerl;
                                else
                                    rval = handleControlLoad(rsval);
                            } else {
                                trap = (5 + 1);
                                rval = rsval;
                            }
                        } else {
                            //if (totalExecuted >= debugStart && debug) System.out.println("Bottom " + ((ir >> 12) & 0x7) + " " + rsval + " " + rv32_load4(rsval));
                            switch ((int) ((ir >> 12) & 0x7)) {
                                case 0b000 -> rval = (byte) rv32_load1(rsval); // Possibly cast to byte before setting rval
                                case 0b001 -> rval = (short) rv32_load2(rsval); // Possibly cast to short before setting rval
                                case 0b010 -> rval = rv32_load4(rsval);
                                case 0b100 -> rval = rv32_load1(rsval);
                                case 0b101 -> rval = rv32_load2(rsval);
                                default -> trap = (2 + 1);
                            }
                        }
                    }
                    case 0b0100011 -> { // Store
                        long rs1 = state.regs[(int) ((ir >> 15) & 0x1f)];
                        long rs2 = state.regs[(int) ((ir >> 20) & 0x1f)];
                        long addy = (((ir >> 7) & 0x1f) | ((ir & 0xfe000000L) >> 20) & 0xFFFFFFFFL);

                        if ((addy & 0x800) > 0) addy |= 0xfffff000L;
                        addy += (rs1 - RV32_RAM_IMAGE_OFFSET) & 0xFFFFFFFFL;
                        addy &= 0xFFFFFFFFL;
                        rdid = 0;

                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: STORE %d, %d, %d %d%s\n", pc, rs1, rs2, addy, state.timerl, extra());

                        if (addy >= ram_amt - 3) {

                            addy -= RV32_RAM_IMAGE_OFFSET;
                            addy &= 0xFFFFFFFFL;
                            if (addy >= 0x10000000 && addy < 0x12000000) {

                                // Should only be stuff like SYSCON, 8250, CLNT
                                if (addy == 0x11004004) { // CLNT
                                    state.timermatchh = rs2;
                                } else if (addy == 0x11004000) { // CLNT
                                    state.timermatchl = rs2;
                                } else if (addy == 0x11100000) { // SYSCON (reboot, poweroff, etc.)
                                    state.pc = pc + 4;
                                    return rs2;
                                } else {
                                    if(handleControlStore( addy, rs2 ) > 0) return rs2;
                                }
                            } else {
                                trap = (7 + 1); // Store access fault.
                                rval = addy + RV32_RAM_IMAGE_OFFSET;
                            }
                        } else {
                            //if (totalExecuted >= debugStart && debug) System.out.println("Bottom " + ((ir >> 12) & 0x7) + " " + rs2 + " " + addy);
                            switch ((int) ((ir >> 12) & 0x7)) {
                                //SB, SH, SW
                                case 0b000 -> rv32_store1(addy, rs2);
                                case 0b001 -> rv32_store2(addy, rs2);
                                case 0b010 -> rv32_store4(addy, rs2);
                                default -> trap = (2 + 1);
                            }
                        }
                    }
                    case 0b0010011, 0b0110011 -> { // Op / Op-immediate
                        long imm = ir >> 20;
                        imm = imm | (((imm & 0x800) > 0) ? 0xfffff000 : 0);
                        imm = imm & 0xFFFFFFFFL;

                        long rs1 = state.regs[(int) ((ir >> 15) & 0x1f)];
                        boolean is_reg = (ir & 0b100000) > 0;
                        long rs2 = is_reg ? state.regs[(int) (imm & 0x1f)] : imm;

                        //if (totalExecuted >= debugStart && debug) System.out.printf("%08x: OP %d, %d, %d, %d, %d, %d%s\n", pc, ir & 0x02000000, ir & 0x40000000, (ir>>12)&7, rs1, is_reg ? 1 : 0, rs2, extra());
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: OP %d, %d, %d%s\n", pc, rs1, rs2, imm, extra());

                        if (is_reg && (ir & 0x02000000) > 0) {
                           // if (totalExecuted >= debugStart && debug) System.out.println("topHalf " + ((ir >> 12) & 7));
                            switch ((int) ((ir >> 12) & 7)) { // 0x02000000 = RV32M
                                case 0b000 -> rval = rs1 * rs2; // MUL
                                case 0b001 -> rval = ((long) ((int) rs1) * (long) ((int) rs2)) >> 32; // MULH
                                case 0b010 -> rval = ((long)((int)rs1) * rs2) >> 32; // MULHSU
                                case 0b011 -> rval = (rs1 * rs2) >> 32; // MULHU
                                case 0b100 -> {if (rs2 == 0) rval =  -1; else rval =  ((int) rs1 / (int) rs2);} // DIV
                                case 0b101 -> {if (rs2 == 0) rval =  0xffffffffL; else rval =  rs1 / rs2;} // DIVU
                                case 0b110 -> {if (rs2 == 0) rval =  rs1; else rval =  ((int) rs1) % ((int) rs2);} // REM
                                case 0b111 -> {if (rs2 == 0) rval =  rs1; else rval =  rs1 % rs2;} // REMU
                            };
                        } else { // These could be either op-immediate or op commands.  Be careful.
                            //if (totalExecuted >= debugStart && debug) System.out.println("bottomHalf " + ((ir >> 12) & 7));
                            switch ((int) ((ir >> 12) & 7)) {
                                case 0b000 -> rval = (is_reg && (ir & 0x40000000) > 0) ? (rs1 - rs2) : (rs1 + rs2);
                                case 0b001 -> rval = rs1 << (rs2 % 32);
                                case 0b010 -> rval = ((int) rs1) < ((int) rs2) ? 1 : 0;
                                case 0b011 -> rval = rs1 < rs2 ? 1 : 0;
                                case 0b100 -> rval = rs1 ^ rs2;
                                case 0b101 -> rval = ((ir & 0x40000000) > 0) ? (((int) rs1) >> (rs2 % 32)) : (rs1 >> (rs2 % 32));
                                case 0b110 -> rval = rs1 | rs2;
                                case 0b111 -> rval = rs1 & rs2;
                            };
                        }
                    }
                    case 0b0001111 -> {rdid = 0;if (totalExecuted >= debugStart && debug) System.out.println("rdid = 0");} // fencetype = (ir >> 12) & 0b111; We ignore fences in this impl.
                    case 0b1110011 -> { // Zifencei+Zicsr
                        long csrno = ir >> 20;
                        int microop = (int) ((ir >> 12) & 0b111);


                        if ((microop & 3) > 0) { // It's a Zicsr function.
                            int rs1imm = (int) ((ir >> 15) & 0x1f);
                            long rs1 = state.regs[rs1imm];
                            long writeval = rs1;

                            if (totalExecuted >= debugStart && debug) System.out.printf("%08x: ZICSR %d, %d, %d%s\n", pc, csrno, microop, rs1, extra());

                            // https://raw.githubusercontent.com/riscv/virtual-memory/main/specs/663-Svpbmt.pdf
                            // Generally, support for Zicsr
                            //if (totalExecuted >= debugStart && debug) System.out.println(state.mie + " " + state.mip + " " + state.mepc + " " + state.mstatus + " " + state.mtval);
                            switch ((int) csrno) {
                                case 0x340 -> rval = state.mscratch;
                                case 0x305 -> rval = state.mtvec;
                                case 0x304 -> rval = state.mie;
                                case 0xC00 -> rval = state.cyclel;
                                case 0x344 -> rval = state.mip;
                                case 0x341 -> rval = state.mepc;
                                case 0x300 -> rval = state.mstatus;
                                case 0x342 -> rval = state.mcause;
                                case 0x343 -> rval = state.mtval;
                                case 0xf11 -> rval = 0xff0ff0ffL; //mvendorid
                                case 0x301 -> rval = 0x40401101L; //misa (XLEN=32, IMA+X)
                                default -> otherCSRRead(csrno, rval);
                            };

                            writeval = switch (microop) {
                                case 0b001 -> rs1;
                                case 0b010 -> rval | rs1;
                                case 0b011 -> rval & ~rs1;
                                case 0b101 -> rs1imm;
                                case 0b110 -> rval | rs1imm;
                                case 0b111 -> rval & ~rs1imm;
                                default -> rval;
                            };

                            switch ((int) csrno) {
                                case 0x340 -> state.mscratch = writeval;
                                case 0x305 -> state.mtvec = writeval;
                                case 0x304 -> state.mie = writeval;
                                case 0x344 -> state.mip = writeval;
                                case 0x341 -> state.mepc = writeval;
                                case 0x300 -> state.mstatus = writeval;
                                case 0x342 -> state.mcause = writeval;
                                case 0x343 -> state.mtval = writeval;
                                default -> handleOtherCSRWrite(image, csrno, writeval);
                            }
                           // if (totalExecuted >= debugStart && debug) System.out.println(state.mie + " " + state.mip + " " + state.mepc + " " + state.mstatus + " " + state.mcause + " " + state.mtval);
                        } else if (microop == 0b000) { // "SYSTEM"

                            rdid = 0;
                            if (csrno == 0x105) { // WFI (Wait for interrupts
                                state.mstatus |= 8; // Enable interrupts
                                state.extraflags |= 4; // Inform environment we want to go to sleep.
                                state.pc = pc + 4;

                                if (totalExecuted >= debugStart && debug) System.out.printf("%08x: WFI%s\n", pc, extra());

                                return 1;
                            } else if ((csrno & 0xff) == 0x02) { // MRET

                                //https://raw.githubusercontent.com/riscv/virtual-memory/main/specs/663-Svpbmt.pdf
                                //Table 7.6. MRET then in mstatus/mstatush sets MPV=0, MPP=0, MIE=MPIE, and MPIE=1. La
                                // Should also update mstatus to reflect correct mode.
                                long startmstatus = state.mstatus;
                                long startextraflags = state.extraflags;
                                state.mstatus = (((startmstatus & 0x80) >> 4) | ((startextraflags & 3) << 11) | 0x80) & 0xFFFFFFFFL;
                                state.extraflags = ((startextraflags & ~3) | ((startmstatus >> 11) & 3)) & 0xFFFFFFFFL;

                                if (totalExecuted >= debugStart && debug) System.out.printf("%08x: MRET %d, %d%s\n", pc, startmstatus, startextraflags, extra());
                                pc = state.mepc - 4;
                            } else {

                                switch ((int) csrno) {
                                    case 0 -> {
                                        //System.out.printf("%08x: ECALL fault", pc);
                                        trap = ((state.extraflags & 3) > 0 ? (11 + 1) : (8 + 1)); // ECALL; 8 = "Environment call from U-mode"; 11 = "Environment call from M-mode"
                                    }
                                    case 1 -> {
                                        trap = (3 + 1); // EBREAK 3 = "Breakpoint"
                                        //System.err.println("Breakpoint hit!");
                                    }
                                    default -> trap = (2 + 1); // Illegal opcode.
                                }
                                if (totalExecuted >= debugStart && debug) System.out.printf("%08x: ZIFENCEI ERROR %d%s\n", pc, trap, extra());
                            }
                        } else {
                            trap = (2 + 1);
                            //if (totalExecuted >= min && desc) System.out.printf("%08x: ZIFENCEI ERROR %d%s\n", pc, trap, extra());
                        }
                    }
                    case 0b0101111 -> { // RV32A
                        long rs1 = state.regs[(int) ((ir >> 15) & 0x1f)];
                        long rs2 = state.regs[(int) ((ir >> 20) & 0x1f)];
                        long irmid = (ir >> 27) & 0x1f;

                        rs1 -= RV32_RAM_IMAGE_OFFSET;

                        // We don't implement load/store from UART or CLNT with RV32A here.

                        if (rs1 >= ram_amt - 3) {
                            if (totalExecuted >= debugStart && debug) System.out.printf("%08x: RV32A %d, %d, %d%s\n", pc, rs1, rs2, irmid, extra());
                            trap = (7 + 1); // Store/AMO access fault
                            rval = rs1 + RV32_RAM_IMAGE_OFFSET;
                        } else {

                            rval = rv32_load4(rs1);

                            // Referenced a bit of https://github.com/franzflasch/riscv_em/blob/master/src/core/core.c
                            boolean dowrite = true;
                            switch ((int) irmid) {
                                case 0b00010 -> dowrite = false; // LR.W
                                case 0b00011 -> rval = 0; // SC.W (Lie and always say it's good)
                                case 0b00001 -> {} // AMOSWAP.W
                                case 0b00000 -> rs2 += rval; // AMOADD.W
                                case 0b00100 -> rs2 ^= rval; // AMOXOR.W
                                case 0b01100 -> rs2 &= rval; // AMOAND.W
                                case 0b01000 -> rs2 |= rval; // AMOOR.W
                                case 0b10000 -> rs2 = (((int) rs2) < ((int) rval)) ? rs2 : rval; // AMOMIN.W
                                case 0b10100 -> rs2 = (((int) rs2) > ((int) rval)) ? rs2 : rval; // AMOMAX.W
                                case 0b11000 -> rs2 = Math.min(rs2, rval); // AMOMINU.W
                                case 0b11100 -> rs2 = Math.max(rs2, rval); // AMOMAX.W
                                default -> {trap = (2 + 1); dowrite = false;} // Not supported.
                            }
                            if (totalExecuted >= debugStart && debug) System.out.printf("%08x: RV32A %d, %d, %d%s\n", pc, rs1, rs2, irmid, extra());
                            if (dowrite) rv32_store4(rs1, rs2);
                        }
                    }
                    default -> {
                        trap = (2 + 1); // Fault: Invalid opcode.
                        if (totalExecuted >= debugStart && debug) System.out.printf("%08x: INVALID%s\n", pc, extra());
                    }
                }
                if (trap == 0) {
                    if (rdid > 0) {
                        //System.out.println(state.regs[1] + " " + rval + " " + rdid);
                        state.regs[(int) rdid] = (rval) & 0xFFFFFFFFL;
                        if (debug && totalExecuted >= debugStart) System.out.printf("%d = %d\n", (int) rdid, state.regs[(int) rdid]);
                    } else if (((state.mip & (1 << 7)) > 0) && ((state.mie & (1 << 7) /*mtie*/) > 0) && ((state.mstatus & 0x8 /*mie*/) > 0))
                        trap = 0x80000007L; // Timer interrupt.
                }

            }
            if( trap > 0 ) {if(fail_on_all_faults > 0) {System.out.println("FAULT");} else trap = handleException(ir, trap);}

            // handle traps and interrupts.
            if (trap > 0) {
                if ((trap & 0x80000000L) > 0) {
                    state.mcause = trap;
                    state.mtval = 0;
                    pc += 4;
                } else {
                    if (showTraps)
                        System.err.printf("ERROR: trap %d @ PC %08x\n", trap, pc);

                    state.mcause = trap - 1;
                    state.mtval = (trap > 5 && trap <= 8) ? rval : pc;
                }
                state.mepc = pc; //TRICKY: The kernel advances mepc automatically.
                //state.mstatus & 8 = MIE, & 0x80 = MPIE
                // On an interrupt, the system moves current MIE into MPIE
                state.mstatus = ((state.mstatus & 0x08) << 4) | (( state.extraflags & 3 ) << 11);
                pc = state.mtvec - 4;

                if ((trap & 0x80000000L) < 1)
                    state.extraflags |= 3;
            }

            state.pc = pc + 4;
        }
        return 0;
    }

    static void ctrlC() {
        //TODO: Implement this
        System.out.println("Control C");
    }

    static void captureKeyboardInput() {
        //TODO: Implement this
    }

    static void resetKeyboardInput() {
        //TODO: Implement this
    }

    static void miniSleep() {
        try {
            Thread.sleep(0, 500000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean is_eofd = false;

    static int readKBByte() {
        if (is_eofd) return 0xffffffff;

        return tty.read();
    }

    static boolean isKBHit() {
        if (is_eofd)
            return false;
        //System.out.println(tty.ready());
        return tty.ready();
    }

    static long handleException(long ir, long code) {
        // Weird opcode emitted by duktape on exit.
        if( code == 3 )
        {
            // Could handle other opcodes here.
        }
        return code;
    }

    long handleControlStore(long addy, long val) {
        if (totalExecuted >= debugStart && debug)
            System.out.println("HandleControlStore " + addy + " " + val);
        //System.out.println("Printed char at " + totalExecuted);
        //System.exit(0);

        if( addy == 0x10000000 ) //UART 8250 / 16550 Data Buffer
        {
            tty.printf("%c", (char) val);
            //System.out.flush();
        }
        return 0;
    }

    static long handleControlLoad(long addy) {
        //
        // System.out.println("HandleControlStore " + addy);
        // Emulating a 8250 / 16550 UART
        if( addy == 0x10000005 )
            return 0x60 | (isKBHit() ? 1 : 0);
        else if( addy == 0x10000000 && isKBHit())
            return readKBByte();
        return 0;
    }

    static void handleOtherCSRWrite(byte[] image, long csrno, long value) {
        if (csrno == 0x136) {
            tty.printf("%d", value);
            //System.out.flush();
        } else if (csrno == 0x137) {
            System.out.printf("%08x", value);
            //System.out.flush();
        } else if (csrno == 0x138) {

            //Print "string"
            long ptrstart = value - RV32_RAM_IMAGE_OFFSET;
            long ptrend = ptrstart;
            if (ptrstart >= ram_amt)
                tty.printf("DEBUG PASSED INVALID PTR (%08x)\n", value);

            while (ptrend < ram_amt) {
                if (image[(int) ptrend] == 0) break;
                ptrend++;
            }
            if (ptrend != ptrstart)
                tty.write(image, (int) ptrstart, (int) ptrend);
            //String str = new String(Arrays.copyOfRange(image, (int) ptrstart, (int) ptrend));
        }
    }
    static long simpleReadNumberInt(char[] number, long defaultNumber) {
        if (number == null || number.length < 1 || number[0] < 1) return defaultNumber;
        int radix = 10;
        int numstart = 0;

        if (number[0] == '0') {
            char nc = number[1];
            numstart = 2;
            if( nc == 0 ) return 0;
            else if( nc == 'x' ) radix = 16;
            else if( nc == 'b' ) radix = 2;
            else { numstart--; radix = 8; }
        }
        if (numstart == number.length)
            return defaultNumber;
        return Long.parseLong(new String(Arrays.copyOfRange(number, numstart, number.length)), radix);
    }

    static void dumpState(State core, byte[] ram_image) {
        long pc = core.pc;
        long pc_offset = pc - RV32_RAM_IMAGE_OFFSET;
        long ir = 0;

        System.out.printf( "PC: %08x ", pc );
        if( pc_offset >= 0 && pc_offset < ram_amt - 3 )
        {
            ir = ram_image[(int) pc_offset];
            System.out.printf( "[0x%08x] ", ir );
        }
        else
            System.out.print( "[xxxxxxxxxx] " );
        long[] regs = core.regs;
        System.out.printf( "Z:%08x ra:%08x sp:%08x gp:%08x tp:%08x t0:%08x t1:%08x t2:%08x s0:%08x s1:%08x a0:%08x a1:%08x a2:%08x a3:%08x a4:%08x a5:%08x ",
                regs[0], regs[1], regs[2], regs[3], regs[4], regs[5], regs[6], regs[7],
                regs[8], regs[9], regs[10], regs[11], regs[12], regs[13], regs[14], regs[15] );
        System.out.printf( "a6:%08x a7:%08x s2:%08x s3:%08x s4:%08x s5:%08x s6:%08x s7:%08x s8:%08x s9:%08x s10:%08x s11:%08x t3:%08x t4:%08x t5:%08x t6:%08x\n",
                regs[16], regs[17], regs[18], regs[19], regs[20], regs[21], regs[22], regs[23],
                regs[24], regs[25], regs[26], regs[27], regs[28], regs[29], regs[30], regs[31] );
    }

    static void otherCSRRead(long a, long b) {
        //TODO
    }
}
