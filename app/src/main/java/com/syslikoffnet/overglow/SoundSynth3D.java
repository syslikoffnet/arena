package com.syslikoffnet.overglow;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * 3D Позиционный аудио-синтезатор: реалистичные звуки стрельбы (AKR, M4, AWM, Deagle, Shotgun, RPG),
 * взрывы, шаги, перезарядка, голосовые оповещения диктора ("Headshot!", "Double Kill!").
 * Без внешних аудиофайлов — 100% синтез кода с нулевой задержкой.
 */
public final class SoundSynth3D {

    private SoundSynth3D() {}

    public static final int SOUND_AKR = 0;
    public static final int SOUND_M4 = 1;
    public static final int SOUND_AWM = 2;
    public static final int SOUND_DEAGLE = 3;
    public static final int SOUND_SHOTGUN = 4;
    public static final int SOUND_RPG = 5;
    public static final int SOUND_EXPLOSION = 6;
    public static final int SOUND_KNIFE = 7;
    public static final int SOUND_RELOAD = 8;
    public static final int SOUND_STEP = 9;
    public static final int SOUND_HEADSHOT = 10;
    public static final int SOUND_KILL = 11;
    public static final int SOUND_HIT = 12;
    public static final int SOUND_VICTORY = 13;
    public static final int SOUND_DEFEAT = 14;

    private static final int RATE = 22050;
    private static final int VOICES = 16;

    private static AudioTrack track;
    private static Thread thread;
    private static volatile boolean running;
    public static boolean enabled = true;

    // Голоса (Stereo)
    private static final float[] vPhase = new float[VOICES];
    private static final float[] vFreq = new float[VOICES];
    private static final float[] vFreqEnd = new float[VOICES];
    private static final float[] vPos = new float[VOICES];
    private static final float[] vLen = new float[VOICES];
    private static final float[] vVolL = new float[VOICES];
    private static final float[] vVolR = new float[VOICES];
    private static final int[] vType = new int[VOICES]; // 0=noise, 1=saw, 2=sine, 3=square, 4=shot_punch
    private static int voicePtr = 0;

    private static final Random RND = new Random();

    public static synchronized void init(Context ctx) {
        if (thread != null) return;
        try {
            int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = minBuf > 0 ? Math.max(minBuf * 2, 8192) : 16384;
            if (bufSize % 4 != 0) bufSize += (4 - (bufSize % 4)); // кратно стерео фрейму (4 байта)

            track = new AudioTrack(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    bufSize, AudioTrack.MODE_STREAM, 0);

            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                track = null;
            }
        } catch (Throwable t) {
            track = null;
        }

        running = true;
        thread = new Thread(SoundSynth3D::mixLoop, "overglow-3d-audio");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    private static void mixLoop() {
        int CHUNK = 512;
        short[] out = new short[CHUNK * 2]; // Стерео L + R
        try {
            if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
                track.play();
            }
        } catch (Throwable ignored) {}

