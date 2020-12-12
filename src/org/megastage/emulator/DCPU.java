package org.megastage.emulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

/**
 * Experimental 1.7 update to Notch's 1.4 emulator
 * @author Notch, Herobrine
 *
 */
public class DCPU
{
    public static final int khz = 1000;
    private static final boolean SHIFT_DISTANCE_5_BITS = true;

    public boolean running = false;
    public File floppyFile;

    public char[] ram = new char[65536];

    public char pc;
    public char sp;
    public char ex;
    public char ia;
    public char[] registers = new char[8];
    public long cycles;

    public final ArrayList<DCPUHardware> hardware = new ArrayList<>();
    public final VirtualClockV2 clock = new VirtualClockV2();
    // public final VirtualClock clockv1 = new VirtualClock();
    public final VirtualKeyboard kbd = new VirtualKeyboard();
    public final VirtualAsciiKeyboard asciiKbd = new VirtualAsciiKeyboard();
    public final VirtualFloppyDrive floppy = new VirtualFloppyDrive();
    public final VirtualHic hic = new VirtualHic();
    public final VirtualRci rci = new VirtualRci();
    public final VirtualSpeaker2 speaker = new VirtualSpeaker2();

    public boolean isSkipping = false;
    public boolean isOnFire = false;
    public boolean queueingEnabled = false; //TODO: Verify implementation
    public char[] interrupts = new char[256];
    public int ip;
    public int iwp;

    private boolean hltMode;

    public LEM1802Viewer view;

