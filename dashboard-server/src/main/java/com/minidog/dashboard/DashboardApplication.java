package com.minidog.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * =============================================================================
 * DashboardApplication.java - Spring Boot应用主入口
 * =============================================================================
 *
 * 【文件作用】
 * Spring Boot应用的启动类，包含main方法。
 * 这是整个后端服务的入口点。
 *
 * 【注解说明】
 * @SpringBootApplication 是一个组合注解，包含：
 * - @Configuration: 标记为配置类
 * - @EnableAutoConfiguration: 启用自动配置
 * - @ComponentScan: 扫描当前包及子包的组件
 *
 * @EnableScheduling 启用定时任务功能
 * 【设计决策】为什么需要定时任务？
 * - 定期计算错误率统计
 * - 定期检查是否需要触发告警
 * - 定期清理过期数据
 *
 * 【面试要点】
 * 问：Spring Boot的启动流程是怎样的？
 * 答：
 * 1. main方法调用SpringApplication.run()
 * 2. 创建ApplicationContext
 * 3. 加载配置，扫描组件
 * 4. 执行自动配置
 * 5. 启动内嵌Tomcat
 * 6. 应用就绪
 */
@SpringBootApplication
@EnableScheduling
public class DashboardApplication {

    /**
     * 【函数说明】应用程序入口
     *
     * 【输入】args: String[] - 命令行参数
     *
     * 【输出】无
     *
     * 【执行流程】
     * 1. SpringApplication.run() 启动Spring容器
     * 2. 自动扫描并注册所有@Component、@Service、@Controller等
     * 3. 启动内嵌Tomcat服务器
     * 4. 应用开始监听HTTP请求
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用
        // 第一个参数是配置类（通常是启动类本身）
        // 第二个参数是命令行参数
        SpringApplication.run(DashboardApplication.class, args);

        // 打印启动成功信息
        System.out.println("==============================================");
        System.out.println("  Mini-Datadog Dashboard Server 启动成功！");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("  H2控制台: http://localhost:8080/h2-console");
        System.out.println("==============================================");
    }
}
