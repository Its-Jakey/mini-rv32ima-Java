package risc;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TTY extends JFrame {
    private final JTextArea out;
    private final List<Byte> charBuffer;

    private int lastSize = 0;

    public TTY() {
        setSize(800, 460);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        charBuffer = new ArrayList<>();

        out = new JTextArea();
        out.setEditable(false);
        out.setLineWrap(true);

        out.setBackground(Color.BLACK);
        out.setForeground(Color.WHITE);

        //DefaultCaret caret = (DefaultCaret)out.getCaret();
        //caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        out.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
                charBuffer.add((byte) e.getKeyChar());
            }
        });

        JScrollPane jsp = new JScrollPane(out);
        jsp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        jsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        jsp.getVerticalScrollBar().setAutoscrolls(true);

        jsp.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent e) {
                if (lastSize != e.getAdjustable().getMaximum()) {
                    e.getAdjustable().setValue(e.getAdjustable().getMaximum());
                    lastSize = e.getAdjustable().getMaximum();
                }
            }
        });

        add(jsp);
    }

    public void write(char c) {
        //out.append(c + "");
        System.out.println(c);
    }

    public void write(byte b) {
        //out.append(((char) b) + "");
        System.out.write(b);
    }

    public void write(byte[] bytes, int off, int len) {
        //out.append(new String(Arrays.copyOfRange(bytes, off, off + len)));
        System.out.write(bytes, off, len);
    }

    public void printf(String format, Object... args) {
        //out.append(String.format(format, args));
        System.out.printf(format, args);
    }

    public int read() {
        if (charBuffer.isEmpty())
            return -1;
        return charBuffer.remove(0);
    }

    public boolean ready() {
        return !charBuffer.isEmpty();
    }
}
