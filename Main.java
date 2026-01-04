import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;

public class Main extends JPanel implements ActionListener, KeyListener {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int PADDLE_WIDTH = 20;
    private static final int PADDLE_HEIGHT = 100;
    private static final int PUCK_SIZE = 22;
    private static final int GOAL_WIDTH = 200;

    private enum GameState { MENU, PLAYING }
    private enum GameMode { PVP, BOT, LEVELS }

    private GameState gameState = GameState.MENU;
    private GameMode gameMode;
    private boolean isVertical = true;
    private int difficulty = 1;
    private int currentLevel = 1;
    private int unlockedLevels = 1;

    // Movement using doubles for sub-pixel smoothness
    private double player1X, player1Y;
    private double player2X, player2Y;
    private double puckX, puckY;
    private double puckDX, puckDY;
    private int player1Score = 0;
    private int player2Score = 0;
    private double levelTimer = 0;

    private boolean[] keys = new boolean[256];
    private Timer timer;
    private Random random = new Random();
    private ArrayList<Obstacle> obstacles = new ArrayList<>();
    private JFrame frame;

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
        new Level("Rookie Ice", 3, 0, 1.0, null),
        new Level("Neon Barriers", 5, 2, 1.2, null),
        new Level("Chaos Master", 5, 3, 1.6, 60) // 60s time limit
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
        timer = new Timer(16, this); // ~60 FPS for smooth motion
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

        if (mode == GameMode.LEVELS) {
            Level lvl = levels[currentLevel - 1];
            for (int i = 0; i < lvl.obstacleCount; i++) {
                obstacles.add(new Obstacle(300 + (i * 100), 200, 15, 80, 3 + i));
            }
        }

