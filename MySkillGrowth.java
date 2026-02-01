/**
 * public: 公共的，代表这个类可以被任何人访问。
 * class: 类，Java程序的基本单位，所有的代码都要写在类里。
 * MySkillGrowth: 类的名字，必须和文件名（MySkillGrowth.java）一模一样。
 */
public class MySkillGrowth {

    /**
     * static: 静态的，代表这个方法不需要创建对象就能运行。
     * void: 空的，代表这个方法执行完后不返回任何结果。
     * main: 主方法，是Java程序的入口，电脑运行Java时第一眼就找它。
     * String[] args: 字符串数组参数，用来接收运行程序时传入的额外指令（目前我们用不到）。
     */
    public static void main(String[] args) {
        
        // int: 整数类型，用来存储没有小数的数字。
        int day = 1; 

        // double: 双精度浮点型，用来存储带小数点的数字，精度很高。
        double skillLevel = 1.0; 

        // 增长率：1.0% 即 0.01。
        double growthRate = 0.01; 

        /**
         * for: 循环语句，让代码重复执行。
         * int i = 0: 初始化，计数器从0开始。
         * i < 30: 循环条件，只要 i 小于 30 就继续跑。
         * i++: 每次跑完循环，计数器加 1。
         */
        for (int i = 0; i < 30; i++) {
            // skillLevel = skillLevel * (1 + 0.01)
            // *= 是简写，代表在自己原本的基础上乘以括号里的数。
            skillLevel *= (1 + growthRate);
        }

        /**
         * System: 系统类。
         * out: 标准输出对象（通常指屏幕）。
         * println: 打印一行内容并换行。
         * "+": 在这里不是加法，是“连接符”，把文字和数字拼在一起。
         */
        System.out.println("经过30天的战术训练，实力从 1.0 增长到: " + skillLevel);
    }
}
