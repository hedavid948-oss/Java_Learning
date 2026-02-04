package com.david.game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class TetrisGame extends JFrame {
    private static final long serialVersionUID = 1L;
    public TetrisGame() {
        initUI();
    }

    private void initUI() {
        setTitle("俄罗斯方块 - Tetris");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(260, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        Board board = new Board();
        add(board);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TetrisGame game = new TetrisGame();
            game.setVisible(true);
        });
    }

    // --- 内部类：棋盘与游戏逻辑 ---
    static class Board extends JPanel implements ActionListener {
        private static final long serialVersionUID = 1L;
        final int BOARD_WIDTH = 10;
        final int BOARD_HEIGHT = 20;
        final int BLOCK_SIZE = 24;

        Timer timer;
        boolean isFallingFinished = false;
        boolean isPaused = false;
        int numLinesRemoved = 0;
        int curX = 0;
        int curY = 0;
        Shape curPiece;
        Tetromino[] board;

        public Board() {
            setFocusable(true);
            curPiece = new Shape();
            timer = new Timer(400, this);
            timer.start();
            board = new Tetromino[BOARD_WIDTH * BOARD_HEIGHT];
            clearBoard();

            addKeyListener(new TAdapter());
            newPiece();
        }

        public void actionPerformed(ActionEvent e) {
            if (isPaused) return;
            if (isFallingFinished) {
                isFallingFinished = false;
                newPiece();
            } else {
                oneLineDown();
            }
        }

        int squareAt(int x, int y) {
            return y * BOARD_WIDTH + x;
        }

        void clearBoard() {
            for (int i = 0; i < BOARD_HEIGHT * BOARD_WIDTH; ++i)
                board[i] = Tetromino.NoShape;
        }

        void newPiece() {
            curPiece.setRandomShape();
            curX = BOARD_WIDTH / 2 - 1;
            curY = 0;

            if (!tryMove(curPiece, curX, curY)) {
                curPiece.setShape(Tetromino.NoShape);
                timer.stop();
                JOptionPane.showMessageDialog(this, "游戏结束！\n得分: " + numLinesRemoved, "游戏结束", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        void dropDown() {
            int newY = curY;
            while (tryMove(curPiece, curX, newY + 1)) {
                newY++;
            }
            pieceDropped();
        }

        void oneLineDown() {
            if (!tryMove(curPiece, curX, curY + 1)) {
                pieceDropped();
            }
        }

        void pieceDropped() {
            for (int i = 0; i < 4; ++i) {
                int x = curX + curPiece.x(i);
                int y = curY + curPiece.y(i);
                // 如果有方块仍位于可见区域以上，说明堆到顶了，游戏结束
                if (y < 0) {
                    curPiece.setShape(Tetromino.NoShape);
                    timer.stop();
                    JOptionPane.showMessageDialog(this, "游戏结束！\n得分: " + numLinesRemoved, "游戏结束", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                board[squareAt(x, y)] = curPiece.getShape();
            }
            removeFullLines();
            isFallingFinished = true;
        }

        void removeFullLines() {
            int numFullLines = 0;

            for (int i = BOARD_HEIGHT - 1; i >= 0; --i) {
                boolean lineIsFull = true;
                for (int j = 0; j < BOARD_WIDTH; ++j) {
                    if (board[squareAt(j, i)] == Tetromino.NoShape) {
                        lineIsFull = false;
                        break;
                    }
                }

                if (lineIsFull) {
                    ++numFullLines;
                    for (int k = i; k > 0; --k) {
                        for (int j = 0; j < BOARD_WIDTH; ++j)
                            board[squareAt(j, k)] = board[squareAt(j, k - 1)];
                    }
                    for (int j = 0; j < BOARD_WIDTH; ++j)
                        board[squareAt(j, 0)] = Tetromino.NoShape;
                    ++i; // recheck same line
                }
            }

            if (numFullLines > 0) {
                numLinesRemoved += numFullLines;
                repaint();
            }
        }

        boolean tryMove(Shape newPiece, int newX, int newY) {
            for (int i = 0; i < 4; ++i) {
                int x = newX + newPiece.x(i);
                int y = newY + newPiece.y(i);

                // 允许 y < 0（方块部分位于顶部不可见区域）在生成时存在，但不允许超出底部或横向越界
                if (x < 0 || x >= BOARD_WIDTH || y >= BOARD_HEIGHT)
                    return false;
                if (y < 0) // 在顶部区域不检查格子占用
                    continue;
                if (board[squareAt(x, y)] != Tetromino.NoShape)
                    return false;
            }

            curPiece = newPiece;
            curX = newX;
            curY = newY;
            repaint();
            return true;
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Dimension size = getSize();
            int boardTop = (int) size.getHeight() - BOARD_HEIGHT * BLOCK_SIZE;

            // 背景
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight());

            // 已固定方块
            for (int i = 0; i < BOARD_HEIGHT; ++i) {
                for (int j = 0; j < BOARD_WIDTH; ++j) {
                    Tetromino shape = board[squareAt(j, i)];
                    if (shape != Tetromino.NoShape)
                        drawSquare(g, j * BLOCK_SIZE, boardTop + i * BLOCK_SIZE, shape);
                }
            }

            // 当前下落方块
            if (curPiece.getShape() != Tetromino.NoShape) {
                for (int i = 0; i < 4; ++i) {
                    int x = curX + curPiece.x(i);
                    int y = curY + curPiece.y(i);
                    drawSquare(g, x * BLOCK_SIZE, boardTop + y * BLOCK_SIZE, curPiece.getShape());
                }
            }

            // 信息
            g.setColor(Color.WHITE);
            g.drawString("分数: " + numLinesRemoved, 10, 20);
            g.drawString("按 ← → 移动，↑ 旋转，↓ 快落，空格 直接落下，P 暂停", 10, getHeight() - 10);
        }

        void drawSquare(Graphics g, int x, int y, Tetromino shape) {
            Color colors[] = {new Color(0, 0, 0), new Color(204, 102, 102),
                    new Color(102, 204, 102), new Color(102, 102, 204),
                    new Color(204, 204, 102), new Color(204, 102, 204),
                    new Color(102, 204, 204), new Color(218, 170, 0)};

            Color color = colors[shape.ordinal()];
            g.setColor(color);
            g.fillRect(x + 1, y + 1, BLOCK_SIZE - 2, BLOCK_SIZE - 2);

            g.setColor(color.brighter());
            g.drawLine(x, y + BLOCK_SIZE - 1, x, y);
            g.drawLine(x, y, x + BLOCK_SIZE - 1, y);

            g.setColor(color.darker());
            g.drawLine(x + 1, y + BLOCK_SIZE - 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1);
            g.drawLine(x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, x + BLOCK_SIZE - 1, y + 1);
        }

        class TAdapter extends KeyAdapter {
            public void keyPressed(KeyEvent e) {
                if (curPiece.getShape() == Tetromino.NoShape) return;

                int keycode = e.getKeyCode();

                if (keycode == KeyEvent.VK_P) {
                    pause();
                    return;
                }

                if (isPaused) return;

                switch (keycode) {
                    case KeyEvent.VK_LEFT:
                        tryMove(curPiece, curX - 1, curY);
                        break;
                    case KeyEvent.VK_RIGHT:
                        tryMove(curPiece, curX + 1, curY);
                        break;
                    case KeyEvent.VK_DOWN:
                        oneLineDown();
                        break;
                    case KeyEvent.VK_UP:
                        tryMove(curPiece.rotateRight(), curX, curY);
                        break;
                    case KeyEvent.VK_SPACE:
                        dropDown();
                        break;
                }
            }
        }

        void pause() {
            isPaused = !isPaused;
            if (isPaused) timer.stop(); else timer.start();
            repaint();
        }
    }

    // --- 形状定义 ---
    enum Tetromino { NoShape, ZShape, SShape, LineShape, TShape, SquareShape, LShape, MirroredLShape }

    static class Shape {
        private Tetromino pieceShape;
        private int coords[][];
        private static final int[][][] coordsTable = new int[][][]{
                {{0,0},{0,0},{0,0},{0,0}},
                {{0,-1},{0,0},{-1,0},{-1,1}}, // Z
                {{0,-1},{0,0},{1,0},{1,1}},  // S
                {{0,-1},{0,0},{0,1},{0,2}},  // Line
                {{-1,0},{0,0},{1,0},{0,1}},  // T
                {{0,0},{1,0},{0,1},{1,1}},   // Square
                {{-1,-1},{0,-1},{0,0},{0,1}},// L
                {{1,-1},{0,-1},{0,0},{0,1}}  // Mirrored L
        };

        public Shape() {
            coords = new int[4][2];
            setShape(Tetromino.NoShape);
        }

        public void setShape(Tetromino shape) {
            for (int i = 0; i < 4; i++) {
                coords[i][0] = coordsTable[shape.ordinal()][i][0];
                coords[i][1] = coordsTable[shape.ordinal()][i][1];
            }
            pieceShape = shape;
        }

        public void setRandomShape() {
            Random r = new Random();
            int x = Math.abs(r.nextInt()) % 7 + 1;
            setShape(Tetromino.values()[x]);
        }

        public Tetromino getShape() { return pieceShape; }
        public int x(int index) { return coords[index][0]; }
        public int y(int index) { return coords[index][1]; }

        public Shape rotateRight() {
            if (pieceShape == Tetromino.SquareShape) return this;

            Shape result = new Shape();
            result.pieceShape = pieceShape;
            for (int i = 0; i < 4; ++i) {
                result.coords[i][0] = y(i);
                result.coords[i][1] = -x(i);
            }
            return result;
        }
    }
}
