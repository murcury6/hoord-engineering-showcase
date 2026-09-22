import java.awt.*;
import javax.swing.*;

/** New portfolio example of an AI application boundary; not a model implementation. */
public final class ApprovalDemo extends JPanel {
    public record Request(String kind, long seed) {}
    public record Candidate(String id, String description) {}
    public interface Provider { Candidate generate(Request request) throws Exception; }

    private final JTextArea status = new JTextArea();
    private final JButton request = new JButton("Request synthetic candidate");
    private final JButton approve = new JButton("Approve for demo");
    private final JButton reject = new JButton("Reject");
    private Candidate candidate;

    public ApprovalDemo() {
        super(new BorderLayout(16, 16));
        setBackground(new Color(12, 17, 28));
        setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel title = new JLabel("AI integration / Human review boundary");
        title.setForeground(new Color(89, 221, 201));
        title.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        add(title, BorderLayout.NORTH);
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setForeground(new Color(226, 235, 245));
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
        status.setText("A private provider owns the AI. The application owns the request, "
            + "job lifecycle and approval decision.\n\nThis demonstration uses a synthetic provider. "
            + "It contains no model and does not make any external calls.");
        add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.setOpaque(false);
        buttons.add(request); buttons.add(approve); buttons.add(reject);
        add(buttons, BorderLayout.SOUTH);
        approve.setEnabled(false); reject.setEnabled(false);
        Provider synthetic = input -> new Candidate("demo-" + input.seed(),
            "Synthetic " + input.kind() + " receipt. No AI inference occurred.");
        request.addActionListener(event -> {
            request.setEnabled(false); approve.setEnabled(false); reject.setEnabled(false);
            candidate = null;
            status.setText("Request queued for the synthetic provider...");
            new SwingWorker<Candidate, Void>() {
                protected Candidate doInBackground() throws Exception {
                    return synthetic.generate(new Request("ship", 42));
                }
                protected void done() {
                    request.setEnabled(true);
                    try {
                        candidate = get();
                        status.setText(candidate.id() + "\n\n" + candidate.description()
                            + "\n\nAwaiting your review. Nothing has been promoted.");
                        approve.setEnabled(true); reject.setEnabled(true);
                    } catch (Exception failure) {
                        status.setText("Provider did not complete. No candidate is eligible for approval.");
                    }
                }
            }.execute();
        });
        approve.addActionListener(event -> finish(true));
        reject.addActionListener(event -> finish(false));
    }

    private void finish(boolean approved) {
        if (candidate == null) return;
        status.setText(candidate.id() + (approved ? " approved in this demo." : " rejected in this demo.")
            + "\n\nNo production asset or data was written.");
        candidate = null;
        approve.setEnabled(false); reject.setEnabled(false);
    }
}
