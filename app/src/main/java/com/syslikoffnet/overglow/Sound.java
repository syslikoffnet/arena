package com.syslikoffnet.overglow;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Звуковой движок без единого аудиофайла: SFX и музыка синтезируются кодом.
 * Один AudioTrack-микшер: музыкальный луп + до 12 голосов эффектов.
 */
public final class Sound {

    private Sound() {}

    // --- ID эффектов ---
    public static final int SHOOT = 0, HIT = 1, KILL = 2, PICKUP = 3, LEVELUP = 4,
            HURT = 5, BOOM = 6, BOSS = 7, CLICK = 8, XP = 9, LASER = 10, HEAL = 11;

    private static final int RATE = 22050;
    private static final int VOICES = 12;

    private static AudioTrack track;
    private static Thread thread;
    private static volatile boolean running;
    private static boolean soundOn = true, musicOn = true;

    private static float[] musicLoop;   // сгенерированный луп
    private static volatile boolean musicPlaying = false;

    // Активные голоса
    private static final float[] vPhase = new float[VOICES];
    private static final float[] vFreq = new float[VOICES];
    private static final float[] vFreqEnd = new float[VOICES];
    private static final float[] vPos = new float[VOICES];
    private static final float[] vLen = new float[VOICES];
    private static final float[] vVol = new float[VOICES];
    private static final int[] vKind = new int[VOICES]; // 0 квадрат 1 пила 2 синус 3 шум
    private static int voicePtr = 0;

    private static final Random RND = new Random();
    private static float lastShoot = 0;

    /** Инициализация (вызов один раз при старте игры). */
    public static synchronized void init(final Context ctx) {
        if (thread != null) return;
        soundOn = S.sound(ctx);
        musicOn = S.music(ctx);

        try {
            int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = minBuf > 0 ? Math.max(minBuf * 2, 4096) : 8192;
            // Размер буфера в байтах должен быть кратен размеру фрейма (2 байта для 16-бит моно)
            if (bufSize % 2 != 0) {
                bufSize++;
            }

            track = new AudioTrack(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    bufSize, AudioTrack.MODE_STREAM,
                    0 /* sessionId */);

            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                track = null;
            }
        } catch (Throwable t) {
            track = null;
        }

        musicLoop = genMusic();

        running = true;
        thread = new Thread(Sound::mixLoop, "overglow-audio");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    public static synchronized void applySettings(Context ctx) {
        soundOn = S.sound(ctx);
        boolean wantMusic = S.music(ctx);
        if (wantMusic != musicOn) {
            musicOn = wantMusic;
            if (wantMusic) startMusic();
        }
    }

