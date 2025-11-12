#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFAB 编码与解码功能测试
测试逻辑：
1. 测试资源文件为 resources 目录下的每一个 mp4 文件，对每一个 mp4 文件进行单独测试
2. 以 mp4 文件的文件名作为目录名，在 build 目录下创建对应的目录，用于存储测试过程的中间文件与测试结果
3. 测试步骤概括为：解码 mp4 为序列帧，将序列帧编码为 ffab，再将 ffab 解码为序列帧，具体步骤如下：
    A. 使用 ffmpeg 将 mp4 文件解码为图片序列帧，帧率为 EXTRACT_FRAMES_FPS
    B. 调用 ffab_encoder.py 将图片序列编码为不同 ASTC 格式的 FFAB 文件
    C. 调用 ffab_decoder.py 将每一个 FFAB 文件解码为图片序列帧
    D. 对比解码后的图片序列帧与原始图片序列帧，要求图片数量与图片分辨率一致，只需要对比文件夹下的第一个图片的分辨率即可
4. 测试过程举例：
    A. 测试资源文件为 resources/test1.mp4
    B. 以 test1.mp4 作为目录名，在 build 目录下创建对应的目录 test1，相对路径为 build/test1
    C. 使用 ffmpeg 将 test1.mp4 解码为图片序列帧，帧率为 EXTRACT_FRAMES_FPS，存储在 build/test1/input_frames 目录下
    D. 调用 ffab_encoder.py 将 build/test1/input_frames 目录下的图片序列编码为不同 ASTC 格式的 FFAB 文件，存储在 build/test1/output_ffab 目录下
    E. 使用并发的方式产生不同 ASTC 格式的 FFAB 文件，每个 ASTC 格式对应一个 FFAB 文件，会生成如 build/test1/output_ffab/output_4x4.ffab、build/test1/output_ffab/output_5x4.ffab 等文件
    F. 调用 ffab_decoder.py 将 build/test1/output_ffab 目录下的每一个 FFAB 文件解码为图片序列帧，解码的图片序列帧存储在 build/test1/output_frames 目录下
    G. 使用并发的方式对每一个 ffab 文件解码为图片序列帧，解码的图片序列帧存储在 build/test1/output_frames 目录下，会生成如 build/test1/output_frames/output_4x4、build/test1/output_frames/output_5x4 等目录，每个目录下存储对应 ASTC 格式的图片序列帧
    H. 对比 build/test1/output_frames 目录下的图片序列帧与原始图片序列帧，要求图片数量与图片分辨率一致，只需要对比文件夹下的第一个图片的分辨率即可
