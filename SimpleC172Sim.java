import java.awt.geom.AffineTransform;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class SimpleC172Sim extends JPanel implements ActionListener, KeyListener {

    // Aircraft parameters (Cessna 172 approximations)
    // Mass range covers light/normal/heavy loading configurations
    double mass       = 1100.0; 
    double wingArea   = 16.2;  
    double g          = 9.81;   
    double rhoSL      = 1.225;  
    double clAlpha    = 5.0; 
    double clMaxClean = 1.4;
    double cd0        = 0.03;
    double kInduced   = 0.05;

    // Stall angle of attack (clean configuration). Based on C172 empirical data.
    double stallAoA   = Math.toRadians(15); 

    double maxThrust  = 1700.0; 

    // Atmospheric model
    double densityAlt = 0.0; // m

    // Wind model
    double windX = 0.0; 
    double windZ = 0.0; 

    // Flight state
    double x     = 0.0;
    double z     = 100.0;  
    double vx    = 50.0;   
    double vz    = 0.0;    
    double pitch = Math.toRadians(5); 

    double Cm_alpha = -0.05; 

    // Pilot controls
    double throttle     = 0.4;  
    double elevator     = 0.0;  
    double trimElevator = 0.0;  
    double flaps        = 0.0;  
    double cgPosition   = 0.0;  

    // Aerodynamic model flags
    boolean useEnhancedStall = false;

    // Linear model: pure linear lift curve, no stall. Used for theory comparison.
    // Demonstrates where thin airfoil / lifting line theory breaks down near stall.
    boolean useLinearModel = false;

    // Stall warning
    boolean stallWarning          = false;
    double  stallWarningThreshold = 0.85;

    // Diagnostics
    double  aoa     = 0.0;
    boolean stalled = false;

    // Current aerodynamic coefficients exposed for HUD and plot panel
    double currentCl = 0.0;
    double currentCd = 0.0;
    double currentCm = 0.0;

    // Simulation timing
    double dt          = 0.02;
    double simTime     = 0.0;
    double nextLogTime = 0.0;
    Timer  timer;

    // UI references
    JComboBox<String> weightBox;
    JSlider densitySlider, cgSlider, windSlider;
    JCheckBox enhancedStallBox, linearModelBox;
    CoeffPlotPanel plotPanel;

    private void refocusSim() { requestFocusInWindow(); }
    double[] computeCoeffs(double aoaRad, double flapSetting,
                           double cgPos, boolean enhanced, boolean linear) {

        double flapDeg = flapSetting * 40.0;

        // Flap lift increment
        double flapLift = 0.02 * flapDeg;

        // Flap drag increment
        double flapDrag = 0.00015 * flapDeg * flapDeg;

        // Flap CL_max increment
        double deltaClMax = 0.4 * (flapDeg / 40.0);
        double effClMax   = clMaxClean + deltaClMax;

        // Flap stall AoA shift
        // This is a simplification; slotted flaps can reduce stall AoA on some aircraft.
        double effStallAoA = stallAoA + Math.toRadians(0.2) * flapDeg;

        double aoaClamped = Math.max(-Math.toRadians(40), Math.min(Math.toRadians(40), aoaRad));

        double cl, cd, cm;

        if (linear) {
            // LINEAR MODEL
            // Pure thin airfoil / lifting line result. No stall modeled.
            cl = clAlpha * aoaClamped + flapLift;
            cd = cd0 + flapDrag + kInduced * cl * cl;
            cm = (Cm_alpha - 0.08 * cgPos) * aoaClamped;

        } else {
            // NONLINEAR MODEL
            if (Math.abs(aoaClamped) <= effStallAoA) {
                // Pre-stall: linear lift curve valid here.
                cl = clAlpha * aoaClamped + flapLift;
                cd = cd0 + flapDrag;

            } else {
                // Post-stall: flow separation causes lift breakdown.
                double aoaOver = Math.abs(aoaClamped) - effStallAoA;

                if (enhanced) {
                    // Enhanced mode: exponential decay from effClMax.
                    double decay = Math.exp(-aoaOver / Math.toRadians(10));
                    cl = effClMax * 0.6 * decay * Math.signum(aoaClamped);
                    cd = cd0 + flapDrag + 0.2 * (aoaOver / Math.toRadians(10));

                } else {
                    // Basic mode: hard cap at 50% of flap-adjusted CL_max.
                    cl = effClMax * 0.5 * Math.signum(aoaClamped);
                    cd = cd0 + flapDrag + 0.1;
                }
            }

            // Clamp CL to flap-dependent CL_max.
            cl = Math.max(-effClMax, Math.min(effClMax, cl));

            // Induced drag from lift
            cd += kInduced * cl * cl;

            // Pitching moment
            double stallCm = 0.0;
            if (enhanced && Math.abs(aoaClamped) > effStallAoA) {
                double aoaOver = Math.abs(aoaClamped) - effStallAoA;
                stallCm = -0.02 * (aoaOver / Math.toRadians(10));
            }
            cm = (Cm_alpha + stallCm - 0.08 * cgPos) * aoaClamped;
        }

        return new double[]{cl, cd, cm};
    }

    // Constructor
    public SimpleC172Sim(double initAlt, double initSpeed, double initThrottle) {
        z        = initAlt;
        vx       = initSpeed;
        throttle = initThrottle;

        setPreferredSize(new Dimension(900, 700));
        setBackground(Color.CYAN);
        setLayout(new BorderLayout());
        setFocusable(true);
        addKeyListener(this);

        timer = new Timer((int)(dt * 1000), this);
        timer.start();

        JPanel physicsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        physicsPanel.setPreferredSize(new Dimension(220, 380));
        physicsPanel.setBackground(Color.GRAY);
        physicsPanel.setFocusable(false);

        physicsPanel.add(makeLabel("Density Altitude:"));
        densitySlider = makeSlider(-2000, 4000, 0, 1500);
        densitySlider.addChangeListener(e -> { densityAlt = densitySlider.getValue(); refocusSim(); });
        physicsPanel.add(densitySlider);

        physicsPanel.add(makeLabel("Wing Loading:"));
        weightBox = new JComboBox<>(new String[]{"Light (900kg)", "Normal (1100kg)", "Heavy (1400kg)"});
        weightBox.setPreferredSize(new Dimension(120, 25));
        weightBox.setSelectedIndex(1);
        weightBox.setFocusable(false);
        weightBox.addActionListener(e -> {
            switch (weightBox.getSelectedIndex()) {
                case 0: mass = 900;  break;
                case 1: mass = 1100; break;
                case 2: mass = 1400; break;
            }
            refocusSim();
        });
        mass = 1100;
        physicsPanel.add(weightBox);

        physicsPanel.add(makeLabel("CG Position:"));
        cgSlider = makeSlider(-100, 100, 0, 50);
        cgSlider.addChangeListener(e -> { cgPosition = cgSlider.getValue() / 100.0; refocusSim(); });
        physicsPanel.add(cgSlider);

        physicsPanel.add(makeLabel("Headwind (+) / Tailwind (-):"));
        windSlider = makeSlider(-20, 20, 0, 10);
        windSlider.addChangeListener(e -> { windX = -windSlider.getValue(); refocusSim(); });
        physicsPanel.add(windSlider);

        enhancedStallBox = makeCheckbox("Enhanced Stall Model", Color.BLACK);
        enhancedStallBox.addActionListener(e -> { useEnhancedStall = enhancedStallBox.isSelected(); refocusSim(); });
        physicsPanel.add(enhancedStallBox);

        linearModelBox = makeCheckbox("Linear Aero Model", Color.YELLOW);
        linearModelBox.addActionListener(e -> { useLinearModel = linearModelBox.isSelected(); refocusSim(); });
        physicsPanel.add(linearModelBox);

        JButton plotBtn = new JButton("Show Coeff Plots");
        plotBtn.setFocusable(false);
        plotBtn.addActionListener(e -> {
            if (plotPanel == null || !plotPanel.isDisplayable()) {
                plotPanel = new CoeffPlotPanel(this);
                JFrame pf = new JFrame("Aerodynamic Coefficients vs AoA");
                pf.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                pf.setContentPane(plotPanel);
                pf.setSize(720, 650);
                pf.setLocationRelativeTo(null);
                pf.setVisible(true);
            }
            refocusSim();
        });
        physicsPanel.add(plotBtn);

        add(physicsPanel, BorderLayout.EAST);
    }

    private JLabel    makeLabel   (String t)                        { JLabel l = new JLabel(t); l.setForeground(Color.BLACK); return l; }
    private JCheckBox makeCheckbox(String t, Color fg)              { JCheckBox c = new JCheckBox(t); c.setForeground(fg); c.setFocusable(false); return c; }
    private JSlider   makeSlider  (int mn, int mx, int v, int tick) { JSlider s = new JSlider(mn, mx, v); s.setMajorTickSpacing(tick); s.setPaintTicks(true); s.setPaintLabels(true); s.setFocusable(false); return s; }

    public void step() {
        // Airspeed
        double airspeedX = vx - windX;
        double airspeedZ = vz - windZ;
        double speed = Math.max(1.0, Math.sqrt(airspeedX * airspeedX + airspeedZ * airspeedZ));

        // Flight path angle (gamma)
        double gamma = Math.atan2(airspeedZ, airspeedX);

        // Angle of attack
        aoa = pitch - gamma;

        double flapDeg     = flaps * 40.0;
        double flapStallMargin = 0.008 * flapDeg;
        double effStallAoA = stallAoA + Math.toRadians(0.2) * flapDeg;
        double effClMax    = clMaxClean + 0.4 * (flapDeg / 40.0);

        stallWarning = Math.abs(aoa) >= (effStallAoA * stallWarningThreshold);
        stalled      = Math.abs(aoa) >  effStallAoA;

        double[] coeffs = computeCoeffs(aoa, flaps, cgPosition, useEnhancedStall, useLinearModel);
        currentCl = coeffs[0];
        currentCd = coeffs[1];
        currentCm = coeffs[2];

        // Density: linear lapse approximation. Valid within ~4000m of sea level.
        double rho = rhoSL * Math.max(0.5, Math.min(2.0, 1.0 - densityAlt / 10000.0));
        double q   = 0.5 * rho * speed * speed; // dynamic pressure

        double L = q * wingArea * currentCl;
        double D = q * wingArea * currentCd;
        double T = throttle * maxThrust;

        double fx = T * Math.cos(pitch) - D * Math.cos(gamma) - L * Math.sin(gamma);
        double fz = T * Math.sin(pitch) - D * Math.sin(gamma) + L * Math.cos(gamma) - mass * g;

        vx += (fx / mass) * dt;
        vz += (fz / mass) * dt;
        x  += vx * dt;
        z  += vz * dt;

        if (z < 0) { z = 0; vz = 0; timer.stop(); }

        //Pitch dynamics
        double cgStabilityFactor = 0.08 * cgPosition;
        double cgElevatorFactor  = 1.0  + 0.25 * cgPosition;

        double stallCmMod = 0.0;
        if (stalled && useEnhancedStall) {
            double aoaOver = Math.abs(aoa) - effStallAoA;
            stallCmMod = -0.02 * (aoaOver / Math.toRadians(10));
        }

        double effectiveCm_alpha = Cm_alpha + stallCmMod - cgStabilityFactor;
        double totalElevator     = elevator + trimElevator;

        // Elevator: direct pitch rate input (simplified actuator model)
        pitch += totalElevator * (0.008 / cgElevatorFactor);

        double targetAoA = trimElevator * 0.1;
        pitch += effectiveCm_alpha * (aoa - targetAoA) * 0.003;
        pitch  = Math.max(Math.toRadians(-40), Math.min(Math.toRadians(40), pitch));

        simTime += dt;
        if (simTime >= nextLogTime) {
            System.out.printf(
                    "t=%.1f | alt=%.1f | V=%.1f | AoA=%.1f° | CL=%.3f | CD=%.3f | Cm=%.4f | model=%s | stalled=%b%n",
                    simTime, z, Math.sqrt(vx*vx + vz*vz), Math.toDegrees(aoa),
                    currentCl, currentCd, currentCm,
                    useLinearModel ? "LINEAR" : (useEnhancedStall ? "NL-enhanced" : "NL-basic"),
                    stalled);
            nextLogTime += 1.0;
        }

        if (plotPanel != null && plotPanel.isDisplayable()) plotPanel.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();

        // Ground and runway markings
        int groundY = (int)(h / 2.0 - (0 - z) * 3.0);
        g.setColor(new Color(60, 180, 60));
        g.fillRect(0, groundY - 20, w, 40);
        g.setColor(Color.LIGHT_GRAY);
        for (int i = -200; i <= 200; i++) {
            int sx = (int)(w / 2.0 + (i * 50.0 - x) * 3.0);
            if (sx >= -10 && sx <= w + 10) g.fillRect(sx - 4, groundY - 12, 8, 8);
        }

        Graphics2D g2 = (Graphics2D) g;
        if (stallWarning) { g2.setColor(new Color(255, 165, 0, 100)); g2.fillRect(0, 0, w, h); }

        // Aircraft body
        AffineTransform old = g2.getTransform();
        g2.setColor(Color.RED);
        g2.translate(w / 2.0, h / 2.0);
        g2.rotate(-pitch);
        g2.fillRect(-30, -4, 60, 8);
        g2.fillPolygon(new int[]{30, 15, 15}, new int[]{0, -10, 10}, 3);

        // Flap deflection indicator
        if (flaps > 0) {
            g2.setColor(Color.YELLOW);
            int fl = (int)(10 * flaps);
            g2.fillRect(-20, 4, 8, fl);
            g2.fillRect( 10, 4, 8, fl);
        }
        g2.setTransform(old);

        // HUD
        g.setColor(Color.BLACK);
        int yp = 20;
        g.drawString(String.format("Alt: %.1f m",    z),                           10, yp); yp += 15;
        g.drawString(String.format("Speed: %.1f m/s", Math.sqrt(vx*vx + vz*vz)),  10, yp); yp += 15;
        g.drawString(String.format("Pitch: %.1f°",   Math.toDegrees(pitch)),       10, yp); yp += 15;
        g.drawString(String.format("AoA: %.1f°",     Math.toDegrees(aoa)),         10, yp); yp += 15;

        // Live coefficient readout — these are the exact values used in force calculations
        g.setColor(new Color(0, 80, 180));
        g.drawString(String.format("CL: %.3f",  currentCl), 10, yp); yp += 15;
        g.drawString(String.format("CD: %.3f",  currentCd), 10, yp); yp += 15;
        g.drawString(String.format("Cm: %.4f",  currentCm), 10, yp); yp += 15;

        g.setColor(Color.BLACK);
        if (stallWarning) { g.setColor(Color.ORANGE); g.drawString("⚠ STALL WARNING", 10, yp); g.setColor(Color.BLACK); }
        else               g.drawString("Stalled: " + stalled, 10, yp);
        yp += 15;

        g.drawString(String.format("Throttle: %.2f",  throttle),      10, yp); yp += 15;
        g.drawString(String.format("Elevator: %.2f",  elevator),      10, yp); yp += 15;
        g.drawString(String.format("Trim: %.2f",      trimElevator),  10, yp); yp += 15;
        g.drawString(String.format("Flaps: %.0f%%",   flaps * 100),   10, yp); yp += 15;
        g.drawString(String.format("CG: %.2f",        cgPosition),    10, yp); yp += 15;
        g.drawString(String.format("Wind: %.1f m/s",  windX),         10, yp); yp += 15;
        g.drawString(String.format("Density Alt: %.0f m", densityAlt),10, yp); yp += 15;
        g.drawString(String.format("Mass: %.0f kg",   mass),          10, yp); yp += 15;

        g.setColor(useLinearModel ? Color.BLUE : new Color(180, 0, 0));
        g.drawString("Model: " + (useLinearModel ? "LINEAR"
                : (useEnhancedStall ? "NONLINEAR (Enhanced)" : "NONLINEAR (Basic)")), 10, yp); yp += 15;

        g.setColor(Color.BLACK);
        g.drawString("Controls: Arrows, Q/A=Trim, F/G=Flaps", 10, yp);
    }

    @Override public void actionPerformed(ActionEvent e) { step(); repaint(); }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:    elevator     = Math.min( 1.0, elevator     + 0.10); break;
            case KeyEvent.VK_DOWN:  elevator     = Math.max(-1.0, elevator     - 0.10); break;
            case KeyEvent.VK_RIGHT: throttle     = Math.min( 1.0, throttle     + 0.05); break;
            case KeyEvent.VK_LEFT:  throttle     = Math.max( 0.0, throttle     - 0.05); break;
            case KeyEvent.VK_Q:     trimElevator = Math.min( 1.0, trimElevator + 0.02); break;
            case KeyEvent.VK_A:     trimElevator = Math.max(-1.0, trimElevator - 0.02); break;
            case KeyEvent.VK_F:     flaps        = Math.min( 1.0, flaps        + 0.25); break;
            case KeyEvent.VK_G:     flaps        = Math.max( 0.0, flaps        - 0.25); break;
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped   (KeyEvent e) {}

    static class CoeffPlotPanel extends JPanel {
        final SimpleC172Sim sim;
        static final int    M       = 55;   // margin px
        static final double AOA_MIN = Math.toRadians(-25);
        static final double AOA_MAX = Math.toRadians( 25);
        static final int    N       = 300;  // sweep resolution

        CoeffPlotPanel(SimpleC172Sim sim) {
            this.sim = sim;
            setBackground(new Color(245, 245, 250));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w  = getWidth();
            int h  = getHeight();
            int pw = w - M * 2;
            int ph = (h - M * 2 - 30) / 3; 

            // CL plot: y range captures full nonlinear sweep including post-stall
            drawPlot(g2, M, M,                    pw, ph, "CL vs Angle of Attack", 0, -1.8,  1.8, true);
            // CD plot: y range from 0 to show drag rise clearly
            drawPlot(g2, M, M + ph + 15,          pw, ph, "CD vs Angle of Attack", 1,  0.0,  0.6, false);
            // Cm plot: symmetric range to show both stable and unstable regions
            drawPlot(g2, M, M + (ph + 15) * 2,   pw, ph, "Cm vs Angle of Attack", 2, -0.35, 0.35, false);

            // Legend
            int lx = M, ly = h - 18;
            g2.setFont(new Font("Arial", Font.PLAIN, 11));

            g2.setColor(new Color(180, 0, 0));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(lx, ly, lx + 20, ly); lx += 24;
            g2.setColor(Color.BLACK);
            g2.drawString("Nonlinear", lx, ly + 4); lx += 70;

            g2.setColor(new Color(0, 80, 200));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(lx, ly, lx + 20, ly); lx += 24;
            g2.setColor(Color.BLACK);
            g2.drawString("Linear", lx, ly + 4); lx += 55;

            float[] dash = {5f, 4f};
            g2.setColor(new Color(220, 100, 0));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
            g2.drawLine(lx, ly, lx + 20, ly); lx += 24;
            g2.setStroke(new BasicStroke(1));
            g2.setColor(Color.BLACK);
            g2.drawString("Stall AoA", lx, ly + 4); lx += 72;

            g2.setColor(new Color(0, 150, 0));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 3f}, 0));
            g2.drawLine(lx, ly, lx + 20, ly); lx += 24;
            g2.setStroke(new BasicStroke(1));
            g2.setColor(Color.BLACK);
            g2.drawString("Current AoA", lx, ly + 4);
        }

        void drawPlot(Graphics2D g2, int ox, int oy, int pw, int ph,
                      String title, int ci, double yMin, double yMax, boolean showStall) {

            // Background and border
            g2.setColor(Color.WHITE);
            g2.fillRect(ox, oy, pw, ph);
            g2.setColor(new Color(180, 180, 180));
            g2.drawRect(ox, oy, pw, ph);

            // Title
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.setColor(Color.BLACK);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(title, ox + (pw - fm.stringWidth(title)) / 2, oy - 6);

            // Horizontal grid lines at 25% intervals
            g2.setColor(new Color(230, 230, 230));
            g2.setStroke(new BasicStroke(0.5f));
            for (int i = 1; i < 4; i++) g2.drawLine(ox, oy + ph*i/4, ox+pw, oy + ph*i/4);

            // Zero-value horizontal reference line
            g2.setColor(new Color(190, 190, 190));
            g2.drawLine(ox, yToScreen(0, yMin, yMax, oy, ph), ox+pw, yToScreen(0, yMin, yMax, oy, ph));

            // AoA = 0 vertical reference line
            g2.drawLine(xToScreen(0, ox, pw), oy, xToScreen(0, ox, pw), oy+ph);
            g2.setStroke(new BasicStroke(1));

            // Stall AoA markers (CL plot only)
            if (showStall) {
                double effStall = sim.stallAoA + Math.toRadians(0.2) * sim.flaps * 40.0;
                float[] dash = {6f, 4f};
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
                g2.setColor(new Color(220, 100, 0));
                for (double s : new double[]{effStall, -effStall}) {
                    int sx = xToScreen(s, ox, pw);
                    g2.drawLine(sx, oy, sx, oy + ph);
                }
                g2.setStroke(new BasicStroke(1));
                g2.setFont(new Font("Arial", Font.PLAIN, 9));
                g2.drawString("stall", xToScreen(effStall, ox, pw) + 3, oy + 11);
            }

            // Nonlinear curve
            g2.setColor(new Color(180, 0, 0));
            g2.setStroke(new BasicStroke(2));
            drawCurve(g2, ox, oy, pw, ph, yMin, yMax, ci, false);

            // Linear curve
            g2.setColor(new Color(0, 80, 200));
            g2.setStroke(new BasicStroke(2));
            drawCurve(g2, ox, oy, pw, ph, yMin, yMax, ci, true);

            // Current AoA cursor
            int curX = Math.max(ox, Math.min(ox+pw, xToScreen(sim.aoa, ox, pw)));
            float[] dash2 = {4f, 3f};
            g2.setColor(new Color(0, 150, 0));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash2, 0));
            g2.drawLine(curX, oy, curX, oy+ph);

            // Dot at current coefficient value on nonlinear curve
            double[] cur = sim.computeCoeffs(sim.aoa, sim.flaps, sim.cgPosition, sim.useEnhancedStall, false);
            int dotY = Math.max(oy, Math.min(oy+ph, yToScreen(cur[ci], yMin, yMax, oy, ph)));
            g2.setStroke(new BasicStroke(1));
            g2.fillOval(curX - 4, dotY - 4, 8, 8);

            // X-axis labels (degrees)
            g2.setFont(new Font("Arial", Font.PLAIN, 10));
            g2.setColor(Color.BLACK);
            for (int d = -20; d <= 20; d += 5) {
                int sx = xToScreen(Math.toRadians(d), ox, pw);
                g2.drawLine(sx, oy+ph, sx, oy+ph+3);
                if (d % 10 == 0) {
                    String lbl = d + "°";
                    g2.drawString(lbl, sx - g2.getFontMetrics().stringWidth(lbl)/2, oy+ph+13);
                }
            }

            // Y-axis labels
            for (int i = 0; i <= 4; i++) {
                double val = yMin + (yMax - yMin) * i / 4.0;
                int    sy  = yToScreen(val, yMin, yMax, oy, ph);
                g2.drawLine(ox-3, sy, ox, sy);
                String lbl = String.format("%.2f", val);
                g2.drawString(lbl, ox - M + 2, sy + 4);
            }
        }

        void drawCurve(Graphics2D g2, int ox, int oy, int pw, int ph,
                       double yMin, double yMax, int ci, boolean linear) {
            int px = -1, py = -1;
            for (int i = 0; i <= N; i++) {
                double a  = AOA_MIN + (AOA_MAX - AOA_MIN) * i / N;
                double[] c = sim.computeCoeffs(a, sim.flaps, sim.cgPosition, sim.useEnhancedStall, linear);
                int sx = xToScreen(a, ox, pw);
                int sy = Math.max(oy-2, Math.min(oy+ph+2, yToScreen(c[ci], yMin, yMax, oy, ph)));
                if (px >= 0) g2.drawLine(px, py, sx, sy);
                px = sx; py = sy;
            }
        }

        int xToScreen(double aoa, int ox, int pw) {
            return ox + (int)((aoa - AOA_MIN) / (AOA_MAX - AOA_MIN) * pw);
        }
        int yToScreen(double v, double yMin, double yMax, int oy, int ph) {
            return oy + ph - (int)((v - yMin) / (yMax - yMin) * ph);
        }
    }

    // StartupFrame — initial condition configuration
    public static class StartupFrame extends JFrame {
        public StartupFrame() {
            setTitle("Cessna 172 Flight Simulator - Setup");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(650, 675);
            setLocationRelativeTo(null);

            JPanel main = new JPanel();
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
            main.setBackground(new Color(240, 240, 245));

            JLabel titleLabel = new JLabel("Cessna 172 Flight Simulator");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
            titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            titleLabel.setForeground(new Color(40, 40, 80));
            main.add(titleLabel);
            main.add(Box.createVerticalStrut(10));

            JLabel sub = new JLabel("Configure Initial Flight Conditions");
            sub.setFont(new Font("Arial", Font.PLAIN, 14));
            sub.setAlignmentX(Component.CENTER_ALIGNMENT);
            sub.setForeground(new Color(80, 80, 120));
            main.add(sub);
            main.add(Box.createVerticalStrut(30));

            JSlider altSl = makeSl(0, 2000, 100, 500, 100);
            JSlider spdSl = makeSl(30,  70,  50,  10,   5);
            JSlider thrSl = makeSl(0,  100,  65,  25,   5);

            JLabel altLbl = makeVL("100 m");
            JLabel spdLbl = makeVL("50 m/s");
            JLabel thrLbl = makeVL("65%");

            altSl.addChangeListener(e -> altLbl.setText(altSl.getValue() + " m"));
            spdSl.addChangeListener(e -> spdLbl.setText(spdSl.getValue() + " m/s"));
            thrSl.addChangeListener(e -> thrLbl.setText(thrSl.getValue() + "%"));

            main.add(buildPanel("Initial Altitude",  altSl, altLbl));
            main.add(Box.createVerticalStrut(15));
            main.add(buildPanel("Initial Airspeed",  spdSl, spdLbl));
            main.add(Box.createVerticalStrut(15));
            main.add(buildPanel("Initial Throttle",  thrSl, thrLbl));
            main.add(Box.createVerticalStrut(25));

            JPanel presets = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
            presets.setBackground(new Color(240, 240, 245));
            JButton cr = makeBtn("Cruise Configuration",   new Color(100,150,200));
            JButton ap = makeBtn("Approach Configuration", new Color(100,150,200));
            cr.addActionListener(e -> { altSl.setValue(1000); spdSl.setValue(50); thrSl.setValue(65); });
            ap.addActionListener(e -> { altSl.setValue( 300); spdSl.setValue(45); thrSl.setValue(60); });
            presets.add(cr); presets.add(ap);
            main.add(presets);
            main.add(Box.createVerticalStrut(25));

            JButton start = makeBtn("START FLIGHT", new Color(50,150,50));
            start.setFont(new Font("Arial", Font.BOLD, 18));
            start.setBorder(BorderFactory.createEmptyBorder(15, 50, 15, 50));
            start.setAlignmentX(Component.CENTER_ALIGNMENT);
            start.addActionListener(e -> {
                dispose();
                JFrame sf = new JFrame("Cessna 172 Physics Simulator");
                sf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                SimpleC172Sim sim = new SimpleC172Sim(altSl.getValue(), spdSl.getValue(), thrSl.getValue() / 100.0);
                sf.setContentPane(sim);
                sf.pack();
                sf.setLocationRelativeTo(null);
                sf.setVisible(true);
            });
            main.add(start);
            add(main);
            setVisible(true);
        }

        static JSlider makeSl(int mn, int mx, int v, int maj, int min) {
            JSlider s = new JSlider(mn, mx, v);
            s.setMajorTickSpacing(maj); s.setMinorTickSpacing(min);
            s.setPaintTicks(true); s.setPaintLabels(true);
            s.setBackground(new Color(240, 240, 245));
            return s;
        }
        static JLabel makeVL(String t) {
            JLabel l = new JLabel(t, SwingConstants.CENTER);
            l.setFont(new Font("Arial", Font.BOLD, 16));
            l.setForeground(new Color(0, 100, 200));
            return l;
        }
        static JButton makeBtn(String t, Color bg) {
            JButton b = new JButton(t);
            b.setFont(new Font("Arial", Font.BOLD, 13));
            b.setBackground(bg); b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            return b;
        }
        static JPanel buildPanel(String label, JSlider slider, JLabel val) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(new Color(240, 240, 245));
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180,180,200), 1),
                    BorderFactory.createEmptyBorder(10, 15, 10, 15)));
            JLabel tl = new JLabel(label);
            tl.setFont(new Font("Arial", Font.BOLD, 14));
            tl.setAlignmentX(Component.CENTER_ALIGNMENT);
            tl.setForeground(new Color(60, 60, 100));
            p.add(tl); p.add(Box.createVerticalStrut(8));
            p.add(slider); p.add(Box.createVerticalStrut(5)); p.add(val);
            return p;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(StartupFrame::new);
    }
}
