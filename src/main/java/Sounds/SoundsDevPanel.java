package Sounds;

import hoordGame.ProjectPaths;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** Procedural game-effect authoring and explicit WAV export. */
public final class SoundsDevPanel extends JPanel {
   private static final long serialVersionUID = 1L;
   private static final Color BG = new Color(7, 10, 19);
   private static final Color PANEL = new Color(18, 24, 38);
   private static final Color FIELD = new Color(11, 16, 28);
   private static final Color TEXT = new Color(235, 243, 255);
   private static final Color DIM = new Color(145, 161, 188);
   private static final Color CYAN = new Color(87, 218, 235);
   private static final Color ERROR = new Color(239, 103, 117);
   private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "sound-effect-generator");
      thread.setDaemon(true);
      return thread;
   });
   private final Path outputRoot = ProjectPaths.userDataRoot().resolve("sound_effects");
   private final JComboBox<SoundEffectGenerator.Kind> kind = new JComboBox<>(SoundEffectGenerator.Kind.values());
   private final JSpinner duration = new JSpinner(new SpinnerNumberModel(0.65, 0.1, 10.0, 0.05));
   private final JSpinner pitch = new JSpinner(new SpinnerNumberModel(220.0, 40.0, 4000.0, 10.0));
   private final JSlider intensity = new JSlider(0, 100, 70);
   private final JTextField seed = new JTextField(Long.toString(ThreadLocalRandom.current().nextLong()));
   private final JCheckBox freshSeed = new JCheckBox("New variation with each Generate", true);
   private final JButton generate = button("Generate");
   private final JButton play = button("Play");
   private final JButton stop = button("Stop");
   private final JButton save = button("Save WAV");
   private final JButton openFolder = button("Open folder");
   private final JSlider volume = new JSlider(0, 100, 65);
   private final JLabel intensityValue = label("70%", CYAN);
   private final JLabel volumeValue = label("65%", CYAN);
   private final JLabel status = label("Choose an effect and generate a variation.", DIM);
   private final JLabel resultLabel = label("No sound generated yet", TEXT);
   private final JTextArea fileInfo = new JTextArea("Generate a sound, then press Play to preview it.");
   private final WaveformPanel waveform = new WaveformPanel();
   private final AtomicLong playbackRevision = new AtomicLong();
   private final Object audioLock = new Object();
   private SoundEffectGenerator.Result result;
   private Path savedPath;
   private boolean busy;
   private boolean playing;
   private volatile boolean active;
   private volatile int volumePercent = 65;
   private SourceDataLine playbackLine;

   public SoundsDevPanel() {
      super(new BorderLayout(12, 12));
      setBackground(BG);
      setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
      JPanel heading = new JPanel(new BorderLayout(0, 6));
      heading.setOpaque(false);
      JLabel title = label("Sounds", TEXT);
      title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
      heading.add(title, BorderLayout.NORTH);
      heading.add(label("Create firing, engine, ability, and other game effects.", DIM), BorderLayout.CENTER);
      add(heading, BorderLayout.NORTH);

      JPanel body = new JPanel(new BorderLayout(14, 0));
      body.setBackground(BG);
      body.add(buildControls(), BorderLayout.WEST);
      body.add(buildPreview(), BorderLayout.CENTER);
      JScrollPane scroll = new JScrollPane(body);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      scroll.getViewport().setBackground(BG);
      scroll.getVerticalScrollBar().setUnitIncrement(16);
      add(scroll, BorderLayout.CENTER);
      status.setBorder(BorderFactory.createEmptyBorder(5, 2, 0, 2));
      add(status, BorderLayout.SOUTH);

      kind.addActionListener(event -> applyPreset());
      intensity.addChangeListener(event -> intensityValue.setText(intensity.getValue() + "%"));
      volume.addChangeListener(event -> {
         volumePercent = volume.getValue();
         volumeValue.setText(volumePercent + "%");
      });
      generate.addActionListener(event -> generateSound());
      play.addActionListener(event -> playSound());
      stop.addActionListener(event -> {
         stopPlayback();
         showStatus("Playback stopped.", false);
      });
      save.addActionListener(event -> saveSound());
      openFolder.addActionListener(event -> openSavedFolder());
      refreshControls();
   }

   private JPanel buildControls() {
      JPanel controls = card(new GridBagLayout());
      controls.setPreferredSize(new Dimension(340, 405));
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = 0;
      c.weightx = 1;
      c.gridwidth = 2;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.insets = new Insets(0, 0, 15, 0);
      controls.add(label("PROCEDURAL EFFECT", CYAN), c);
      kind.setBackground(FIELD);
      kind.setForeground(TEXT);
      kind.setPreferredSize(new Dimension(180, 30));
      addRow(controls, c, "Effect", kind);
      styleSpinner(duration, "0.00 's'");
      styleSpinner(pitch, "0 'Hz'");
      addRow(controls, c, "Duration", duration);
      addRow(controls, c, "Pitch", pitch);
      addRow(controls, c, "Intensity", sliderRow(intensity, intensityValue));
      seed.setBackground(FIELD);
      seed.setForeground(TEXT);
      seed.setCaretColor(TEXT);
      seed.setToolTipText("Use the same settings and seed to recreate a sound.");
      addRow(controls, c, "Seed", seed);
      c.gridx = 0;
      c.gridwidth = 2;
      c.gridy++;
      freshSeed.setOpaque(false);
      freshSeed.setForeground(DIM);
      controls.add(freshSeed, c);
      c.gridy++;
      generate.setForeground(CYAN);
      controls.add(generate, c);
      c.gridy++;
      c.weighty = 1;
      controls.add(new JLabel(), c);
      return controls;
   }

   private JPanel buildPreview() {
      JPanel preview = card(new BorderLayout(0, 14));
      preview.setPreferredSize(new Dimension(440, 405));
      preview.add(resultLabel, BorderLayout.NORTH);
      preview.add(waveform, BorderLayout.CENTER);
      JPanel bottom = new JPanel(new GridBagLayout());
      bottom.setOpaque(false);
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = 0;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.insets = new Insets(0, 0, 10, 0);
      JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
      transport.setOpaque(false);
      transport.add(play);
      transport.add(stop);
      transport.add(save);
      transport.add(openFolder);
      bottom.add(transport, c);
      c.gridy++;
      JPanel volumeRow = new JPanel(new BorderLayout(8, 0));
      volumeRow.setOpaque(false);
      volumeRow.add(label("Preview volume", DIM), BorderLayout.WEST);
      volumeRow.add(sliderRow(volume, volumeValue), BorderLayout.CENTER);
      bottom.add(volumeRow, c);
      c.gridy++;
      fileInfo.setEditable(false);
      fileInfo.setOpaque(false);
      fileInfo.setForeground(DIM);
      fileInfo.setFont(getFont().deriveFont(12f));
      fileInfo.setLineWrap(true);
      fileInfo.setWrapStyleWord(true);
      fileInfo.setRows(3);
      fileInfo.setToolTipText("WAV destination: " + outputRoot.toAbsolutePath());
      bottom.add(fileInfo, c);
      preview.add(bottom, BorderLayout.SOUTH);
      return preview;
   }

   public void setActive(boolean active) {
      this.active = active;
      if (!active) {
         boolean wasPlaying = playing;
         stopPlayback();
         if (wasPlaying) showStatus("Playback stopped.", false);
      }
      refreshControls();
   }

   @Override public void removeNotify() {
      setActive(false);
      super.removeNotify();
   }

   private void applyPreset() {
      SoundEffectGenerator.Kind selected = (SoundEffectGenerator.Kind)kind.getSelectedItem();
      if (selected == null) return;
      double[] preset = switch (selected) {
         case FIRING -> new double[]{0.65, 220, 70};
         case ENGINE -> new double[]{4, 90, 65};
         case ABILITY -> new double[]{1.8, 520, 70};
         case IMPACT -> new double[]{0.4, 150, 80};
         case EXPLOSION -> new double[]{2.5, 70, 90};
         case PICKUP -> new double[]{0.7, 880, 55};
         case INTERFACE -> new double[]{0.15, 1000, 40};
      };
      duration.setValue(preset[0]);
      pitch.setValue(preset[1]);
      intensity.setValue((int)preset[2]);
   }

   private void generateSound() {
      if (busy) return;
      final SoundEffectGenerator.Parameters parameters;
      try {
         duration.commitEdit();
         pitch.commitEdit();
         if (freshSeed.isSelected()) seed.setText(Long.toString(ThreadLocalRandom.current().nextLong()));
         parameters = new SoundEffectGenerator.Parameters((SoundEffectGenerator.Kind)kind.getSelectedItem(),
            ((Number)duration.getValue()).doubleValue(), ((Number)pitch.getValue()).doubleValue(),
            intensity.getValue() / 100.0, Long.parseLong(seed.getText().trim()));
      } catch (Exception error) {
         showStatus("Check duration (0.1–10 s), pitch (40–4000 Hz), and whole-number seed.", true);
         return;
      }
      stopPlayback();
      busy = true;
      refreshControls();
      showStatus("Generating " + parameters.kind().toString().toLowerCase(Locale.ROOT) + "…", false);
      worker.execute(() -> {
         try {
            SoundEffectGenerator.Result generated = SoundEffectGenerator.generate(parameters);
            SwingUtilities.invokeLater(() -> {
               result = generated;
               savedPath = null;
               waveform.setResult(generated);
               resultLabel.setText(String.format(Locale.ROOT, "%s  •  %.2f s  •  %,d Hz",
                  parameters.kind(), generated.samples().length / (double)generated.sampleRate(), generated.sampleRate()));
               fileInfo.setText("Seed " + parameters.seed() + "\nReady to preview or save as WAV.");
               fileInfo.setToolTipText("WAV destination: " + outputRoot.toAbsolutePath());
               finishWork("Sound generated. Press Play to preview.", false);
            });
         } catch (Exception error) {
            SwingUtilities.invokeLater(() -> finishWork("Generation failed: " + errorText(error), true));
         }
      });
   }

   private void saveSound() {
      if (busy || result == null) return;
      SoundEffectGenerator.Result current = result;
      stopPlayback();
      busy = true;
      refreshControls();
      showStatus("Saving WAV…", false);
      worker.execute(() -> {
         try {
            Files.createDirectories(outputRoot);
            String name = current.parameters().kind().name().toLowerCase(Locale.ROOT) + "_"
               + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
               + "_" + UUID.randomUUID() + ".wav";
            Path destination = outputRoot.resolve(name);
            SoundEffectGenerator.writeWav(current, destination);
            SwingUtilities.invokeLater(() -> {
               savedPath = destination;
               fileInfo.setText("Saved: " + destination.getFileName());
               fileInfo.setToolTipText(destination.toAbsolutePath().toString());
               finishWork("WAV saved. Open folder to find it.", false);
            });
         } catch (Exception error) {
            SwingUtilities.invokeLater(() -> finishWork("Save failed: " + errorText(error), true));
         }
      });
   }

   private void openSavedFolder() {
      if (savedPath == null) return;
      Path folder = savedPath.getParent();
      worker.execute(() -> {
         try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
               throw new IllegalStateException("Opening folders is unavailable on this system. " + folder);
            Desktop.getDesktop().open(folder.toFile());
         } catch (Exception error) {
            SwingUtilities.invokeLater(() -> showStatus("Could not open folder: " + errorText(error), true));
         }
      });
   }

   private void playSound() {
      if (busy || result == null || !active || playing) return;
      SoundEffectGenerator.Result current = result;
      long revision = playbackRevision.incrementAndGet();
      playing = true;
      refreshControls();
      showStatus("Opening preview audio…", false);
      Thread thread = new Thread(() -> {
         SourceDataLine line = null;
         String failure = null;
         try {
            AudioFormat format = new AudioFormat(current.sampleRate(), 16, 2, true, false);
            line = openSharedLine(format);
            synchronized (audioLock) {
               if (revision != playbackRevision.get() || !active) return;
               playbackLine = line;
            }
            line.start();
            SwingUtilities.invokeLater(() -> {
               if (revision == playbackRevision.get()) showStatus("Playing preview…", false);
            });
            float[] samples = current.samples();
            byte[] bytes = new byte[4096];
            double previousGain = volumePercent / 100.0;
            for (int offset = 0; offset < samples.length && revision == playbackRevision.get(); offset += 1024) {
               int frames = Math.min(1024, samples.length - offset);
               double gain = volumePercent / 100.0;
               for (int i = 0; i < frames; i++) {
                  double level = previousGain + (gain - previousGain) * (i + 1.0) / frames;
                  int value = (int)(Math.max(-1, Math.min(1, samples[offset + i])) * 32767 * level);
                  bytes[i * 4] = bytes[i * 4 + 2] = (byte)value;
                  bytes[i * 4 + 1] = bytes[i * 4 + 3] = (byte)(value >> 8);
               }
               previousGain = gain;
               int written = 0;
               while (written < frames * 4 && revision == playbackRevision.get()) {
                  int count = line.write(bytes, written, frames * 4 - written);
                  if (count <= 0) throw new IllegalStateException("Audio output stopped accepting samples.");
                  written += count;
               }
            }
            if (revision == playbackRevision.get()) line.drain();
         } catch (Exception error) {
            failure = errorText(error);
         } finally {
            synchronized (audioLock) { if (playbackLine == line) playbackLine = null; }
            closeLine(line);
            String message = failure;
            SwingUtilities.invokeLater(() -> {
               if (revision != playbackRevision.get()) return;
               playing = false;
               refreshControls();
               showStatus(message == null ? "Preview finished." : "Playback failed: " + message, message != null);
            });
         }
      }, "sound-effect-playback");
      thread.setDaemon(true);
      thread.start();
   }

   private static SourceDataLine openSharedLine(AudioFormat format) throws Exception {
      DataLine.Info request = new DataLine.Info(SourceDataLine.class, format);
      Exception failure = null;
      // Match Music's shared-bus policy; never acquire an exclusive physical endpoint.
      for (String preference : new String[]{"Voicemeeter AUX Input", "Voicemeeter Input", "Primary Sound Driver"}) {
         for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (!info.getName().toLowerCase(Locale.ROOT).contains(preference.toLowerCase(Locale.ROOT))) continue;
            SourceDataLine line = null;
            try {
               Mixer mixer = AudioSystem.getMixer(info);
               if (!mixer.isLineSupported(request)) continue;
               int maximum = mixer.getMaxLines(request);
               if (maximum == 0 || maximum == 1) continue;
               line = (SourceDataLine)mixer.getLine(request);
               line.open(format, 16384);
               return line;
            } catch (Exception error) {
               closeLine(line);
               failure = error;
            }
         }
      }
      throw new IllegalStateException("No shared audio output is available"
         + (failure == null ? "." : ": " + errorText(failure)), failure);
   }

   private void stopPlayback() {
      playbackRevision.incrementAndGet();
      SourceDataLine line;
      synchronized (audioLock) { line = playbackLine; playbackLine = null; }
      closeLine(line);
      playing = false;
      refreshControls();
   }

   private static void closeLine(SourceDataLine line) {
      if (line == null) return;
      try { line.stop(); } catch (Exception ignored) { }
      try { line.flush(); } catch (Exception ignored) { }
      try { line.close(); } catch (Exception ignored) { }
   }

   private void finishWork(String text, boolean error) {
      busy = false;
      refreshControls();
      showStatus(text, error);
   }

   private void refreshControls() {
      generate.setEnabled(!busy);
      kind.setEnabled(!busy);
      duration.setEnabled(!busy);
      pitch.setEnabled(!busy);
      intensity.setEnabled(!busy);
      seed.setEnabled(!busy);
      freshSeed.setEnabled(!busy);
      play.setEnabled(active && !busy && !playing && result != null);
      stop.setEnabled(playing);
      save.setEnabled(!busy && result != null);
      openFolder.setEnabled(!busy && savedPath != null);
   }

   private void showStatus(String text, boolean error) {
      status.setText(text);
      status.setToolTipText(text);
      status.setForeground(error ? ERROR : DIM);
   }

   private static String errorText(Exception error) {
      return error.getMessage() == null || error.getMessage().isBlank()
         ? error.getClass().getSimpleName() : error.getMessage();
   }

   private static JPanel card(java.awt.LayoutManager layout) {
      JPanel panel = new JPanel(layout);
      panel.setBackground(PANEL);
      panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(35, 47, 69)),
         BorderFactory.createEmptyBorder(18, 18, 18, 18)));
      return panel;
   }

   private static JLabel label(String text, Color color) {
      JLabel label = new JLabel(text);
      label.setForeground(color);
      return label;
   }

   private static JButton button(String text) {
      JButton button = new JButton(text);
      button.setBackground(new Color(23, 31, 49));
      button.setForeground(TEXT);
      button.setFocusPainted(false);
      button.setMargin(new Insets(8, 12, 8, 12));
      return button;
   }

   private static JPanel sliderRow(JSlider slider, JLabel value) {
      JPanel row = new JPanel(new BorderLayout(6, 0));
      row.setOpaque(false);
      slider.setOpaque(false);
      slider.setPreferredSize(new Dimension(130, 28));
      value.setPreferredSize(new Dimension(38, 28));
      row.add(slider, BorderLayout.CENTER);
      row.add(value, BorderLayout.EAST);
      return row;
   }

   private static void styleSpinner(JSpinner spinner, String format) {
      spinner.setEditor(new JSpinner.NumberEditor(spinner, format));
      JTextField text = ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField();
      text.setBackground(FIELD);
      text.setForeground(TEXT);
      text.setCaretColor(TEXT);
   }

   private static void addRow(JPanel panel, GridBagConstraints c, String name, java.awt.Component field) {
      c.gridy++;
      c.gridx = 0;
      c.gridwidth = 1;
      c.weightx = 0;
      c.insets = new Insets(0, 0, 14, 12);
      panel.add(label(name, DIM), c);
      c.gridx = 1;
      c.weightx = 1;
      c.insets = new Insets(0, 0, 14, 0);
      panel.add(field, c);
   }

   private static final class WaveformPanel extends JPanel {
      private static final long serialVersionUID = 1L;
      private float[] peaks;

      WaveformPanel() {
         setBackground(FIELD);
         setPreferredSize(new Dimension(350, 180));
      }

      void setResult(SoundEffectGenerator.Result result) {
         peaks = new float[1024];
         float[] samples = result.samples();
         for (int i = 0; i < samples.length; i++) {
            int bucket = (int)((long)i * peaks.length / samples.length);
            peaks[bucket] = Math.max(peaks[bucket], Math.abs(samples[i]));
         }
         repaint();
      }

      @Override protected void paintComponent(Graphics graphics) {
         super.paintComponent(graphics);
         Graphics2D g = (Graphics2D)graphics.create();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         int center = getHeight() / 2;
         g.setColor(new Color(35, 47, 69));
         g.drawLine(16, center, getWidth() - 16, center);
         if (peaks == null) {
            g.setColor(DIM);
            String text = "Your sound will appear here";
            g.drawString(text, Math.max(16, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2), center - 16);
         } else {
            g.setColor(CYAN);
            int width = Math.max(1, getWidth() - 32);
            for (int x = 0; x < width; x++) {
               int bucket = (int)((long)x * peaks.length / width);
               int height = Math.round(peaks[bucket] * Math.max(0, center - 20));
               g.drawLine(x + 16, center - height, x + 16, center + height);
            }
         }
         g.dispose();
      }
   }
}
