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
    double densityAlt = 0.0;
    JComboBox<String> weightBox;
    JSlider densitySlider;
    JSlider cgSlider;

    // Enhanced stall modeling toggle
    boolean useEnhancedStall = false;
    JCheckBox enhancedStallBox;

    // Wind/gust modeling
    double windX = 0.0; // m/s horizontal wind
    double windZ = 0.0; // m/s vertical wind (gusts)
    JSlider windSlider;

    // Stall warning system
    boolean stallWarning = false;
    double stallWarningThreshold = 0.85; // Warn at 85% of stall AoA

    // state
    double x = 0.0, z = 100.0;
    double vx = 50.0, vz = 0.0;
    double pitch = Math.toRadians(5);
    //This simulator intensionally uses first-order pitch response for clarity
    double Cm_alpha = -0.05;


    // controls
    double throttle = 0.4;
    double elevator = 0.0;
    double trimElevator = 0.0;
    double flaps = 0.0;
    double cgPosition = 0.0;

    // diagnostics
    double aoa = 0.0;
    boolean stalled = false;

    // time
    double dt = 0.02;
    double simTime = 0.0;
    double nextLogTime = 0.0;
    Timer timer;

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
        physicsPanel.setPreferredSize(new Dimension(220, 280));
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

        // Wind/Gust Slider
        JLabel windLabel = new JLabel("Headwind (+) / Tailwind (-):");
        windLabel.setForeground(Color.BLACK);
        physicsPanel.add(windLabel);

        windSlider = new JSlider(-20, 20, 0);
        windSlider.setMajorTickSpacing(10);
        windSlider.setPaintTicks(true);
        windSlider.setPaintLabels(true);
        windSlider.setFocusable(false);
        windSlider.addChangeListener(e -> {
            windX = -windSlider.getValue(); // Negative = headwind reduces groundspeed
            refocusSim();
        });
        physicsPanel.add(windSlider);

        // Enhanced Stall Model Checkbox
        enhancedStallBox = new JCheckBox("Enhanced Stall Model");
        enhancedStallBox.setForeground(Color.BLACK);
        enhancedStallBox.setFocusable(false);
        enhancedStallBox.addActionListener(e -> {
            useEnhancedStall = enhancedStallBox.isSelected();
            refocusSim();
        });
        physicsPanel.add(enhancedStallBox);

        add(physicsPanel, BorderLayout.EAST);
    }

    public void step() {
        // Apply wind to get airspeed
        double airspeedX = vx - windX;
        double airspeedZ = vz - windZ;
        double speed = Math.sqrt(airspeedX * airspeedX + airspeedZ * airspeedZ);
        if (speed < 1.0) speed = 1.0;

        double gamma = Math.atan2(airspeedZ, airspeedX);
        aoa = pitch - gamma;

        // Flaps effect on lift and drag
        double flapAngleDeg = flaps * 40.0;
        double flapLiftIncrement = 0.02 * flapAngleDeg;
        double flapDragIncrement = 0.00015 * flapAngleDeg * flapAngleDeg;
        double flapStallMargin = 0.008 * flapAngleDeg;

        // Stall model with enhancement option
        double effectiveClMax = clMax + flapStallMargin;
        double effectiveStallAoA = stallAoA + Math.toRadians(0.2) * flapAngleDeg;

        // Stall warning system
        stallWarning = Math.abs(aoa) >= (effectiveStallAoA * stallWarningThreshold);

        double aoaClamped = Math.max(-Math.toRadians(40), Math.min(Math.toRadians(40), aoa));
        double cl;
        double cd = cd0 + flapDragIncrement;

        if (Math.abs(aoaClamped) <= effectiveStallAoA) {
            // Pre-stall: linear lift curve
            cl = clAlpha * aoaClamped + flapLiftIncrement;
            stalled = false;
        } else {
            // Post-stall
            stalled = true;
            double aoaOver = Math.abs(aoaClamped) - effectiveStallAoA;

            if (useEnhancedStall) {
                // ENHANCED MODE: Gradual degradation
                double decay = Math.exp(-aoaOver / Math.toRadians(10));
                cl = effectiveClMax * 0.6 * decay * Math.signum(aoaClamped);

                // Increased drag in stall
                cd += 0.2 * (aoaOver / Math.toRadians(10));
            } else {
                // BASIC MODE: Simple drop
                cl = effectiveClMax * 0.5 * Math.signum(aoaClamped);
                cd += 0.1;
            }
        }

        cl = Math.max(-effectiveClMax, Math.min(effectiveClMax, cl));
        cd += kInduced * cl * cl;

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
        double cgStabilityFactor = 0.08 * cgPosition;
        double cgElevatorFactor = 1.0 + 0.25 * cgPosition;
        double stallCmModifier = 0.0;
        if (stalled && useEnhancedStall) {
            double aoaOver = Math.abs(aoa) - effectiveStallAoA;
            stallCmModifier = -0.02 * (aoaOver / Math.toRadians(10));
        }
        double effectiveCm_alpha = Cm_alpha + stallCmModifier - cgStabilityFactor;

        // Total elevator input includes trim
        double totalElevator = elevator + trimElevator;

        // Pitch control
        double elevatorEffectiveness = 0.008 / cgElevatorFactor;
        double pitchChange = totalElevator * elevatorEffectiveness;
        pitch += pitchChange;

        // Aerodynamic pitch stability
        double targetAoA = trimElevator * 0.1;
        double aoaError = aoa - targetAoA;
        double stabilityMoment = effectiveCm_alpha * aoaError * 0.003;
        pitch += stabilityMoment;

        // Clamp pitch
        pitch = Math.max(Math.toRadians(-40), Math.min(Math.toRadians(40), pitch));

        simTime += dt;

        if (simTime >= nextLogTime) {
            double speedMs = Math.sqrt(vx * vx + vz * vz);
            System.out.printf(
                    "t=%.1f | alt=%.1f | V=%.1f | AoA=%.1f° | cl=%.3f | cd=%.3f | wind=%.1f | warn=%b | stalled=%b%n",
                    simTime, z, speedMs,
                    Math.toDegrees(aoa),
                    cl, cd,
                    windX,
                    stallWarning,
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

        // Stall warning indicator
        if (stallWarning) {
            g2.setColor(new Color(255, 165, 0, 128)); // Orange transparent overlay
            g2.fillRect(0, 0, w, h);
        }

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
        int yPos = 20;
        g.drawString(String.format("Alt: %.1f m", z), 10, yPos); yPos += 15;
        g.drawString(String.format("Speed: %.1f m/s", Math.sqrt(vx * vx + vz * vz)), 10, yPos); yPos += 15;
        g.drawString(String.format("Pitch: %.1f°", Math.toDegrees(pitch)), 10, yPos); yPos += 15;
        g.drawString(String.format("AoA: %.1f°", Math.toDegrees(aoa)), 10, yPos); yPos += 15;

        // Stall warning display
        if (stallWarning) {
            g.setColor(Color.ORANGE);
            g.drawString("⚠ STALL WARNING", 10, yPos);
            g.setColor(Color.BLACK);
        } else {
            g.drawString("Stalled: " + stalled, 10, yPos);
        }
        yPos += 15;

        g.drawString(String.format("Throttle: %.2f", throttle), 10, yPos); yPos += 15;
        g.drawString(String.format("Elevator: %.2f", elevator), 10, yPos); yPos += 15;
        g.drawString(String.format("Trim: %.2f", trimElevator), 10, yPos); yPos += 15;
        g.drawString(String.format("Flaps: %.0f%%", flaps * 100), 10, yPos); yPos += 15;
        g.drawString(String.format("CG: %.2f", cgPosition), 10, yPos); yPos += 15;
        g.drawString(String.format("Wind: %.1f m/s", windX), 10, yPos); yPos += 15;
        g.drawString(String.format("Density Alt: %.0f m", densityAlt), 10, yPos); yPos += 15;
        g.drawString(String.format("Mass: %.0f kg", mass), 10, yPos); yPos += 15;
        g.drawString("Enhanced Stall: " + (useEnhancedStall ? "ON" : "OFF"), 10, yPos); yPos += 15;
        g.drawString("Controls: Arrows, Q/A=Trim, F/G=Flaps", 10, yPos);
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
        else if (code == KeyEvent.VK_Q) trimElevator = Math.min(1.0, trimElevator + 0.02);
        else if (code == KeyEvent.VK_A) trimElevator = Math.max(-1.0, trimElevator - 0.02);
        else if (code == KeyEvent.VK_F) flaps = Math.min(1.0, flaps + 0.25);
        else if (code == KeyEvent.VK_G) flaps = Math.max(0.0, flaps - 0.25);
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    public static class StartupFrame extends JFrame {
        public StartupFrame() {
            setTitle("Cessna 172 Flight Simulator - Setup");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(650, 675);
            setLocationRelativeTo(null);

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
            mainPanel.setBackground(new Color(240, 240, 245));

            // Title
            JLabel titleLabel = new JLabel("Cessna 172 Flight Simulator");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            titleLabel.setForeground(new Color(40, 40, 80));
            mainPanel.add(titleLabel);

            mainPanel.add(Box.createVerticalStrut(10));

            JLabel subtitleLabel = new JLabel("Configure Initial Flight Conditions");
            subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            subtitleLabel.setForeground(new Color(80, 80, 120));
            mainPanel.add(subtitleLabel);

            mainPanel.add(Box.createVerticalStrut(30));

            // Altitude Panel
            JPanel altPanel = createSettingPanel("Initial Altitude");
            JSlider altSlider = new JSlider(0, 2000, 100);
            altSlider.setMajorTickSpacing(500);
            altSlider.setMinorTickSpacing(100);
            altSlider.setPaintTicks(true);
            altSlider.setPaintLabels(true);
            altSlider.setBackground(new Color(240, 240, 245));
            JLabel altLabel = new JLabel("100 m", SwingConstants.CENTER);
            altLabel.setFont(new Font("Arial", Font.BOLD, 16));
            altLabel.setForeground(new Color(0, 100, 200));
            altSlider.addChangeListener(e -> altLabel.setText(altSlider.getValue() + " m"));
            altPanel.add(altSlider);
            altPanel.add(Box.createVerticalStrut(5));
            altPanel.add(altLabel);
            mainPanel.add(altPanel);

            mainPanel.add(Box.createVerticalStrut(15));

            // Speed Panel
            JPanel speedPanel = createSettingPanel("Initial Airspeed");
            JSlider speedSlider = new JSlider(30, 70, 50);
            speedSlider.setMajorTickSpacing(10);
            speedSlider.setMinorTickSpacing(5);
            speedSlider.setPaintTicks(true);
            speedSlider.setPaintLabels(true);
            speedSlider.setBackground(new Color(240, 240, 245));
            JLabel speedLabel = new JLabel("50 m/s", SwingConstants.CENTER);
            speedLabel.setFont(new Font("Arial", Font.BOLD, 16));
            speedLabel.setForeground(new Color(0, 100, 200));
            speedSlider.addChangeListener(e -> speedLabel.setText(speedSlider.getValue() + " m/s"));
            speedPanel.add(speedSlider);
            speedPanel.add(Box.createVerticalStrut(5));
            speedPanel.add(speedLabel);
            mainPanel.add(speedPanel);

            mainPanel.add(Box.createVerticalStrut(15));

            // Throttle Panel
            JPanel throttlePanel = createSettingPanel("Initial Throttle");
            JSlider throttleSlider = new JSlider(0, 100, 65);
            throttleSlider.setMajorTickSpacing(25);
            throttleSlider.setMinorTickSpacing(5);
            throttleSlider.setPaintTicks(true);
            throttleSlider.setPaintLabels(true);
            throttleSlider.setBackground(new Color(240, 240, 245));
            JLabel throttleLabel = new JLabel("65%", SwingConstants.CENTER);
            throttleLabel.setFont(new Font("Arial", Font.BOLD, 16));
            throttleLabel.setForeground(new Color(0, 100, 200));
            throttleSlider.addChangeListener(e -> throttleLabel.setText(throttleSlider.getValue() + "%"));
            throttlePanel.add(throttleSlider);
            throttlePanel.add(Box.createVerticalStrut(5));
            throttlePanel.add(throttleLabel);
            mainPanel.add(throttlePanel);

            mainPanel.add(Box.createVerticalStrut(25));

            // Preset Buttons
            JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
            presetPanel.setBackground(new Color(240, 240, 245));

            JButton cruiseBtn = new JButton("Cruise Configuration");
            cruiseBtn.setFont(new Font("Arial", Font.BOLD, 13));
            cruiseBtn.setBackground(new Color(100, 150, 200));
            cruiseBtn.setForeground(Color.WHITE);
            cruiseBtn.setFocusPainted(false);
            cruiseBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            cruiseBtn.addActionListener(e -> {
                altSlider.setValue(1000);
                speedSlider.setValue(50);
                throttleSlider.setValue(65);
            });
            presetPanel.add(cruiseBtn);

            JButton approachBtn = new JButton("Approach Configuration");
            approachBtn.setFont(new Font("Arial", Font.BOLD, 13));
            approachBtn.setBackground(new Color(100, 150, 200));
            approachBtn.setForeground(Color.WHITE);
            approachBtn.setFocusPainted(false);
            approachBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            approachBtn.addActionListener(e -> {
                altSlider.setValue(300);
                speedSlider.setValue(45);
                throttleSlider.setValue(60);
            });
            presetPanel.add(approachBtn);

            mainPanel.add(presetPanel);
            mainPanel.add(Box.createVerticalStrut(25));

            // Start Button
            JButton startBtn = new JButton("START FLIGHT");
            startBtn.setFont(new Font("Arial", Font.BOLD, 18));
            startBtn.setBackground(new Color(50, 150, 50));
            startBtn.setForeground(Color.WHITE);
            startBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            startBtn.setFocusPainted(false);
            startBtn.setBorder(BorderFactory.createEmptyBorder(15, 50, 15, 50));
            startBtn.addActionListener(e -> {
                double initAlt = altSlider.getValue();
                double initSpeed = speedSlider.getValue();
                double initThrottle = throttleSlider.getValue() / 100.0;

                dispose();

                JFrame simFrame = new JFrame("Cessna 172 Physics Simulator");
                simFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                SimpleC172Sim sim = new SimpleC172Sim(initAlt, initSpeed, initThrottle);
                simFrame.setContentPane(sim);
                simFrame.pack();
                simFrame.setLocationRelativeTo(null);
                simFrame.setVisible(true);
            });
            mainPanel.add(startBtn);

            add(mainPanel);
            setVisible(true);
        }

        private JPanel createSettingPanel(String label) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(new Color(240, 240, 245));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180, 180, 200), 1),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)
            ));

            JLabel titleLabel = new JLabel(label);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            titleLabel.setForeground(new Color(60, 60, 100));
            panel.add(titleLabel);
            panel.add(Box.createVerticalStrut(8));

            return panel;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StartupFrame());
    }
}
