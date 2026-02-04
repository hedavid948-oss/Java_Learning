package com.david.game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedList;
import java.util.Random;
import java.awt.Point;

/**
 * SnakeGame - 一个简洁的 Swing 实现的贪吃蛇游戏。
 * 代码遵循 smart-pillbox-2026 的 v1.0 编程规范：类名大驼峰，变量小驼峰，带必要 Javadoc 注释。
 */
public class SnakeGame extends JFrame {
    private static final long serialVersionUID = 1L;

    /** 游戏主面板 */
    private final Board board;

    /** 构造函数，初始化 UI */
    public SnakeGame() {
        setTitle("贪吃蛇 - Snake");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        board = new Board();
        add(board);
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * 启动入口（保留以便单独运行测试）
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SnakeGame game = new SnakeGame();
            game.setVisible(true);
        });
    }

    /**
     * 内部类：Board 负责游戏逻辑与绘制
     */
    private static class Board extends JPanel implements ActionListener {
        private static final long serialVersionUID = 1L;
        private static final int BOARD_COLS = 20;
        private static final int BOARD_ROWS = 20;
        private static final int DOT_SIZE = 20; // 单位方格像素
        private static final int PANEL_WIDTH = BOARD_COLS * DOT_SIZE;
        private static final int PANEL_HEIGHT = BOARD_ROWS * DOT_SIZE;
        private static final int INITIAL_SNAKE_LENGTH = 5;
        private static final int TIMER_DELAY = 140; // ms 刷新周期

        private final LinkedList<Point> snake = new LinkedList<>(); // 使用 LinkedList 存储蛇身（逻辑1）
        private Point food; // 食物位置
        private int dirX = 1; // 方向：x -1/0/1
        private int dirY = 0; // 方向：y -1/0/1
        private boolean inGame = true;
        private Timer timer;
        private int score = 0;
        private boolean paused = false;
        private final Random rand = new Random();

        /** 构造并启动游戏 */
        Board() {
            setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT + 30));
            setBackground(Color.BLACK);
            setFocusable(true);
            initGame();
            addKeyListener(new TAdapter());
        }

        /** 初始化或重新开始游戏 */
        private void initGame() {
            snake.clear();
            int startX = BOARD_COLS / 2;
            int startY = BOARD_ROWS / 2;
            for (int i = 0; i < INITIAL_SNAKE_LENGTH; i++) {
                snake.add(new Point(startX - i, startY));
            }
            dirX = 1; dirY = 0;
            score = 0;
            inGame = true;
            placeFood();
            if (timer != null) timer.stop();
            timer = new Timer(TIMER_DELAY, this);
            timer.start();
        }

        /** 随机放置食物，避免与蛇身重合 */
        private void placeFood() {
            while (true) {
                int x = rand.nextInt(BOARD_COLS);
                int y = rand.nextInt(BOARD_ROWS);
                Point p = new Point(x, y);
                boolean collides = false;
                for (Point s : snake) if (s.equals(p)) { collides = true; break; }
                if (!collides) { food = p; break; }
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!inGame) return;
            if (!paused) {
                move();
                checkCollision();
                checkFood();
            }
            repaint(); // 每次计时器触发重绘（逻辑2：刷新界面周期）
        }

        /** 移动蛇：尾部移到新头部 */
        private void move() {
            Point head = snake.getFirst();
            int newX = head.x + dirX;
            int newY = head.y + dirY;
            Point newHead = new Point(newX, newY);
            snake.addFirst(newHead);
            snake.removeLast();
        }

        /** 检查是否吃到食物并处理长度与分数 */
        private void checkFood() {
            Point head = snake.getFirst();
            if (head.equals(food)) {
                // 吃到食物：在头前添加一个新的头（增加长度），并重新放食物
                Point newHead = new Point(head.x, head.y);
                snake.addFirst(newHead);
                score += 10; // 得分规则（逻辑3）
                placeFood();
            }
        }

        /** 碰撞检测（边界与自身） */
        private void checkCollision() {
            Point head = snake.getFirst();
            // 碰壁
            if (head.x < 0 || head.x >= BOARD_COLS || head.y < 0 || head.y >= BOARD_ROWS) {
                inGame = false;
                timer.stop();
            }
            // 碰到自己
            for (int i = 1; i < snake.size(); i++) {
                if (head.equals(snake.get(i))) {
                    inGame = false;
                    timer.stop();
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            doDrawing(g);
        }

        /** 绘制游戏内容 */
        private void doDrawing(Graphics g) {
            if (inGame) {
                // 绘制食物
                g.setColor(Color.RED);
                g.fillOval(food.x * DOT_SIZE, food.y * DOT_SIZE, DOT_SIZE, DOT_SIZE);

                // 绘制蛇
                for (int i = 0; i < snake.size(); i++) {
                    Point p = snake.get(i);
                    if (i == 0) {
                        g.setColor(Color.GREEN.brighter());
                    } else {
                        g.setColor(Color.GREEN);
                    }
                    g.fillRect(p.x * DOT_SIZE, p.y * DOT_SIZE, DOT_SIZE, DOT_SIZE);
                }

                // 分数
                g.setColor(Color.WHITE);
                g.drawString("分数: " + score, 10, PANEL_HEIGHT + 20);
                if (paused) {
                    g.setColor(Color.YELLOW);
                    g.drawString("已暂停 (P 恢复)", PANEL_WIDTH/2 - 40, PANEL_HEIGHT + 20);
                }
            } else {
                gameOver(g);
            }
        }

        /** 游戏结束显示 */
        private void gameOver(Graphics g) {
            String msg = "游戏结束";
            String scr = "得分: " + score;
            Font small = new Font("Helvetica", Font.BOLD, 18);
            g.setColor(Color.WHITE);
            g.setFont(small);
            FontMetrics fm = g.getFontMetrics();
            int msgWidth = fm.stringWidth(msg);
            int scrWidth = fm.stringWidth(scr);
            g.drawString(msg, (PANEL_WIDTH - msgWidth) / 2, PANEL_HEIGHT / 2 - 10);
            g.drawString(scr, (PANEL_WIDTH - scrWidth) / 2, PANEL_HEIGHT / 2 + 10);

            g.drawString("按 R 重玩，按 Esc 返回菜单", PANEL_WIDTH / 2 - 110, PANEL_HEIGHT / 2 + 40);
        }

        /** 键盘适配器：方向控制、暂停、重启、返回菜单 */
        private class TAdapter extends KeyAdapter {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();

                switch (key) {
                    case KeyEvent.VK_LEFT:
                        if (dirX != 1) { dirX = -1; dirY = 0; }
                        break;
                    case KeyEvent.VK_RIGHT:
                        if (dirX != -1) { dirX = 1; dirY = 0; }
                        break;
                    case KeyEvent.VK_UP:
                        if (dirY != 1) { dirX = 0; dirY = -1; }
                        break;
                    case KeyEvent.VK_DOWN:
                        if (dirY != -1) { dirX = 0; dirY = 1; }
                        break;
                    case KeyEvent.VK_P:
                        paused = !paused;
                        break;
                    case KeyEvent.VK_R:
                        initGame();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        // 返回菜单：关闭当前窗口并启动 TestGame 菜单
                        SwingUtilities.invokeLater(() -> {
                            JFrame top = (JFrame) SwingUtilities.getWindowAncestor(Board.this);
                            top.dispose();
                            SwingUtilities.invokeLater(() -> TestGame.main(new String[0]));
                        });
                        break;
                }
            }
        }
    }
}