        initPositions();
        resetPuck();
        gameState = GameState.PLAYING;
        timer.start();
    }

    private void initPositions() {
        if (isVertical) {
            player1X = WIDTH / 2.0 - PADDLE_HEIGHT / 2.0;
            player1Y = HEIGHT - 60;
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
        double speed = 6.0;
        if (gameMode == GameMode.LEVELS) speed *= levels[currentLevel-1].speedMultiplier;
        
        puckDX = (random.nextBoolean() ? 1 : -1) * (3 + random.nextDouble() * 2);
        puckDY = (random.nextBoolean() ? 1 : -1) * speed;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == GameState.PLAYING) {
            updateGame();
            repaint();
        }
    }

    private void updateGame() {
        double pSpeed = 9.0;

        // Player 1 Movement (WASD or Arrows)
        if (isVertical) {
            if (keys[KeyEvent.VK_A]) player1X = Math.max(0, player1X - pSpeed);
            if (keys[KeyEvent.VK_D]) player1X = Math.min(WIDTH - PADDLE_HEIGHT, player1X + pSpeed);
        } else {
            if (keys[KeyEvent.VK_W]) player1Y = Math.max(0, player1Y - pSpeed);
            if (keys[KeyEvent.VK_S]) player1Y = Math.min(HEIGHT - PADDLE_HEIGHT, player1Y + pSpeed);
        }

        // Player 2 / Bot Movement
        if (gameMode == GameMode.PVP) {
            if (isVertical) {
                if (keys[KeyEvent.VK_LEFT]) player2X = Math.max(0, player2X - pSpeed);
                if (keys[KeyEvent.VK_RIGHT]) player2X = Math.min(WIDTH - PADDLE_HEIGHT, player2X + pSpeed);
            } else {
                if (keys[KeyEvent.VK_UP]) player2Y = Math.max(0, player2Y - pSpeed);
                if (keys[KeyEvent.VK_DOWN]) player2Y = Math.min(HEIGHT - PADDLE_HEIGHT, player2Y + pSpeed);
            }
        } else {
            moveBot();
        }

        // Puck Physics
        puckX += puckDX;
        puckY += puckDY;

        // Wall Bounces
        if (isVertical) {
            if (puckX <= 0 || puckX >= WIDTH - PUCK_SIZE) puckDX *= -1;
        } else {
            if (puckY <= 0 || puckY >= HEIGHT - PUCK_SIZE) puckDY *= -1;
        }

        handleCollisions();
        checkScoring();
    }

    private void moveBot() {
        double botSpeed = difficulty * 2 + 2;
        if (isVertical) {
            double targetX = puckX + PUCK_SIZE/2 - PADDLE_HEIGHT/2;
            if (player2X < targetX) player2X = Math.min(player2X + botSpeed, targetX);
            else if (player2X > targetX) player2X = Math.max(player2X - botSpeed, targetX);
            player2X = Math.max(0, Math.min(WIDTH - PADDLE_HEIGHT, player2X));
        }
    }

    private void handleCollisions() {
        Rectangle p1 = new Rectangle((int)player1X, (int)player1Y, isVertical?PADDLE_HEIGHT:PADDLE_WIDTH, isVertical?PADDLE_WIDTH:PADDLE_HEIGHT);
        Rectangle p2 = new Rectangle((int)player2X, (int)player2Y, isVertical?PADDLE_HEIGHT:PADDLE_WIDTH, isVertical?PADDLE_WIDTH:PADDLE_HEIGHT);
        Rectangle puck = new Rectangle((int)puckX, (int)puckY, PUCK_SIZE, PUCK_SIZE);

        if (puck.intersects(p1)) {
            if (isVertical) { puckDY = -Math.abs(puckDY); puckDX += (puckX - (player1X + 40)) * 0.1; }
            else { puckDX = Math.abs(puckDX); puckDY += (puckY - (player1Y + 40)) * 0.1; }
        }
        if (puck.intersects(p2)) {
            if (isVertical) { puckDY = Math.abs(puckDY); puckDX += (puckX - (player2X + 40)) * 0.1; }
            else { puckDX = -Math.abs(puckDX); puckDY += (puckY - (player2Y + 40)) * 0.1; }
        }

        for (Obstacle obs : obstacles) {
            obs.y += obs.dy;
            if (obs.y < 100 || obs.y > HEIGHT - 180) obs.dy *= -1;
            if (puck.intersects(new Rectangle((int)obs.x, (int)obs.y, (int)obs.w, (int)obs.h))) puckDX *= -1;
        }
    }

    private void checkScoring() {
        boolean scored = false;
        if (isVertical) {
            if (puckY < 0) { player1Score++; scored = true; }
            else if (puckY > HEIGHT) { player2Score++; scored = true; }
        } else {
            if (puckX < 0) { player2Score++; scored = true; }
            else if (puckX > WIDTH) { player1Score++; scored = true; }
        }

        if (scored) {
            resetPuck();
            checkWinCondition();
        }
    }

    private void checkWinCondition() {
        int target = (gameMode == GameMode.LEVELS) ? levels[currentLevel-1].winScore : 7;
        if (player1Score >= target || player2Score >= target) {
            timer.stop();
            if (gameMode == GameMode.LEVELS && player1Score >= target) {
                if (currentLevel == unlockedLevels && unlockedLevels < 3) unlockedLevels++;
                JOptionPane.showMessageDialog(this, "Level " + currentLevel + " Cleared!");
            } else {
                JOptionPane.showMessageDialog(this, (player1Score > player2Score ? "Player 1" : "Opponent") + " Wins!");
            }
            showMenu();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Field
        g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), 0, HEIGHT, new Color(30, 41, 59)));
        g2.fillRect(0, 0, WIDTH, HEIGHT);
        
        g2.setColor(new Color(56, 189, 248, 40));
        g2.setStroke(new BasicStroke(4));
        if (isVertical) g2.drawLine(0, HEIGHT/2, WIDTH, HEIGHT/2);
        else g2.drawLine(WIDTH/2, 0, WIDTH/2, HEIGHT);
        g2.drawOval(WIDTH/2 - 75, HEIGHT/2 - 75, 150, 150);

        // Draw Puck
        drawGlow(g2, (int)puckX, (int)puckY, PUCK_SIZE, PUCK_SIZE, new Color(250, 204, 21), true);
        
        // Draw Paddles
        drawGlow(g2, (int)player1X, (int)player1Y, isVertical?PADDLE_HEIGHT:PADDLE_WIDTH, isVertical?PADDLE_WIDTH:PADDLE_HEIGHT, new Color(34, 197, 94), false);
        drawGlow(g2, (int)player2X, (int)player2Y, isVertical?PADDLE_HEIGHT:PADDLE_WIDTH, isVertical?PADDLE_WIDTH:PADDLE_HEIGHT, new Color(239, 68, 68), false);

        // Draw Obstacles
        for (Obstacle obs : obstacles) drawGlow(g2, (int)obs.x, (int)obs.y, (int)obs.w, (int)obs.h, Color.CYAN, false);

        // UI
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Orbitron", Font.BOLD, 30));
        g2.drawString(player1Score + " - " + player2Score, WIDTH/2 - 40, 40);
        if (gameMode == GameMode.LEVELS) g2.drawString("LVL " + currentLevel, 20, 40);
    }

    private void drawGlow(Graphics2D g2, int x, int y, int w, int h, Color c, boolean circle) {
        for (int i = 8; i > 0; i--) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 20 - i*2));
            if (circle) g2.fillOval(x-i, y-i, w+i*2, h+i*2);
            else g2.fillRoundRect(x-i, y-i, w+i*2, h+i*2, 10, 10);
        }
        g2.setColor(c);
        if (circle) g2.fillOval(x, y, w, h);
        else g2.fillRoundRect(x, y, w, h, 10, 10);
    }

    public void showMenu() {
        gameState = GameState.MENU;
        String[] options = {
            "2 Players (Horizontal)", 
            "Bot: Easy", "Bot: Medium", "Bot: Hard",
            "Level 1: " + levels[0].name,
            "Level 2: " + levels[1].name + (unlockedLevels < 2 ? " [LOCKED]" : ""),
            "Level 3: " + levels[2].name + (unlockedLevels < 3 ? " [LOCKED]" : "")
        };

        int choice = JOptionPane.showOptionDialog(frame, "Select Mode", "Main Menu", 
                     JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

        if (choice == 0) startGame(GameMode.PVP, 1, 1);
        else if (choice >= 1 && choice <= 3) startGame(GameMode.BOT, choice, 1);
        else if (choice >= 4) {
            int lvl = choice - 3;
            if (lvl <= unlockedLevels) startGame(GameMode.LEVELS, 2, lvl);
            else { JOptionPane.showMessageDialog(frame, "Complete previous levels first!"); showMenu(); }
        }
    }

    public void keyPressed(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = true; }
    public void keyReleased(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false; }
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Cyber Hockey 2026");
        Main game = new Main(frame);
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        game.showMenu();
    }
}