    public static void resume() {
        if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                track.play();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void pause() {
        if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                track.pause();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void mixLoop() {
        int CHUNK = 1024;
        short[] out = new short[CHUNK];
        int mPos = 0;
        try {
            if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
                track.play();
            }
        } catch (Throwable ignored) {
        }
        while (running) {
            for (int i = 0; i < CHUNK; i++) {
                float s = 0;
                // Музыка
                if (musicPlaying && musicLoop != null && musicLoop.length > 0) {
                    s += musicLoop[mPos] * 0.30f;
                    mPos++;
                    if (mPos >= musicLoop.length) mPos = 0;
                }
                // Голоса эффектов
                for (int v = 0; v < VOICES; v++) {
                    if (vPos[v] >= vLen[v]) continue;
                    float t = vPos[v] / vLen[v];           // 0..1
                    float env = (1 - t) * (1 - t);          // затухание
                    float f = vFreq[v] + (vFreqEnd[v] - vFreq[v]) * t;
                    float sample;
                    float ph = vPhase[v];
                    switch (vKind[v]) {
                        case 0: sample = ph < 0.5f ? 1f : -1f; break;   // квадрат
                        case 1: sample = ph * 2f - 1f; break;           // пила
                        case 2: sample = (float) Math.sin(ph * Math.PI * 2); break;
                        default: sample = RND.nextFloat() * 2f - 1f;    // шум
                    }
                    s += sample * env * vVol[v];
                    vPhase[v] = (ph + f / RATE) % 1f;
                    vPos[v]++;
                }
                out[i] = (short) (Math.max(-1f, Math.min(1f, s)) * 32767f);
            }
            if (track != null && track.getState() == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.write(out, 0, CHUNK);
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /** Запустить звук (короткий синтез). */
    public static void play(int id) {
        if (!soundOn) return;
        // Ограничение частоты назойливых звуков
        float now = System.nanoTime() / 1_000_000_000f;
        if (id == SHOOT) {
            if (now - lastShoot < 0.06f) return;
            lastShoot = now;
        }
        switch (id) {
            case SHOOT: voice(880, 240, 0.05f, 0.10f, 0); break;
            case HIT: voice(300, 200, 0.04f, 0.06f, 3); break;
            case KILL: voice(500, 90, 0.16f, 0.14f, 1); break;
            case PICKUP: voice(700, 1400, 0.09f, 0.12f, 2); break;
            case XP: voice(900, 1600, 0.05f, 0.05f, 2); break;
            case LEVELUP: voice(440, 880, 0.5f, 0.2f, 2); break;
            case HURT: voice(160, 70, 0.25f, 0.30f, 1); break;
            case BOOM: voice(90, 30, 0.4f, 0.35f, 3); break;
            case BOSS: voice(70, 45, 1.0f, 0.4f, 1); break;
            case CLICK: voice(600, 900, 0.05f, 0.10f, 2); break;
            case LASER: voice(1400, 300, 0.18f, 0.12f, 1); break;
            case HEAL: voice(520, 1040, 0.3f, 0.15f, 2); break;
            default: break;
        }
    }

    private static void voice(float f0, float f1, float len, float vol, int kind) {
        int v = voicePtr;
        voicePtr = (voicePtr + 1) % VOICES;
        vFreq[v] = f0;
        vFreqEnd[v] = f1;
        vLen[v] = Math.max(1f, len * RATE);
        vPos[v] = 0;
        vVol[v] = vol;
        vKind[v] = kind;
        vPhase[v] = 0;
    }

    // ------------------------------------------------------------- музыка

    /** Синтез-вэйв луп: бас + арпеджио + пэд, 8 тактов по 130 BPM. */
    private static float[] genMusic() {
        int bpm = 130;
        int beat = RATE * 60 / bpm;         // сэмплов на долю
        int bar = beat * 4;                 // такт
        int total = bar * 8;
        float[] mix = new float[total];

        // Аккордовая последовательность: Am F C G (по 2 такта)
        float[][] chords = {
                {220f, 261.63f, 329.63f},   // Am
                {174.61f, 220f, 261.63f},   // F
                {196f, 261.63f, 329.63f},   // G
                {130.81f, 164.81f, 196f},   // C
        };
        float[] bassRoots = {110f, 87.31f, 98f, 65.41f};

        Random rnd = new Random(42); // детерминированный луп

        for (int b = 0; b < 32; b++) {           // 32 доли
            int chord = (b / 8) % 4;
            // Бас: восьмые, квадрат
            for (int e = 0; e < 2; e++) {
                int start = b * beat + e * beat / 2;
                float f = bassRoots[chord] * (e == 3 ? 2f : 1f);
                addNote(mix, start, beat / 2 - 200, f * 0.5f, 0.20f, 0);
            }
            // Арпеджио: шестнадцатые на пиле, нота из аккорда
            for (int s = 0; s < 4; s++) {
                if (rnd.nextInt(8) < 6) {
                    int start = b * beat + s * beat / 4;
                    float f = chords[chord][rnd.nextInt(3)] * 2f;
                    addNote(mix, start, beat / 4 - 400, f, 0.055f, 1);
                }
            }
        }
        // Пэд: длинные синусы на весь такт
        for (int barI = 0; barI < 8; barI++) {
            int chord = (barI / 2) % 4;
            for (float f : chords[chord]) {
                addNote(mix, barI * bar, bar - 1, f, 0.030f, 2);
            }
        }
        // Хай-хэт: шум на каждую восьмую
        for (int e = 0; e < 64; e++) {
            addNote(mix, e * beat / 2, 900, 0, 0.020f, 3);
        }

        return mix;
    }

    private static void addNote(float[] mix, int start, int len, float freq,
                                float vol, int kind) {
        int end = Math.min(mix.length, start + Math.max(1, len));
        float phase = 0;
        for (int i = start; i < end; i++) {
            float t = (i - start) / (float) Math.max(1, end - start);
            float env = kind == 2 ? 0.7f + 0.3f * (float) Math.sin(t * Math.PI)
                    : (1 - t);
            float s;
            switch (kind) {
                case 0: s = phase < 0.5f ? 1f : -1f; break;
                case 1: s = phase * 2f - 1f; break;
                case 2: s = (float) Math.sin(phase * Math.PI * 2); break;
                default: s = RND.nextFloat() * 2f - 1f; break;
            }
            mix[i] += s * env * vol;
            phase = (phase + freq / RATE) % 1f;
        }
    }

    public static void startMusic() {
        if (musicOn) musicPlaying = true;
    }

    public static void stopMusic() {
        musicPlaying = false;
    }

    public static synchronized void destroy() {
        running = false;
        if (thread != null) {
            try {
                thread.join(800);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
        if (track != null) {
            try {
                if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                    track.stop();
                }
                track.release();
            } catch (Throwable ignored) {
            }
            track = null;
        }
    }
}
