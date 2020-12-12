package org.megastage.emulator;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.SquareOscillator;

public class VirtualSpeaker2 extends DCPUHardware {
    public static final int TYPE = 0xC0F00001, REVISION = 0x0001, MANUFACTURER = 0x5672746B;

	private final SquareOscillator squareOscillator0 = new SquareOscillator();
	private final SquareOscillator squareOscillator1 = new SquareOscillator();

	public VirtualSpeaker2() {
        super(TYPE, REVISION, MANUFACTURER);

		squareOscillator0.amplitude.set(0);
		squareOscillator1.amplitude.set(0);

		Synthesizer synth = JSyn.createSynthesizer();
		LineOut lineOut = new LineOut();
		synth.add(lineOut);
		synth.add(squareOscillator0);
		synth.add(squareOscillator1);

		synth.start();
		lineOut.start();
		squareOscillator0.start();
		squareOscillator1.start();

		squareOscillator0.output.connect(0, lineOut.input, 0);
		squareOscillator0.output.connect(0, lineOut.input, 1);
		squareOscillator1.output.connect(0, lineOut.input, 0);
		squareOscillator1.output.connect(0, lineOut.input, 1);
    }

    public void interrupt() {
        int a = dcpu.registers[0];

		switch(a) {
			case 0: //SET_FREQUENCY_CHANNEL_1
				if(dcpu.registers[1] != 0) {
					squareOscillator0.amplitude.set(0.05);
					squareOscillator0.frequency.set(dcpu.registers[1]);
				} else {
					squareOscillator0.amplitude.set(0.0);
				}
				break;
			case 1: //SET_FREQUENCY_CHANNEL_2
				if(dcpu.registers[1] != 0) {
					squareOscillator1.amplitude.set(0.03);
					squareOscillator1.frequency.set(dcpu.registers[1]);
				} else {
					squareOscillator1.amplitude.set(0.0);
				}
				break;
		}
    }

    @Override
    public void powerOff() {
        squareOscillator0.amplitude.set(0);
		squareOscillator1.amplitude.set(0);
    }
}
