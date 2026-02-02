package src.com.david.game;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ShapeGame extends JFrame {
    // 1. 游戏状态变量 (Data Members)
    private int clicks = 0;          // 总点击数
    private int sides = 3;           // 当前边数（从三角形开始）
    private String gameState = "START"; // 状态：START, PLAYING, WIN

    public ShapeGame() {
        setTitle("多边形进化游戏");
        setSize(500, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 2. 核心画布：负责所有的显示逻辑
        GamePanel panel = new GamePanel();
        add(panel);

        // 3. 鼠标监听：所有的交互逻辑
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleInteraction();
                panel.repaint(); // 每次点击后重新绘图
            }
        });
    }

    private void handleInteraction() {
        if (gameState.equals("START")) {
            gameState = "PLAYING";
        } else if (gameState.equals("PLAYING")) {
            clicks++;
            // 每 10 下增加一条边
            sides = 3 + (clicks / 10);
            
            // 胜利判定：256 边形
            if (sides >= 256) {
                gameState = "WIN";
            }
        } else if (gameState.equals("WIN")) {
            // 胜利后点击返回初始状态
            clicks = 0;
            sides = 3;
            gameState = "START";
        }
    }

    // 内部类：专门处理绘图
    class GamePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (gameState.equals("START")) {
                drawCenteredString(g2d, "多边形进化论", 200);
                drawCenteredString(g2d, "点击屏幕开始游戏", 250);
            } else if (gameState.equals("PLAYING")) {
                g2d.drawString("总点击数: " + clicks, 20, 30);
                g2d.drawString("当前形状: " + sides + " 边形", 20, 50);
                drawPolygon(g2d, sides);
            } else if (gameState.equals("WIN")) {
                g2d.setColor(Color.RED);
                drawCenteredString(g2d, "你赢了！已进化为极限圆形", 200);
                g2d.setColor(Color.BLACK);
                drawCenteredString(g2d, "点击返回主菜单", 250);
            }
        }

        private void drawPolygon(Graphics2D g, int n) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int radius = 150;
            Polygon p = new Polygon();

            for (int i = 0; i < n; i++) {
                // 计算每个顶点的坐标
                int x = (int) (cx + radius * Math.cos(i * 2 * Math.PI / n));
                int y = (int) (cy + radius * Math.sin(i * 2 * Math.PI / n));
                p.addPoint(x, y);
            }
            g.setColor(new Color(100, 149, 237)); // 康乃馨蓝
            g.fillPolygon(p);
            g.setColor(Color.BLACK);
            g.drawPolygon(p);
        }

        private void drawCenteredString(Graphics2D g, String text, int y) {
            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            g.drawString(text, x, y);
        }
    }

    public static void main(String[] args) {
        // 在 Java Swing 中，建议在事件分发线程中启动 UI
        SwingUtilities.invokeLater(() -> {
            new ShapeGame().setVisible(true);
        });
    }
}