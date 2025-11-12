#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FFAB解码器测试脚本
测试ffab_decoder.py的基本功能和错误处理
"""

import unittest
import os
import sys
import tempfile
from pathlib import Path
import struct

# 添加父目录到路径，以便导入ffab_decoder
sys.path.append(str(Path(__file__).parent.parent))

try:
    from ffab_decoder import (
        FFAB_MAGIC,
        FFAB_VERSION_0x0001,
        ASTC_FORMAT_CODES,
        ASTC_CODE_TO_FORMAT,
        check_astc_decoder
    )
except ImportError as e:
    print(f"错误：无法导入ffab_decoder模块: {e}")
    sys.exit(1)


class TestFFABDecoder(unittest.TestCase):
    """FFAB解码器测试类"""

    def test_constants(self):
        """测试常量定义是否正确"""
        # 验证魔数和版本号
        self.assertEqual(FFAB_MAGIC, 0xFFAB)
        self.assertEqual(FFAB_VERSION_0x0001, 0x0001)

        # 验证ASTC格式映射
        self.assertIn('4x4', ASTC_FORMAT_CODES)
        self.assertEqual(ASTC_FORMAT_CODES['4x4'], 0x0001)
        self.assertIn('6x6', ASTC_FORMAT_CODES)
        self.assertEqual(ASTC_FORMAT_CODES['6x6'], 0x0005)

        # 验证反向映射
        self.assertEqual(ASTC_CODE_TO_FORMAT[0x0001], '4x4')
        self.assertEqual(ASTC_CODE_TO_FORMAT[0x0005], '6x6')

    def test_create_mock_ffab_file(self):
        """测试创建模拟FFAB文件的结构"""
        with tempfile.TemporaryDirectory() as temp_dir:
            mock_file = os.path.join(temp_dir, 'mock.ffab')
            
            # 创建一个简单的FFAB文件结构进行测试
            with open(mock_file, 'wb') as f:
                # 写入文件头（4字节）
                header = struct.pack('>HH', FFAB_MAGIC, FFAB_VERSION_0x0001)
                f.write(header)
                
                # 写入Meta信息区（8字节）
                image_count = 2
                width = 100
                height = 100
                astc_format_code = ASTC_FORMAT_CODES['6x6']
                meta = struct.pack('>HHHH', image_count, width, height, astc_format_code)
                f.write(meta)
                
                # 计算数据区起始位置
                data_start_offset = 4 + 8 + (image_count * 12)
                
                # 写入索引表
                # 第一帧
                frame1_offset = data_start_offset
                frame1_length = 100
                f.write(struct.pack('>QI', frame1_offset, frame1_length))
                
                # 第二帧
                frame2_offset = frame1_offset + frame1_length
                frame2_length = 150
                f.write(struct.pack('>QI', frame2_offset, frame2_length))
                
                # 写入一些模拟的图像数据
                f.write(b'\x00' * frame1_length)
                f.write(b'\x01' * frame2_length)
            
            # 验证文件是否创建成功
            self.assertTrue(os.path.exists(mock_file))
            self.assertTrue(os.path.getsize(mock_file) > 0)

    def test_astc_decoder_check(self):
        """测试ASTC解码器检查函数（此测试仅验证函数存在，不验证实际功能）"""
        # 检查函数是否存在且返回布尔值
        result = check_astc_decoder()
        self.assertIsInstance(result, bool)
        # 这里不检查具体返回值，因为测试环境可能没有安装astcenc


if __name__ == '__main__':
    unittest.main()