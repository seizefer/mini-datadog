#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
LogSimulator.py - 日志模拟生成器
=============================================================================

【文件作用】
模拟真实应用产生的日志，用于测试日志监控系统。
这个脚本会持续生成不同级别(INFO/WARN/ERROR)的日志到指定文件。

【设计决策】
为什么用Python而不是Java？
- Python脚本启动快，修改方便，适合做模拟器
- 生成日志不需要复杂的框架，Python更轻量
- 面试时可以展示多语言能力

【日志格式设计】
采用标准格式: [时间戳] [级别] [来源] 消息
例如: [2024-01-01 10:00:00] [ERROR] [PaymentService] 支付失败

为什么用这个格式？
- 时间戳方便排序和查询
- 级别方便过滤和统计
- 来源方便定位问题模块
"""

import random
import time
import datetime
import os
import argparse

# =============================================================================
# 配置常量区域
# =============================================================================

# 【设计决策】日志级别权重
# 为什么ERROR占10%？因为真实系统错误率通常在5-15%之间
# 这个比例可以让我们的监控系统有数据可看，又不会全是错误
LOG_LEVELS = {
    'INFO': 60,   # 60% - 正常信息，占大多数
    'WARN': 30,   # 30% - 警告信息，需要关注
    'ERROR': 10   # 10% - 错误信息，需要处理
}

# 【设计决策】模拟多个服务来源
# 为什么模拟多个服务？
# 1. 更接近真实的微服务架构
# 2. 可以测试按服务过滤的功能
# 3. 面试时可以说"支持多服务监控"
SERVICES = [
    'UserService',      # 用户服务
    'PaymentService',   # 支付服务
    'OrderService',     # 订单服务
    'InventoryService', # 库存服务
    'NotifyService'     # 通知服务
]

# 【设计决策】预定义日志消息模板
# 为什么要预定义？
# 1. 让日志看起来更真实
# 2. 方便测试搜索和过滤功能
# 3. 每个级别的消息内容符合该级别的语义
LOG_MESSAGES = {
    'INFO': [
        '用户登录成功，用户ID: {}',
        '订单创建成功，订单号: {}',
        '数据库查询完成，耗时: {}ms',
        '缓存命中，key: {}',
        'API调用成功，接口: {}',
        '定时任务执行完成，任务ID: {}',
        '消息发送成功，消息ID: {}',
        '文件上传完成，文件名: {}'
    ],
    'WARN': [
        '数据库连接池使用率超过80%，当前: {}%',
        'API响应时间过长: {}ms',
        '缓存未命中，key: {}',
        '重试第{}次，原因: 网络超时',
        '内存使用率较高: {}%',
        '请求队列积压: {}个',
        '磁盘空间不足: 剩余{}GB',
        '并发连接数接近上限: {}'
    ],
    'ERROR': [
        '数据库连接失败: {}',
        '支付处理异常: {}',
        '空指针异常: NullPointerException at {}',
        '服务调用超时: {}',
        '认证失败: {}',
        '文件读取错误: {}',
        '消息队列连接断开: {}',
        '外部API调用失败: {}'
    ]
}


def select_log_level():
    """
    【函数说明】根据权重随机选择日志级别

    【输入】无

    【输出】str - 返回 'INFO'、'WARN' 或 'ERROR'

    【设计决策】为什么用权重随机？
    - 真实系统的日志分布不是均匀的
    - INFO最多，ERROR最少，这符合真实情况
    - 使用random.choices可以方便地实现加权随机

    【实现原理】
    random.choices会根据weights参数的权重来选择
    weights=[60, 30, 10]表示INFO有60%概率，WARN有30%，ERROR有10%
    """
    levels = list(LOG_LEVELS.keys())
    weights = list(LOG_LEVELS.values())

    # random.choices返回一个列表，取第一个元素
    return random.choices(levels, weights=weights, k=1)[0]


def generate_random_value(level):
    """
    【函数说明】根据日志级别生成合适的随机值

    【输入】level: str - 日志级别 ('INFO'/'WARN'/'ERROR')

    【输出】str - 用于填充日志消息模板的随机值

    【设计决策】为什么不同级别用不同的值生成策略？
    - INFO级别通常是ID、耗时等正常数据
    - WARN级别通常是百分比、数量等需要关注的数值
    - ERROR级别通常是错误描述、异常信息

    这样生成的日志更真实，面试时可以说"模拟了真实的业务场景"
    """
    if level == 'INFO':
        # INFO级别：生成正常的业务数据
        return random.choice([
            str(random.randint(10000, 99999)),  # 用户ID/订单号
            str(random.randint(10, 200)),       # 耗时(ms)
            f'cache_key_{random.randint(1, 100)}',  # 缓存key
            f'/api/v1/resource/{random.randint(1, 50)}'  # API路径
        ])
    elif level == 'WARN':
        # WARN级别：生成需要关注的数值
        return random.choice([
            str(random.randint(80, 95)),   # 高百分比
            str(random.randint(500, 2000)), # 较长耗时
            str(random.randint(50, 200)),   # 队列积压数
            str(random.randint(5, 20))      # 剩余空间
        ])
    else:  # ERROR
        # ERROR级别：生成错误相关的信息
        return random.choice([
            'Connection refused',
            'Timeout after 30000ms',
            'com.minidog.service.UserService.process()',
            'Invalid token',
            'File not found: /data/config.json',
            'Queue connection lost'
        ])


def generate_log_entry():
    """
    【函数说明】生成一条完整的日志记录

    【输入】无

    【输出】str - 格式化的日志字符串

    【日志格式】
    [2024-01-01 10:00:00] [ERROR] [PaymentService] 支付处理异常: Timeout

    【设计决策】为什么用这个格式？
    1. 方括号分隔：方便正则表达式解析
    2. ISO格式时间戳：标准格式，排序友好
    3. 服务名在消息前：方便按服务过滤

    【面试要点】
    可以说"日志格式参考了业界标准的实践，便于后续解析和检索"
    """
    # 步骤1：获取当前时间戳
    timestamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    # 步骤2：随机选择日志级别
    level = select_log_level()

    # 步骤3：随机选择服务来源
    service = random.choice(SERVICES)

    # 步骤4：随机选择消息模板并填充值
    message_template = random.choice(LOG_MESSAGES[level])
    random_value = generate_random_value(level)
    message = message_template.format(random_value)

    # 步骤5：组装成完整的日志行
    log_line = f'[{timestamp}] [{level}] [{service}] {message}'

    return log_line


def run_simulator(output_file, interval, duration):
    """
    【函数说明】运行日志模拟器主循环

    【输入】
    - output_file: str - 日志输出文件路径
    - interval: float - 每条日志之间的间隔(秒)
    - duration: int - 总运行时长(秒)，0表示永久运行

    【输出】无（直接写入文件）

    【设计决策】为什么用追加模式写文件？
    - 追加模式('a')不会覆盖已有日志
    - 模拟真实的日志文件增长过程
    - LogAgent可以实时监控文件变化

    【设计决策】为什么每次写入后flush？
    - 确保日志立即写入磁盘
    - LogAgent可以立即检测到新日志
    - 避免缓冲区导致的延迟

    【技术要点】
    文件操作使用with语句，确保异常时也能正确关闭文件
    """
    print(f'🚀 日志模拟器启动')
    print(f'📁 输出文件: {output_file}')
    print(f'⏱️  生成间隔: {interval}秒')
    print(f'⏳ 运行时长: {"永久" if duration == 0 else str(duration) + "秒"}')
    print('-' * 50)

    # 记录开始时间，用于计算运行时长
    start_time = time.time()
    log_count = 0

    # 确保输出目录存在
    output_dir = os.path.dirname(output_file)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)

    try:
        # 使用追加模式打开文件
        # 【关键点】'a'模式：append追加，不覆盖已有内容
        with open(output_file, 'a', encoding='utf-8') as f:
            while True:
                # 检查是否超过运行时长
                if duration > 0 and (time.time() - start_time) >= duration:
                    print(f'\n✅ 运行时长已到，共生成 {log_count} 条日志')
                    break

                # 生成一条日志
                log_entry = generate_log_entry()

                # 写入文件
                f.write(log_entry + '\n')

                # 【关键点】立即刷新缓冲区，确保日志实时写入
                # 如果不flush，日志可能在缓冲区里，LogAgent检测不到
                f.flush()

                log_count += 1

                # 在控制台也打印一份，方便观察
                print(f'[{log_count}] {log_entry}')

                # 等待指定间隔
                time.sleep(interval)

    except KeyboardInterrupt:
        # 用户按Ctrl+C中断
        print(f'\n⏹️  用户中断，共生成 {log_count} 条日志')
    except Exception as e:
        print(f'\n❌ 发生错误: {e}')
        raise


def main():
    """
    【函数说明】程序入口，解析命令行参数并启动模拟器

    【命令行参数】
    -o/--output: 输出文件路径，默认./logs/app.log
    -i/--interval: 日志生成间隔(秒)，默认1秒
    -d/--duration: 运行时长(秒)，默认0(永久)

    【使用示例】
    python LogSimulator.py                    # 使用默认参数
    python LogSimulator.py -o /tmp/test.log   # 指定输出文件
    python LogSimulator.py -i 0.5 -d 60       # 每0.5秒生成一条，运行60秒

    【设计决策】为什么用argparse？
    - Python标准库，无需额外安装
    - 自动生成帮助信息
    - 参数验证和类型转换
    """
    # 创建参数解析器
    parser = argparse.ArgumentParser(
        description='Mini-Datadog 日志模拟器 - 生成模拟日志用于测试监控系统',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
使用示例:
  python LogSimulator.py                      # 使用默认参数
  python LogSimulator.py -o /tmp/app.log      # 指定输出文件
  python LogSimulator.py -i 0.5               # 每0.5秒生成一条
  python LogSimulator.py -d 300               # 运行5分钟后停止
        '''
    )

    # 定义命令行参数
    parser.add_argument(
        '-o', '--output',
        default='./logs/app.log',
        help='日志输出文件路径 (默认: ./logs/app.log)'
    )

    parser.add_argument(
        '-i', '--interval',
        type=float,
        default=1.0,
        help='日志生成间隔，单位秒 (默认: 1.0)'
    )

    parser.add_argument(
        '-d', '--duration',
        type=int,
        default=0,
        help='运行时长，单位秒，0表示永久运行 (默认: 0)'
    )

    # 解析参数
    args = parser.parse_args()

    # 启动模拟器
    run_simulator(args.output, args.interval, args.duration)


# Python的标准入口点写法
# 只有直接运行这个脚本时才会执行main()
# 如果是被其他模块import的话不会执行
if __name__ == '__main__':
    main()
