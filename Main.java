import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class Main extends JPanel implements ActionListener, KeyListener {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int PADDLE_WIDTH = 20;
    private static final int PADDLE_HEIGHT = 100;
    private static final int PUCK_SIZE = 20;
    private static final int GOAL_WIDTH = 100;

    private int player1Y = HEIGHT / 2 - PADDLE_HEIGHT / 2;
    private int player2Y = HEIGHT / 2 - PADDLE_HEIGHT / 2;
    private int puckX = WIDTH / 2 - PUCK_SIZE / 2;
    private int puckY = HEIGHT / 2 - PUCK_SIZE / 2;
    private int puckDX = 5;
    private int puckDY = 5;
    private int player1Score = 0;
    private int player2Score = 0;

    private boolean up1 = false, down1 = false;
    private boolean up2 = false, down2 = false;

    private Timer timer;
    private boolean isVsBot = false;
    private int botDifficulty = 1; // 1: easy, 2: medium, 3: hard
    private Random random = new Random();

    public Main() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        addKeyListener(this);
        setFocusable(true);
        timer = new Timer(10, this);
    }

    public void startGame(boolean vsBot, int difficulty) {
        this.isVsBot = vsBot;
        this.botDifficulty = difficulty;
        resetPuck();
        timer.start();
    }

    private void resetPuck() {
        puckX = WIDTH / 2 - PUCK_SIZE / 2;
        puckY = HEIGHT / 2 - PUCK_SIZE / 2;
        puckDX = random.nextBoolean() ? 5 : -5;
        puckDY = random.nextInt(11) - 5; // -5 to 5
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw rink lines
        g.setColor(Color.WHITE);
        g.drawLine(WIDTH / 2, 0, WIDTH / 2, HEIGHT); // Center line
        g.drawRect(0, (HEIGHT - GOAL_WIDTH) / 2, 1, GOAL_WIDTH); // Left goal
        g.drawRect(WIDTH - 1, (HEIGHT - GOAL_WIDTH) / 2, 1, GOAL_WIDTH); // Right goal

        // Draw paddles
        g.fillRect(50, player1Y, PADDLE_WIDTH, PADDLE_HEIGHT); // Player 1
        g.fillRect(WIDTH - 50 - PADDLE_WIDTH, player2Y, PADDLE_WIDTH, PADDLE_HEIGHT); // Player 2 or Bot

        // Draw puck
        g.fillOval(puckX, puckY, PUCK_SIZE, PUCK_SIZE);

        // Draw scores
        g.drawString("Player 1: " + player1Score, 10, 20);
        g.drawString(isVsBot ? "Bot: " + player2Score : "Player 2: " + player2Score, WIDTH - 100, 20);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Move player 1
        if (up1 && player1Y > 0) player1Y -= 5;
        if (down1 && player1Y < HEIGHT - PADDLE_HEIGHT) player1Y += 5;

        // Move player 2 or bot
        if (!isVsBot) {
            if (up2 && player2Y > 0) player2Y -= 5;
            if (down2 && player2Y < HEIGHT - PADDLE_HEIGHT) player2Y += 5;
        } else {
            moveBot();
        }

        // Move puck
        puckX += puckDX;
        puckY += puckDY;

        // Puck wall collision
        if (puckY <= 0 || puckY >= HEIGHT - PUCK_SIZE) {
            puckDY = -puckDY;
        }

        // Puck paddle collision
        // Player 1
        if (puckX <= 50 + PADDLE_WIDTH && puckX >= 50 &&
            puckY + PUCK_SIZE >= player1Y && puckY <= player1Y + PADDLE_HEIGHT) {
            puckDX = -puckDX;
            puckDY += random.nextInt(3) - 1; // Slight random angle
        }
        // Player 2/Bot
        if (puckX + PUCK_SIZE >= WIDTH - 50 - PADDLE_WIDTH && puckX + PUCK_SIZE <= WIDTH - 50 &&
            puckY + PUCK_SIZE >= player2Y && puckY <= player2Y + PADDLE_HEIGHT) {
            puckDX = -puckDX;
            puckDY += random.nextInt(3) - 1; // Slight random angle
        }

        // Scoring
        if (puckX <= 0) {
            if (puckY >= (HEIGHT - GOAL_WIDTH) / 2 && puckY <= (HEIGHT + GOAL_WIDTH) / 2) {
                player2Score++;
                resetPuck();
            } else {
                puckDX = -puckDX; // Bounce off wall if not goal
            }
        }
        if (puckX >= WIDTH - PUCK_SIZE) {
            if (puckY >= (HEIGHT - GOAL_WIDTH) / 2 && puckY <= (HEIGHT + GOAL_WIDTH) / 2) {
                player1Score++;
                resetPuck();
            } else {
                puckDX = -puckDX; // Bounce off wall if not goal
            }
        }

        repaint();
    }

    private void moveBot() {
        int botSpeed = botDifficulty * 2 + 1; // Easy:3, Med:5, Hard:7
        int reactionDelay = 4 - botDifficulty; // Easy:3, Med:2, Hard:1 (lower delay = faster reaction)

        // Simple AI: track puck Y with some delay and error
        int targetY = puckY + PUCK_SIZE / 2 - PADDLE_HEIGHT / 2;
        if (random.nextInt(reactionDelay + 1) == 0) { // Simulate reaction delay
            int error = (4 - botDifficulty) * 20; // Easy more error
            targetY += random.nextInt(error * 2) - error;
        }

        if (player2Y < targetY && player2Y < HEIGHT - PADDLE_HEIGHT) {
            player2Y += botSpeed;
        } else if (player2Y > targetY && player2Y > 0) {
            player2Y -= botSpeed;
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W) up1 = true;
        if (key == KeyEvent.VK_S) down1 = true;
        if (!isVsBot) {
            if (key == KeyEvent.VK_UP) up2 = true;
            if (key == KeyEvent.VK_DOWN) down2 = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W) up1 = false;
        if (key == KeyEvent.VK_S) down1 = false;
        if (!isVsBot) {
            if (key == KeyEvent.VK_UP) up2 = false;
            if (key == KeyEvent.VK_DOWN) down2 = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Hockey Game");
        Main game = new Main();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Simple menu
        String[] options = {"Vs Player", "Vs Bot Easy", "Vs Bot Medium", "Vs Bot Hard"};
        String choice = (String) JOptionPane.showInputDialog(frame, "Choose mode:", "Menu", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

        boolean vsBot = !choice.equals("Vs Player");
        int difficulty = 1;
        if (vsBot) {
            if (choice.equals("Vs Bot Medium")) difficulty = 2;
            else if (choice.equals("Vs Bot Hard")) difficulty = 3;
        }
        game.startGame(vsBot, difficulty);
    }
}