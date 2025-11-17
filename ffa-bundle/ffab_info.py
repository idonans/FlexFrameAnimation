#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFAB 文件信息查看工具
读取并显示FFAB格式文件的基本信息
"""

import os
import sys
import struct
import argparse
from typing import Dict, Any

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


def read_ffab_header(file_path: str) -> Dict[str, Any]:
    """
    读取FFAB文件头，校验魔数和版本号

    Args:
        file_path: FFAB文件路径

    Returns:
        包含魔数和版本号的字典

    Raises:
        ValueError: 如果文件格式无效或版本不支持
    """
    with open(file_path, 'rb') as f:
        # 读取文件头（4字节）: 魔数(2字节) + 版本号(2字节)
        header = f.read(4)
        magic, version = struct.unpack('>HH', header)

        # 验证魔数
        if magic != FFAB_MAGIC:
            raise ValueError(f"无效的FFAB文件: 魔数不匹配 (期望: 0x{FFAB_MAGIC:04X}, 实际: 0x{magic:04X})")

        # 验证版本
        if version != FFAB_VERSION_0x0001:
            raise ValueError(f"不支持的FFAB文件版本: 0x{version:04X}")

        return {
            'magic': magic,
            'version': version,
            'version_hex': f"0x{version:04X}"
        }


def read_ffab_meta(file_path: str) -> Dict[str, Any]:
    """
    读取FFAB文件的Meta信息区

    Args:
        file_path: FFAB文件路径

    Returns:
        包含Meta信息的字典
    """
    with open(file_path, 'rb') as f:
        # 跳过文件头（4字节）
        f.seek(4)
        # 读取Meta信息区（8字节）: 图片数量(2字节) + 宽度(2字节) + 高度(2字节) + ASTC格式代码(2字节)
        meta = f.read(8)
        image_count, width, height, astc_format_code = struct.unpack('>HHHH', meta)

        # 获取ASTC格式名称
        astc_format = ASTC_CODE_TO_FORMAT.get(astc_format_code, f"未知(0x{astc_format_code:04X})")

        return {
            'image_count': image_count,
            'width': width,
            'height': height,
            'resolution': f"{width}x{height}",
            'astc_format_code': astc_format_code,
            'astc_format': astc_format,
            'astc_format_hex': f"0x{astc_format_code:04X}"
        }


def read_ffab_index_table(file_path: str, image_count: int) -> Dict[str, Any]:
    """
    读取FFAB文件的索引表

    Args:
        file_path: FFAB文件路径
        image_count: 图片数量

    Returns:
        包含索引表信息的字典
    """
    index_entries = []
    total_compressed_size = 0
    min_data_size = float('inf')
    max_data_size = 0

    # 计算索引表起始位置
    # 文件头(4字节) + Meta信息区(8字节)
    index_start_offset = 4 + 8

    with open(file_path, 'rb') as f:
        # 移动到索引表位置
        f.seek(index_start_offset)

        # 读取索引表
        for i in range(image_count):
            # 每个索引条目12字节: 偏移量(8字节) + 数据长度(4字节)
            entry_data = f.read(12)
            offset, data_length = struct.unpack('>QI', entry_data)
            index_entries.append({
                'frame': i,
                'offset': offset,
                'data_length': data_length
            })

            # 统计信息
            total_compressed_size += data_length
            min_data_size = min(min_data_size, data_length)
            max_data_size = max(max_data_size, data_length)

    # 计算平均数据大小
    avg_data_size = total_compressed_size / image_count if image_count > 0 else 0

    return {
        'index_start_offset': index_start_offset,
        'entries': index_entries,
        'total_compressed_size': total_compressed_size,
        'min_data_size': min_data_size,
        'max_data_size': max_data_size,
        'avg_data_size': avg_data_size
    }


def get_file_info(file_path: str) -> Dict[str, Any]:
    """
    获取FFAB文件的完整信息

    Args:
        file_path: FFAB文件路径

    Returns:
        包含所有文件信息的字典
    """
    # 检查文件是否存在
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"文件不存在: {file_path}")

    # 获取文件大小
    file_size = os.path.getsize(file_path)

    # 读取文件头
    header_info = read_ffab_header(file_path)

    # 读取Meta信息
    meta_info = read_ffab_meta(file_path)

    # 读取索引表
    index_info = read_ffab_index_table(file_path, meta_info['image_count'])

    # 计算数据区起始位置
    data_start_offset = 4 + 8 + (meta_info['image_count'] * 12)

    # 计算压缩比
    uncompressed_size = meta_info['width'] * meta_info['height'] * 4 * meta_info['image_count']  # RGBA格式
    compression_ratio = uncompressed_size / index_info['total_compressed_size'] if index_info['total_compressed_size'] > 0 else 0

    return {
        'file_path': file_path,
        'file_size': file_size,
        'header': header_info,
        'meta': meta_info,
        'index': index_info,
        'data_start_offset': data_start_offset,
        'uncompressed_size': uncompressed_size,
        'compression_ratio': compression_ratio
    }


def print_file_info(info: Dict[str, Any], verbose: bool = False) -> None:
    """
    打印FFAB文件信息

    Args:
        info: 文件信息字典
        verbose: 是否显示详细信息
    """
    print("=" * 60)
    print(f"FFAB文件信息: {info['file_path']}")
    print("=" * 60)

    # 文件基本信息
    print(f"文件大小: {info['file_size']:,} 字节 ({info['file_size'] / 1024:.2f} KB)")

    # 文件头信息
    header = info['header']
    print(f"魔数: 0x{header['magic']:04X} {'✓' if header['magic'] == FFAB_MAGIC else '✗'}")
    print(f"版本: {header['version_hex']} {'✓' if header['version'] == FFAB_VERSION_0x0001 else '✗'}")

    # Meta信息
    meta = info['meta']
    print(f"图片数量: {meta['image_count']}")
    print(f"图片尺寸: {meta['resolution']}")
    print(f"ASTC格式: {meta['astc_format']} ({meta['astc_format_hex']})")

    # 索引表信息
    index = info['index']
    print(f"索引表位置: 偏移量 {index['index_start_offset']}")
    print(f"数据区位置: 偏移量 {info['data_start_offset']}")
    print(f"压缩数据总大小: {index['total_compressed_size']:,} 字节 ({index['total_compressed_size'] / 1024:.2f} KB)")

    # 压缩统计
    print(f"压缩比: {info['compression_ratio']:.2f}:1")
    print(f"每帧平均大小: {index['avg_data_size']:.2f} 字节")
    print(f"最小帧大小: {index['min_data_size']} 字节")
    print(f"最大帧大小: {index['max_data_size']} 字节")

    # 详细信息
    if verbose:
        print("\n" + "-" * 60)
        print("详细信息:")
        print("-" * 60)
        
        print(f"未压缩总大小: {info['uncompressed_size']:,} 字节 ({info['uncompressed_size'] / 1024 / 1024:.2f} MB)")
        print(f"压缩后总大小: {index['total_compressed_size']:,} 字节 ({index['total_compressed_size'] / 1024 / 1024:.2f} MB)")
        print(f"空间节省: {info['uncompressed_size'] - index['total_compressed_size']:,} 字节 ({(1 - 1/info['compression_ratio'])*100:.1f}%)")

        print("\n索引表详情:")
        print(f"{'帧号':<8} {'偏移量':<12} {'数据长度':<12} {'数据大小'}")
        print("-" * 50)
        for entry in index['entries']:
            offset = entry['offset']
            data_length = entry['data_length']
            size_str = f"{data_length / 1024:.2f} KB" if data_length > 1024 else f"{data_length} B"
            print(f"{entry['frame']:<8} {offset:<12} {data_length:<12} {size_str}")

    print("=" * 60)


def main():
    """
    主函数
    """
    parser = argparse.ArgumentParser(description='FFAB文件信息查看工具')
    parser.add_argument('input_file', help='输入的FFAB文件路径')
    parser.add_argument('-v', '--verbose', action='store_true', help='显示详细信息')

    args = parser.parse_args()

    try:
        # 获取文件信息
        info = get_file_info(args.input_file)

        # 打印文件信息
        print_file_info(info, args.verbose)

    except Exception as e:
        print(f"错误: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()