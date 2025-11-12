#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFAB 编码工具
将PNG或JPEG图片序列编码成FFAB格式文件
"""

import os
import sys
import struct
import argparse
import subprocess
from pathlib import Path
from typing import List, Tuple
import tempfile

try:
    from PIL import Image
    import numpy as np
except ImportError as e:
    print(f"错误：缺少必要的依赖库 {e}")
    print("请运行: pip install pillow numpy")
    sys.exit(1)

# FFAB 文件头魔数
FFAB_MAGIC = 0xFFAB

# astc 压缩格式定义与对应的编码映射，压缩格式同时匹配 astc block size (blockdim) 定义
ASTC_FORMAT_CODES = {
    '4x4': 0x0001,
    '5x4': 0x0002,
    '5x5': 0x0003,
    '6x5': 0x0004,
    '6x6': 0x0005,
    '8x5': 0x0006,
    '8x6': 0x0007,
    '8x8': 0x0008,
    '10x5': 0x0009,
    '10x6': 0x000A,
    '10x8': 0x000B,
    '10x10': 0x000C,
    '12x10': 0x000D,
    '12x12': 0x000E
}

# 支持的图片格式
SUPPORTED_FORMATS = ('.png', '.jpg', '.jpeg')


def check_astc_encoder() -> bool:
    """检查ASTC编码器是否可用"""
    try:
        result = subprocess.run(['astcenc', '-help'],
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE,
                               text=True)
        return result.returncode == 0
    except FileNotFoundError:
        return False


def check_astc_format(format_name: str) -> str:
    """校验ASTC格式"""
    format_name = format_name.lower()
    if format_name in ASTC_FORMAT_CODES.keys():
        return format_name
    else:
        raise ValueError(f"无效的ASTC格式: {format_name}")


def get_astc_format_code(format_name: str) -> int:
    """获取ASTC格式代码"""
    format_name = format_name.lower()
    if format_name in ASTC_FORMAT_CODES.keys():
        return ASTC_FORMAT_CODES[format_name]
    else:
        raise ValueError(f"无效的ASTC格式: {format_name}")


def compress_with_astc(img_data: np.ndarray, astc_format: str, quality: float) -> bytes:
    """
    使用ASTC编码器压缩图片

    Args:
        img_data: 图片数据
        astc_format: ASTC压缩格式 (4x4, 5x4, 5x5, 6x5, 6x6, 8x5, 8x6, 8x8, 10x5, 10x6, 10x8, 10x10, 12x10, 12x12)
        quality: 压缩质量 (0.0 - 100.0)

    Returns:
        压缩后的数据
    """
    # 创建临时工作文件夹
    with tempfile.TemporaryDirectory(prefix='ffab_') as temp_dir:
        # 将 img_data 转换为 PIL 图像并保存到 input.png 文件
        input_path = os.path.join(temp_dir, 'input.png')
        img = Image.fromarray(img_data)
        img.save(input_path, 'PNG')

        # 使用 astcenc 编码器压缩 input.png 文件
        output_path = os.path.join(temp_dir, 'output.astc')

        # 构建ASTC编码命令
        cmd = [
            'astcenc',
            '-cl', input_path, output_path, astc_format,
            str(quality)
        ]

        # 执行ASTC编码
        result = subprocess.run(cmd, 
                               stdout=subprocess.PIPE, 
                               stderr=subprocess.PIPE,
                               text=True)

        if result.returncode != 0:
            raise RuntimeError(f"ASTC编码失败: {result.stderr} ${result.stdout}")

        # 读取 output.astc 文件内容，作为压缩后的数据返回
        with open(output_path, 'rb') as f:
            compressed_data = f.read()

        return compressed_data
    # 临时工作文件夹会在 with 块结束后自动清理


def load_images_from_folder(folder_path: str) -> List[Tuple[str, np.ndarray]]:
    """
    从文件夹加载所有图片，如果遇到不识别的图片或文件、文件夹为空，会抛出异常。

    Args:
        folder_path: 图片文件夹路径

    Returns:
        图片文件名和numpy数组数据的列表
    """
    images = []
    folder = Path(folder_path)

    if not folder.exists():
        raise FileNotFoundError(f"文件夹不存在: {folder_path}")

    image_files = []

    # 遍历文件夹的所有文件，如果文件不是支持的图片格式，抛出异常，否则添加到列表中
    for file in folder.iterdir():
        if file.suffix.lower() not in SUPPORTED_FORMATS:
            raise ValueError(f"不支持的图片格式: {file.name}")
        else:
            image_files.append(file)

    # 对图片文件列表按名称排序
    image_files = sorted(image_files)

    if not image_files:
        raise ValueError(f"文件夹中没有找到支持的图片格式: {SUPPORTED_FORMATS}")

    # 遍历 image_files，加载图片并添加到列表中
    for img_file in image_files:
        try:
            with Image.open(img_file) as img:
                # 转换为RGBA格式（保持透明度）
                img = img.convert('RGBA')
                # 转换为numpy数组
                img_array = np.array(img)

                images.append((img_file.name, img_array))
                print(f"已加载: {img_file.name} ({img_array.shape})")
        except Exception as e:
            raise RuntimeError(f"无法加载图片 {img_file.name}: {e}")

    return images


def check_images_dimensions(images: List[Tuple[str, np.ndarray]]) -> Tuple[int, int]:
    """
    检查所有图片的尺寸是否一致

    Args:
        images: 图片列表

    Returns:
        图片的宽度和高度

    Raises:
        ValueError: 如果图片尺寸不一致
    """
    if not images:
        raise ValueError("没有可用的图片")

    first_img_name, first_img_data = images[0]
    height, width = first_img_data.shape[:2]

    for img_name, img_data in images[1:]:
        h, w = img_data.shape[:2]
        if h != height or w != width:
            raise ValueError(f"图片尺寸不一致: {first_img_name} ({width}x{height}) vs {img_name} ({w}x{h})")

    return width, height


def generate_astc_header(width: int, height: int, astc_format: str) -> bytes:
    """
    生成ASTC文件头(16字节)
    ```
    struct astc_header
    {
        uint8_t magic[4];
        uint8_t block_x;
        uint8_t block_y;
        uint8_t block_z;
        uint8_t dim_x[3];
        uint8_t dim_y[3];
        uint8_t dim_z[3];
    }
    ```

    Args:
        width: 图片宽度
        height: 图片高度
        astc_format: ASTC格式 (4x4, 5x4, 5x5, 6x5, 6x6, 8x5, 8x6, 8x8, 10x5, 10x6, 10x8, 10x10, 12x10, 12x12)

    Returns:
        ASTC文件头数据
    """
    # 从 astc_format 中拆解 block_x 与 block_y
    # 注意：二维图片中，block_z 固定为 1，dim_z 固定为 1
    block_x, block_y = map(int, astc_format.split('x'))

    # 构建ASTC文件头（使用大端序）
    header = struct.pack('>4BBBB3B3B3B',
                         0x13, 0xAB, 0xA1, 0x5C,
                         block_x, block_y, 1,
                         width & 0xFF, (width >> 8) & 0xFF, (width >> 16) & 0xFF,
                         height & 0xFF, (height >> 8) & 0xFF, (height >> 16) & 0xFF,
                         1, 0, 0)

    return header


def create_ffab_file_v1(images: List[Tuple[str, np.ndarray]], output_path: str, astc_format: str, quality: float) -> None:
    """
    创建FFAB文件 (版本1)
    `版本1 (0x0001) 定义内容概括：
    1. 文件头(4字节):FFAB_MAGIC (0xFFAB) + 版本号(0x0001)
    2. Meta信息区(8字节):图片数量(2字节) + 图片宽度(2字节) + 图片高度(2字节) + ASTC格式代码(2字节)
    3. 索引表(每项12字节):每个索引项包含数据偏移量(8字节) + 数据长度(4字节)
    4. 数据区:连续存储所有图片的ASTC压缩数据, 不包括 astc header (16字节)

    Args:
        images: 图片列表
        output_path: 输出文件路径
        astc_format: ASTC格式 (4x4, 5x4, 5x5, 6x5, 6x6, 8x5, 8x6, 8x8, 10x5, 10x6, 10x8, 10x10, 12x10, 12x12)
        quality: ASTC压缩质量 (0.0-100.0)
    """
    if not images:
        raise ValueError("没有可用的图片")

    # 检查所有图片的尺寸是否一致
    width, height = check_images_dimensions(images)

    # 获取ASTC格式代码
    astc_format_code = get_astc_format_code(astc_format)

    # 准备文件头（使用大端序），当前为版本0x0001
    header = struct.pack('>HH', FFAB_MAGIC, 0x0001)

    # 准备Meta信息区（使用大端序）
    image_count = len(images)
    meta = struct.pack('>HHHH', image_count, width, height, astc_format_code)

    # 准备索引表和图片数据
    index_entries = []
    image_data_blocks = []

    # 计算数据区起始位置
    # 文件头(4字节) + Meta信息区(8字节) + 索引表(每项12字节)
    data_start_offset = 4 + 8 + (image_count * 12)

    current_offset = data_start_offset

    # 根据 meta 信息生成 `.astc` header 的内容(16字节)
    astc_header = generate_astc_header(width, height, astc_format)

    # 处理每张图片
    for img_name, img_data in images:
        # 使用ASTC编码器压缩图片
        astc_compressed_data = compress_with_astc(img_data, astc_format, quality)

        # 对比压缩数据的前 16 个字节是否与 astc_header 相同
        # https://github.com/ARM-software/astc-encoder/blob/main/Docs/FileFormat.md
        # .astc 文件的前 16 个字节是文件头
        if astc_compressed_data[:16] != astc_header:
            raise ValueError(f"图片 {img_name} 的 ASTC 压缩数据前 16 个字节与 astc_header 不匹配")

        # 记录实际压缩数据（不包括 astc header）
        compressed_data = astc_compressed_data[16:]

        data_length = len(compressed_data)

        # 添加索引项（使用大端序）
        index_entries.append(struct.pack('>QI', current_offset, data_length))

        # 添加压缩数据
        image_data_blocks.append(compressed_data)

        # 更新偏移量
        current_offset += data_length

        print(f"已处理: {img_name} -> {data_length} 字节")

    # 合并索引表
    index_table = b''.join(index_entries)

    # 合并图片数据
    image_data = b''.join(image_data_blocks)

    # 写入文件
    with open(output_path, 'wb') as f:
        # 写入文件头
        f.write(header)

        # 写入Meta信息区
        f.write(meta)

        # 写入索引表
        f.write(index_table)

        # 写入图片数据
        f.write(image_data)

    # 输出统计信息
    file_size = os.path.getsize(output_path)
    print(f"\nFFAB文件创建成功:")
    print(f"  输出文件: {output_path}")
    print(f"  ffab 版本: 0x0001")
    print(f"  图片数量: {image_count}")
    print(f"  图片尺寸: {width}x{height}")
    print(f"  ASTC格式: {astc_format}")
    print(f"  文件大小: {file_size} 字节")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='FFAB编码工具 - 将图片序列编码成FFAB格式')
    parser.add_argument('input_folder', help='包含PNG或JPEG图片的输入文件夹')
    parser.add_argument('output_file', help='输出的FFAB文件路径')
    parser.add_argument('--format', choices=list(ASTC_FORMAT_CODES.keys()), default='6x6',
                       help='ASTC压缩格式 (默认: 6x6)')
    parser.add_argument('--quality', type=float, default=50,
                       help='ASTC压缩质量 (0.0-100.0, 默认: 50)')

    args = parser.parse_args()

    try:
        # 检查ASTC编码器是否可用
        if not check_astc_encoder():
            print("错误：未找到ASTC编码器 (astcenc)")
            print("请从 https://github.com/ARM-software/astc-encoder 下载并安装")
            sys.exit(1)

        # 校验ASTC格式
        astc_format = check_astc_format(args.format)

        # 校验ASTC质量
        if not (0 <= args.quality <= 100):
            print("错误：ASTC质量必须在0-100之间")
            sys.exit(1)

        # 加载图片
        print(f"正在从文件夹加载图片: {args.input_folder}")
        images = load_images_from_folder(args.input_folder)

        # 创建FFAB文件
        print(f"\n正在创建FFAB文件: {args.output_file}")
        create_ffab_file_v1(images, args.output_file, astc_format, args.quality)
    except Exception as e:
        print(f"错误: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()