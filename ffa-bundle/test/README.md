# FFAB 编码与解码功能测试说明

这个目录包含了用于测试 FFAB 编码器和解码器功能的脚本。测试脚本可以将视频文件解码为图片序列，使用不同的 ASTC 压缩格式将其编码为 FFAB 文件，并验证解码后的帧与原始帧的一致性。

## 测试脚本功能

`test_ffab.py` 脚本实现以下功能：

1. 支持测试 resources 目录下的所有 mp4 视频文件
   - 对每个 mp4 文件进行独立的测试流程
   - 以视频文件名作为测试目录名

2. 测试流程包括：
   - 使用 ffmpeg 将视频解码为图片序列帧（帧率为 24fps）
   - 调用 `ffab_encoder.py` 将图片序列编码为不同 ASTC 格式的 FFAB 文件
   - 调用 `ffab_decoder.py` 将 FFAB 文件解码回图片序列帧
   - 对比解码后的帧与原始帧的数量和分辨率是否一致

3. 目录结构：
   - 输入帧：`build/[视频名]/input_frames/`
   - FFAB 文件：`build/[视频名]/output_ffab/`
   - 输出帧：`build/[视频名]/output_frames/[格式名]/`

4. 文件命名规则：
   - 帧文件：`frame_0001.png`, `frame_0002.png` 等
   - FFAB 文件：`output_4x4.ffab`, `output_5x4.ffab` 等

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

1. 将测试视频文件（mp4格式）放置在 `test/resources` 目录下

2. 安装所需的 Python 包：
   ```bash
   pip install pillow numpy
   ```

3. 运行测试脚本：
   ```bash
   python test_ffab.py
   ```

## 测试结果

测试完成后，脚本将输出：
- 总视频数
- 成功视频数
- 失败视频列表（如果有）
- 每个视频测试过程中的详细信息

所有测试文件将保存在 `build` 目录中，按视频名组织子目录。

## 注意事项

1. 确保测试视频文件（mp4格式）存在于 `test/resources` 目录下
2. 测试过程可能需要一定的时间，尤其是处理多个视频文件或使用较高质量的 ASTC 压缩设置时
3. 确保有足够的磁盘空间存储提取的帧、生成的 FFAB 文件和解码后的帧
4. 测试会重置 `build` 目录，请注意备份重要数据
5. 对比解码帧时，将检查帧数量是否一致，并验证每个目录下第一个图片的分辨率是否与原始帧一致