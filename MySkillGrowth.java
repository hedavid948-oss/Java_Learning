public class MySkillGrowth {
    public static void main(String[] args) {
        int day = 1;
        double skillLevel = 1.0;
        double growthRate = 0.01; // 每天进步1%

        for (int i = 0; i < 30; i++) {
            skillLevel *= (1 + growthRate);
        }

        System.out.println("经过30天的战术训练，实力从 1.0 增长到: " + skillLevel);
    }
}
// 2026-02-01 战术目标达成：成功配置 JDK 17 并跑通 VS Code + Git 工作流。