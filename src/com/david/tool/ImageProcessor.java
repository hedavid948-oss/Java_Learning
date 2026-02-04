package com.david.tool;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简易颜色分割（量化颜色并按颜色聚合）实现。用于从彩色区域地图中分出各个国家/地区的区域块。
 * 注意：该实现为 MVP，适用于颜色区分明显的地图。可替换为语义分割模型以提高质量。
 */
public class ImageProcessor {

    public static List<Region> segmentByColor(BufferedImage img, int quantizeStep) {
        int w = img.getWidth();
        int h = img.getHeight();
        Map<Integer, RegionAccumulator> acc = new HashMap<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int a = (rgb >> 24) & 0xff;
                if (a < 10) continue; // 透明跳过
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int qr = (r / quantizeStep) * quantizeStep;
                int qg = (g / quantizeStep) * quantizeStep;
                int qb = (b / quantizeStep) * quantizeStep;
                int key = (qr << 16) | (qg << 8) | qb;
                RegionAccumulator aacc = acc.computeIfAbsent(key, k -> new RegionAccumulator(key));
                aacc.addPixel(x, y);
            }
        }

        List<Region> regions = new ArrayList<>();
        int minPixels = Math.max(100, (w * h) / 10000); // 动态阈值，避免噪点
        for (RegionAccumulator aacc : acc.values()) {
            if (aacc.count < minPixels) continue;
            Region r = aacc.toRegion(img);
            regions.add(r);
        }
        return regions;
    }

    static class RegionAccumulator {
        int color;
        int count = 0;
        long sumX = 0, sumY = 0;
        java.util.List<Point> pixels = new java.util.ArrayList<>();

        RegionAccumulator(int color) { this.color = color; }

        void addPixel(int x, int y) {
            count++;
            sumX += x; sumY += y;
            pixels.add(new Point(x, y));
        }

        Region toRegion(BufferedImage src) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
            for (Point p : pixels) {
                if (p.x < minX) minX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.x > maxX) maxX = p.x;
                if (p.y > maxY) maxY = p.y;
            }
            int w = maxX - minX + 1;
            int h = maxY - minY + 1;
            BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (Point p : pixels) {
                int mx = p.x - minX;
                int my = p.y - minY;
                mask.setRGB(mx, my, 0xffffffff);
                img.setRGB(mx, my, src.getRGB(p.x, p.y));
            }
            Region r = new Region();
            r.pixelCount = count;
            r.centroidX = (double) sumX / count;
            r.centroidY = (double) sumY / count;
            r.color = color;
            r.mask = mask;
            r.image = img;
            r.name = "";
            return r;
        }
    }
}
