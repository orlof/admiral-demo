package org.megastage.emulator;

import java.util.Random;

public abstract class DCPUHardware {
    public static final int TYPE_LEM = 0x7349F615;
    public static final int TYPE_KEYBOARD = 0x30CF7406;
    public static final int TYPE_CLOCK = 0x12D0B402;
    public static final int MANUFACTORER_NYA_ELEKTRISKA = 0x1C6C8B36;
    public static final int MANUFACTORER_MOJANG = 0x4AB55488;
    public static final int MANUFACTORER_MACKAPAR = 0x1EB37E91;
    public int type;
    public int revision;
    public int manufactorer;
    public DCPU dcpu;

    public DCPUHardware(int type, int revision, int manufactorer) {
        this.type = type;
        this.revision = revision;
        this.manufactorer = manufactorer;
    }

    public void connectTo(DCPU dcpu) {
        this.dcpu = dcpu;
        dcpu.addHardware(this);
    }

    public void query() {
        this.dcpu.registers[0] = (char) (this.type & 0xFFFF);
        this.dcpu.registers[1] = (char) (this.type >> 16 & 0xFFFF);
        this.dcpu.registers[2] = (char) (this.revision & 0xFFFF);
        this.dcpu.registers[3] = (char) (this.manufactorer & 0xFFFF);
        this.dcpu.registers[4] = (char) (this.manufactorer >> 16 & 0xFFFF);
    }

    public void interrupt() {
    }

    public void tick60hz() {
    }

    public void powerOff() {
    }

    public void powerOn() {
    }

    public boolean isConnected() {
        return dcpu != null;
    }

    public void onDestroy() {
    }
}