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


// TODO: 创建一个智能药盒类，包含药品名称和提醒时间
class SmartPillBox {
    String pillName;                    //String 是引用类型（大写开头），用于存储文本（药品名称）。
    int remindTime; // 提醒时间，单位：小时

    public SmartPillBox(String pillName, int remindTime) {// 构造方法，用于创建智能药盒对象
        this.pillName = pillName;// 初始化药品名称
        this.remindTime = remindTime;// 初始化提醒时间
    }
}
