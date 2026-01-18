import java.awt.geom.AffineTransform;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class SimpleC172Sim extends JPanel implements ActionListener, KeyListener {
    //Cessna 172 approximations
    double mass = 1100.0; // kg
    double wingArea = 16.2; // m^2
    double g = 9.81; // m/s^2
    double rhoSeaLevel = 1.225; // kg/m^3 base density
    double clAlpha = 5.0;
    double clMax = 1.4;
    double cd0 = 0.03;
    double kInduced = 0.05;
    double stallAoA = Math.toRadians(15);
    double maxThrust = 1700.0;

    // Physics parameters
    double densityAlt = 0.0; // Density alt
    JComboBox<String> weightBox;
    JSlider densitySlider;
    JSlider cgSlider;

    // state
    double x = 0.0, z = 100.0;
    double vx = 50.0, vz = 0.0;
    double pitch = Math.toRadians(5);
    double maxElevatorDeflection = Math.toRadians(20);
    // --- ADD near state variables ---
    double pitchRate = 0.0;        // rad/s
    double meanChord = 1.5;        // m
    double Iyy = 1800.0;           // kg·m²

    double Cm_alpha = -0.05;
    double Cm_elevator = -0.3;
    double Cm_q = -0.5;
    double tailVolume = 0.5;

    // controls
    double throttle = 0.4;
    double elevator = 0.0;
    double trimElevator = 0.0;  // NEW: Trim setting
    double flaps = 0.0;          // NEW: 0.0 = up, 1.0 = full (40 degrees for C172)
    double cgPosition = 0.0;     // NEW: -1.0 = aft limit, 0.0 = neutral, +1.0 = forward limit

    // diagnostics?
    double aoa = 0.0;
    boolean stalled = false;

    // time
    double dt = 0.02;
    double simTime = 0.0;
    double nextLogTime = 0.0;
    Timer timer;

    // focus
    private void refocusSim() {
        requestFocusInWindow();
    }

    public SimpleC172Sim(double initAlt, double initSpeed, double initThrottle) {
        z = initAlt;
        vx = initSpeed;
        throttle = initThrottle;

        setPreferredSize(new Dimension(900, 700));
        setBackground(Color.CYAN);
        setLayout(new BorderLayout());

        setFocusable(true);
        addKeyListener(this);

        timer = new Timer((int)(dt * 1000), this);
        timer.start();

        // control panel (right)
        JPanel physicsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        physicsPanel.setPreferredSize(new Dimension(220, 180));
        physicsPanel.setBackground(Color.GRAY);
        physicsPanel.setFocusable(false);

        // Density Altitude Slider
        JLabel densityLabel = new JLabel("Density Altitude:");
        densityLabel.setForeground(Color.BLACK);
        physicsPanel.add(densityLabel);

        densitySlider = new JSlider(-2000, 4000, 0);
        densitySlider.setMajorTickSpacing(1500);
        densitySlider.setPaintTicks(true);
        densitySlider.setPaintLabels(true);
        densitySlider.setFocusable(false);
        densitySlider.addChangeListener(e -> {
            densityAlt = densitySlider.getValue();
            refocusSim();
        });
        physicsPanel.add(densitySlider);

        // Wing Loading Dropbox
        JLabel weightLabel = new JLabel("Wing Loading:");
        weightLabel.setForeground(Color.BLACK);
        physicsPanel.add(weightLabel);

        weightBox = new JComboBox<>(new String[]{
                "Light (900kg)",
                "Normal (1100kg)",
                "Heavy (1400kg)"
        });
        weightBox.setPreferredSize(new Dimension(120, 25));
        weightBox.setSelectedIndex(1);
        weightBox.setFocusable(false);
        weightBox.addActionListener(e -> {
            switch(weightBox.getSelectedIndex()) {
                case 0: mass = 900; break;
                case 1: mass = 1100; break;
                case 2: mass = 1400; break;
            }
            refocusSim();
        });

        mass = 1100;
        physicsPanel.add(weightBox);

        // CG Position Slider
        JLabel cgLabel = new JLabel("CG Position:");
        cgLabel.setForeground(Color.BLACK);
        physicsPanel.add(cgLabel);

        cgSlider = new JSlider(-100, 100, 0);
        cgSlider.setMajorTickSpacing(50);
        cgSlider.setPaintTicks(true);
        cgSlider.setPaintLabels(true);
        cgSlider.setFocusable(false);
        cgSlider.addChangeListener(e -> {
            cgPosition = cgSlider.getValue() / 100.0;
            refocusSim();
        });
        physicsPanel.add(cgSlider);

        add(physicsPanel, BorderLayout.EAST);
    }

    public void step() {
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < 1.0) speed = 1.0;

        double gamma = Math.atan2(vz, vx);
        aoa = pitch - gamma;

        // Flaps effect on lift and drag (C172: 0-10-20-30-40 degrees)
        double flapAngleDeg = flaps * 40.0; // Max 40 degrees
        double flapLiftIncrement = 0.02 * flapAngleDeg;  // ~0.8 delta-Cl at full flaps
        double flapDragIncrement = 0.00015 * flapAngleDeg * flapAngleDeg; // Quadratic drag increase
        double flapStallMargin = 0.008 * flapAngleDeg;    // ~0.32 delta-Clmax at full flaps

        // Stall model with flap effect
        double effectiveClMax = clMax + flapStallMargin;
        double effectiveStallAoA = stallAoA + Math.toRadians(0.2) * flapAngleDeg; // Small AoA benefit

        double aoaClamped = Math.max(-Math.toRadians(40), Math.min(Math.toRadians(40), aoa));
        double cl;
        if (Math.abs(aoaClamped) <= effectiveStallAoA) {
            cl = clAlpha * aoaClamped + flapLiftIncrement;
            stalled = false;
        } else {
            stalled = true;
            double aoaOver = Math.abs(aoaClamped) - effectiveStallAoA;
            double decay = Math.exp(-aoaOver / Math.toRadians(10));
            cl = effectiveClMax * 0.6 * decay * Math.signum(aoaClamped);
        }
        cl = Math.max(-effectiveClMax, Math.min(effectiveClMax, cl));

        double cd = cd0 + kInduced * cl * cl + flapDragIncrement;

        // Density altitude effect
        double tempEffect = 1.0 - densityAlt / 10000.0;
        double rho = rhoSeaLevel * Math.max(0.5, Math.min(2.0, tempEffect));

        double q = 0.5 * rho * speed * speed;
        double L = q * wingArea * cl;
        double D = q * wingArea * cd;
        double T = throttle * maxThrust;

        double fx = T * Math.cos(pitch) - D * Math.cos(gamma) - L * Math.sin(gamma);
        double fz = T * Math.sin(pitch) - D * Math.sin(gamma) + L * Math.cos(gamma) - mass * g;

        double ax = fx / mass;
        double az = fz / mass;

        vx += ax * dt;
        vz += az * dt;
        x += vx * dt;
        z += vz * dt;

        if (z < 0) {
            z = 0;
            vz = 0;
            timer.stop();
        }

        // CG effect on pitch stability and control
        // C172 CG range: ~35-48 inches aft of datum
        // Forward CG: more stable, heavier elevator forces, higher stall speed
        // Aft CG: less stable, lighter elevator forces, lower stall speed, risk of spin
        double cgStabilityFactor = 0.08 * cgPosition;  // Forward CG increases restoring moment
        double cgElevatorFactor = 1.0 + 0.25 * cgPosition; // Forward CG needs more deflection

        // CG affects the neutral point and static margin
        double effectiveCm_alpha = Cm_alpha - cgStabilityFactor;

        // Total elevator input includes trim
        double totalElevator = elevator + trimElevator;

        // Pitch control: elevator deflection creates pitching moment
        // CG position affects how much elevator deflection is needed
        double elevatorEffectiveness = 0.008 / cgElevatorFactor;
        double pitchChange = totalElevator * elevatorEffectiveness;
        pitch += pitchChange;

        // Aerodynamic pitch stability: restoring moment proportional to AoA
        // This simulates the horizontal stabilizer creating a nose-down moment at high AoA
        double targetAoA = trimElevator * 0.1; // Trim sets equilibrium AoA
        double aoaError = aoa - targetAoA;
        double stabilityMoment = effectiveCm_alpha * aoaError * 0.003;
        pitch += stabilityMoment;

        // Clamp pitch
        pitch = Math.max(Math.toRadians(-40), Math.min(Math.toRadians(40), pitch));

        simTime += dt;

        if (simTime >= nextLogTime) {
            double speedMs = Math.sqrt(vx * vx + vz * vz);
            System.out.printf(
                    "t=%.1f | alt=%.1f | V=%.1f | pitch=%.1f° | AoA=%.1f° | cl=%.3f | L=%.1fN | W=%.1fN | flaps=%.0f%% | trim=%.2f | CG=%.2f | stalled=%b%n",
                    simTime, z, speedMs,
                    Math.toDegrees(pitch),
                    Math.toDegrees(aoa),
                    cl, L, mass * g,
                    flaps * 100,
                    trimElevator,
                    cgPosition,
                    stalled
            );
            nextLogTime += 1.0;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();
        double pixelsPerMeter = 3.0;

        double cameraX = x;
        double cameraZ = z;

        int groundY = (int)(h / 2 - (0 - cameraZ) * pixelsPerMeter);
        g.setColor(new Color(60, 180, 60));
        g.fillRect(0, groundY - 20, w, 40);

        g.setColor(Color.LIGHT_GRAY);
        double stripeSpacing = 50.0;
        for (int i = -200; i <= 200; i++) {
            double stripeX = i * stripeSpacing;
            int stripeScreenX = (int)(w / 2 + (stripeX - cameraX) * pixelsPerMeter);
            if (stripeScreenX >= -10 && stripeScreenX <= w + 10) {
                g.fillRect(stripeScreenX - 4, groundY - 12, 8, 8);
            }
        }

        int planeScreenX = w / 2;
        int planeScreenY = h / 2;

        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.RED);
        AffineTransform old = g2.getTransform();

        g2.translate(planeScreenX, planeScreenY);
        g2.rotate(-pitch);

        int bodyLength = 60;
        int bodyHeight = 8;
        g2.fillRect(-bodyLength / 2, -bodyHeight / 2, bodyLength, bodyHeight);

        int[] xs = {bodyLength / 2, bodyLength / 2 - 15, bodyLength / 2 - 15};
        int[] ys = {0, -10, 10};
        g2.fillPolygon(xs, ys, 3);

        // Draw flaps indicator
        if (flaps > 0) {
            g2.setColor(Color.YELLOW);
            int flapX = -bodyLength / 2 + 10;
            int flapY = bodyHeight / 2;
            int flapLength = (int)(10 * flaps);
            g2.fillRect(flapX, flapY, 8, flapLength);
            g2.fillRect(flapX + 30, flapY, 8, flapLength);
        }

        g2.setTransform(old);

        g.setColor(Color.BLACK);
        g.drawString(String.format("Alt: %.1f m", z), 10, 20);
        g.drawString(String.format("Speed: %.1f m/s", Math.sqrt(vx * vx + vz * vz)), 10, 35);
        g.drawString(String.format("Pitch: %.1f°", Math.toDegrees(pitch)), 10, 50);
        g.drawString(String.format("AoA: %.1f°", Math.toDegrees(aoa)), 10, 65);
        g.drawString("Stalled: " + stalled, 10, 80);
        g.drawString(String.format("Throttle: %.2f", throttle), 10, 95);
        g.drawString(String.format("Elevator: %.2f", elevator), 10, 110);
        g.drawString(String.format("Trim: %.2f", trimElevator), 10, 125);
        g.drawString(String.format("Flaps: %.0f%%", flaps * 100), 10, 140);
        g.drawString(String.format("CG: %.2f", cgPosition), 10, 155);
        g.drawString(String.format("Density Alt: %.0f m", densityAlt), 10, 170);
        g.drawString(String.format("Mass: %.0f kg", mass), 10, 185);
        g.drawString("Controls: Arrows, Q/A=Trim, F/G=Flaps", 10, 200);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        step();
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_UP) elevator = Math.min(1.0, elevator + 0.1);
        else if (code == KeyEvent.VK_DOWN) elevator = Math.max(-1.0, elevator - 0.1);
        else if (code == KeyEvent.VK_RIGHT) throttle = Math.min(1.0, throttle + 0.05);
        else if (code == KeyEvent.VK_LEFT) throttle = Math.max(0.0, throttle - 0.05);
            // Trim controls
        else if (code == KeyEvent.VK_Q) trimElevator = Math.min(1.0, trimElevator + 0.02);
        else if (code == KeyEvent.VK_A) trimElevator = Math.max(-1.0, trimElevator - 0.02);
            // Flap controls
        else if (code == KeyEvent.VK_F) flaps = Math.min(1.0, flaps + 0.25); // 25% increments (notches)
        else if (code == KeyEvent.VK_G) flaps = Math.max(0.0, flaps - 0.25);
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    // Startup menu class
    public static class StartupFrame extends JFrame {
        public StartupFrame() {
            setTitle("Cessna 172 Flight Setup");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new GridLayout(6, 2, 10, 10));
            setSize(500, 400);
            setLocationRelativeTo(null);

            // Initial Altitude
            add(new JLabel("Initial Altitude (m):"));
            JSlider altSlider = new JSlider(0, 2000, 100);
            altSlider.setMajorTickSpacing(500);
            altSlider.setPaintTicks(true);
            altSlider.setPaintLabels(true);
            JLabel altLabel = new JLabel("100 m");
            altSlider.addChangeListener(e -> altLabel.setText(altSlider.getValue() + " m"));
            add(altSlider);
            add(altLabel);

            // Initial Speed
            add(new JLabel("Initial Speed (m/s):"));
            JSlider speedSlider = new JSlider(30, 70, 50);
            speedSlider.setMajorTickSpacing(10);
            speedSlider.setPaintTicks(true);
            speedSlider.setPaintLabels(true);
            JLabel speedLabel = new JLabel("50 m/s");
            speedSlider.addChangeListener(e -> speedLabel.setText(speedSlider.getValue() + " m/s"));
            add(speedSlider);
            add(speedLabel);

            // Initial Throttle
            add(new JLabel("Initial Throttle:"));
            JSlider throttleSlider = new JSlider(20, 100, 40);
            throttleSlider.setMajorTickSpacing(20);
            throttleSlider.setPaintTicks(true);
            throttleSlider.setPaintLabels(true);
            JLabel throttleLabel = new JLabel("0.40");
            throttleSlider.addChangeListener(e -> throttleLabel.setText(String.format("%.2f", throttleSlider.getValue()/100.0)));
            add(throttleSlider);
            add(throttleLabel);

            // Preset buttons
            JButton cruiseBtn = new JButton("Cruise");
            cruiseBtn.addActionListener(e -> {
                altSlider.setValue(1000);
                speedSlider.setValue(50);
                throttleSlider.setValue(40);
            });
            add(cruiseBtn);

            JButton approachBtn = new JButton("Approach");
            approachBtn.addActionListener(e -> {
                altSlider.setValue(300);
                speedSlider.setValue(45);
                throttleSlider.setValue(35);
            });
            add(approachBtn);

            // Start button
            JButton startBtn = new JButton("Start Flight");
            startBtn.addActionListener(e -> {
                double initAlt = altSlider.getValue();
                double initSpeed = speedSlider.getValue();
                double initThrottle = throttleSlider.getValue() / 100.0;

                dispose(); // Close startup window

                // Launch simulator
                JFrame simFrame = new JFrame("Cessna 172 Physics Simulator");
                simFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                SimpleC172Sim sim = new SimpleC172Sim(initAlt, initSpeed, initThrottle);
                simFrame.setContentPane(sim);
                simFrame.pack();
                simFrame.setLocationRelativeTo(null);
                simFrame.setVisible(true);
            });
            add(startBtn);
            add(new JLabel()); // spacer

            setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StartupFrame());
    }
}
