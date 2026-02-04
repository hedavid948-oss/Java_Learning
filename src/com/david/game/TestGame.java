package com.david.game;
import javax.swing.*;
import java.awt.*;

public class TestGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(TestGame::createAndShowMenu);
    }

    private static void createAndShowMenu() {
        JFrame frame = new JFrame("选择游戏");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(360, 200);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        JLabel title = new JLabel("请选择游戏", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        main.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 4, 10, 10));
        JButton tetrisBtn = new JButton("俄罗斯方块");
        JButton shapeBtn = new JButton("多边形进化游戏");
        JButton snakeBtn = new JButton("贪吃蛇");
        JButton neonBtn = new JButton("Neon Conquest");
        center.add(tetrisBtn);
        center.add(shapeBtn);
        center.add(snakeBtn);
        center.add(neonBtn);
        main.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exit = new JButton("退出");
        footer.add(exit);
        main.add(footer, BorderLayout.SOUTH);

        tetrisBtn.addActionListener(e -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> new TetrisGame().setVisible(true));
        });

        shapeBtn.addActionListener(e -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> new ShapeGame().setVisible(true));
        });

        snakeBtn.addActionListener(e -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> new SnakeGame().setVisible(true));
        });

        neonBtn.addActionListener(e -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> new NeonConquest().setVisible(true));
        });

        exit.addActionListener(e -> System.exit(0));

        frame.add(main);
        frame.setVisible(true);
    }
}
