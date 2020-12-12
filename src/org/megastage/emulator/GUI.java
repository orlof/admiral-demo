package org.megastage.emulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.*;

public class GUI {
    private final DCPU dcpu;

    private final JFrame mainFrame = new JFrame("Admiral Demo");

    private final JLabel floppyLabel = new JLabel();

    private JPanel lem;

    public GUI(DCPU dcpu) {
        this.dcpu = dcpu;
    }

    public void init() {
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.getContentPane().add(createHwPanel());
        mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        mainFrame.setVisible(true);
        mainFrame.createBufferStrategy(2);
        mainFrame.pack();
    }

    private JComponent createHwPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(createLemPanel(), BorderLayout.CENTER);
        p.add(createFloppiesPanel(), BorderLayout.PAGE_END);
        return p;
    }

    private Component createFloppiesPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(createFloppyPanel());
        return p;
    }

    public void updateFloppyName() {
        if(dcpu.floppyFile == null) {
            floppyLabel.setText("No disc");
        } else {
            String filename = dcpu.floppyFile.getName();
            floppyLabel.setText(filename);
        }
    }

    private Component createFloppyPanel() {
        JPanel mainPanel = new JPanel(new GridLayout(1, 1));

        JPanel filePanel = new JPanel(new FlowLayout());

        updateFloppyName();
        filePanel.add(floppyLabel);

//        mainPanel.add(createJButton("Eject", e -> dcpu.floppy.eject()));
        filePanel.add(createJButton("Load", e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Choose floppy image file");
            jfc.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret = jfc.showOpenDialog(mainFrame);
            if (ret == JFileChooser.APPROVE_OPTION) {
                File floppyFile = jfc.getSelectedFile();

                if (floppyFile.isFile()) {
                    dcpu.floppyFile = floppyFile;
                    try {
                        InputStream is = new FileInputStream(floppyFile);
                        dcpu.floppy.insert(new FloppyDisk(is));
                        is.close();
                        updateFloppyName();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    dcpu.floppyFile = null;
                    dcpu.floppy.eject();
                    updateFloppyName();
                }
            } else {
                dcpu.floppyFile = null;
                dcpu.floppy.eject();
                updateFloppyName();
            }
        }));
        filePanel.add(createJButton("Save", e -> {
            if(dcpu.floppyFile != null) {
                try {
                    dcpu.floppy.getDisk().save(dcpu.floppyFile);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }));
        filePanel.add(createJButton("New", e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Choose floppy image file");
            jfc.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret = jfc.showSaveDialog(mainFrame);
            if (ret == JFileChooser.APPROVE_OPTION) {

                dcpu.floppyFile = jfc.getSelectedFile();
                try {
                    InputStream is = new ByteArrayInputStream(new byte[0]);
                    dcpu.floppy.insert(new FloppyDisk(is));
                    is.close();
                    dcpu.floppy.getDisk().save(dcpu.floppyFile);
                    updateFloppyName();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }));

        mainPanel.add(filePanel);
        return mainPanel;
    }

    private JComponent createLemPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        lem = new JPanel(new BorderLayout());
        lem.setBorder(BorderFactory.createLineBorder(Color.lightGray,2,true));
        dcpu.view.canvas.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                lem.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2, true));
            }

            @Override
            public void focusLost(FocusEvent e) {
                lem.setBorder(BorderFactory.createLineBorder(Color.lightGray, 2, true));
            }
        });
        lem.add(dcpu.view.canvas, BorderLayout.CENTER);
        p.add(lem, BorderLayout.CENTER);
        return p;
    }

    private JButton createJButton(String label, ActionListener listener) {
        JButton button = new JButton(label);
        button.addActionListener(listener);
        return button;
    }

}