"""

import shutil
import sys
import subprocess
from pathlib import Path
from concurrent.futures import ProcessPoolExecutor, as_completed
from PIL import Image

# 测试视频提取帧率
EXTRACT_FRAMES_FPS = 24

# 测试 ASTC 格式列表
ASTC_FORMATS = ['4x4', '6x6', '8x8', '12x12']

# 测试压缩质量
ASTC_QUALITY = 100.0

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


def reset_build_directory(build_dir):
    """重置 build 目录"""
    # 删除 build 目录及其子文件
    if build_dir.exists():
        shutil.rmtree(build_dir)
    # 创建 build 目录
    build_dir.mkdir(exist_ok=True)


def create_test_directories(base_dir):
    """为单个测试视频创建所需的目录结构"""
    # 确保base_dir存在
    base_dir.mkdir(parents=True, exist_ok=True)

    input_frames_dir = base_dir / 'input_frames'
    output_ffab_dir = base_dir / 'output_ffab'
    output_frames_dir = base_dir / 'output_frames'

    # 创建目录，使用parents=True确保所有父目录都被创建
    input_frames_dir.mkdir(parents=True, exist_ok=True)
    output_ffab_dir.mkdir(parents=True, exist_ok=True)
    output_frames_dir.mkdir(parents=True, exist_ok=True)

    return input_frames_dir, output_ffab_dir, output_frames_dir


def extract_frames(video_path, frames_dir):
    """使用 ffmpeg 提取视频帧"""
    if not Path(video_path).exists():
        print(f"错误：视频文件不存在: {video_path}")
        return False

    # 使用 ffmpeg 提取帧，按顺序命名为 frame_%04d.png
    command = [
        'ffmpeg',
        '-i', str(video_path),
        '-vf', f'fps={EXTRACT_FRAMES_FPS}',
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


def encode_to_ffab(tools_dir: Path, input_dir: Path, output_dir: Path, astc_format: str) -> bool:
    """使用 ffab_encoder.py 编码为 FFAB 文件"""
    encoder_script = tools_dir / 'ffab_encoder.py'

    # 构建输出文件路径
    output_file = output_dir / f'output_{astc_format}.ffab'

    # 构建命令
    command = [
        sys.executable,
        str(encoder_script),
        str(input_dir),
        str(output_file),
        '--format', astc_format,
        '--quality', str(ASTC_QUALITY)
    ]

    success = run_command(command)
    if not success:
        print(f"编码失败: {astc_format}")
        print(f"输入目录: {input_dir}")
        print(f"输出文件: {output_file}")
        return False

    print(f"成功编码 {astc_format} 格式到 {output_file}")
    return True


def decode_from_ffab(tools_dir: Path, ffab_file: Path, output_dir: Path) -> bool:
    """使用 ffab_decoder.py 解码 FFAB 文件"""
    decoder_script = tools_dir / 'ffab_decoder.py'

    # 构建命令
    command = [
        sys.executable,
        str(decoder_script),
        str(ffab_file),
        str(output_dir)
    ]

    success = run_command(command)
    if not success:
        print(f"解码失败: {ffab_file}")
        return False

    print(f"成功解码 {ffab_file} 到 {output_dir}")
    return True


def compare_frames(original_dir: Path, decoded_dir: Path) -> bool:
    """对比原始帧和解码后的帧，检查数量和分辨率是否一致"""
    # 获取目录下的全部文件列表，不要过滤文件名与文件格式
    original_files = sorted(list(original_dir.glob('*')))
    decoded_files = sorted(list(decoded_dir.glob('*')))

    # 检查文件数量是否一致
    if len(original_files) != len(decoded_files):
        print(f"文件数量不一致: 原始={len(original_files)}, 解码={len(decoded_files)}")
        return False

    # 检查 original_files 与 decoded_files 中的第一个文件的分辨率是否一致
    first_orig_file = original_files[0]
    first_dec_file = decoded_files[0]

    # 使用 PIL 获取分辨率并检查是否一致
    with Image.open(first_orig_file) as orig_img, Image.open(first_dec_file) as dec_img:
        # 获取分辨率
        orig_width, orig_height = orig_img.size
        dec_width, dec_height = dec_img.size

        # 检查分辨率是否一致
        if orig_width != dec_width or orig_height != dec_height:
            print(f"分辨率不一致: {first_orig_file}={orig_width}x{orig_height}, {first_dec_file}={dec_width}x{dec_height}")
            return False

    return True


def process_video(tools_dir: Path, video_path: Path, build_dir: Path):
    """处理单个视频文件的测试流程"""
    print(f"\n=== 开始处理视频: {video_path.name} ===")

    # 获取视频文件名（不含扩展名）作为测试目录名
    video_name = video_path.stem
    test_dir = build_dir / video_name

    # 创建测试目录结构
    input_frames_dir, output_ffab_dir, output_frames_dir = create_test_directories(test_dir)

    # 提取视频帧
    print(f"\n--- 提取视频帧 ---")
    if not extract_frames(video_path, input_frames_dir):
        return False

    # 编码为FFAB文件
    print(f"\n--- 编码为FFAB文件 ---")
    encode_success = True
    failed_formats = []

    # 准备所有要处理的格式
    formats_to_test = ASTC_FORMATS
    print(f"将并行处理 {len(formats_to_test)} 种 ASTC 格式")

    # 使用进程池并行执行编码任务
    with ProcessPoolExecutor() as executor:
        # 提交所有编码任务
        future_to_format = {}
        for astc_format in formats_to_test:
            print(f"提交编码任务: {astc_format} 格式")
            future = executor.submit(encode_to_ffab, tools_dir, input_frames_dir, output_ffab_dir, astc_format)
            future_to_format[future] = astc_format

        # 收集所有任务的结果
        for future in as_completed(future_to_format):
            astc_format = future_to_format[future]
            try:
                success = future.result()
                if not success:
                    encode_success = False
                    failed_formats.append(astc_format)
            except Exception as e:
                print(f"处理 {astc_format} 格式时出错: {str(e)}")
                encode_success = False
                failed_formats.append(astc_format)

    if not encode_success:
        print(f"编码失败格式: {', '.join(failed_formats)}")
        return False

    # 解码FFAB文件
    print(f"\n--- 解码FFAB文件 ---")
    decode_success = True

    # 获取所有FFAB文件
    ffab_files = list(output_ffab_dir.glob('*.ffab'))
    if not ffab_files:
        print("错误：未找到FFAB文件")
        return False

    # 使用进程池并行执行解码任务
    with ProcessPoolExecutor() as executor:
        # 提交所有解码任务
        future_to_file = {}
        for ffab_file in ffab_files:
            # 从文件名提取格式信息，如output_4x4.ffab -> output_4x4
            format_name = ffab_file.stem
            output_dir = output_frames_dir / format_name
            output_dir.mkdir(exist_ok=True)

            print(f"提交解码任务: {ffab_file.name}")
            future = executor.submit(decode_from_ffab, tools_dir, ffab_file, output_dir)
            future_to_file[future] = ffab_file

        # 收集所有任务的结果
        for future in as_completed(future_to_file):
            ffab_file = future_to_file[future]
            try:
                success = future.result()
                if not success:
                    decode_success = False
            except Exception as e:
                print(f"解码 {ffab_file.name} 时出错: {str(e)}")
                decode_success = False

    if not decode_success:
        print("部分或全部FFAB文件解码失败")
        return False

    # 对比解码后的帧与原始帧
    print(f"\n--- 对比解码帧与原始帧 ---")
    compare_success = True

    # 检查每个格式的解码结果
    for ffab_file in ffab_files:
        format_name = ffab_file.stem
        decoded_dir = output_frames_dir / format_name

        print(f"对比 {format_name} 格式的帧...")
        if not compare_frames(input_frames_dir, decoded_dir):
            print(f"{format_name} 格式帧对比失败")
            compare_success = False
        else:
            print(f"{format_name} 格式帧对比成功")

    return compare_success


def main():
    """主函数"""
    # 获取目录路径
    tools_dir = Path(__file__).parent.parent
    test_dir = tools_dir / 'test'
    resources_dir = test_dir / 'resources'
    build_dir = test_dir / 'build'

    # 重置build目录
    reset_build_directory(build_dir)

    # 检查必要工具
    if not check_ffmpeg_availability():
        return 1

    if not check_astc_encoder_availability():
        return 1

    # 检查resources目录是否存在
    if not resources_dir.exists():
        print(f"错误：resources目录不存在: {resources_dir}")
        return 1

    # 获取resources目录下的所有mp4文件
    mp4_files = list(resources_dir.glob('*.mp4'))
    if not mp4_files:
        print(f"错误：resources目录下未找到mp4文件: {resources_dir}")
        return 1

    print(f"找到 {len(mp4_files)} 个测试视频文件")

    # 处理每个视频文件
    failed_videos = []

    for video_path in mp4_files:
        if not process_video(tools_dir, video_path, build_dir):
            failed_videos.append(video_path.name)

    # 输出测试结果摘要
    print("\n=== 测试结果摘要 ===")
    print(f"总视频数: {len(mp4_files)}")
    print(f"成功视频数: {len(mp4_files) - len(failed_videos)}")

    if failed_videos:
        print(f"失败视频: {', '.join(failed_videos)}")
        print("测试失败!")
        return 1
    else:
        print("所有视频测试成功!")
        return 0

if __name__ == '__main__':
    sys.exit(main())