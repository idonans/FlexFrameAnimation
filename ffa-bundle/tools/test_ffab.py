#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FFAB 编码工具测试脚本
用于测试ASTC编码工具的基本功能
"""

import os
import sys
import tempfile
from PIL import Image

def create_test_images(output_dir, count=5, size=(256, 256)):
    """创建测试图片"""
    os.makedirs(output_dir, exist_ok=True)

    for i in range(1, count + 1):
        # 创建一个简单的渐变图片（RGBA格式）
        img = Image.new('RGBA', size)
        pixels = img.load()

        for x in range(size[0]):
            for y in range(size[1]):
                # 创建一个简单的颜色渐变
                r = int(255 * x / size[0])
                g = int(255 * y / size[1])
                b = int(255 * (i - 1) / (count - 1))
                # 添加透明度变化
                a = 255  # 完全不透明
                pixels[x, y] = (r, g, b, a)

        # 保存为PNG
        filename = f"frame_{i:03d}.png"
        filepath = os.path.join(output_dir, filename)
        img.save(filepath, "PNG")
        print(f"创建测试图片: {filepath}")

def test_encoder(encoder_script, input_dir, output_file, format_type, quality):
    """测试编码工具"""
    print(f"\n测试编码工具: {encoder_script}")
    print(f"输入目录: {input_dir}")
    print(f"输出文件: {output_file}")
    print(f"格式: {format_type}")
    print(f"质量: {quality}")

    # 构建命令
    cmd = [
        sys.executable,
        encoder_script,
        input_dir,
        output_file,
        "--format", format_type,
        "--quality", quality
    ]

    # 执行命令
    import subprocess
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode == 0:
        print("编码成功!")
        print(f"输出文件大小: {os.path.getsize(output_file)} 字节")
        return True
    else:
        print("编码失败!")
        print("错误信息:", result.stderr)
        print("标准输出:", result.stdout)
        return False

def main():
    """主函数"""
    # 获取脚本目录
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # 创建临时目录（不使用上下文管理器，这样就不会自动删除）
    temp_dir = tempfile.mkdtemp(prefix="ffab_test_")
    print(f"创建临时目录: {temp_dir}")

    try:
        run_tests(script_dir, temp_dir)
        print(f"\n临时目录保留在: {temp_dir}")
        print("如需清理，请手动删除此目录")
    except Exception as e:
        print(f"测试过程中发生错误: {e}")
        print(f"临时目录保留在: {temp_dir}")
        print("如需清理，请手动删除此目录")


def run_tests(script_dir, temp_dir):
    """运行测试的核心逻辑"""
    # 创建测试图片目录和输出目录
    test_images_dir = os.path.join(temp_dir, "test_images")
    output_dir = os.path.join(temp_dir, "test_output")
    create_test_images(test_images_dir)
    os.makedirs(output_dir, exist_ok=True)

    # 测试编码工具
    encoder = os.path.join(script_dir, "ffab_encoder.py")

    # 检查编码工具是否存在
    if not os.path.exists(encoder):
        print(f"编码工具不存在: {encoder}")
        return

    # 定义所有格式和质量的组合
    formats = ["4x4", "6x6", "8x8"]
    qualities = ["fast", "medium", "thorough"]

    # 存储测试结果
    test_results = {}

    # 测试所有格式和质量的组合
    for fmt in formats:
        for quality in qualities:
            # 生成输出文件名
            output_file = os.path.join(output_dir, f"test_{fmt}_{quality}.ffab")

            # 执行测试
            print(f"\n测试格式: {fmt}, 质量: {quality}")
            success = test_encoder(encoder, test_images_dir, output_file, fmt, quality)
            test_results[(fmt, quality)] = success

    # 输出测试结果
    print("\n测试结果:")

    # 按格式分组显示结果
    for fmt in formats:
        print(f"\n格式 {fmt} 测试结果:")
        for quality in qualities:
            success = test_results[(fmt, quality)]
            print(f"  质量 {quality}: {'成功' if success else '失败'}")

    # 统计总成功率
    total_tests = len(test_results)
    successful_tests = sum(1 for success in test_results.values() if success)
    print(f"\n总体测试结果: {successful_tests}/{total_tests} 成功 ({successful_tests/total_tests*100:.1f}%)")

    # 显示输出目录位置
    print(f"\n所有测试输出文件位于: {output_dir}")

if __name__ == "__main__":
    main()