    public int getAddrB(int type) {
        switch (type & 0xF8) {
            case 0x00:
                return 0x10000 + (type & 0x7);
            case 0x08:
                return registers[type & 0x7];
            case 0x10:
                cycles++;
                return ram[pc++] + registers[type & 0x7] & 0xFFFF;
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return (--sp) & 0xFFFF;
                    case 0x1:
                        return sp & 0xFFFF;
                    case 0x2:
                        cycles++;
                        return ram[pc++] + sp & 0xFFFF;
                    case 0x3:
                        return 0x10008;
                    case 0x4:
                        return 0x10009;
                    case 0x5:
                        return 0x10010;
                    case 0x6:
                        cycles++;
                        return ram[pc++];
                }
                cycles++;
                return 0x20000 | ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public int getAddrA(int type) {
        if (type >= 0x20) {
            return 0x20000 | (type & 0x1F) + 0xFFFF & 0xFFFF;
        }

        switch (type & 0xF8) {
            case 0x00:
                return 0x10000 + (type & 0x7);
            case 0x08:
                return registers[type & 0x7];
            case 0x10:
                cycles++;
                return ram[pc++] + registers[type & 0x7] & 0xFFFF;
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return sp++ & 0xFFFF;
                    case 0x1:
                        return sp & 0xFFFF;
                    case 0x2:
                        cycles++;
                        return ram[pc++] + sp & 0xFFFF;
                    case 0x3:
                        return 0x10008;
                    case 0x4:
                        return 0x10009;
                    case 0x5:
                        return 0x10010;
                    case 0x6:
                        cycles++;
                        return ram[pc++];
                }
                cycles++;
                return 0x20000 | ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public char getValA(int type) {
        if (type >= 0x20) {
            return (char)((type & 0x1F) + 0xFFFF);
        }

        switch (type & 0xF8) {
            case 0x00:
                return registers[type & 0x7];
            case 0x08:
                return ram[registers[type & 0x7]];
            case 0x10:
                cycles++;
                return ram[ram[pc++] + registers[type & 0x7] & 0xFFFF];
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return ram[sp++ & 0xFFFF];
                    case 0x1:
                        return ram[sp & 0xFFFF];
                    case 0x2:
                        cycles++;
                        return ram[ram[pc++] + sp & 0xFFFF];
                    case 0x3:
                        return sp;
                    case 0x4:
                        return pc;
                    case 0x5:
                        return ex;
                    case 0x6:
                        cycles++;
                        return ram[ram[pc++]];
                }
                cycles++;
                return ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public char get(int addr) {
        if (addr < 0x10000)
            return ram[addr & 0xFFFF];
        if (addr < 0x10008)
            return registers[addr & 0x7];
        if (addr >= 0x20000)
            return (char)addr;
        if (addr == 0x10008)
            return sp;
        if (addr == 0x10009)
            return pc;
        if (addr == 0x10010)
            return ex;
        throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
    }

    public void set(int addr, char val) {
        if (addr < 0x10000)
            ram[addr & 0xFFFF] = val;
        else if (addr < 0x10008) {
            registers[addr & 0x7] = val;
        } else if (addr < 0x20000) {
            if (addr == 0x10008)
                sp = val;
            else if (addr == 0x10009) {
                pc = val;
                rememberJump();
            } else if (addr == 0x10010)
                ex = val;
            else
                throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
        }
    }

    public static int getInstructionLength(char opcode) {
        int len = 1;
        int cmd = opcode & 0x1F;
        if (cmd == 0) {
            cmd = opcode >> 5 & 0x1F;
            if (cmd > 0) {
                int atype = opcode >> 10 & 0x3F;
                if (((atype & 0xF8) == 16) || (atype == 31) || (atype == 30)) len++;
            }
        }
        else {
            int atype = opcode >> 5 & 0x1F;
            int btype = opcode >> 10 & 0x3F;
            if ((atype >= 0x10 && atype <= 0x017) || atype == 0x1a || atype == 0x1e || atype == 0x1f) len++;
            if ((btype >= 0x10 && btype <= 0x017) || btype == 0x1a || btype == 0x1e || btype == 0x1f) len++;
        }
        return len;
    }

    public void skip() {
        isSkipping = true;
    }

    public HashMap<Character, SortedMap<Character, Integer>> jumps = new HashMap<>();
    public char startPC;

    public void tick() {
        startPC = pc;

        if (isOnFire) {
//      cycles += 10; //Disabled to match speed of crashing seen in livestreams
            /* For Java 7+
              int pos = ThreadLocalRandom.current().nextInt();
            char val = (char) (pos >> 16);//(char) ThreadLocalRandom.current().nextInt(65536);
            int len = (int)(1 / (ThreadLocalRandom.current().nextFloat() + 0.001f)) - 80;
            */
            int pos = (int)(Math.random() * 0x10000) & 0xFFFF;
            char val = (char) ((int)(Math.random() * 0x10000) & 0xFFFF);
            int len = (int)(1 / (Math.random() + 0.001f)) - 0x50;
            for (int i = 0; i < len; i++) {
                ram[(pos + i) & 0xFFFF] = val;
            }
        }

        if (isSkipping) {
            cycles++;

            char opcode = ram[pc];
            int cmd = opcode & 0x1F;
            pc = (char)(pc + getInstructionLength(opcode));
            isSkipping = (cmd >= 16) && (cmd <= 23);
            return;
        }

        if (!queueingEnabled) {
            if (ip != iwp) {
                char a = interrupts[ip = ip + 1 & 0xFF];
                if (ia > 0) {
                    queueingEnabled = true;
                    ram[--sp & 0xFFFF] = pc;
                    ram[--sp & 0xFFFF] = registers[0];
                    registers[0] = a;
                    pc = ia;
                    return;
                }
            }
        }

        cycles++;

        if(hltMode) return;

        char opcode = ram[pc++];

        int cmd = opcode & 0x1F;
        if (cmd == 0) {
            cmd = opcode >> 5 & 0x1F;
            if (cmd != 0)
            {
                int atype = opcode >> 10 & 0x3F;
                int aaddr = getAddrA(atype);
                char a = get(aaddr);

                switch (cmd) {
                    case 1: //JSR
                        cycles += 2;
                        ram[--sp & 0xFFFF] = pc;
                        pc = a;

                        rememberJump();

                        break;
//        case 7: //HCF
//          cycles += 8;
//          isOnFire = true;
//          break;
                    case 8: //INT
                        cycles += 3;
                        interrupt(a);
                        break;
                    case 9: //IAG
                        set(aaddr, ia);
                        break;
                    case 10: //IAS
                        ia = a;
                        break;
                    case 11: //RFI
                        cycles += 2;
                        //disables interrupt queueing, pops A from the stack, then pops PC from the stack
                        queueingEnabled = false;
                        registers[0] = ram[sp++ & 0xFFFF];
                        pc = ram[sp++ & 0xFFFF];
                        break;
                    case 12: //IAQ
                        cycles++;
                        //if a is nonzero, interrupts will be added to the queue instead of triggered. if a is zero, interrupts will be triggered as normal again
                        queueingEnabled = a != 0;
                        break;
                    case 16: //HWN
                        cycles++;
                        set(aaddr, (char)hardware.size());
                        break;
                    case 17: //HWQ
                        cycles += 3;
                        synchronized (hardware) {
                            if (a < hardware.size()) {
                                hardware.get(a).query();
                            }
                        }
                        break;
                    case 18: //HWI
                        cycles += 3;
                        synchronized (hardware) {
                            if (a < hardware.size()) {
                                hardware.get(a).interrupt();
                            }
                        }
                        break;
                    case 19: //LOG
                        System.out.println(startPC + " LOG " + hex(a) + "  " + cycles);
                        break;
                    case 20: //BRK
                        running = false;
                        break;
                    case 21: //HLT
                        hltMode = true;
                        break;
                    default:
                        break;
                }
            }
        } else {
            int atype = opcode >> 10 & 0x3F;

            char a = getValA(atype);

            int btype = opcode >> 5 & 0x1F;
            int baddr = getAddrB(btype);
            char b = get(baddr);

            switch (cmd) {
                case 1: //SET
                    b = a;
                    break;
                case 2:{ //ADD
                    cycles++;
                    int val = b + a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 3:{ //SUB
                    cycles++;
                    int val = b - a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 4:{ //MUL
                    cycles++;
                    int val = b * a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 5:{ //MLI
                    cycles++;
                    int val = (short)b * (short)a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 6:{ //DIV
                    cycles += 2;
                    if (a == 0) {
                        b = ex = 0;
                    } else {
                        set(baddr, (char) (b / a));
                        ex = (char) (((long) b << 16) / a);
                        return;
                    }
                    break;
                }case 7:{ //DVI
                    cycles += 2;
                    if (a == 0) {
                        b = ex = 0;
                    } else {
                        set(baddr, (char) ((short) b / (short) a));
                        ex = (char) ((b << 16) / ((short) a));
                        return;
                    }
                    break;
                }case 8: //MOD
                    cycles += 2;
                    if (a == 0)
                        b = 0;
                    else {
                        b = (char)(b % a);
                    }
                    break;
                case 9: //MDI
                    cycles += 2;
                    if (a == 0)
                        b = 0;
                    else {
                        b = (char)((short)b % (short)a);
                    }
                    break;
                case 10: //AND
                    b = (char)(b & a);
                    break;
                case 11: //BOR
                    b = (char)(b | a);
                    break;
                case 12: //XOR
                    b = (char)(b ^ a);
                    break;
                case 13: { //SHR
                    if(!SHIFT_DISTANCE_5_BITS && a > 31) {
                        set(baddr, (char) 0);
                        ex = (char) 0;
                    } else {
                        set(baddr, (char) (b >>> a));
                        ex = (char) (b << 16 >>> a);
                    }
                    return;
                }
                case 14: { //ASR
                    if(!SHIFT_DISTANCE_5_BITS && a > 31) {
                        a = 31;
                    }
                    set(baddr, (char)((short)b >> a));
                    ex = (char)(b << 16 >> a);
                    return;
                }
                case 15: //SHL
                    if(!SHIFT_DISTANCE_5_BITS && a > 31) {
                        set(baddr, (char) 0);
                        ex = (char) 0;
                    } else {
                        set(baddr, (char) (b << a));
                        ex = (char) (b << a >> 16);
                    }
                    return;
                case 16: //IFB
                    cycles++;
                    if ((b & a) == 0) skip();
                    return;
                case 17: //IFC
                    cycles++;
                    if ((b & a) != 0) skip();
                    return;
                case 18: //IFE
                    cycles++;
                    if (b != a) skip();
                    return;
                case 19: //IFN
                    cycles++;
                    if (b == a) skip();
                    return;
                case 20: //IFG
                    cycles++;
                    if (b <= a) skip();
                    return;
                case 21: //IFA
                    cycles++;
                    if ((short)b <= (short)a) skip();
                    return;
                case 22: //IFL
                    cycles++;
                    if (b >= a) skip();
                    return;
                case 23: //IFU
                    cycles++;
                    if ((short)b >= (short)a) skip();
                    return;
                case 26:{ //ADX
                    cycles+=2;
                    int val = b + a + ex;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 27:{ //SBX
                    cycles+=2;
                    int val = b - a + ex;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 30: //STI
                    cycles++;
                    b = a;
                    set(baddr, b);
                    registers[6]++;
                    registers[7]++;
                    return;
                case 31: //STD
                    cycles++;
                    b = a;
                    set(baddr, b);
                    registers[6]--;
                    registers[7]--;
                    return;
                case 24:
                case 25:
            }
            set(baddr, b);
        }
    }

    private void rememberJump() {
        if(!jumps.containsKey(pc)) {
            jumps.put(pc, new TreeMap<>());
        }
        if(!jumps.get(pc).containsKey(startPC)) {
            jumps.get(pc).put(startPC, 1);
        } else {
            jumps.get(pc).put(startPC, jumps.get(pc).get(startPC) + 1);
        }
    }

    public final String hex(int v) {
        //return String.valueOf(v);
        return String.format("%04X", v);
    }

    public void interrupt(char a) {
        interrupts[iwp = iwp + 1 & 0xFF] = a;
        if (iwp == ip) isOnFire = true;
    }

    public void tickHardware() {
        synchronized (hardware) {
            for (DCPUHardware aHardware : hardware) {
                aHardware.tick60hz();
            }
        }
    }

    public void addHardware(DCPUHardware hw) {
        synchronized (hardware) {
            hardware.add(hw);
        }
    }

    public void removeHardware(DCPUHardware hw) {
        synchronized (hardware) {
            hardware.remove(hw);
        }
    }

    public void run() {
        (new Thread(() -> {
            running = true;
            int hz = 100 * khz;
            int cyclesPerFrame = hz / 60 + 1;

            long nsPerFrame = 16666666L;

            while (running) {
                long nextFrameTime = System.nanoTime() + nsPerFrame;
                while (System.nanoTime() < nextFrameTime) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                long cyclesFrameEnd = cycles + cyclesPerFrame;

                while (cycles < cyclesFrameEnd) {
                    tick();
                }

                tickHardware();
            }
        })).start();
    }

    public void loadBinary(InputStream is) throws IOException {
        int i = 0;
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(is))) {
            for (; i < ram.length; i++) {
                ram[i] = dis.readChar();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            for (; i < ram.length; i++) {
                ram[i] = 0;
            }
        }
        is.close();
    }

    public static void main(String[] args) {
        DCPU dcpu = new DCPU();

        SwingUtilities.invokeLater(() -> {
            try {
                dcpu.setup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setup() throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        InputStream in = getClass().getResourceAsStream("/admiral.bin");
        loadBinary(in);

        clock.connectTo(this);
        //clockv1.connectTo(this);
        //kbd.connectTo(this);
        asciiKbd.connectTo(this);
        floppy.connectTo(this);
        hic.connectTo(this);
        rci.connectTo(this);
        speaker.connectTo(this);

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(e -> {
            if(view.canvas.isFocusOwner()) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    if(kbd.isConnected())
                        kbd.keyPressed(e.getKeyCode(), e.getKeyChar());
                    if(asciiKbd.isConnected())
                        asciiKbd.keyPressed(e.getKeyCode(), e.getKeyChar());
                } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                    if(kbd.isConnected())
                        kbd.keyReleased(e.getKeyCode(), e.getKeyChar());
                    if(asciiKbd.isConnected())
                        asciiKbd.keyReleased(e.getKeyCode(), e.getKeyChar());
                }
            }
            return false;
        });

        // final VirtualMonitor mon = new VirtualMonitor();
        final VirtualMonitor mon = new VirtualPixie();
        mon.connectTo(this);

        view = new LEM1802Viewer();
        view.attach(mon);

        GUI gui = new GUI(this);
        gui.init();

        view.canvas.setup();

        for (DCPUHardware hw : hardware) {
            hw.powerOn();
        }

        running = true;
        run();
    }

}
