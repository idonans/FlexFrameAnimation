# FFAB 编码器测试说明

这个目录包含了用于测试 FFAB 编码器功能的脚本。测试脚本可以将视频文件解码为图片序列，并使用不同的 ASTC 压缩格式将其编码为 FFAB 文件。

## 测试脚本功能

`test_ffab_encoder.py` 脚本实现以下功能：

1. 使用 ffmpeg 将 `frame_test.mp4` 视频解码为图片序列帧
   - 按照 30fps 帧率提取帧
   - 帧保存在 `tools/test/build/frame_test_frames` 目录下
   - 帧文件命名为 `frame_0001.png`, `frame_0002.png` 等格式

2. 调用 `ffab_encoder.py` 将图片序列编码为 FFAB 文件
   - 测试所有支持的 ASTC 格式（从 4x4 到 12x12）
   - 所有格式定义参考 `ffab_encoder.py` 文件中的 `ASTC_FORMAT_CODES`

3. FFAB 输出文件保存在 `tools/test/build/ffab` 目录下
   - 文件命名规则：`frame_test_4x4.ffab`, `frame_test_5x4.ffab` 等

## 环境要求

运行测试脚本需要以下工具：

1. **Python 3**
   - 需要安装以下 Python 包：
     - Pillow (PIL)
     - numpy

2. **ffmpeg**
   - 用于视频解码和帧提取
   - 请确保 ffmpeg 在系统 PATH 中可用

3. **astcenc**
   - ASTC 压缩编码器
   - 可从 https://github.com/ARM-software/astc-encoder 下载并安装
   - 请确保 astcenc 在系统 PATH 中可用

## 使用方法

1. 将测试视频文件重命名为 `frame_test.mp4` 并放置在 `tools/test` 目录下

2. 安装所需的 Python 包：
   ```bash
   pip install pillow numpy
   ```

3. 运行测试脚本：
   ```bash
   python test_ffab_encoder.py
   ```

## 测试结果

测试完成后，脚本将输出：
- 成功/失败的 ASTC 格式数量
- 失败的格式列表（如果有）
- FFAB 文件的输出目录路径

FFAB 文件将保存在 `tools/test/build/ffab` 目录中，每个文件对应一种 ASTC 压缩格式。

## 注意事项

1. 确保测试视频 `frame_test.mp4` 存在，否则测试将无法进行
2. 测试过程可能需要一定的时间，尤其是使用较高质量的 ASTC 压缩设置时
3. 确保有足够的磁盘空间存储提取的帧和生成的 FFAB 文件