package risc;

public class State {
    public long[] regs = new long[32];

    public long pc;
    public long mstatus;
    public long cyclel;
    public long cycleh;

    public long timerl;
    public long timerh;
    public long timermatchl;
    public long timermatchh;

    public long mscratch;
    public long mtvec;
    public long mie;
    public long mip;

    public long mepc;
    public long mtval;
    public long mcause;

    // Note: only a few bits are used.  (Machine = 3, User = 0)
    // Bits 0..1 = privilege.
    // Bit 2 = WFI (Wait for interrupt)
    public long extraflags;
}
