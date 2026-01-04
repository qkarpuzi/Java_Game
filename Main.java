import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

public class Main extends JPanel implements ActionListener, KeyListener, ComponentListener {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // Paddle base sizes (we swap them depending on orientation)
    private static final int PADDLE_WIDTH = 20;
    private static final int PADDLE_HEIGHT = 100;

    private static final int PUCK_SIZE = 22;

    // Goal opening size (on the side where you score)
    private static final int GOAL_WIDTH = 200;
    private static final int GOAL_DEPTH = 18; // just visual thickness

    // Keep some breathing room around the center line so paddles don't cross it
    private static final int MID_GAP = 8;

    // Level time
    private static final int LEVEL_TIME_SECONDS = 90; // 1:30

    // Virtual resolution (we draw everything relative to this)
    private static final double VIRTUAL_W = WIDTH;
    private static final double VIRTUAL_H = HEIGHT;

    private enum GameState { MENU, PLAYING }
    private enum GameMode { PVP, BOT, LEVELS }

    private GameState gameState = GameState.MENU;
    private GameMode gameMode;

    /**
     * isVertical = true  => goals are TOP/BOTTOM (players have top-half vs bottom-half)
     * isVertical = false => goals are LEFT/RIGHT (players have left-half vs right-half)
     */
    private boolean isVertical = true;

    private int difficulty = 1;
    private int currentLevel = 1;
    private int unlockedLevels = 1;

    // Positions in VIRTUAL coordinates (800x600)
    private double player1X, player1Y;
    private double player2X, player2Y;
    private double puckX, puckY;
    private double puckDX, puckDY;

    private int player1Score = 0;
    private int player2Score = 0;

    // Level timer (seconds)
    private double levelTimer = 0;

    private boolean[] keys = new boolean[256];
    private Timer timer;
    private Random random = new Random();
    private ArrayList<Obstacle> obstacles = new ArrayList<>();
    private JFrame frame;

    // For real delta-time smoothing
    private long lastTickNanos = 0;

    // --- BOT AI ---
    private double botTargetX = WIDTH / 2.0;
    private double botTargetY = 60;
    private double botThinkTimer = 0;

    // --- LEVEL SYSTEM ---
    private static class Level {
        String name;
        int winScore;
        int obstacleCount;
        double speedMultiplier;
        Integer timeLimit; // seconds

        Level(String name, int winScore, int obstacleCount, double speedMultiplier, Integer timeLimit) {
            this.name = name;
            this.winScore = winScore;
            this.obstacleCount = obstacleCount;
            this.speedMultiplier = speedMultiplier;
            this.timeLimit = timeLimit;
        }
    }

    private Level[] levels = {
            new Level("Rookie Ice",     3, 0, 1.0, LEVEL_TIME_SECONDS),
            new Level("Neon Barriers",  5, 2, 1.2, LEVEL_TIME_SECONDS),
            new Level("Chaos Master",   5, 3, 1.6, LEVEL_TIME_SECONDS)
    };

