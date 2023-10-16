package risc;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        int show_help = 0;
        int time_divisor = 1;
        final boolean fixed_update = false;
        final boolean do_sleep = true;
        final boolean single_step = false;
        int dtb_ptr;
        long instct = -1;


        Rv32ima emu = new Rv32ima();

        //String filepath = "/home/jacob/IdeaProjects/RISC-V 32/linux";
        String filepath = "linux";
        //String filepath = "/run/media/jacobm/Backup Plus/ARCH Backup/jacob/buildroot/output/images/Image";

        if (filepath != null) {
            byte[] prog = Files.readAllBytes(Path.of(filepath));
            int flen = prog.length;

            System.arraycopy(prog, 0, emu.image, 0, flen);
        }

        byte[] dtb = Files.readAllBytes(Path.of("default64mbdtc.bin"));

        dtb_ptr = emu.image.length - dtb.length - 192; // End of memory is saved for the state
        System.arraycopy(dtb, 0, emu.image, dtb_ptr, dtb.length);
        System.out.println("ROM END: " + (dtb_ptr + dtb.length));

        /*
        TODO: this thing that idk is needed
        // The core lives at the end of RAM.
	    core = (struct MiniRV32IMAState *)(ram_image + ram_amt - sizeof( struct MiniRV32IMAState ));
         */
        emu.state.pc = Rv32ima.RV32_RAM_IMAGE_OFFSET;
        emu.state.regs[10] = 0; // hart ID
        emu.state.regs[11] = dtb_ptr + Rv32ima.RV32_RAM_IMAGE_OFFSET;
        emu.state.extraflags |= 3; // Machine-mode.

        System.out.printf("PC: %d, dtb_ptr: %d\n", emu.state.pc, dtb_ptr);

        //emu.dumpMem("imgdump.bin");


        // Image is loaded.
        long rt;
        long lastTime = fixed_update ? 0 : (Rv32ima.getTimeMicroseconds() / time_divisor);
        int instrs_per_flip = single_step ? 1 : 1024;

        for (rt = 0; rt < instct + 1 || instct < 0; rt += instrs_per_flip) {
            //System.out.printf("    LAST_TIME: %d\n", lastTime);
            long this_ccount = emu.state.cyclel;
            long elapsedUs;

            if (fixed_update)
                elapsedUs = this_ccount / time_divisor - lastTime;
            else
                elapsedUs = Rv32ima.getTimeMicroseconds() / time_divisor - lastTime;
            lastTime += elapsedUs;

            if (single_step)
                Rv32ima.dumpState(emu.state, emu.image);

            int ret = (int) emu.rv32IMAStep(0, elapsedUs, instrs_per_flip);
            switch (ret) {
                case 0 -> {}
                case 1 -> {if (do_sleep) Rv32ima.miniSleep(); this_ccount += instrs_per_flip;}
                case 3 -> instct = 0;
                case 0x7777 -> {} // TODO: Implement restart
                case 0x5555 -> {System.out.printf( "POWEROFF@0x%08x%08x\n", emu.state.cycleh, emu.state.cyclel ); return;} //syscon code for power-off
                default -> System.out.println("Unknown failure");
            }
        }
        Rv32ima.dumpState(emu.state, emu.image);
    }
}