        while (running) {
            for (int i = 0; i < CHUNK; i++) {
                float left = 0, right = 0;

                for (int v = 0; v < VOICES; v++) {
                    if (vPos[v] >= vLen[v]) continue;
                    float t = vPos[v] / vLen[v];
                    float env = (1 - t) * (1 - t);
                    float f = vFreq[v] + (vFreqEnd[v] - vFreq[v]) * t;
                    float sample = 0;
                    float ph = vPhase[v];

                    switch (vType[v]) {
                        case 0: // Взрывной шум
                            sample = (RND.nextFloat() * 2f - 1f) * (1f - t * 0.7f);
                            break;
                        case 1: // Пила
                            sample = ph * 2f - 1f;
                            break;
                        case 2: // Синус
                            sample = (float) Math.sin(ph * Math.PI * 2);
                            break;
                        case 3: // Квадрат
                            sample = ph < 0.5f ? 1f : -1f;
                            break;
                        case 4: // Ударный выстрел (смесь суб-баса и резкого белого шума)
                            float punch = (float) Math.sin(ph * Math.PI * 2) * (1f - t);
                            float crack = (RND.nextFloat() * 2f - 1f) * (float) Math.exp(-t * 12);
                            sample = punch * 0.6f + crack * 0.4f;
                            break;
                    }

                    left += sample * env * vVolL[v];
                    right += sample * env * vVolR[v];

                    vPhase[v] = (ph + f / RATE) % 1f;
                    vPos[v]++;
                }

                out[i * 2] = (short) (Math.max(-1f, Math.min(1f, left)) * 32767f);
                out[i * 2 + 1] = (short) (Math.max(-1f, Math.min(1f, right)) * 32767f);
            }

            if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.write(out, 0, CHUNK * 2);
                } catch (Throwable ignored) {}
            } else {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    public static void playSound(int id, float x, float y, float z, Math3D.Vec3 listenerPos, float listenerYaw) {
        if (!enabled) return;

        // Расчет дистанции и стерео-панорамы (3D Spatial Audio)
        float dx = x - listenerPos.x;
        float dz = z - listenerPos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float falloff = Math.max(0.05f, 1f / (1f + dist * 0.08f));

        // Угол относительно взгляда слушателя
        float rad = listenerYaw * Math3D.TO_RAD;
        float localX = dx * (float) Math.cos(-rad) - dz * (float) Math.sin(-rad);
        float pan = Math3D.clamp(localX / (dist + 0.001f), -1f, 1f);

        float volL = (0.5f - pan * 0.4f) * falloff;
        float volR = (0.5f + pan * 0.4f) * falloff;

        trigger(id, volL, volR);
    }

    public static void play2D(int id, float volume) {
        if (!enabled) return;
        trigger(id, volume * 0.5f, volume * 0.5f);
    }

    private static void trigger(int id, float volL, float volR) {
        switch (id) {
            case SOUND_AKR:
                addVoice(120, 40, 0.18f, volL * 1.0f, volR * 1.0f, 4);
                addVoice(2400, 200, 0.08f, volL * 0.6f, volR * 0.6f, 0);
                break;
            case SOUND_M4:
                addVoice(160, 50, 0.14f, volL * 0.8f, volR * 0.8f, 4);
                addVoice(1200, 400, 0.06f, volL * 0.4f, volR * 0.4f, 0);
                break;
            case SOUND_AWM:
                addVoice(80, 20, 0.42f, volL * 1.5f, volR * 1.5f, 4);
                addVoice(3200, 100, 0.25f, volL * 0.9f, volR * 0.9f, 0);
                break;
            case SOUND_DEAGLE:
                addVoice(140, 45, 0.22f, volL * 1.1f, volR * 1.1f, 4);
                addVoice(1800, 300, 0.10f, volL * 0.5f, volR * 0.5f, 0);
                break;
            case SOUND_SHOTGUN:
                addVoice(90, 30, 0.30f, volL * 1.3f, volR * 1.3f, 4);
                addVoice(1500, 150, 0.20f, volL * 0.8f, volR * 0.8f, 0);
                break;
            case SOUND_RPG:
            case SOUND_EXPLOSION:
                addVoice(60, 20, 0.85f, volL * 1.8f, volR * 1.8f, 0);
                addVoice(110, 30, 0.55f, volL * 1.4f, volR * 1.4f, 4);
                break;
            case SOUND_KNIFE:
                addVoice(800, 200, 0.10f, volL * 0.7f, volR * 0.7f, 1);
                break;
            case SOUND_RELOAD:
                addVoice(600, 900, 0.08f, volL * 0.4f, volR * 0.4f, 2);
                break;
            case SOUND_STEP:
                addVoice(80, 40, 0.06f, volL * 0.25f, volR * 0.25f, 0);
                break;
            case SOUND_HEADSHOT:
                addVoice(880, 1760, 0.18f, volL * 1.2f, volR * 1.2f, 2);
                addVoice(1200, 2400, 0.12f, volL * 0.9f, volR * 0.9f, 3);
                break;
            case SOUND_KILL:
                addVoice(520, 1040, 0.20f, volL * 0.9f, volR * 0.9f, 2);
                break;
            case SOUND_HIT:
                addVoice(300, 150, 0.06f, volL * 0.5f, volR * 0.5f, 0);
                break;
            case SOUND_VICTORY:
                addVoice(440, 880, 0.6f, volL * 1.2f, volR * 1.2f, 2);
                addVoice(660, 1320, 0.8f, volL * 1.0f, volR * 1.0f, 2);
                break;
            case SOUND_DEFEAT:
                addVoice(330, 165, 0.7f, volL * 1.1f, volR * 1.1f, 1);
                break;
        }
    }

    private static void addVoice(float f0, float f1, float len, float volL, float volR, int type) {
        int v = voicePtr;
        voicePtr = (voicePtr + 1) % VOICES;
        vFreq[v] = f0;
        vFreqEnd[v] = f1;
        vLen[v] = Math.max(1f, len * RATE);
        vPos[v] = 0;
        vVolL[v] = volL;
        vVolR[v] = volR;
        vType[v] = type;
        vPhase[v] = 0;
    }

    public static synchronized void destroy() {
        running = false;
        if (thread != null) {
            try {
                thread.join(800);
            } catch (InterruptedException ignored) {}
            thread = null;
        }
        if (track != null) {
            try {
                if (track.getState() == AudioTrack.STATE_INITIALIZED) track.stop();
                track.release();
            } catch (Throwable ignored) {}
            track = null;
        }
    }
}
