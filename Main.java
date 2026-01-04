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

    // Overtime + penalties
    private static final int OVERTIME_SECONDS = 30;
    private static final int PENALTY_SHOTS = 2;

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

    // For "don't go past the puck" feel
    private double prevP1X, prevP1Y;
    private double prevP2X, prevP2Y;

    private int player1Score = 0;
    private int player2Score = 0;

    // Level timer (seconds)
    private double levelTimer = 0;

    // Overtime + penalties state
    private boolean overtime = false;
    private double overtimeTimer = 0;
    private boolean penalties = false;
    private int penaltyRound = 0;
    private int penaltyP1 = 0;
    private int penaltyP2 = 0;
    private boolean penaltySuddenDeath = false;

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

    // --- Visual FX (simple neon particles) ---
    private static class Particle {
        double x, y, vx, vy, life;
        Particle(double x, double y, double vx, double vy, double life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life;
        }
    }
    private ArrayList<Particle> particles = new ArrayList<>();
    private double shake = 0;

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

        addComponentListener(this);

        timer = new Timer(16, this);
    }

    public void startGame(GameMode mode, int diff, int level) {
        this.gameMode = mode;
        this.currentLevel = level;

        // PVP = Horizontal, BOT/LEVELS = Vertical (as you had)
        this.isVertical = (mode != GameMode.PVP);

        // ✅ Bot difficulty gradually harder by LEVEL number
        if (mode == GameMode.LEVELS) {
            this.difficulty = clampInt(level, 1, 3); // level 1 easy, level 2 medium, level 3 hard
        } else {
            this.difficulty = diff;
        }

        player1Score = 0;
        player2Score = 0;

        levelTimer = 0;
        overtime = false;
        overtimeTimer = 0;
        penalties = false;
        penaltyRound = 0;
        penaltyP1 = 0;
        penaltyP2 = 0;
        penaltySuddenDeath = false;

        obstacles.clear();
        particles.clear();
        shake = 0;

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

        prevP1X = player1X; prevP1Y = player1Y;
        prevP2X = player2X; prevP2Y = player2Y;
    }

    private void resetPuck() {
        puckX = WIDTH / 2.0 - PUCK_SIZE / 2.0;
        puckY = HEIGHT / 2.0 - PUCK_SIZE / 2.0;

        double baseSpeed = 360.0;
        if (gameMode == GameMode.LEVELS) baseSpeed *= levels[currentLevel - 1].speedMultiplier;

        // Overtime: a tiny bit faster
        if (overtime) baseSpeed *= 1.08;

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
        updateParticles(dt);
        repaint();
    }

    private void updateGame(double dt) {
        double paddleSpeed = 540.0; // slightly snappier

        double pW = isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH;
        double pH = isVertical ? PADDLE_WIDTH  : PADDLE_HEIGHT;

        // Save prev (for "don’t go past puck")
        prevP1X = player1X; prevP1Y = player1Y;
        prevP2X = player2X; prevP2Y = player2Y;

        // Player 1 WASD
        if (keys[KeyEvent.VK_A]) player1X -= paddleSpeed * dt;
        if (keys[KeyEvent.VK_D]) player1X += paddleSpeed * dt;
        if (keys[KeyEvent.VK_W]) player1Y -= paddleSpeed * dt;
        if (keys[KeyEvent.VK_S]) player1Y += paddleSpeed * dt;

        // Clamp P1
        if (isVertical) {
            player1X = clamp(player1X, 0, WIDTH - pW);
            player1Y = clamp(player1Y, HEIGHT / 2.0 + MID_GAP, HEIGHT - pH);
        } else {
            player1X = clamp(player1X, 0, WIDTH / 2.0 - MID_GAP - pW);
            player1Y = clamp(player1Y, 0, HEIGHT - pH);
        }

        // Player 2 or Bot
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

        // ✅ Prevent paddles from going "through" the puck (feels like back-side hits it)
        resolvePaddlePuckOverlap();

        // ===== TIME RULES (LEVELS) =====
        if (gameMode == GameMode.LEVELS) {
            if (!overtime && !penalties) {
                levelTimer += dt;

                // If regular time ends
                if (levelTimer >= LEVEL_TIME_SECONDS) {
                    if (player1Score == player2Score) {
                        overtime = true;
                        overtimeTimer = 0;
                        spawnTextBurst(WIDTH / 2.0, HEIGHT / 2.0, 18);
                        JOptionPane.showMessageDialog(this, "OVERTIME! 30 seconds.");
                        resetPuck();
                    } else {
                        timer.stop();
                        JOptionPane.showMessageDialog(this, (player1Score > player2Score ? "You win!" : "You lose!"));
                        showMenu();
                        return;
                    }
                }
            } else if (overtime && !penalties) {
                overtimeTimer += dt;

                // If OT ends
                if (overtimeTimer >= OVERTIME_SECONDS) {
                    if (player1Score == player2Score) {
                        overtime = false;
                        penalties = true;
                        startPenalties();
                        return;
                    } else {
                        timer.stop();
                        JOptionPane.showMessageDialog(this, (player1Score > player2Score ? "You win in Overtime!" : "You lose in Overtime!"));
                        showMenu();
                        return;
                    }
                }
            }
        }

        // === PUCK PHYSICS ===
        puckX += puckDX * dt;
        puckY += puckDY * dt;

        // Bounce on the “non-goal” walls only
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

    // ===== Penalties (simple, fun, and quick) =====
    private void startPenalties() {
        // 2 penalties each; if still tie -> sudden death until someone wins
        penaltyRound = 0;
        penaltyP1 = 0;
        penaltyP2 = 0;
        penaltySuddenDeath = false;

        timer.stop(); // pause gameplay
        doPenalties();
    }

    private void doPenalties() {
        // Probability tuned by difficulty: easy bot = you score more / bot scores less
        double pYouScore, pBotScore;

        if (difficulty == 1) { // Easy
            pYouScore = 0.70;
            pBotScore = 0.40;
        } else if (difficulty == 2) { // Medium
            pYouScore = 0.60;
            pBotScore = 0.55;
        } else { // Hard
            pYouScore = 0.52;
            pBotScore = 0.68;
        }

        // 2 rounds each
        for (int i = 0; i < PENALTY_SHOTS; i++) {
            boolean you = random.nextDouble() < pYouScore;
            boolean bot = random.nextDouble() < pBotScore;
            if (you) penaltyP1++;
            if (bot) penaltyP2++;
            penaltyRound++;
        }

        // If tied -> sudden death (1 shot each until different)
        if (penaltyP1 == penaltyP2) {
            penaltySuddenDeath = true;
            int guard = 0;
            while (penaltyP1 == penaltyP2 && guard < 50) {
                boolean you = random.nextDouble() < pYouScore;
                boolean bot = random.nextDouble() < pBotScore;
                if (you) penaltyP1++;
                if (bot) penaltyP2++;
                guard++;
            }
        }

        String msg =
                "PENALTIES!\n" +
                "You: " + penaltyP1 + "  -  Opponent: " + penaltyP2 +
                (penaltySuddenDeath ? "\n(Sudden Death)" : "");

        JOptionPane.showMessageDialog(this, msg);

        // Decide winner
        if (penaltyP1 > penaltyP2) {
            // Level cleared
            if (currentLevel == unlockedLevels && unlockedLevels < levels.length) unlockedLevels++;
            JOptionPane.showMessageDialog(this, "You win on penalties! Level cleared.");
        } else {
            JOptionPane.showMessageDialog(this, "You lose on penalties! Try again.");
        }

        showMenu();
    }

    // =========================
    // BOT: easy -> medium -> hard (and more realistic)
    // =========================
    private void moveBot(double dt) {
        double pW = isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH;
        double pH = isVertical ? PADDLE_WIDTH  : PADDLE_HEIGHT;

        double botSpeed;
        double reaction;
        double aimError;
        double chaseChance;

        // ✅ Bot 1 easy, 2 medium, 3 hard
        if (difficulty == 1) { // Easy (beatable)
            botSpeed = 255.0;
            reaction = 0.24;
            aimError = 95.0;
            chaseChance = 0.60;
        } else if (difficulty == 2) { // Medium
            botSpeed = 360.0;
            reaction = 0.14;
            aimError = 38.0;
            chaseChance = 0.85;
        } else { // Hard (very hard)
            botSpeed = 480.0;
            reaction = 0.09;
            aimError = 12.0;
            chaseChance = 0.97;
        }

        // Overtime: bot becomes slightly more aggressive
        if (overtime) {
            botSpeed *= 1.05;
            aimError *= 0.90;
            reaction *= 0.95;
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

                    double yBias = (difficulty == 1 ? 8 : 22);
                    botTargetY = clamp(defenseY + yBias, 0, topMaxY);

                } else {
                    // Never freeze: go home/guard
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

    // ✅ Keeps paddle from overlapping the puck (so it "hits" it instead of going past)
    private void resolvePaddlePuckOverlap() {
        Rectangle puckR = new Rectangle((int)puckX, (int)puckY, PUCK_SIZE, PUCK_SIZE);

        Rectangle p1 = getPaddleRect(player1X, player1Y);
        if (p1.intersects(puckR)) {
            player1X = prevP1X;
            player1Y = prevP1Y;
        }

        Rectangle p2 = getPaddleRect(player2X, player2Y);
        if (p2.intersects(puckR)) {
            player2X = prevP2X;
            player2Y = prevP2Y;
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

        if (puck.intersects(p1)) {
            resolvePaddleHit(true);
            addImpactFX(puckX + PUCK_SIZE/2.0, puckY + PUCK_SIZE/2.0, new Color(34,197,94));
        } else if (puck.intersects(p2)) {
            resolvePaddleHit(false);
            addImpactFX(puckX + PUCK_SIZE/2.0, puckY + PUCK_SIZE/2.0, new Color(239,68,68));
        }

        for (Obstacle obs : obstacles) {
            obs.y += obs.dy * dt;

            if (obs.y < 110) { obs.y = 110; obs.dy *= -1; }
            else if (obs.y > HEIGHT - 190) { obs.y = HEIGHT - 190; obs.dy *= -1; }

            Rectangle r = new Rectangle((int) obs.x, (int) obs.y, (int) obs.w, (int) obs.h);
            if (puck.intersects(r)) {
                puckDX *= -1;
                puckX += Math.signum(puckDX) * 6;
                addImpactFX(obs.x + obs.w/2.0, obs.y + obs.h/2.0, Color.CYAN);
            }
        }
    }

    private void resolvePaddleHit(boolean hitP1) {
        double paddleX = hitP1 ? player1X : player2X;
        double paddleY = hitP1 ? player1Y : player2Y;

        double maxSpeed = (gameMode == GameMode.LEVELS)
                ? 640.0 * levels[currentLevel - 1].speedMultiplier
                : 640.0;

        if (overtime) maxSpeed *= 1.06;

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

        double minSpeed = 270.0;
        speed = Math.hypot(puckDX, puckDY);
        if (speed < minSpeed) {
            double scale = minSpeed / Math.max(1.0, speed);
            puckDX *= scale;
            puckDY *= scale;
        }

        shake = Math.min(6, shake + 1.8);
    }

    private void checkScoring() {
        boolean scored = false;

        if (isVertical) {
            double goalLeft = WIDTH / 2.0 - GOAL_WIDTH / 2.0;
            double goalRight = WIDTH / 2.0 + GOAL_WIDTH / 2.0;

            boolean inGoalX = (puckX + PUCK_SIZE / 2.0) >= goalLeft && (puckX + PUCK_SIZE / 2.0) <= goalRight;

            if (puckY < -PUCK_SIZE) {
                if (inGoalX) {
                    player1Score++;
                    scored = true;
                    addGoalFX(true);
                } else {
                    puckY = -PUCK_SIZE;
                    puckDY *= -1;
                }
            } else if (puckY > HEIGHT + PUCK_SIZE) {
                if (inGoalX) {
                    player2Score++;
                    scored = true;
                    addGoalFX(false);
                } else {
                    puckY = HEIGHT + PUCK_SIZE;
                    puckDY *= -1;
                }
            }
        } else {
            double goalTop = HEIGHT / 2.0 - GOAL_WIDTH / 2.0;
            double goalBottom = HEIGHT / 2.0 + GOAL_WIDTH / 2.0;

            boolean inGoalY = (puckY + PUCK_SIZE / 2.0) >= goalTop && (puckY + PUCK_SIZE / 2.0) <= goalBottom;

            if (puckX < -PUCK_SIZE) {
                if (inGoalY) {
                    player2Score++;
                    scored = true;
                    addGoalFX(false);
                } else {
                    puckX = -PUCK_SIZE;
                    puckDX *= -1;
                }
            } else if (puckX > WIDTH + PUCK_SIZE) {
                if (inGoalY) {
                    player1Score++;
                    scored = true;
                    addGoalFX(true);
                } else {
                    puckX = WIDTH + PUCK_SIZE;
                    puckDX *= -1;
                }
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

    // ===== Passionate neon FX helpers =====
    private void addImpactFX(double x, double y, Color c) {
        for (int i = 0; i < 14; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 80 + random.nextDouble() * 220;
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 0.6 + random.nextDouble() * 0.5));
        }
    }

    private void addGoalFX(boolean youScored) {
        Color c = youScored ? new Color(34,197,94) : new Color(239,68,68);
        double gx = WIDTH / 2.0;
        double gy = youScored ? 45 : HEIGHT - 45;
        for (int i = 0; i < 70; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 140 + random.nextDouble() * 380;
            particles.add(new Particle(gx, gy, Math.cos(a) * sp, Math.sin(a) * sp, 0.9 + random.nextDouble() * 0.8));
        }
        shake = 10;
    }

    private void spawnTextBurst(double x, double y, int count) {
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double sp = 60 + random.nextDouble() * 160;
            particles.add(new Particle(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 0.8 + random.nextDouble() * 0.7));
        }
    }

    private void updateParticles(double dt) {
        shake = Math.max(0, shake - 18 * dt);

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;

            // friction + slight drift
            p.vx *= (1.0 - 1.4 * dt);
            p.vy *= (1.0 - 1.4 * dt);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // FULL FIT scaling (aspect ratio)
        int w = getWidth();
        int h = getHeight();

        double sx = w / VIRTUAL_W;
        double sy = h / VIRTUAL_H;
        double s = Math.min(sx, sy);

        double drawW = VIRTUAL_W * s;
        double drawH = VIRTUAL_H * s;
        double ox = (w - drawW) / 2.0;
        double oy = (h - drawH) / 2.0;

        // screen shake (subtle)
        double shx = (shake > 0) ? (random.nextDouble() - 0.5) * shake : 0;
        double shy = (shake > 0) ? (random.nextDouble() - 0.5) * shake : 0;

        g2.translate(ox + shx, oy + shy);
        g2.scale(s, s);

        // Background: deeper neon gradient + vignette-ish overlay
        g2.setPaint(new GradientPaint(0, 0, new Color(8, 12, 26), 0, HEIGHT, new Color(18, 24, 45)));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setPaint(new RadialGradientPaint(
                new Point(WIDTH / 2, HEIGHT / 2),
                430f,
                new float[]{0f, 1f},
                new Color[]{new Color(56, 189, 248, 32), new Color(0, 0, 0, 0)}
        ));
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        // Subtle grid lines
        g2.setColor(new Color(56, 189, 248, 10));
        for (int i = 0; i <= WIDTH; i += 40) g2.drawLine(i, 0, i, HEIGHT);
        for (int j = 0; j <= HEIGHT; j += 40) g2.drawLine(0, j, WIDTH, j);

        // Center line + circle glow
        g2.setColor(new Color(56, 189, 248, 55));
        g2.setStroke(new BasicStroke(4));

        if (isVertical) g2.drawLine(0, HEIGHT / 2, WIDTH, HEIGHT / 2);
        else g2.drawLine(WIDTH / 2, 0, WIDTH / 2, HEIGHT);

        g2.setColor(new Color(56, 189, 248, 45));
        g2.drawOval(WIDTH / 2 - 78, HEIGHT / 2 - 78, 156, 156);
        g2.setColor(new Color(56, 189, 248, 18));
        g2.fillOval(WIDTH / 2 - 78, HEIGHT / 2 - 78, 156, 156);

        // Goals
        drawGoals(g2);

        // Particles behind
        drawParticles(g2);

        // Puck
        drawGlow(g2, (int) puckX, (int) puckY, PUCK_SIZE, PUCK_SIZE, new Color(250, 204, 21), true);

        // Paddles
        drawGlow(g2, (int) player1X, (int) player1Y,
                isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH,
                isVertical ? PADDLE_WIDTH : PADDLE_HEIGHT,
                new Color(34, 197, 94),
                false
        );

        drawGlow(g2, (int) player2X, (int) player2Y,
                isVertical ? PADDLE_HEIGHT : PADDLE_WIDTH,
                isVertical ? PADDLE_WIDTH : PADDLE_HEIGHT,
                new Color(239, 68, 68),
                false
        );

        // Obstacles
        for (Obstacle obs : obstacles) {
            drawGlow(g2, (int) obs.x, (int) obs.y, (int) obs.w, (int) obs.h, Color.CYAN, false);
        }

        // UI Top HUD (glassy)
        drawHud(g2);

        g2.dispose();
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            int a = (int) (180 * Math.max(0, Math.min(1, p.life)));
            g2.setColor(new Color(56, 189, 248, a));
            int r = 3 + (int)(3 * p.life);
            g2.fillOval((int)p.x - r, (int)p.y - r, r*2, r*2);
        }
    }

    private void drawHud(Graphics2D g2) {
        // glass panel
        g2.setColor(new Color(255, 255, 255, 16));
        g2.fillRoundRect(WIDTH/2 - 160, 10, 320, 54, 18, 18);
        g2.setColor(new Color(56, 189, 248, 55));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(WIDTH/2 - 160, 10, 320, 54, 18, 18);

        // Score
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 34));
        g2.drawString(player1Score + " - " + player2Score, WIDTH / 2 - 55, 50);

        // Mode text
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(new Color(56, 189, 248, 200));

        String modeTxt = (gameMode == GameMode.PVP) ? "PVP" : (gameMode == GameMode.BOT ? "BOT" : "LEVELS");
        String diffTxt = (difficulty == 1) ? "EASY" : (difficulty == 2 ? "MEDIUM" : "HARD");

        g2.drawString(modeTxt + " • " + diffTxt, 18, 28);

        // Timer for levels (with OT)
        if (gameMode == GameMode.LEVELS) {
            int remainingMain = Math.max(0, LEVEL_TIME_SECONDS - (int)Math.floor(levelTimer));
            String timeMain = String.format("%d:%02d", remainingMain / 60, remainingMain % 60);

            if (!overtime && !penalties) {
                g2.drawString("TIME: " + timeMain, WIDTH - 130, 28);
            } else if (overtime && !penalties) {
                int remOT = Math.max(0, OVERTIME_SECONDS - (int)Math.floor(overtimeTimer));
                g2.setColor(new Color(250, 204, 21, 220));
                g2.drawString("OT: 0:" + String.format("%02d", remOT), WIDTH - 120, 28);
            }
            g2.setColor(new Color(56, 189, 248, 200));
            g2.drawString("LVL " + currentLevel + " • " + levels[currentLevel-1].name, 18, 50);
        }
    }

    private void drawGoals(Graphics2D g2) {
        g2.setStroke(new BasicStroke(3));
        g2.setColor(new Color(56, 189, 248, 110));

        if (isVertical) {
            int goalX = WIDTH / 2 - GOAL_WIDTH / 2;

            // Top goal opening
            g2.drawRect(goalX, 0, GOAL_WIDTH, GOAL_DEPTH);
            // Bottom goal opening
            g2.drawRect(goalX, HEIGHT - GOAL_DEPTH, GOAL_WIDTH, GOAL_DEPTH);

            // Net glow
            g2.setColor(new Color(56, 189, 248, 30));
            g2.fillRect(goalX, 0, GOAL_WIDTH, GOAL_DEPTH);
            g2.fillRect(goalX, HEIGHT - GOAL_DEPTH, GOAL_WIDTH, GOAL_DEPTH);

        } else {
            int goalY = HEIGHT / 2 - GOAL_WIDTH / 2;

            // Left goal opening
            g2.drawRect(0, goalY, GOAL_DEPTH, GOAL_WIDTH);
            // Right goal opening
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
        for (int i = 9; i > 0; i--) {
            int alpha = Math.max(0, 22 - i * 2);
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            if (circle) g2.fillOval(x - i, y - i, w + i * 2, h + i * 2);
            else g2.fillRoundRect(x - i, y - i, w + i * 2, h + i * 2, 12, 12);
        }

        g2.setColor(c);
        if (circle) g2.fillOval(x, y, w, h);
        else g2.fillRoundRect(x, y, w, h, 12, 12);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private int clampInt(int v, int min, int max) {
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

        int choice = JOptionPane.showOptionDialog(
                frame,
                "Select Mode",
                "Main Menu",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == -1) {
            requestFocusInWindow();
            return;
        }

        if (choice == 0) startGame(GameMode.PVP, 1, 1);
        else if (choice >= 1 && choice <= 3) startGame(GameMode.BOT, choice, 1);
        else if (choice >= 4) {
            int lvl = choice - 3;
            if (lvl <= unlockedLevels) startGame(GameMode.LEVELS, 2, lvl); // diff ignored for LEVELS (auto by level)
            else {
                JOptionPane.showMessageDialog(frame, "Complete previous levels first!");
                showMenu();
            }
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

    // ComponentListener
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

        // start maximized + resizable
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setResizable(true);

        frame.setVisible(true);
        game.showMenu();
    }
}
