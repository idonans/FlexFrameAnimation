#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFAB 编码器功能测试
测试逻辑：
1. 使用 ffmpeg 将 frame_test.mp4 解码为图片序列帧
2. 调用 ffab_encoder.py 将图片序列编码为不同 ASTC 格式的 FFAB 文件
"""

import shutil
import sys
import subprocess
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))
from ffab_encoder import ASTC_FORMAT_CODES

def run_command(command, cwd=None):
    """运行命令并返回结果"""
    print(f"执行命令: {' '.join(command)}")
    result = subprocess.run(
        command,
        cwd=cwd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )

    if result.returncode != 0:
        print(f"命令执行失败: {result.stderr}")
        print(f"命令输出: {result.stdout}")
        return False

    return True


def check_ffmpeg_availability():
    """检查 ffmpeg 是否可用"""
    success = run_command(['ffmpeg', '-version'])
    if not success:
        print("错误：未找到 ffmpeg")
        print("请安装 ffmpeg 并确保其在系统 PATH 中")
        return False
    return True


def check_astc_encoder_availability():
    """检查 astcenc 是否可用"""
    success = run_command(['astcenc', '-help'])
    if not success:
        print("错误：未找到 ASTC 编码器 (astcenc)")
        print("请从 https://github.com/ARM-software/astc-encoder 下载并安装")
        return False
    return True


def reset_directories():
    """重置测试所需的目录"""
    tools_dir = Path(__file__).parent.parent
    test_dir = tools_dir / 'test'
    build_dir = test_dir / 'build'
    frames_dir = build_dir / 'frame_test_frames'
    ffab_dir = build_dir / 'ffab'

    # 删除 build 目录及其子文件
    if build_dir.exists():
        shutil.rmtree(build_dir)

    # 创建目录
    build_dir.mkdir(exist_ok=True)
    frames_dir.mkdir(exist_ok=True)
    ffab_dir.mkdir(exist_ok=True)

    return tools_dir, frames_dir, ffab_dir


def extract_frames(video_path, frames_dir, fps=30):
    """使用 ffmpeg 提取视频帧"""
    if not Path(video_path).exists():
        print(f"错误：视频文件不存在: {video_path}")
        return False

    # 使用 ffmpeg 提取帧，按顺序命名为 frame_%04d.png
    command = [
        'ffmpeg',
        '-i', str(video_path),
        '-vf', f'fps={fps}',
        '-q:v', '2',  # 高质量输出
        str(frames_dir / 'frame_%04d.png')
    ]

    success = run_command(command)
    if not success:
        print("帧提取失败")
        return False

    # 检查是否成功提取了帧
    frame_files = list(frames_dir.glob('frame_*.png'))
    if not frame_files:
        print("警告：未提取到任何帧")
        return False

    print(f"成功提取 {len(frame_files)} 帧到 {frames_dir}")
    return True


def encode_to_ffab(tools_dir, input_dir, output_dir, astc_format):
    """使用 ffab_encoder.py 编码为 FFAB 文件"""
    encoder_script = tools_dir / 'ffab_encoder.py'
    output_file = output_dir / f'frame_test_{astc_format}.ffab'

    # 构建命令
    command = [
        sys.executable,
        str(encoder_script),
        str(input_dir),
        str(output_file),
        '--format', astc_format
    ]

    success = run_command(command)
    if not success:
        print(f"编码失败: {astc_format}")
        print(f"输入目录: {input_dir}")
        print(f"输出文件: {output_file}")
        return False

    print(f"成功编码 {astc_format} 格式到 {output_file}")
    return True


def main():
    """主函数"""

    # 重置目录结构
    tools_dir, frames_dir, ffab_dir = reset_directories()
    video_path = tools_dir / 'test' / 'frame_test.mp4'

    # 检查必要工具
    if not check_ffmpeg_availability():
        return 1

    if not check_astc_encoder_availability():
        return 1

    # 检查测试视频是否存在
    if not video_path.exists():
        print(f"错误：测试视频文件不存在: {video_path}")
        print("请将测试视频放置在 test 目录下并命名为 frame_test.mp4")
        return 1

    # 提取视频帧
    print("\n=== 开始提取视频帧 ===")
    if not extract_frames(video_path, frames_dir, fps=30):
        return 1

    # 测试所有 ASTC 格式
    print("\n=== 开始测试所有 ASTC 格式 ===")
    success_count = 0
    failed_formats = []

    # 按照从 4x4 到 12x12 的顺序测试所有格式
    for astc_format in sorted(ASTC_FORMAT_CODES.keys()):
        print(f"\n测试 {astc_format} 格式...")
        if encode_to_ffab(tools_dir, frames_dir, ffab_dir, astc_format):
            success_count += 1
        else:
            failed_formats.append(astc_format)

    # 输出测试结果摘要
    print("\n=== 测试结果摘要 ===")
    print(f"总格式数: {len(ASTC_FORMAT_CODES)}")
    print(f"成功格式数: {success_count}")
    if failed_formats:
        print(f"失败格式: {', '.join(failed_formats)}")
    else:
        print("所有格式测试成功!")

    print(f"\nFFAB 文件输出目录: {ffab_dir}")

    return 0 if not failed_formats else 1

if __name__ == '__main__':
    sys.exit(main())