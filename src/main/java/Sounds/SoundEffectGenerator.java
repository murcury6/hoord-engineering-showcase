package Sounds;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Random;

/** Deterministic, local synthesis of short game sound effects. */
public final class SoundEffectGenerator {
    private static final int SAMPLE_RATE = 44100;
    private static final double TAU = Math.PI * 2.0;

    public enum Kind {
        FIRING("Firing"), ENGINE("Engine"), ABILITY("Ability"), IMPACT("Impact"),
        EXPLOSION("Explosion"), PICKUP("Pickup"), INTERFACE("Interface");

        private final String label;
        Kind(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Parameters(Kind kind, double durationSeconds, double pitchHz,
                             double intensity, long seed) { }
    public record Result(Parameters parameters, float[] samples, int sampleRate) { }

    private SoundEffectGenerator() { }

    public static Result generate(Parameters parameters) {
        validate(parameters);
        int length = (int)Math.round(parameters.durationSeconds() * SAMPLE_RATE);
        float[] samples = new float[length];
        Random random = new Random(parameters.seed());
        double phase = random.nextDouble() * TAU;
        double secondPhase = random.nextDouble() * TAU;
        double texturePhase = random.nextDouble() * TAU;
        double color = 0.9 + random.nextDouble() * 0.2;
        double slowNoise = 0.0;
        double fastNoise = 0.0;
        double peak = 0.0;
        double duration = length / (double)SAMPLE_RATE;
        double pitch = parameters.pitchHz();
        double intensity = parameters.intensity();

        for (int i = 0; i < length; i++) {
            double time = i / (double)SAMPLE_RATE;
            double progress = i / (double)(length - 1);
            double noise = random.nextDouble() * 2.0 - 1.0;
            slowNoise += 0.035 * (noise - slowNoise);
            fastNoise += 0.32 * (noise - fastNoise);
            double frequency;
            double secondFrequency;
            double value;

            switch (parameters.kind()) {
                case FIRING -> {
                    frequency = pitch * (0.32 + 1.9 * Math.exp(-progress * 7.0));
                    secondFrequency = frequency * (1.98 + 0.025 * color);
                    value = (Math.sin(phase) + 0.3 * Math.sin(secondPhase))
                            * Math.exp(-progress * 5.0);
                    value += (noise - fastNoise) * (0.18 + intensity * 0.2)
                            * Math.exp(-progress * 30.0);
                }
                case ENGINE -> {
                    // Whole-cycle rates and continuous modulation avoid jumps in the motor texture.
                    double motor = Math.max(1.0, Math.rint(pitch * duration)) / duration;
                    double pulse = Math.max(1.0, Math.rint((5.0 + color * 4.0) * duration)) / duration;
                    frequency = motor * (1.0 + 0.012 * Math.sin(TAU * pulse * time + texturePhase));
                    secondFrequency = motor * 2.0;
                    double rumble = 0.82 + 0.18 * Math.sin(TAU * pulse * time + texturePhase);
                    value = (Math.sin(phase) + 0.36 * Math.sin(secondPhase)) * rumble;
                    value += slowNoise * (0.8 + intensity) + fastNoise * 0.09;
                }
                case ABILITY -> {
                    frequency = pitch * (0.55 + progress * progress * 1.45);
                    secondFrequency = frequency * (1.5 + color * 0.015);
                    double swell = 0.4 + 0.6 * Math.sin(Math.PI * progress);
                    double shimmer = 0.75 + 0.25 * Math.sin(TAU * (9.0 + 12.0 * progress) * time + texturePhase);
                    value = (Math.sin(phase) + 0.45 * Math.sin(secondPhase)) * swell * shimmer;
                    value += fastNoise * 0.12 * (1.0 - progress);
                }
                case IMPACT -> {
                    frequency = pitch * (0.72 + 0.4 * Math.exp(-progress * 18.0));
                    secondFrequency = pitch * 2.71 * color;
                    value = Math.sin(phase) * Math.exp(-progress * 9.0)
                            + 0.55 * Math.sin(secondPhase) * Math.exp(-progress * 16.0)
                            + fastNoise * 1.3 * Math.exp(-progress * 24.0);
                }
                case EXPLOSION -> {
                    frequency = Math.max(25.0, pitch * 0.24) * (1.0 - progress * 0.7);
                    secondFrequency = frequency * 1.37;
                    value = slowNoise * 5.0 * Math.exp(-progress * 3.5)
                            + fastNoise * 1.4 * Math.exp(-progress * 10.0)
                            + Math.sin(phase) * 0.45 * Math.exp(-progress * 6.0);
                }
                case PICKUP -> {
                    // A continuous glide links the major-triad steps without phase resets.
                    double step = progress * 3.0;
                    double semitone = step < 1.0 ? 4.0 * smooth(step)
                            : step < 2.0 ? 4.0 + 3.0 * smooth(step - 1.0)
                            : 7.0 + 5.0 * smooth(step - 2.0);
                    frequency = pitch * Math.pow(2.0, semitone / 12.0);
                    secondFrequency = frequency * 2.0;
                    value = (Math.sin(phase) + 0.27 * Math.sin(secondPhase))
                            * (0.65 + 0.35 * Math.pow(Math.sin(Math.PI * step), 2.0))
                            * (1.0 - progress * 0.35);
                }
                case INTERFACE -> {
                    frequency = pitch * (1.0 + 0.25 * smooth(progress));
                    secondFrequency = frequency * 1.5;
                    value = (Math.sin(phase) + 0.22 * Math.sin(secondPhase))
                            * Math.exp(-progress * 3.0);
                }
                default -> throw new IllegalStateException("Unsupported effect kind");
            }

            phase = advance(phase, frequency);
            secondPhase = advance(secondPhase, secondFrequency);
            double attack = parameters.kind() == Kind.ENGINE ? Math.min(0.06, duration * 0.15)
                    : Math.min(0.006, duration * 0.05);
            double release = parameters.kind() == Kind.ENGINE ? Math.min(0.06, duration * 0.15)
                    : Math.min(0.04, duration * 0.2);
            double remaining = (length - 1 - i) / (double)SAMPLE_RATE;
            value *= smooth(time / attack) * smooth(remaining / release);
            samples[i] = (float)value;
            peak = Math.max(peak, Math.abs(samples[i]));
        }

        // Keep headroom and make intensity control loudness consistently across effect families.
        double gain = peak > 0.0 ? 0.9 * intensity / peak : 0.0;
        for (int i = 0; i < samples.length; i++) samples[i] = (float)(samples[i] * gain);
        samples[0] = 0.0F;
        samples[samples.length - 1] = 0.0F;
        return new Result(parameters, samples, SAMPLE_RATE);
    }

    /** Writes standard mono PCM16 WAV data; an existing destination is never replaced. */
    public static void writeWav(Result result, Path destination) throws IOException {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(destination, "destination");
        validate(result.parameters());
        float[] samples = Objects.requireNonNull(result.samples(), "samples");
        if (result.sampleRate() != SAMPLE_RATE
                || samples.length != (int)Math.round(result.parameters().durationSeconds() * SAMPLE_RATE)) {
            throw new IllegalArgumentException("Result must contain the generated 44100 Hz mono sample count");
        }
        for (float sample : samples) {
            if (!Float.isFinite(sample) || Math.abs(sample) > 1.0F) {
                throw new IllegalArgumentException("Samples must be finite and normalized to [-1, 1]");
            }
        }
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int value = Math.round(samples[i] * 32767.0F);
            pcm[i * 2] = (byte)value;
            pcm[i * 2 + 1] = (byte)(value >>> 8);
        }
        try (OutputStream output = Files.newOutputStream(destination,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            output.write(new byte[]{'R', 'I', 'F', 'F'});
            littleEndian(output, 36 + pcm.length, 4);
            output.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
            littleEndian(output, 16, 4);
            littleEndian(output, 1, 2);
            littleEndian(output, 1, 2);
            littleEndian(output, SAMPLE_RATE, 4);
            littleEndian(output, SAMPLE_RATE * 2, 4);
            littleEndian(output, 2, 2);
            littleEndian(output, 16, 2);
            output.write(new byte[]{'d', 'a', 't', 'a'});
            littleEndian(output, pcm.length, 4);
            output.write(pcm);
        }
    }

    private static void validate(Parameters parameters) {
        if (parameters == null || parameters.kind() == null) {
            throw new IllegalArgumentException("Choose a sound effect kind");
        }
        range(parameters.durationSeconds(), 0.1, 10.0, "Duration in seconds");
        range(parameters.pitchHz(), 40.0, 4000.0, "Pitch in Hz");
        range(parameters.intensity(), 0.0, 1.0, "Intensity");
    }

    private static void range(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }

    private static double smooth(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double advance(double phase, double frequency) {
        return (phase + TAU * Math.min(SAMPLE_RATE * 0.42, frequency) / SAMPLE_RATE) % TAU;
    }

    private static void littleEndian(OutputStream output, int value, int bytes) throws IOException {
        for (int i = 0; i < bytes; i++) output.write(value >>> (8 * i) & 0xff);
    }
}
