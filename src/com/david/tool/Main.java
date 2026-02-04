package com.david.tool;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 简单的 GUI：接收图片拖拽，调用 ImageProcessor 做分割，显示结果并导出到 output_maps
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    static class MainFrame extends JFrame {
        private final JLabel imageLabel = new JLabel("请把地图图片拖拽到此处", SwingConstants.CENTER);
        private BufferedImage currentImage;
        private java.util.List<Region> regions;
        private final DefaultListModel<String> regionListModel = new DefaultListModel<>();

        public MainFrame() {
            setTitle("Map → GameMap Tool");
            setSize(900, 700);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            imageLabel.setBackground(Color.DARK_GRAY);
            imageLabel.setOpaque(true);
            add(new JScrollPane(imageLabel), BorderLayout.CENTER);

            JPanel right = new JPanel(new BorderLayout());
            JList<String> regionList = new JList<>(regionListModel);
            right.add(new JLabel("检测到的区域（可通过 AI 填充名称）"), BorderLayout.NORTH);
            right.add(new JScrollPane(regionList), BorderLayout.CENTER);

            JPanel bottom = new JPanel();
            JButton btnExport = new JButton("导出地图包");
            JButton btnRunAI = new JButton("调用 AI 识别名称");
            bottom.add(btnRunAI);
            bottom.add(btnExport);
            right.add(bottom, BorderLayout.SOUTH);

            add(right, BorderLayout.EAST);

            // 拖拽支持
            new DropTarget(imageLabel, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(dtde.getDropAction());
                        Object transfer = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (transfer instanceof java.util.List) {
                            java.util.List files = (java.util.List) transfer;
                            if (!files.isEmpty()) {
                                File f = (File) files.get(0);
                                loadImage(f);
                            }
                        }
                    } catch (Exception ex) {
                        showError(ex);
                    }
                }
            });

            btnExport.addActionListener(e -> exportMap());
            btnRunAI.addActionListener(e -> runAINameDetection());
        }

        private void loadImage(File f) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) throw new IOException("无法识别该图片格式");
                currentImage = img;
                imageLabel.setIcon(new ImageIcon(img.getScaledInstance(-1, 600, Image.SCALE_SMOOTH)));
                imageLabel.setText(null);
                // 分割
                new Thread(() -> {
                    regions = ImageProcessor.segmentByColor(currentImage, 32);
                    SwingUtilities.invokeLater(() -> refreshRegionList());
                }).start();
            } catch (Exception ex) {
                showError(ex);
            }
        }

        private void refreshRegionList() {
            regionListModel.clear();
            if (regions == null) return;
            for (int i = 0; i < regions.size(); i++) {
                Region r = regions.get(i);
                regionListModel.addElement(String.format("%02d: %s (centroid=%.0f,%.0f, pixels=%d)", i, r.name == null ? "(未命名)" : r.name, r.centroidX, r.centroidY, r.pixelCount));
            }
        }

        private void runAINameDetection() {
            if (regions == null || regions.isEmpty()) return;
            new Thread(() -> {
                AIClient ai = new DummyAIClient();
                for (Region r : regions) {
                    try {
                        String label = ai.identifyLabel(r.image);
                        if (label != null && !label.trim().isEmpty()) r.name = label.trim();
                    } catch (Exception e) {
                        // 忽略单个错误
                    }
                }
                SwingUtilities.invokeLater(this::refreshRegionList);
            }).start();
        }

        private void exportMap() {
            if (currentImage == null || regions == null) return;
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                File outDir = new File("output_maps/map_" + stamp);
                File imagesDir = new File(outDir, "images");
                File masksDir = new File(outDir, "masks");
                imagesDir.mkdirs(); masksDir.mkdirs();
                ImageIO.write(currentImage, "png", new File(imagesDir, "original.png"));

                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append(String.format("  \"generatedAt\": \"%s\",\n", stamp));
                json.append("  \"regions\": [\n");
                for (int i = 0; i < regions.size(); i++) {
                    Region r = regions.get(i);
                    String imgName = String.format("region_%02d.png", i);
                    String maskName = String.format("mask_%02d.png", i);
                    ImageIO.write(r.image, "png", new File(imagesDir, imgName));
                    ImageIO.write(r.mask, "png", new File(masksDir, maskName));
                    json.append("    {");
                    json.append(String.format("\"id\": %d, \"name\": \"%s\", \"centroid\": [%d, %d], \"image\": \"images/%s\", \"mask\": \"masks/%s\", \"pixels\": %d", i, r.name == null ? "" : escape(r.name), (int)r.centroidX, (int)r.centroidY, imgName, maskName, r.pixelCount));
                    json.append("}");
                    if (i < regions.size() - 1) json.append(",\n");
                    else json.append("\n");
                }
                json.append("  ]\n}");
                java.nio.file.Files.writeString(new File(outDir, "regions.json").toPath(), json.toString());
                JOptionPane.showMessageDialog(this, "导出完成：" + outDir.getAbsolutePath());
            } catch (Exception ex) {
                showError(ex);
            }
        }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private void showError(Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.toString(), "错误", JOptionPane.ERROR_MESSAGE));
        }
    }
}
