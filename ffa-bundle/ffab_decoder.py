#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFAB 解码工具
将FFAB格式文件解码成PNG图片序列
"""

import os
import sys
import struct
import argparse
import subprocess
from pathlib import Path
from typing import List, Tuple
import tempfile

# 尝试导入必要的库
try:
    from PIL import Image
    import numpy as np
except ImportError as e:
    print(f"错误：缺少必要的依赖库 {e}")
    print("请运行: pip install pillow numpy")
    sys.exit(1)

# FFAB 文件格式常量
FFAB_MAGIC = 0xFFAB
FFAB_VERSION_0x0001 = 0x0001

# ASTC 压缩格式及对应的编码映射
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

# 反向映射：从格式代码到格式名称
ASTC_CODE_TO_FORMAT = {v: k for k, v in ASTC_FORMAT_CODES.items()}

# 输出图片格式
OUTPUT_FORMAT = 'PNG'


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


def check_astc_decoder() -> bool:
    """检查ASTC解码器是否可用"""
    try:
        result = subprocess.run(['astcenc', '-help'],
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE,
                               text=True)
        return result.returncode == 0
    except FileNotFoundError:
        return False


def decode_astc_data(compressed_data: bytes, width: int, height: int, astc_format: str) -> np.ndarray:
    """
    使用ASTC解码器解码图片数据

    Args:
        compressed_data: 压缩的ASTC数据
        width: 图片宽度
        height: 图片高度
        astc_format: ASTC格式 (4x4, 5x4, 5x5, 6x5, 6x6, 8x5, 8x6, 8x8, 10x5, 10x6, 10x8, 10x10, 12x10, 12x12)

    Returns:
        解码后的numpy数组图像数据
    """
    # 生成ASTC文件头（16字节）
    astc_header = generate_astc_header(width, height, astc_format)

    # 创建临时工作文件夹
    with tempfile.TemporaryDirectory(prefix='ffab_') as temp_dir:
        # 将压缩数据写入临时文件，先写入ASTC头部，再写入压缩数据
        astc_path = os.path.join(temp_dir, 'input.astc')
        with open(astc_path, 'wb') as f:
            f.write(astc_header)
            f.write(compressed_data)

        # 解码后的输出路径
        output_path = os.path.join(temp_dir, 'output.png')

        # 构建ASTC解码命令
        # -dl decompress with linear LDR
        cmd = [
            'astcenc',
            '-dl', astc_path, output_path
        ]

        # 执行ASTC解码
        result = subprocess.run(cmd, 
                               stdout=subprocess.PIPE, 
                               stderr=subprocess.PIPE,
                               text=True)

        if result.returncode != 0:
            raise RuntimeError(f"ASTC解码失败: {result.stderr}")

        # 读取解码后的图片
        with Image.open(output_path) as img:
            # 转换为RGBA格式（保持透明度）
            img = img.convert('RGBA')
            # 转换为numpy数组
            img_array = np.array(img)

        return img_array


def read_ffab_header(file_path: str) -> int:
    """
    读取FFAB文件头，校验魔数和版本号

    Args:
        file_path: FFAB文件路径

    Returns:
        版本号

    Raises:
        ValueError: 如果文件格式无效或版本不支持
    """
    with open(file_path, 'rb') as f:
        # 读取文件头（4字节）: 魔数(2字节) + 版本号(2字节)
        header = f.read(4)
        magic, version = struct.unpack('>HH', header)

        # 验证魔数
        if magic != FFAB_MAGIC:
            raise ValueError(f"无效的FFAB文件: 魔数不匹配")

        # 验证版本
        if version != FFAB_VERSION_0x0001:
            raise ValueError(f"不支持的FFAB文件版本: 0x{version:04X}")

        return version


def read_ffab_meta(file_path: str) -> Tuple[int, int, int, int]:
    """
    读取FFAB文件的Meta信息区

    Args:
        file_path: FFAB文件路径

    Returns:
        (图片数量, 图片宽度, 图片高度, ASTC格式代码)
    """
    with open(file_path, 'rb') as f:
        # 跳过文件头（4字节）
        f.seek(4)
        # 读取Meta信息区（8字节）: 图片数量(2字节) + 宽度(2字节) + 高度(2字节) + ASTC格式代码(2字节)
        meta = f.read(8)
        image_count, width, height, astc_format_code = struct.unpack('>HHHH', meta)

        return image_count, width, height, astc_format_code


def read_ffab_index_table(file_path: str, image_count: int) -> List[Tuple[int, int]]:
    """
    读取FFAB文件的索引表

    Args:
        file_path: FFAB文件路径
        image_count: 图片数量

    Returns:
        索引表条目列表，每个条目包含(偏移量, 数据长度)
    """
    index_entries = []

    # 计算索引表起始位置
    # 文件头(4字节) + Meta信息区(8字节)
    index_start_offset = 4 + 8

    with open(file_path, 'rb') as f:
        # 移动到索引表位置
        f.seek(index_start_offset)

        # 读取索引表
        for _ in range(image_count):
            # 每个索引条目12字节: 偏移量(8字节) + 数据长度(4字节)
            entry_data = f.read(12)
            offset, data_length = struct.unpack('>QI', entry_data)
            index_entries.append((offset, data_length))

    return index_entries


def read_compressed_image_data(file_path: str, offset: int, data_length: int) -> bytes:
    """
    读取压缩的图像数据

    Args:
        file_path: FFAB文件路径
        offset: 数据偏移量
        data_length: 数据长度

    Returns:
        压缩的图像数据
    """
    with open(file_path, 'rb') as f:
        f.seek(offset)
        compressed_data = f.read(data_length)
    return compressed_data


def decode_ffab_file(file_path: str, output_folder: str) -> None:
    """
    解码FFAB文件到指定输出文件夹

    Args:
        file_path: FFAB文件路径
        output_folder: 输出文件夹路径
    """
    # 确保输出文件夹存在
    output_dir = Path(output_folder)
    output_dir.mkdir(parents=True, exist_ok=True)

    # 读取版本号
    version = read_ffab_header(file_path)
    # 断言，版本号为0x0001
    assert version == FFAB_VERSION_0x0001, f"版本号错误，预期0x{FFAB_VERSION_0x0001:04X}"

    # 读取Meta信息区
    image_count, width, height, astc_format_code = read_ffab_meta(file_path)

    # 获取ASTC格式名称
    if astc_format_code not in ASTC_CODE_TO_FORMAT:
        raise ValueError(f"未知的ASTC格式代码: 0x{astc_format_code:04X}")
    astc_format = ASTC_CODE_TO_FORMAT[astc_format_code]

    print(f"FFAB文件信息:")
    print(f"  ffab 版本: 0x{version:04X}")
    print(f"  图片数量: {image_count}")
    print(f"  图片尺寸: {width}x{height}")
    print(f"  ASTC格式: {astc_format}")

    # 读取索引表
    index_entries = read_ffab_index_table(file_path, image_count)

    # 解码每张图片
    for i, (offset, data_length) in enumerate(index_entries):
        # 读取压缩数据
        compressed_data = read_compressed_image_data(file_path, offset, data_length)

        # 解码ASTC数据
        print(f"正在解码第{i+1}/{image_count}张图片...")
        img_array = decode_astc_data(compressed_data, width, height, astc_format)

        # 保存图片
        output_filename = f"frame_{i:04d}.png"
        output_path = output_dir / output_filename
        img = Image.fromarray(img_array)
        img.save(output_path, OUTPUT_FORMAT)

        print(f"已保存: {output_filename}")

    print(f"\n解码完成！所有图片已保存到: {output_folder}")


def main():
    """
    主函数
    """
    parser = argparse.ArgumentParser(description='FFAB解码工具 - 将FFAB格式文件解码成PNG图片序列')
    parser.add_argument('input_file', help='输入的FFAB文件路径')
    parser.add_argument('output_folder', help='输出的图片文件夹路径')

    args = parser.parse_args()

    try:
        # 检查ASTC解码器是否可用
        if not check_astc_decoder():
            print("错误：未找到ASTC解码器 (astcenc)")
            print("请从 https://github.com/ARM-software/astc-encoder 下载并安装")
            sys.exit(1)

        # 检查输入文件是否存在
        if not os.path.exists(args.input_file):
            raise FileNotFoundError(f"输入文件不存在: {args.input_file}")

        # 解码FFAB文件
        print(f"正在解码文件: {args.input_file}")
        decode_ffab_file(args.input_file, args.output_folder)

    except Exception as e:
        print(f"错误: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()