package com.david.game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * NeonConquest - 极简 RTS 原型（Phase 1: 核心引擎与数据结构）
 * <p>
 * 特性：
 * - Cell 表示圆圈，包含位置、半径、力量、归属等属性
 * - Troop 表示移动单位（当前为基础结构，支持位置更新）
 * - 每秒 tick：占领阵营的 Cell 力量 +1（中立不增长）
 * - 渲染：根据力量深度调整颜色（深度 = force / 100.0）
 * - 支持点击选中 Cell，并在其周围绘制高亮圈
 */
public class NeonConquest extends JFrame {
    private static final long serialVersionUID = 1L;

    /** 界面主要画板 */
    private final GamePanel gamePanel;

    /** 构造器 */
    public NeonConquest() {
        setTitle("Neon Conquest");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // 顶部工具条用于放置返回箭头
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton backBtn = new JButton("←");
        backBtn.setToolTipText("返回开始界面");
        backBtn.addActionListener(e -> onRequestExit());
        topBar.add(backBtn);
        add(topBar, BorderLayout.NORTH);

        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void onRequestExit() {
        // 询问确认
        int r = JOptionPane.showConfirmDialog(this, "确定要退出并返回开始界面吗？", "确认退出", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            // 尝试停止游戏计时器（如果存在）
            Window w = SwingUtilities.getWindowAncestor(gamePanel);
            if (w instanceof JFrame) {
                JFrame f = (JFrame) w;
                f.dispose();
            }
            // 返回菜单
            SwingUtilities.invokeLater(() -> com.david.game.TestGame.main(new String[0]));
        }
    }

    /** 启动入口（方便调试） */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NeonConquest().setVisible(true));
    }

    /** 阵营枚举 */
    public enum Owner { NEUTRAL, RED, BLUE }

    /**
     * Cell: 代表圆圈（据点）
     */
    public static class Cell {
        public int x;
        public int y;
        public int radius;
        public int force;
        public Owner owner;
        public boolean isRegenerating;

        /** 构造新的 Cell */
        public Cell(int x, int y, int radius, Owner owner, int force) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.owner = owner;
            this.force = force;
            this.isRegenerating = true;
        }

        /** 返回中心点 */
        public Point2D.Double center() { return new Point2D.Double(x, y); }

        /** 每秒 tick 更新：中立不增长，占领阵营 +1/s（若可再生） */
        public void tick() {
            if (owner != Owner.NEUTRAL && isRegenerating) {
                force += 1;
            }
        }
    }

    /**
     * Troop: 代表移动单位（基础结构）
     */
    public static class Troop {
        public Point2D.Double currentPos;
        public Cell targetCell;
        public double speed; // pixels per second
        public Owner owner;

        public Troop(Point2D.Double currentPos, Cell targetCell, double speed, Owner owner) {
            this.currentPos = currentPos;
            this.targetCell = targetCell;
            this.speed = speed;
            this.owner = owner;
        }

        /** 更新位置，按 targetCell 移动；返回 true 表示已到达目标 */
        public boolean update(double dtSeconds) {
            if (targetCell == null) return false;
            double dx = targetCell.x - currentPos.x;
            double dy = targetCell.y - currentPos.y;
            double dist = Math.hypot(dx, dy);
            if (dist < 1e-6) return true;
            double move = speed * dtSeconds;
            if (move >= dist) {
                currentPos.x = targetCell.x;
                currentPos.y = targetCell.y;
                return true;
            } else {
                currentPos.x += dx / dist * move;
                currentPos.y += dy / dist * move;
                return false;
            }
        }
    }

    /** 游戏主画板 */
    private static class GamePanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {
            private static final long serialVersionUID = 1L;
        private final List<Cell> cells = new ArrayList<>();
        // 用聚合箭头表示移动（隐藏单个士兵）
        private final List<Arrow> arrows = new ArrayList<>();
        private Cell selectedCell = null;
        private final java.util.List<Cell> selectedCells = new ArrayList<>(); // 多选集合

        // 控制方（demo 用 RED），以及拖拽相关状态
        private final Owner playerOwner = Owner.RED;
        private Cell dragStartCell = null;
        private Point2D.Double dragPointWorld = null; // 拖拽的世界坐标
        private Cell hoverTarget = null;
        private final List<Sender> senders = new ArrayList<>();
        private final java.util.List<Cell> selectedTargets = new ArrayList<>(); // 目标集合（可多选）
        private final java.util.List<Pulse> pulses = new ArrayList<>();

        // 缩放与选择框
        private double scale = 1.0;
        private final double minScaleToShowTroops = 0.6;
        private boolean isSelecting = false;
        private Point2D.Double selectStartWorld = null;
        private Point2D.Double selectCurrentWorld = null;

        // 平移（右键拖拽）
        private double panX = 0.0;
        private double panY = 0.0;
        private boolean isPanning = false;
        private Point lastPanScreen = null;


        // 游戏统计与计时
        private long startTimeMillis = 0L;
        private int totalTroopsSent = 0;
        private boolean gameEnded = false;

        private final Timer tickTimer; // 每秒 tick
        private final Timer animTimer; // 动画刷新（30 FPS）
        private long lastAnimTime;

        GamePanel() {
            setPreferredSize(new Dimension(800, 600));
            setBackground(Color.BLACK);
            initSampleCells();
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);

            // 记录游戏开始时间
            startTimeMillis = System.currentTimeMillis();

            tickTimer = new Timer(1000, e -> doTick());
            tickTimer.start();

            lastAnimTime = System.nanoTime();
            animTimer = new Timer(33, e -> {
                long now = System.nanoTime();
                double dt = (now - lastAnimTime) / 1_000_000_000.0;
                lastAnimTime = now;
                updateAnimation(dt);
                repaint();
            });
            animTimer.start();

            setFocusable(true);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_D) {
                        commitDistribution();
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        // clear target selections
                        selectedTargets.clear();
                        selectedCells.clear();
                        repaint();
                    }
                }
            });
        }

        /** 初始化示例 Cell 布局 */
        private void initSampleCells() {
            cells.clear();
            cells.add(new Cell(150, 150, 32, Owner.RED, 10));
            cells.add(new Cell(400, 120, 28, Owner.NEUTRAL, 0));
            cells.add(new Cell(650, 180, 40, Owner.BLUE, 20));
            cells.add(new Cell(200, 400, 32, Owner.NEUTRAL, 0));
            cells.add(new Cell(500, 380, 36, Owner.NEUTRAL, 0));
        }

        /** 每秒执行逻辑：更新 Cell.force */
        private void doTick() {
            for (Cell c : cells) {
                c.tick();
            }
            // 可以在此扩展：产生部队、资源等
            repaint();
        }

        /** 聚合箭头表示的一次传输批次（保存剩余到达时间和数量） */
        private static class Batch {
            int count;
            double timeLeft;
            double totalTime; // 保存原始 travel time 以便渲染时计算进度
            Batch(int count, double timeLeft) { this.count = count; this.timeLeft = timeLeft; this.totalTime = timeLeft; }
        }

        /** Arrow: 聚合表征从 source -> target 的所有在途部队 */
        private class Arrow {
            final Cell source;
            final Cell target;
            final java.util.List<Batch> batches = new ArrayList<>();
            int sourcePower = 0; // 用于影响箭头粗细（基于发起时兵力）

            Arrow(Cell s, Cell t) { this.source = s; this.target = t; this.sourcePower = Math.max(0, s.force); }

            void addBatch(int count, double travelTime) {
                // 合并到最近的批次以减少对象数量（若末批次时间差小于 0.05s）
                if (!batches.isEmpty()) {
                    Batch last = batches.get(batches.size() - 1);
                    if (Math.abs(last.timeLeft - travelTime) < 0.05) {
                        last.count += count;
                        sourcePower += count; return;
                    }
                }
                batches.add(new Batch(count, travelTime));
                sourcePower += count;
            }

            void update(double dt) {
                // 减少 timeLeft 并在到达时应用到目标
                java.util.List<Batch> arrived = new ArrayList<>();
                for (Batch b : new ArrayList<>(batches)) {
                    b.timeLeft -= dt;
                    if (b.timeLeft <= 0) arrived.add(b);
                }
                for (Batch b : arrived) {
                    batches.remove(b);
                    // 应用到达
                    if (target == null) continue;
                    if (source.owner == target.owner) {
                        target.force += b.count;
                    } else {
                        target.force -= b.count;
                        if (target.force < 0) {
                            target.owner = source.owner;
                            target.force = Math.abs(target.force);
                        }
                    }
                    // 当批次到达，减少 sourcePower 显示
                    sourcePower = Math.max(0, sourcePower - b.count);
                }
            }

            int inFlight() { int s = 0; for (Batch b : batches) s += b.count; return s; }

            boolean isEmpty() { return batches.isEmpty(); }
        }

        /** 发送器：在 source -> target 之间按固定速率产生命令（每秒 4 个） */
        // Sender 新增了 preConsumed 构造参数，若 preConsumed=true 则 source.force 已在创建前被扣除，spawn 时不再重复扣减，
        // 此方式用于“全军出击”场景，避免 race condition
        private class Sender {
            private final Cell source;
            private final Cell target;
            private int remaining;
            private final Timer spawnTimer;
            private final int intervalMs = 250; // 4 per second
            private final boolean preConsumed; // 如果 true，发送前已把力量从 source 清零，不再在 spawn 时扣减

            Sender(Cell source, Cell target, int count, boolean preConsumed) {
                this.source = source;
                this.target = target;
                this.remaining = count;
                this.preConsumed = preConsumed;
                this.spawnTimer = new Timer(intervalMs, e -> spawnOne());
                this.spawnTimer.start();
            }

            private void spawnOne() {
                if (remaining <= 0) {
                    spawnTimer.stop();
                    return;
                }
                if (!preConsumed && source.force <= 0) {
                    spawnTimer.stop();
                    return;
                }

                if (gameEnded) { spawnTimer.stop(); return; }
                // 以聚合箭头表示：创建或找到 Arrow，然后添加一个到达批次（count=1，travelTime由distance/speed决定）
                Arrow a = getOrCreateArrow(source, target);
                double dist = source.center().distance(target.center());
                double speed = 120.0; // px/s
                double travelTime = dist / speed;
                a.addBatch(1, travelTime);

                if (!preConsumed) {
                    source.force -= 1; // 每生成一个，源点力量减少 1
                }
                totalTroopsSent++;
                remaining--;

                if (remaining <= 0) spawnTimer.stop();
            }

            void stop() { spawnTimer.stop(); }
        }

        /** 更新动画/部队位置，并处理到达 */
        private void updateAnimation(double dtSeconds) {
            List<Troop> arrived = new ArrayList<>();
            // 更新箭头中的批次（聚合移动）
            List<Arrow> removed = new ArrayList<>();
            for (Arrow a : arrows) {
                a.update(dtSeconds);
                if (a.isEmpty()) removed.add(a);
            }
            arrows.removeAll(removed);

            // 更新 pulses
            java.util.List<Pulse> dead = new ArrayList<>();
            for (Pulse p : pulses) {
                p.timeLeft -= dtSeconds;
                p.radius += dtSeconds * 60;
                if (p.timeLeft <= 0) dead.add(p);
            }
            pulses.removeAll(dead);

            checkVictory();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // apply scaling around center
            AffineTransform old = g2d.getTransform();
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            g2d.translate(cx, cy);
            g2d.scale(scale, scale);
            g2d.translate(-cx, -cy);
            // apply pan in world coordinates
            g2d.translate(panX, panY);

            // 绘制所有 Cell
            for (Cell c : cells) {
                drawCell(g2d, c);
            }

            // 根据当前缩放决定绘制模式：默认显示小圆点（士兵），缩放到箭头模式时显示短箭头
            boolean arrowMode = scale < minScaleToShowTroops; // 当缩放小于阈值时使用箭头模式（否则显示每个士兵）
            if (!arrowMode) {
                // 正常尺度：绘制小圆点表示每个士兵
                for (Arrow a : arrows) {
                    drawTroopsForArrow(g2d, a);
                }
            } else {
                // 箭头模式：显示缩短 50%、加粗 1 倍的箭头
                for (Arrow a : arrows) {
                    drawAggregatedArrow(g2d, a, true);
                }
            }

            // 绘制拖拽线与目标高亮（拖拽点使用世界坐标）
            if (dragStartCell != null && dragPointWorld != null) {
                g2d.setColor(new Color(255, 255, 255, 100));
                g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6}, 0));
                g2d.drawLine(dragStartCell.x, dragStartCell.y, (int) dragPointWorld.x, (int) dragPointWorld.y);
                if (hoverTarget != null) {
                    g2d.setColor(new Color(255, 255, 0, 140));
                    g2d.setStroke(new BasicStroke(3));
                    g2d.drawOval(hoverTarget.x - hoverTarget.radius - 8, hoverTarget.y - hoverTarget.radius - 8,
                            hoverTarget.radius * 2 + 16, hoverTarget.radius * 2 + 16);
                }
            }

            // 绘制多选高亮
            if (!selectedCells.isEmpty()) {
                g2d.setColor(new Color(255, 255, 255, 120));
                g2d.setStroke(new BasicStroke(2));
                for (Cell sc : selectedCells) {
                    g2d.drawOval(sc.x - sc.radius - 6, sc.y - sc.radius - 6, sc.radius * 2 + 12, sc.radius * 2 + 12);
                }
            }

            // 绘制目标集合高亮
            if (!selectedTargets.isEmpty()) {
                g2d.setColor(new Color(255, 165, 0, 160));
                g2d.setStroke(new BasicStroke(3));
                for (Cell tc : selectedTargets) {
                    g2d.drawOval(tc.x - tc.radius - 8, tc.y - tc.radius - 8, tc.radius * 2 + 16, tc.radius * 2 + 16);
                }
            }

            // 绘制选中高亮（单选）
            if (selectedCell != null) {
                g2d.setColor(new Color(255, 255, 255, 120));
                g2d.setStroke(new BasicStroke(3));
                g2d.drawOval(selectedCell.x - selectedCell.radius - 6, selectedCell.y - selectedCell.radius - 6,
                        selectedCell.radius * 2 + 12, selectedCell.radius * 2 + 12);
            }

            // restore transform before HUD
            g2d.setTransform(old);

            // HUD 信息（不缩放）
            int activeInFlight = arrows.stream().mapToInt(Arrow::inFlight).sum();
            g2d.setColor(Color.WHITE);
            g2d.drawString("Cells: " + cells.size() + "    Moving: " + activeInFlight + "    Scale: " + String.format("%.2f", scale), 10, 16);

            // 显示瞬时提示信息
            if (messageText != null && System.currentTimeMillis() < messageExpireTimeMillis) {
                FontMetrics fm2 = g2d.getFontMetrics();
                int mw = fm2.stringWidth(messageText);
                g2d.setColor(new Color(255, 255, 255, 200));
                g2d.drawString(messageText, (getWidth() - mw) / 2, 36);
            }

            // 绘制 pulses（不缩放）
            for (Pulse p : pulses) {
                Graphics2D g3 = (Graphics2D) g.create();
                g3.setColor(new Color(255, 255, 0, (int) (180 * Math.max(0, p.timeLeft))));
                g3.setStroke(new BasicStroke(3));
                g3.drawOval((int) (p.x - p.radius), (int) (p.y - p.radius), (int) (p.radius * 2), (int) (p.radius * 2));
                g3.dispose();
            }

        }

        /** 绘制单个 Cell，颜色深度根据 force/100.0 */
        private void drawCell(Graphics2D g2d, Cell c) {
            float depth = Math.min(1.0f, c.force / 100.0f);
            Color base = ownerBaseColor(c.owner);
            Color color = blendWithBlack(base, depth);

            g2d.setColor(color);
            g2d.fillOval(c.x - c.radius, c.y - c.radius, c.radius * 2, c.radius * 2);

            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(c.x - c.radius, c.y - c.radius, c.radius * 2, c.radius * 2);

            // 绘制力量值
            String txt = String.valueOf(c.force);
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(txt);
            g2d.drawString(txt, c.x - tw / 2, c.y + fm.getAscent() / 2);
        }

        /** 绘制 troop */
        private void drawTroop(Graphics2D g2d, Troop t) {
            Color base = ownerBaseColor(t.owner);
            g2d.setColor(base);
            int r = 6;
            g2d.fillOval((int) t.currentPos.x - r, (int) t.currentPos.y - r, r * 2, r * 2);
        }

        /** 缩放下使用箭头表示 troop 方向 */
        private void drawArrowForTroop(Graphics2D g2d, Troop t) {
            if (t.targetCell == null) return;
            Color base = ownerBaseColor(t.owner);
            g2d.setColor(base);
            int sx = (int) t.currentPos.x;
            int sy = (int) t.currentPos.y;
            int tx = t.targetCell.x;
            int ty = t.targetCell.y;
            double dx = tx - sx;
            double dy = ty - sy;
            double ang = Math.atan2(dy, dx);
            int len = Math.max(10, (int) Math.hypot(dx, dy) / 6);
            int ex = sx + (int) (Math.cos(ang) * len);
            int ey = sy + (int) (Math.sin(ang) * len);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(sx, sy, ex, ey);
            // triangle head
            int hs = 6;
            int ax = ex - (int) (Math.cos(ang + Math.PI / 6) * hs);
            int ay = ey - (int) (Math.sin(ang + Math.PI / 6) * hs);
            int bx = ex - (int) (Math.cos(ang - Math.PI / 6) * hs);
            int by = ey - (int) (Math.sin(ang - Math.PI / 6) * hs);
            int[] xs = {ex, ax, bx};
            int[] ys = {ey, ay, by};
            g2d.fillPolygon(xs, ys, 3);
        }

        /** 正常缩放下以小圆点表示在途士兵（通过 Arrow.batches 渲染） */
        private void drawTroopsForArrow(Graphics2D g2d, Arrow a) {
            if (a.source == null || a.target == null) return;
            int sx = a.source.x;
            int sy = a.source.y;
            int tx = a.target.x;
            int ty = a.target.y;
            double dx = tx - sx;
            double dy = ty - sy;
            double dist = Math.hypot(dx, dy);
            if (dist < 1) return;
            double ux = dx / dist;
            double uy = dy / dist;
            // 每个批次内部按间隔分布小圆点，表现为队列移动
            for (Batch b : a.batches) {
                if (b.totalTime <= 0) continue;
                double progress = 1.0 - (b.timeLeft / b.totalTime);
                double basePos = progress * dist;
                double spacing = Math.min(8.0, dist / (b.count + 1.0));
                for (int i = 0; i < b.count; i++) {
                    double pos = basePos - i * spacing;
                    if (pos < 0) pos = 0;
                    if (pos > dist) pos = dist;
                    int px = (int) (sx + ux * pos);
                    int py = (int) (sy + uy * pos);
                    int r = 3; // 士兵半径
                    g2d.setColor(ownerBaseColor(a.source.owner));
                    g2d.fillOval(px - r, py - r, r * 2, r * 2);
                }
            }
        }

        /** 根据阵营返回基础颜色 */
        private Color ownerBaseColor(Owner o) {
            switch (o) {
                case RED:
                    return new Color(0xFF3B30); // red
                case BLUE:
                    return new Color(0x007AFF); // blue
                default:
                    return new Color(0x888888); // neutral gray
            }
        }

        // Pulse: simple visual pulse on target
        private static class Pulse {
            int x, y;
            double radius;
            double timeLeft;
            Pulse(int x, int y, double radius, double timeLeft) { this.x = x; this.y = y; this.radius = radius; this.timeLeft = timeLeft; }
        }

        // Attack summary container
        private static class AttackSummary {
            int totalSent;
            Map<Cell, Integer> perSource = new HashMap<>();
        }

        // 聚合箭头绘制：粗线，箭头头部，粗细受 source.force 与当前在途数量影响
        private void drawAggregatedArrow(Graphics2D g2d, Arrow a, boolean shortMode) {
            if (a.source == null || a.target == null) return;
            int sx = a.source.x;
            int sy = a.source.y;
            int tx = a.target.x;
            int ty = a.target.y;
            double dx = tx - sx;
            double dy = ty - sy;
            double dist = Math.hypot(dx, dy);
            if (dist < 1) return;
            double ang = Math.atan2(dy, dx);

            // 粗细基于源点发起时兵力与在途兵力
            int thickness = 2 + (a.sourcePower / 8) + (a.inFlight() / 4);
            thickness = Math.max(2, Math.min(48, thickness));
            if (shortMode) thickness = thickness * 2; // 箭头模式下加粗一倍

            g2d.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Color base = ownerBaseColor(a.source.owner);
            g2d.setColor(base);

            // 根据 shortMode 缩短到 50%
            double lengthScale = shortMode ? 0.5 : 1.0;
            double effectiveDist = dist * lengthScale;
            double headOffset = Math.min(20 + thickness, effectiveDist * 0.15);
            int ex = (int) (sx + Math.cos(ang) * (effectiveDist - headOffset));
            int ey = (int) (sy + Math.sin(ang) * (effectiveDist - headOffset));
            g2d.drawLine(sx, sy, ex, ey);

            // 三角形头部，尖端位于 effectiveDist 位置
            int txTip = (int) (sx + Math.cos(ang) * effectiveDist);
            int tyTip = (int) (sy + Math.sin(ang) * effectiveDist);
            int hs = 8 + thickness / 3;
            int ax = txTip - (int) (Math.cos(ang + Math.PI / 6) * hs);
            int ay = tyTip - (int) (Math.sin(ang + Math.PI / 6) * hs);
            int bx = txTip - (int) (Math.cos(ang - Math.PI / 6) * hs);
            int by = tyTip - (int) (Math.sin(ang - Math.PI / 6) * hs);
            int[] xs = {txTip, ax, bx};
            int[] ys = {tyTip, ay, by};
            g2d.fillPolygon(xs, ys, 3);

            // 在箭头旁边显示在途数量（如果足够大）
            int inFlight = a.inFlight();
            if (inFlight > 0) {
                String s = "×" + inFlight;
                Font f = g2d.getFont().deriveFont(Font.BOLD, 14f);
                g2d.setFont(f);
                FontMetrics fm = g2d.getFontMetrics();
                int tw = fm.stringWidth(s);
                int txp = (sx + ex) / 2;
                int typ = (sy + ey) / 2;
                g2d.setColor(new Color(255,255,255,220));
                g2d.fillRoundRect(txp - tw/2 - 6, typ - fm.getAscent(), tw + 12, fm.getAscent() + 6, 6, 6);
                g2d.setColor(Color.BLACK);
                g2d.drawString(s, txp - tw/2, typ);
            }
        }

        /** 将颜色按深度与黑色混合（深度 0..1） */
        private Color blendWithBlack(Color c, float depth) {
            depth = Math.max(0f, Math.min(1f, depth));
            int r = (int) (c.getRed() * (1 - depth));
            int g = (int) (c.getGreen() * (1 - depth));
            int b = (int) (c.getBlue() * (1 - depth));
            // 让高强度更亮一些：在深度上再叠加一层基色
            r = (int) Math.min(255, r + c.getRed() * depth * 0.6);
            g = (int) Math.min(255, g + c.getGreen() * depth * 0.6);
            b = (int) Math.min(255, b + c.getBlue() * depth * 0.6);
            return new Color(r, g, b);
        }

        /** 鼠标点击选中 cell */
        private Point2D.Double worldFromScreen(Point p) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            double wx = (p.x - cx) / scale + cx - panX;
            double wy = (p.y - cy) / scale + cy - panY;
            return new Point2D.Double(wx, wy);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            Point2D.Double wp = worldFromScreen(e.getPoint());
            Cell found = null;
            for (Cell c : cells) {
                double dx = c.x - wp.x;
                double dy = c.y - wp.y;
                if (dx * dx + dy * dy <= c.radius * c.radius) { found = c; break; }
            }

            if (e.getClickCount() == 2 && found != null && found.owner == playerOwner) {
                // 双击：选中所有我方兵营
                selectedCells.clear();
                for (Cell c : cells) if (c.owner == playerOwner) selectedCells.add(c);
                selectedCell = null;
                showTransientMessage("全军出击准备（双击后左键敌方兵营以发动）", 800);
            } else if (SwingUtilities.isLeftMouseButton(e) && found != null && found.owner != playerOwner && !selectedCells.isEmpty()) {
                // 全选后左键敌方兵营 -> 全军出击
                Cell target = found;
                int totalSent = 0;
                for (Cell s : new ArrayList<>(selectedCells)) {
                    int sendCount = s.force; // 所有兵力
                    if (sendCount <= 0) continue;
                    // 预先扣减并创建发送器（preConsumed = true）
                    s.force -= sendCount;
                    Sender sender = new Sender(s, target, sendCount, true);
                    senders.add(sender);
                    totalSent += sendCount;
                }
                if (totalSent > 0) {
                    showTransientMessage("全军出击！已发兵: " + totalSent, 800);
                }
                // 清空选中
                selectedCells.clear();
                selectedCell = null;
            } else {
                // 单击：选单个
                selectedCell = found;
                selectedCells.clear();
            }
            repaint();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // Right button starts panning
            if (SwingUtilities.isRightMouseButton(e)) {
                isPanning = true;
                lastPanScreen = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                return;
            }

            Point2D.Double wp = worldFromScreen(e.getPoint());
            Cell found = null;
            for (Cell c : cells) {
                double dx = c.x - wp.x;
                double dy = c.y - wp.y;
                if (dx * dx + dy * dy <= c.radius * c.radius) { found = c; break; }
            }
            // 区分：按下在己方兵营上 -> 准备拖拽出兵；按下在空白处（左键）-> 开始框选
            if (SwingUtilities.isLeftMouseButton(e) && found != null && found.owner == playerOwner && found.force > 1) {
                dragStartCell = found;
                dragPointWorld = wp;
                hoverTarget = null;
                isSelecting = false;
            } else if (SwingUtilities.isLeftMouseButton(e) && found == null) {
                // 空白处左键开始框选
                isSelecting = true;
                selectStartWorld = wp;
                selectCurrentWorld = wp;
                selectedCells.clear();
                dragStartCell = null;
                dragPointWorld = null;
                hoverTarget = null;
            } else {
                dragStartCell = null;
                dragPointWorld = null;
                hoverTarget = null;
                isSelecting = false;
            }
            repaint();
        }



        @Override
        public void mouseReleased(MouseEvent e) {
            // Stop panning on right button release
            if (isPanning && SwingUtilities.isRightMouseButton(e)) {
                isPanning = false;
                lastPanScreen = null;
                setCursor(Cursor.getDefaultCursor());
                return;
            }

            if (isSelecting) {
                selectCurrentWorld = worldFromScreen(e.getPoint());
                double x1 = Math.min(selectStartWorld.x, selectCurrentWorld.x);
                double x2 = Math.max(selectStartWorld.x, selectCurrentWorld.x);
                double y1 = Math.min(selectStartWorld.y, selectCurrentWorld.y);
                double y2 = Math.max(selectStartWorld.y, selectCurrentWorld.y);
                selectedCells.clear();
                for (Cell c : cells) {
                    if (c.owner == playerOwner && c.x >= x1 && c.x <= x2 && c.y >= y1 && c.y <= y2) selectedCells.add(c);
                }
                isSelecting = false;
            } else if (dragStartCell != null) {
                if (hoverTarget != null && hoverTarget != dragStartCell) {
                    int sendCount = Math.max(1, dragStartCell.force / 2);
                    Sender s = new Sender(dragStartCell, hoverTarget, sendCount, false);
                    senders.add(s);
                }
            }
            dragStartCell = null;
            dragPointWorld = null;
            hoverTarget = null;
            repaint();
        }

        /** 检查胜利条件：玩家占领所有兵营 */
        private void checkVictory() {
            if (gameEnded) return;
            for (Cell c : cells) {
                if (c.owner != playerOwner) return;
            }
            // 如果走到这里，视为胜利
            long duration = System.currentTimeMillis() - startTimeMillis;
            endGame(true, duration);
        }

        private Arrow getOrCreateArrow(Cell source, Cell target) {
            for (Arrow a : arrows) {
                if (a.source == source && a.target == target) return a;
            }
            Arrow a = new Arrow(source, target);
            arrows.add(a);
            return a;
        }

        // commit distribution: distribute all selected sources' forces across selectedTargets evenly
        private void commitDistribution() {
            if (selectedCells.isEmpty() || selectedTargets.isEmpty()) return;
            // gather sources and their available forces
            java.util.List<Cell> sources = new ArrayList<>(selectedCells);
            java.util.List<Cell> targets = new ArrayList<>(selectedTargets);
            int total = 0;
            for (Cell s : sources) total += s.force;
            if (total <= 0) return;

            int nTargets = targets.size();
            int base = total / nTargets;
            int rem = total % nTargets;
            Map<Cell, Integer> targetNeed = new HashMap<>();
            for (int i = 0; i < targets.size(); i++) {
                int need = base + (i < rem ? 1 : 0);
                targetNeed.put(targets.get(i), need);
            }

            // pre-deduct all source forces and track remaining per source
            Map<Cell, Integer> sourceRem = new HashMap<>();
            for (Cell s : sources) { sourceRem.put(s, s.force); s.force = 0; }

            AttackSummary summary = new AttackSummary();
            summary.totalSent = total;

            // allocate to targets from sources sequentially
            for (Cell t : targets) {
                int need = targetNeed.get(t);
                for (Cell s : sources) {
                    if (need <= 0) break;
                    int avail = sourceRem.get(s);
                    if (avail <= 0) continue;
                    int give = Math.min(avail, need);
                    sourceRem.put(s, avail - give);
                    need -= give;
                    // create sender per pair
                    Sender sender = new Sender(s, t, give, true);
                    senders.add(sender);
                    summary.perSource.put(s, summary.perSource.getOrDefault(s, 0) + give);
                }
            }

            // show summary and small sound/pulse
            Toolkit.getDefaultToolkit().beep();
            for (Cell t : targets) pulses.add(new Pulse(t.x, t.y, 12, 0.6));
            showAttackSummary(summary);

            // clear selections
            selectedTargets.clear();
            selectedCells.clear();
            repaint();
        }

        private void showAttackSummary(AttackSummary s) {
            StringBuilder sb = new StringBuilder();
            sb.append("全军出击已发兵: ").append(s.totalSent).append("\n");
            for (Map.Entry<Cell,Integer> e : s.perSource.entrySet()) {
                sb.append(String.format("来源(%d,%d): %d\n", e.getKey().x, e.getKey().y, e.getValue()));
            }
            // use non-blocking dialog
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(GamePanel.this, sb.toString(), "出兵统计", JOptionPane.INFORMATION_MESSAGE));
        }

        // transient message helper
        private String messageText = null;
        private long messageExpireTimeMillis = 0L;
        private void showTransientMessage(String text, long ms) {
            this.messageText = text;
            this.messageExpireTimeMillis = System.currentTimeMillis() + ms;
        }

        /** 结束游戏，若 isVictory=true 则显示结算页面，否则为中途退出 */
        private void endGame(boolean isVictory, long durationMs) {
            gameEnded = true;
            // 停止所有计时器与发送器
            tickTimer.stop();
            animTimer.stop();
            for (Sender s : senders) s.stop();
            senders.clear();

            if (isVictory) {
                String timeStr = formatDuration(durationMs);
                String msg = String.format("胜利！\n游戏时间: %s\n消耗兵力: %d\n\n返回开始界面？", timeStr, totalTroopsSent);
                int r = JOptionPane.showConfirmDialog(this, msg, "结算", JOptionPane.DEFAULT_OPTION);
                // 返回菜单
                SwingUtilities.invokeLater(() -> {
                    JFrame top = (JFrame) SwingUtilities.getWindowAncestor(GamePanel.this);
                    top.dispose();
                    com.david.game.TestGame.main(new String[0]);
                });
            } else {
                int confirm = JOptionPane.showConfirmDialog(this, "确认退出并返回开始界面？", "确认退出", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    SwingUtilities.invokeLater(() -> {
                        JFrame top = (JFrame) SwingUtilities.getWindowAncestor(GamePanel.this);
                        top.dispose();
                        com.david.game.TestGame.main(new String[0]);
                    });
                } else {
                    // 如果选择取消，允许恢复（简单重启计时器）
                    gameEnded = false;
                    tickTimer.start();
                    animTimer.start();
                }
            }
        }

        private String formatDuration(long ms) {
            long s = ms / 1000;
            long mm = s / 60;
            long ss = s % 60;
            return String.format("%02d:%02d", mm, ss);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            // Handle panning with right mouse button
            if (isPanning && lastPanScreen != null && SwingUtilities.isRightMouseButton(e)) {
                Point cur = e.getPoint();
                int dx = cur.x - lastPanScreen.x;
                int dy = cur.y - lastPanScreen.y;
                // pan in world space
                panX += dx / scale;
                panY += dy / scale;
                lastPanScreen = cur;
                repaint();
                return;
            }

            Point2D.Double wp = worldFromScreen(e.getPoint());
            if (isSelecting) {
                selectCurrentWorld = wp;
                repaint();
                return;
            }
            if (dragStartCell != null) {
                dragPointWorld = wp;
                hoverTarget = null;
                for (Cell c : cells) {
                    double dx = c.x - wp.x;
                    double dy = c.y - wp.y;
                    if (dx * dx + dy * dy <= c.radius * c.radius) { hoverTarget = c; break; }
                }
                repaint();
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            // 不在拖拽时也可以用于 hover 其他逻辑（保留空实现）
        }

        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            double delta = -e.getPreciseWheelRotation();
            double factor = Math.pow(1.12, delta);
            scale = Math.max(0.3, Math.min(2.5, scale * factor));
            repaint();
        }
    }
}