    private static class Obstacle {
        double x, y, w, h, dy;
        Obstacle(double x, double y, double w, double h, double dy) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.dy = dy;
        }
    }

    public Main(JFrame frame) {
        this.frame = frame;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(15, 23, 42));
        addKeyListener(this);
        setFocusable(true);

        // Listen for resize so we can re-render full-fit
        addComponentListener(this);

        timer = new Timer(16, this);
    }

    public void startGame(GameMode mode, int diff, int level) {
        this.gameMode = mode;
        this.difficulty = diff;
        this.currentLevel = level;

        this.isVertical = (mode != GameMode.PVP);

        player1Score = 0;
        player2Score = 0;
        levelTimer = 0;
        obstacles.clear();

        botThinkTimer = 0;
        botTargetX = WIDTH / 2.0;
        botTargetY = 60;

        if (mode == GameMode.LEVELS) {
            Level lvl = levels[currentLevel - 1];
            for (int i = 0; i < lvl.obstacleCount; i++) {
                obstacles.add(new Obstacle(300 + (i * 100), 200, 15, 80, 120 + i * 30));
            }
        }

        initPositions();
        resetPuck();

        gameState = GameState.PLAYING;
        lastTickNanos = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    private void initPositions() {
        if (isVertical) {
            player1X = WIDTH / 2.0 - PADDLE_HEIGHT / 2.0;
            player1Y = HEIGHT - 120;

            player2X = WIDTH / 2.0 - PADDLE_HEIGHT / 2.0;
            player2Y = 40;
        } else {
            player1X = 40;
            player1Y = HEIGHT / 2.0 - PADDLE_HEIGHT / 2.0;

            player2X = WIDTH - 60;
            player2Y = HEIGHT / 2.0 - PADDLE_HEIGHT / 2.0;
        }
    }

    private void resetPuck() {
        puckX = WIDTH / 2.0 - PUCK_SIZE / 2.0;
        puckY = HEIGHT / 2.0 - PUCK_SIZE / 2.0;

        double baseSpeed = 360.0;
        if (gameMode == GameMode.LEVELS) baseSpeed *= levels[currentLevel - 1].speedMultiplier;

        double angle = Math.toRadians(25 + random.nextInt(130));
        if (random.nextBoolean()) angle = -angle;

        if (isVertical) {
            puckDY = (random.nextBoolean() ? -1 : 1) * baseSpeed;
            puckDX = (Math.cos(angle) * baseSpeed) * 0.55;
        } else {
            puckDX = (random.nextBoolean() ? -1 : 1) * baseSpeed;
            puckDY = (Math.sin(angle) * baseSpeed) * 0.55;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState != GameState.PLAYING) return;

        long now = System.nanoTime();
        double dt = (now - lastTickNanos) / 1_000_000_000.0;
        lastTickNanos = now;
        if (dt > 0.05) dt = 0.05;

        updateGame(dt);
        repaint();
    }

    private void updateGame(double dt) {
        double paddleSpeed = 520.0;

        double pW = isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH;
        double pH = isVertical ? PADDLE_WIDTH  : PADDLE_HEIGHT;

        // Player 1 WASD
        if (keys[KeyEvent.VK_A]) player1X -= paddleSpeed * dt;
        if (keys[KeyEvent.VK_D]) player1X += paddleSpeed * dt;
        if (keys[KeyEvent.VK_W]) player1Y -= paddleSpeed * dt;
        if (keys[KeyEvent.VK_S]) player1Y += paddleSpeed * dt;

        if (isVertical) {
            player1X = clamp(player1X, 0, WIDTH - pW);
            player1Y = clamp(player1Y, HEIGHT / 2.0 + MID_GAP, HEIGHT - pH);
        } else {
            player1X = clamp(player1X, 0, WIDTH / 2.0 - MID_GAP - pW);
            player1Y = clamp(player1Y, 0, HEIGHT - pH);
        }

        if (gameMode == GameMode.PVP) {
            if (keys[KeyEvent.VK_LEFT])  player2X -= paddleSpeed * dt;
            if (keys[KeyEvent.VK_RIGHT]) player2X += paddleSpeed * dt;
            if (keys[KeyEvent.VK_UP])    player2Y -= paddleSpeed * dt;
            if (keys[KeyEvent.VK_DOWN])  player2Y += paddleSpeed * dt;

            if (isVertical) {
                player2X = clamp(player2X, 0, WIDTH - pW);
                player2Y = clamp(player2Y, 0, HEIGHT / 2.0 - MID_GAP - pH);
            } else {
                player2X = clamp(player2X, WIDTH / 2.0 + MID_GAP, WIDTH - pW);
                player2Y = clamp(player2Y, 0, HEIGHT - pH);
            }
        } else {
            moveBot(dt);
        }

        if (gameMode == GameMode.LEVELS) {
            levelTimer += dt;
            Integer limit = levels[currentLevel - 1].timeLimit;
            if (limit != null && levelTimer >= limit) {
                timer.stop();
                JOptionPane.showMessageDialog(this, "Time's up! Try again.");
                showMenu();
                return;
            }
        }

        puckX += puckDX * dt;
        puckY += puckDY * dt;

        if (isVertical) {
            if (puckX <= 0) { puckX = 0; puckDX *= -1; }
            else if (puckX >= WIDTH - PUCK_SIZE) { puckX = WIDTH - PUCK_SIZE; puckDX *= -1; }
        } else {
            if (puckY <= 0) { puckY = 0; puckDY *= -1; }
            else if (puckY >= HEIGHT - PUCK_SIZE) { puckY = HEIGHT - PUCK_SIZE; puckDY *= -1; }
        }

        handleCollisions(dt);
        checkScoring();
    }

    // ✅ Better balanced bot levels (easy/medium/hard)
    private void moveBot(double dt) {
        double pW = isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH;
        double pH = isVertical ? PADDLE_WIDTH  : PADDLE_HEIGHT;

        double botSpeed;
        double reaction;
        double aimError;
        double chaseChance;

        if (difficulty == 1) { // Easy (beatable)
            botSpeed = 260.0;
            reaction = 0.22;
            aimError = 80.0;
            chaseChance = 0.65;
        } else if (difficulty == 2) { // Medium
            botSpeed = 360.0;
            reaction = 0.14;
            aimError = 35.0;
            chaseChance = 0.85;
        } else { // Hard (very hard)
            botSpeed = 470.0;
            reaction = 0.09;
            aimError = 12.0;
            chaseChance = 0.97;
        }

        botThinkTimer -= dt;
        if (botThinkTimer <= 0) {
            botThinkTimer = reaction;

            if (isVertical) {
                double topMaxY = HEIGHT / 2.0 - MID_GAP - pH;
                double homeX = WIDTH / 2.0 - pW / 2.0;
                double homeY = 35;

                boolean puckInBotHalf = (puckY <= HEIGHT / 2.0);
                boolean puckMovingToBot = (puckDY < 0);

                if ((puckInBotHalf || puckMovingToBot) && random.nextDouble() < chaseChance) {
                    double defenseY = 85;

                    double t;
                    if (puckDY >= -1e-6) t = 0;
                    else {
                        t = (defenseY - puckY) / puckDY;
                        if (t < 0) t = 0;
                        if (t > 1.1) t = 1.1;
                    }

                    double predictedX = reflectPredictX(puckX + puckDX * t);
                    predictedX += (random.nextDouble() * 2 - 1) * aimError;

                    botTargetX = clamp(predictedX - pW / 2.0, 0, WIDTH - pW);

                    // Easy bot doesn’t track Y much (more beatable)
                    double yBias = (difficulty == 1 ? 10 : 22);
                    botTargetY = clamp(defenseY + yBias, 0, topMaxY);
                } else {
                    // return to home (so it never freezes)
                    botTargetX = clamp(homeX + (random.nextDouble() - 0.5) * aimError * 0.25, 0, WIDTH - pW);
                    botTargetY = clamp(homeY, 0, topMaxY);
                }

            } else {
                double rightMinX = WIDTH / 2.0 + MID_GAP;
                double rightMaxX = WIDTH - pW;
                double homeX = WIDTH - 60 - pW;
                double homeY = HEIGHT / 2.0 - pH / 2.0;

                boolean puckInBotHalf = (puckX >= WIDTH / 2.0);
                boolean puckMovingToBot = (puckDX > 0);

                if ((puckInBotHalf || puckMovingToBot) && random.nextDouble() < chaseChance) {
                    double defenseX = WIDTH - 95;

                    double t;
                    if (puckDX <= 1e-6) t = 0;
                    else {
                        t = (defenseX - puckX) / puckDX;
                        if (t < 0) t = 0;
                        if (t > 1.1) t = 1.1;
                    }

                    double predictedY = reflectPredictY(puckY + puckDY * t);
                    predictedY += (random.nextDouble() * 2 - 1) * aimError;

                    botTargetY = clamp(predictedY - pH / 2.0, 0, HEIGHT - pH);
                    botTargetX = clamp(defenseX, rightMinX, rightMaxX);
                } else {
                    botTargetX = clamp(homeX, rightMinX, rightMaxX);
                    botTargetY = clamp(homeY + (random.nextDouble() - 0.5) * aimError * 0.25, 0, HEIGHT - pH);
                }
            }
        }

        double step = botSpeed * dt;

        if (player2X < botTargetX) player2X = Math.min(player2X + step, botTargetX);
        else if (player2X > botTargetX) player2X = Math.max(player2X - step, botTargetX);

        if (player2Y < botTargetY) player2Y = Math.min(player2Y + step, botTargetY);
        else if (player2Y > botTargetY) player2Y = Math.max(player2Y - step, botTargetY);

        if (isVertical) {
            player2X = clamp(player2X, 0, WIDTH - pW);
            player2Y = clamp(player2Y, 0, HEIGHT / 2.0 - MID_GAP - pH);
        } else {
            player2X = clamp(player2X, WIDTH / 2.0 + MID_GAP, WIDTH - pW);
            player2Y = clamp(player2Y, 0, HEIGHT - pH);
        }
    }

    private double reflectPredictX(double x) {
        double min = 0;
        double max = WIDTH - PUCK_SIZE;
        double span = max - min;
        double v = x - min;
        if (span <= 0) return min;

        double m = v % (2 * span);
        if (m < 0) m += 2 * span;
        if (m > span) m = 2 * span - m;
        return min + m;
    }

    private double reflectPredictY(double y) {
        double min = 0;
        double max = HEIGHT - PUCK_SIZE;
        double span = max - min;
        double v = y - min;
        if (span <= 0) return min;

        double m = v % (2 * span);
        if (m < 0) m += 2 * span;
        if (m > span) m = 2 * span - m;
        return min + m;
    }

    private void handleCollisions(double dt) {
        Rectangle p1 = getPaddleRect(player1X, player1Y);
        Rectangle p2 = getPaddleRect(player2X, player2Y);
        Rectangle puck = new Rectangle((int) puckX, (int) puckY, PUCK_SIZE, PUCK_SIZE);

        if (puck.intersects(p1)) resolvePaddleHit(true);
        else if (puck.intersects(p2)) resolvePaddleHit(false);

        for (Obstacle obs : obstacles) {
            obs.y += obs.dy * dt;

            if (obs.y < 110) { obs.y = 110; obs.dy *= -1; }
            else if (obs.y > HEIGHT - 190) { obs.y = HEIGHT - 190; obs.dy *= -1; }

            Rectangle r = new Rectangle((int) obs.x, (int) obs.y, (int) obs.w, (int) obs.h);
            if (puck.intersects(r)) {
                puckDX *= -1;
                puckX += Math.signum(puckDX) * 6;
            }
        }
    }

    private void resolvePaddleHit(boolean hitP1) {
        double paddleX = hitP1 ? player1X : player2X;
        double paddleY = hitP1 ? player1Y : player2Y;

        double maxSpeed = (gameMode == GameMode.LEVELS) ? 620.0 * levels[currentLevel - 1].speedMultiplier : 620.0;

        if (isVertical) {
            double paddleCenter = paddleX + PADDLE_HEIGHT / 2.0;
            double hitPos = (puckX + PUCK_SIZE / 2.0) - paddleCenter;
            double spin = hitPos * 6.0;

            if (hitP1) puckDY = -Math.abs(puckDY);
            else       puckDY =  Math.abs(puckDY);

            puckDX += spin;

            if (hitP1) puckY = paddleY - PUCK_SIZE - 1;
            else       puckY = paddleY + PADDLE_WIDTH + 1;

        } else {
            double paddleCenter = paddleY + PADDLE_HEIGHT / 2.0;
            double hitPos = (puckY + PUCK_SIZE / 2.0) - paddleCenter;
            double spin = hitPos * 6.0;

            if (hitP1) puckDX =  Math.abs(puckDX);
            else       puckDX = -Math.abs(puckDX);

            puckDY += spin;

            if (hitP1) puckX = paddleX + PADDLE_WIDTH + 1;
            else       puckX = paddleX - PUCK_SIZE - 1;
        }

        double speed = Math.hypot(puckDX, puckDY);
        if (speed > maxSpeed) {
            double scale = maxSpeed / speed;
            puckDX *= scale;
            puckDY *= scale;
        }

        double minSpeed = 260.0;
        speed = Math.hypot(puckDX, puckDY);
        if (speed < minSpeed) {
            double scale = minSpeed / Math.max(1.0, speed);
            puckDX *= scale;
            puckDY *= scale;
        }
    }

    private void checkScoring() {
        boolean scored = false;

        if (isVertical) {
            double goalLeft = WIDTH / 2.0 - GOAL_WIDTH / 2.0;
            double goalRight = WIDTH / 2.0 + GOAL_WIDTH / 2.0;

            boolean inGoalX = (puckX + PUCK_SIZE / 2.0) >= goalLeft && (puckX + PUCK_SIZE / 2.0) <= goalRight;

            if (puckY < -PUCK_SIZE) {
                if (inGoalX) { player1Score++; scored = true; }
                else { puckY = -PUCK_SIZE; puckDY *= -1; }
            } else if (puckY > HEIGHT + PUCK_SIZE) {
                if (inGoalX) { player2Score++; scored = true; }
                else { puckY = HEIGHT + PUCK_SIZE; puckDY *= -1; }
            }
        } else {
            double goalTop = HEIGHT / 2.0 - GOAL_WIDTH / 2.0;
            double goalBottom = HEIGHT / 2.0 + GOAL_WIDTH / 2.0;

            boolean inGoalY = (puckY + PUCK_SIZE / 2.0) >= goalTop && (puckY + PUCK_SIZE / 2.0) <= goalBottom;

            if (puckX < -PUCK_SIZE) {
                if (inGoalY) { player2Score++; scored = true; }
                else { puckX = -PUCK_SIZE; puckDX *= -1; }
            } else if (puckX > WIDTH + PUCK_SIZE) {
                if (inGoalY) { player1Score++; scored = true; }
                else { puckX = WIDTH + PUCK_SIZE; puckDX *= -1; }
            }
        }

        if (scored) {
            resetPuck();
            checkWinCondition();
        }
    }

    private void checkWinCondition() {
        int target = (gameMode == GameMode.LEVELS) ? levels[currentLevel - 1].winScore : 7;

        if (player1Score >= target || player2Score >= target) {
            timer.stop();

            if (gameMode == GameMode.LEVELS && player1Score >= target) {
                if (currentLevel == unlockedLevels && unlockedLevels < levels.length) unlockedLevels++;
                JOptionPane.showMessageDialog(this, "Level " + currentLevel + " Cleared! Next level unlocked.");
            } else {
                JOptionPane.showMessageDialog(this, (player1Score > player2Score ? "Player 1" : "Opponent") + " Wins!");
            }

            showMenu();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // ✅ FULL FIT: scale virtual 800x600 to panel size (keeps aspect ratio)
        int w = getWidth();
        int h = getHeight();

        double sx = w / VIRTUAL_W;
        double sy = h / VIRTUAL_H;
        double s = Math.min(sx, sy);

        double drawW = VIRTUAL_W * s;
        double drawH = VIRTUAL_H * s;
        double ox = (w - drawW) / 2.0;
        double oy = (h - drawH) / 2.0;

        g2.translate(ox, oy);
        g2.scale(s, s);

        // Field background
        g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), 0, HEIGHT, new Color(30, 41, 59)));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        // Lines + center circle
        g2.setColor(new Color(56, 189, 248, 40));
        g2.setStroke(new BasicStroke(4));

        if (isVertical) g2.drawLine(0, HEIGHT / 2, WIDTH, HEIGHT / 2);
        else g2.drawLine(WIDTH / 2, 0, WIDTH / 2, HEIGHT);

        g2.drawOval(WIDTH / 2 - 75, HEIGHT / 2 - 75, 150, 150);

        drawGoals(g2);

        drawGlow(g2, (int) puckX, (int) puckY, PUCK_SIZE, PUCK_SIZE, new Color(250, 204, 21), true);

        drawGlow(g2, (int) player1X, (int) player1Y,
                isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH,
                isVertical ? PADDLE_WIDTH : PADDLE_HEIGHT,
                new Color(34, 197, 94), false);

        drawGlow(g2, (int) player2X, (int) player2Y,
                isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH,
                isVertical ? PADDLE_WIDTH : PADDLE_HEIGHT,
                new Color(239, 68, 68), false);

        for (Obstacle obs : obstacles) {
            drawGlow(g2, (int) obs.x, (int) obs.y, (int) obs.w, (int) obs.h, Color.CYAN, false);
        }

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 30));
        g2.drawString(player1Score + " - " + player2Score, WIDTH / 2 - 50, 40);

        if (gameMode == GameMode.LEVELS) {
            g2.drawString("LVL " + currentLevel, 20, 40);
            int remaining = Math.max(0, LEVEL_TIME_SECONDS - (int) Math.floor(levelTimer));
            String timeText = String.format("%d:%02d", remaining / 60, remaining % 60);
            g2.drawString(timeText, WIDTH - 110, 40);
        }

        g2.dispose();
    }

    private void drawGoals(Graphics2D g2) {
        g2.setStroke(new BasicStroke(3));
        g2.setColor(new Color(56, 189, 248, 90));

        if (isVertical) {
            int goalX = WIDTH / 2 - GOAL_WIDTH / 2;
            g2.drawRect(goalX, 0, GOAL_WIDTH, GOAL_DEPTH);
            g2.drawRect(goalX, HEIGHT - GOAL_DEPTH, GOAL_WIDTH, GOAL_DEPTH);

            g2.setColor(new Color(56, 189, 248, 30));
            g2.fillRect(goalX, 0, GOAL_WIDTH, GOAL_DEPTH);
            g2.fillRect(goalX, HEIGHT - GOAL_DEPTH, GOAL_WIDTH, GOAL_DEPTH);
        } else {
            int goalY = HEIGHT / 2 - GOAL_WIDTH / 2;
            g2.drawRect(0, goalY, GOAL_DEPTH, GOAL_WIDTH);
            g2.drawRect(WIDTH - GOAL_DEPTH, goalY, GOAL_DEPTH, GOAL_WIDTH);

            g2.setColor(new Color(56, 189, 248, 30));
            g2.fillRect(0, goalY, GOAL_DEPTH, GOAL_WIDTH);
            g2.fillRect(WIDTH - GOAL_DEPTH, goalY, GOAL_DEPTH, GOAL_WIDTH);
        }
    }

    private Rectangle getPaddleRect(double x, double y) {
        int w = isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH;
        int h = isVertical ? PADDLE_WIDTH : PADDLE_HEIGHT;
        return new Rectangle((int) x, (int) y, w, h);
    }

    private void drawGlow(Graphics2D g2, int x, int y, int w, int h, Color c, boolean circle) {
        for (int i = 8; i > 0; i--) {
            int alpha = Math.max(0, 20 - i * 2);
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            if (circle) g2.fillOval(x - i, y - i, w + i * 2, h + i * 2);
            else g2.fillRoundRect(x - i, y - i, w + i * 2, h + i * 2, 10, 10);
        }
        g2.setColor(c);
        if (circle) g2.fillOval(x, y, w, h);
        else g2.fillRoundRect(x, y, w, h, 10, 10);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public void showMenu() {
        gameState = GameState.MENU;
        if (timer != null) timer.stop();

        String[] options = {
                "2 Players (Horizontal)",
                "Bot: Easy", "Bot: Medium", "Bot: Hard",
                "Level 1: " + levels[0].name,
                "Level 2: " + levels[1].name + (unlockedLevels < 2 ? " [LOCKED]" : ""),
                "Level 3: " + levels[2].name + (unlockedLevels < 3 ? " [LOCKED]" : "")
        };

        int choice = JOptionPane.showOptionDialog(frame, "Select Mode", "Main Menu",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

        if (choice == -1) { requestFocusInWindow(); return; }

        if (choice == 0) startGame(GameMode.PVP, 1, 1);
        else if (choice >= 1 && choice <= 3) startGame(GameMode.BOT, choice, 1);
        else if (choice >= 4) {
            int lvl = choice - 3;
            if (lvl <= unlockedLevels) startGame(GameMode.LEVELS, 2, lvl);
            else { JOptionPane.showMessageDialog(frame, "Complete previous levels first!"); showMenu(); }
        }
    }

    private void handleEscPress() {
        for (int i = 0; i < keys.length; i++) keys[i] = false;
        if (timer != null) timer.stop();
        showMenu();
    }

    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE) { handleEscPress(); return; }
        if (code < 256) keys[code] = true;
    }

    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false;
    }

    public void keyTyped(KeyEvent e) {}

    // ComponentListener (repaint on resize)
    public void componentResized(ComponentEvent e) { repaint(); }
    public void componentMoved(ComponentEvent e) {}
    public void componentShown(ComponentEvent e) {}
    public void componentHidden(ComponentEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Cyber Hockey 2026");
        Main game = new Main(frame);

        frame.setLayout(new BorderLayout());
        frame.add(game, BorderLayout.CENTER);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 650));

        // ✅ Start maximized (close to fullscreen) and resizable
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setResizable(true);

        frame.setVisible(true);
        game.showMenu();
    }
}
