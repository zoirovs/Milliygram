package org.telegram.milliy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Ovoz o'zgartirgich: ovozli xabar yozilayotganda PCM (16 bit, mono) bo'laklari kodlanishdan oldin o'zgartiriladi.
 * Effektlar: bola (baland), bahodir (past), robot, aks-sado. Tezlik o'zgarmaydi (real vaqtda granular pitch-shift).
 */
public class MilliyVoiceFx {

    public static final int OFF = 0, CHILD = 1, DEEP = 2, ROBOT = 3, ECHO = 4;

    private static int sampleRate;
    private static long lastCallMs;
    private static int effect;

    // pitch-shift holati
    private static float[] ring;
    private static int writePos;
    private static float delay;
    private static int window;

    // robot / aks-sado holati
    private static double phase;
    private static float[] echo;
    private static int echoPos;

    public static int effect() {
        return MilliyPro.prefs().getInt("voice_fx", OFF);
    }

    private static void reset(int rate, int fx) {
        sampleRate = rate;
        effect = fx;
        window = Math.max(256, rate / 20); // 50 ms
        ring = new float[window * 2];
        writePos = 0;
        delay = 0;
        phase = 0;
        echo = new float[Math.max(1, rate / 4)]; // 250 ms
        echoPos = 0;
    }

    /** MediaController yozish oqimidan chaqiriladi. buffer — native tartibdagi 16 bitli namunalar, len — baytlar soni. */
    public static void process(ByteBuffer buffer, int len, int rate) {
        int fx = effect();
        if (fx == OFF || len <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (rate != sampleRate || fx != effect || now - lastCallMs > 1000 || ring == null) {
            reset(rate, fx); // yangi yozuv boshlandi
        }
        lastCallMs = now;
        ByteBuffer b = buffer.duplicate().order(ByteOrder.nativeOrder());
        int n = len / 2;
        for (int i = 0; i < n; i++) {
            int pos = i * 2;
            float x = b.getShort(pos);
            float y;
            switch (fx) {
                case CHILD:
                    y = pitch(x, 1.5f);
                    break;
                case DEEP:
                    y = pitch(x, 0.72f);
                    break;
                case ROBOT:
                    phase += 2 * Math.PI * 55.0 / sampleRate;
                    if (phase > 2 * Math.PI) {
                        phase -= 2 * Math.PI;
                    }
                    y = (float) (x * Math.sin(phase)) * 1.6f;
                    break;
                default: {
                    float d = echo[echoPos];
                    y = x + d * 0.45f;
                    echo[echoPos] = y;
                    echoPos = (echoPos + 1) % echo.length;
                    break;
                }
            }
            if (y > 32767) {
                y = 32767;
            } else if (y < -32768) {
                y = -32768;
            }
            b.putShort(pos, (short) y);
        }
    }

    /** Ikki o'qish nuqtali, Hann bilan almashib turuvchi kechikish chizig'i (granular pitch-shift). */
    private static float pitch(float x, float ratio) {
        int size = ring.length;
        ring[writePos] = x;
        delay += 1f - ratio;
        if (delay < 0) {
            delay += window;
        } else if (delay >= window) {
            delay -= window;
        }
        float d2 = delay + window / 2f;
        if (d2 >= window) {
            d2 -= window;
        }
        float g1 = (float) Math.pow(Math.sin(Math.PI * delay / window), 2);
        float y = g1 * read(writePos - delay, size) + (1f - g1) * read(writePos - d2, size);
        writePos = (writePos + 1) % size;
        return y;
    }

    private static float read(float pos, int size) {
        while (pos < 0) {
            pos += size;
        }
        int i0 = (int) pos;
        float frac = pos - i0;
        int i1 = (i0 + 1) % size;
        i0 %= size;
        return ring[i0] * (1f - frac) + ring[i1] * frac;
    }
}
