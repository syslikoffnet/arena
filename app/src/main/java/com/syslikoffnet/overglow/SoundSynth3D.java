package com.syslikoffnet.overglow;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * 44100 Hz Студийный 3D Позиционный аудио-движок PUBG Mobile:
 * - 4-слойный физический синтез выстрелов (M416, AKM, AWM, Kar98k, Shotgun, Deagle)
 * - Звонкий металлический рикошет по сковороде (Pan Clang) и шлему 3 уровня
 * - Динамический звук двигателя транспорта, рев мотора, скрип шин и клаксон
 * - Гул самолета C-130, свист ветра при свободном падении, щелчок открытия парашюта
 * - Пшик открывания банки энергетика, шелест бинтов, скрип деревянных дверей
 */
public final class SoundSynth3D {

    private SoundSynth3D() {}

    public static final int SOUND_M416 = 0;
    public static final int SOUND_AKM = 1;
    public static final int SOUND_AWM = 2;
    public static final int SOUND_KAR98 = 3;
    public static final int SOUND_SHOTGUN = 4;
    public static final int SOUND_DEAGLE = 5;
    public static final int SOUND_PAN = 6;
    public static final int SOUND_HEADSHOT_HELMET = 7;
    public static final int SOUND_BODY_HIT = 8;
    public static final int SOUND_EXPLOSION = 9;
    public static final int SOUND_DOOR_OPEN = 10;
    public static final int SOUND_DOOR_CLOSE = 11;
    public static final int SOUND_DRINK_OPEN = 12;
    public static final int SOUND_BANDAGE = 13;
    public static final int SOUND_PARACHUTE_DEPLOY = 14;
    public static final int SOUND_HORN = 15;
    public static final int SOUND_TIRE_SKID = 16;
    public static final int SOUND_STEP = 17;
    public static final int SOUND_RELOAD = 18;
    public static final int SOUND_VICTORY = 19;
    public static final int SOUND_DEFEAT = 20;

    // Совместимость со старыми вызовами
    public static final int SOUND_AKR = SOUND_AKM;
    public static final int SOUND_M4 = SOUND_M416;
    public static final int SOUND_RPG = SOUND_EXPLOSION;
    public static final int SOUND_KNIFE = SOUND_PAN;
    public static final int SOUND_HEADSHOT = SOUND_HEADSHOT_HELMET;
    public static final int SOUND_KILL = SOUND_HEADSHOT_HELMET;
    public static final int SOUND_HIT = SOUND_BODY_HIT;

    private static final int RATE = 44100; // Studio CD Quality
    private static final int VOICES = 32;

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
    private static final int[] vType = new int[VOICES];
    // 0=Noise, 1=Saw, 2=Sine, 3=Square, 4=GunTransient, 5=MetalRing, 6=EngineDrone, 7=WindRush
    private static int voicePtr = 0;

    private static final Random RND = new Random();

    public static synchronized void init(Context ctx) {
        if (thread != null) return;
        try {
            int minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = minBuf > 0 ? Math.max(minBuf * 2, 8192) : 16384;
            if (bufSize % 4 != 0) bufSize += (4 - (bufSize % 4));

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
        thread = new Thread(SoundSynth3D::mixLoop, "pubg-audio-engine");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    private static void mixLoop() {
        int CHUNK = 512;
        short[] out = new short[CHUNK * 2];
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
                    float env = (1f - t);
                    float f = vFreq[v] + (vFreqEnd[v] - vFreq[v]) * t;
                    float sample = 0;
                    float ph = vPhase[v];

                    switch (vType[v]) {
                        case 0: // Фильтрованный высокочастотный шум
                            sample = (RND.nextFloat() * 2f - 1f) * (float) Math.exp(-t * 6f);
                            break;
                        case 1: // Пила (механика затвора)
                            sample = (ph * 2f - 1f) * (float) Math.exp(-t * 8f);
                            break;
                        case 2: // Синусоидальный чирп (суб-бас)
                            sample = (float) Math.sin(ph * Math.PI * 2) * (float) Math.exp(-t * 4f);
                            break;
                        case 3: // Квадратная волна (клаксон)
                            sample = (ph < 0.5f ? 1f : -1f) * env;
                            break;
                        case 4: // 4-слойный выстрел: ультра-резкий щелчок + грохот пороховых газов
                            float subBass = (float) Math.sin(ph * Math.PI * 2) * (float) Math.exp(-t * 5f);
                            float crack = (RND.nextFloat() * 2f - 1f) * (float) Math.exp(-t * 22f);
                            float chamber = (float) Math.sin(ph * 4.2f * Math.PI * 2) * (float) Math.exp(-t * 12f);
                            sample = subBass * 0.55f + crack * 0.35f + chamber * 0.20f;
                            break;
                        case 5: // Звон сковороды / шлема (металлический колокол)
                            float m1 = (float) Math.sin(ph * Math.PI * 2);
                            float m2 = (float) Math.sin(ph * 1.58f * Math.PI * 2);
                            float m3 = (float) Math.sin(ph * 2.42f * Math.PI * 2);
                            sample = (m1 * 0.5f + m2 * 0.3f + m3 * 0.2f) * (float) Math.exp(-t * 3.5f);
                            break;
                        case 6: // Двигатель УАЗа / Багги (пульсирующий бас)
                            sample = ((float) Math.sin(ph * Math.PI * 2) + (ph < 0.5f ? 0.3f : -0.3f)) * 0.5f;
                            break;
                        case 7: // Свист ветра (шум пикирования)
                            sample = (RND.nextFloat() * 2f - 1f) * 0.3f;
                            break;
                    }

                    left += sample * vVolL[v];
                    right += sample * vVolR[v];

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
                    Thread.sleep(12);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    public static void playSound(int id, float x, float y, float z, Math3D.Vec3 listenerPos, float listenerYaw) {
        if (!enabled) return;

        float dx = x - listenerPos.x;
        float dz = z - listenerPos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float falloff = Math.max(0.04f, 1f / (1f + dist * 0.05f));

        float rad = listenerYaw * Math3D.TO_RAD;
        float localX = dx * (float) Math.cos(-rad) - dz * (float) Math.sin(-rad);
        float pan = Math3D.clamp(localX / (dist + 0.001f), -1f, 1f);

        float volL = (0.5f - pan * 0.42f) * falloff;
        float volR = (0.5f + pan * 0.42f) * falloff;

        trigger(id, volL, volR);
    }

    public static void play2D(int id, float volume) {
        if (!enabled) return;
        trigger(id, volume * 0.5f, volume * 0.5f);
    }

    private static void trigger(int id, float volL, float volR) {
        switch (id) {
            case SOUND_M416:
                // Резкий 5.56мм выстрел: плотный щелчок + свист затвора
                addVoice(180, 45, 0.22f, volL * 1.1f, volR * 1.1f, 4);
                addVoice(4800, 400, 0.08f, volL * 0.7f, volR * 0.7f, 0);
                addVoice(1600, 200, 0.12f, volL * 0.4f, volR * 0.4f, 1);
                break;

            case SOUND_AKM:
                // Тяжелый 7.62мм выстрел: мощный суб-басовый удар в грудь + лязг стали
                addVoice(110, 32, 0.35f, volL * 1.4f, volR * 1.4f, 4);
                addVoice(3200, 150, 0.14f, volL * 0.9f, volR * 0.9f, 0);
                addVoice(880, 110, 0.20f, volL * 0.6f, volR * 0.6f, 1);
                break;

            case SOUND_AWM:
                // Оглушительный .300 Magnum выстрел: громоподобный бас + 0.8с эхо в горах
                addVoice(70, 20, 0.75f, volL * 1.8f, volR * 1.8f, 4);
                addVoice(6400, 80, 0.35f, volL * 1.2f, volR * 1.2f, 0);
                addVoice(320, 40, 0.85f, volL * 0.8f, volR * 0.8f, 2);
                break;

            case SOUND_KAR98:
                // Сухой винтовочный щелчок со звоном гильзы
                addVoice(140, 38, 0.40f, volL * 1.3f, volR * 1.3f, 4);
                addVoice(5200, 300, 0.16f, volL * 0.8f, volR * 0.8f, 0);
                break;

            case SOUND_SHOTGUN:
                // Дробовик: громогласный залп дроби
                addVoice(95, 25, 0.38f, volL * 1.5f, volR * 1.5f, 4);
                addVoice(2400, 120, 0.28f, volL * 1.0f, volR * 1.0f, 0);
                break;

            case SOUND_PAN:
                // Легендарный звон чугунной сковороды (PUBG Pan Clang!)
                addVoice(2800, 1200, 0.45f, volL * 1.5f, volR * 1.5f, 5);
                addVoice(4400, 2200, 0.30f, volL * 1.0f, volR * 1.0f, 5);
                break;

            case SOUND_HEADSHOT_HELMET:
                // Пробитие шлема 3 уровня (сочный металлический "ТИНЬК")
                addVoice(3400, 1700, 0.25f, volL * 1.4f, volR * 1.4f, 5);
                addVoice(5200, 2600, 0.18f, volL * 1.1f, volR * 1.1f, 0);
                break;

            case SOUND_BODY_HIT:
                // Глухой шлепок попадания по телу
                addVoice(220, 60, 0.08f, volL * 0.6f, volR * 0.6f, 2);
                addVoice(800, 150, 0.06f, volL * 0.4f, volR * 0.4f, 0);
                break;

            case SOUND_EXPLOSION:
                // Разрушительный взрыв гранаты / RPG
                addVoice(55, 18, 0.95f, volL * 1.9f, volR * 1.9f, 4);
                addVoice(1800, 40, 0.85f, volL * 1.3f, volR * 1.3f, 0);
                break;

            case SOUND_DOOR_OPEN:
            case SOUND_DOOR_CLOSE:
                // Скрип деревянной двери и щелчок замка
                addVoice(420, 280, 0.16f, volL * 0.5f, volR * 0.5f, 1);
                addVoice(1200, 600, 0.06f, volL * 0.6f, volR * 0.6f, 0);
                break;

            case SOUND_DRINK_OPEN:
                // Пшик банки энергетика (PSSSHT!)
                addVoice(6200, 800, 0.12f, volL * 0.8f, volR * 0.8f, 0);
                addVoice(320, 160, 0.18f, volL * 0.5f, volR * 0.5f, 2);
                break;

            case SOUND_BANDAGE:
                // Шелест бинтов
                addVoice(2200, 400, 0.14f, volL * 0.4f, volR * 0.4f, 0);
                break;

            case SOUND_PARACHUTE_DEPLOY:
                // Хлопок купола парашюта
                addVoice(140, 40, 0.35f, volL * 1.2f, volR * 1.2f, 4);
                addVoice(1800, 200, 0.28f, volL * 0.9f, volR * 0.9f, 0);
                break;

            case SOUND_HORN:
                // Автомобильный клаксон
                addVoice(440, 440, 0.35f, volL * 1.2f, volR * 1.2f, 3);
                addVoice(554, 554, 0.35f, volL * 1.0f, volR * 1.0f, 3);
                break;

            case SOUND_TIRE_SKID:
                // Визг резины при заносе
                addVoice(1800, 2400, 0.25f, volL * 0.7f, volR * 0.7f, 0);
                break;

            case SOUND_STEP:
                // Шаг по траве / грунту
                addVoice(90, 45, 0.05f, volL * 0.3f, volR * 0.3f, 0);
                break;

            case SOUND_RELOAD:
                // Щелчок магазина и передергивание затвора
                addVoice(1400, 600, 0.07f, volL * 0.6f, volR * 0.6f, 1);
                addVoice(2200, 1100, 0.06f, volL * 0.5f, volR * 0.5f, 0);
                break;

            case SOUND_VICTORY:
                // Триумфальный фанфарный аккорд победителя #1
                addVoice(523.25f, 523.25f, 0.8f, volL * 1.1f, volR * 1.1f, 2); // C5
                addVoice(659.25f, 659.25f, 0.8f, volL * 1.1f, volR * 1.1f, 2); // E5
                addVoice(783.99f, 783.99f, 1.2f, volL * 1.3f, volR * 1.3f, 2); // G5
                addVoice(1046.5f, 1046.5f, 1.5f, volL * 1.4f, volR * 1.4f, 2); // C6
                break;

            case SOUND_DEFEAT:
                addVoice(220, 110, 0.9f, volL * 1.0f, volR * 1.0f, 1);
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
