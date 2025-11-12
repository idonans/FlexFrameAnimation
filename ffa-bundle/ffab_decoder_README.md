# FFAB 解码工具使用说明

## 概述

`ffab_decoder.py` 是一个用于将FFAB格式文件解码成PNG图片序列的工具。它能够读取FFAB文件，解析文件结构，解码压缩的ASTC图像数据，并将每一帧图像保存为PNG文件。

## 功能特性

- 支持解码FFAB格式的动画文件
- 自动解析文件头信息，识别图片数量、尺寸和压缩格式
- 使用ASTC解码器将压缩数据转换回PNG图像
- 将解码后的所有帧保存到指定文件夹
- 提供详细的进度输出和错误处理

## 依赖要求

### 软件依赖

1. **Python 3.x** - 运行脚本的基础环境
2. **ASTC Encoder** - 用于解码ASTC压缩数据
   - 下载地址: https://github.com/ARM-software/astc-encoder
   - 确保 `astcenc` 命令可以在系统路径中访问

### Python 库依赖

1. **Pillow (PIL)** - 图像处理
2. **NumPy** - 数组处理

可以通过以下命令安装所需的Python库：

```bash
pip install pillow numpy
```

## 使用方法

### 基本用法

```bash
python ffab_decoder.py <input_file> <output_folder>
```

参数说明：
- `<input_file>` - 输入的FFAB文件路径
- `<output_folder>` - 输出的图片文件夹路径

### 示例

```bash
# 将animation.ffab解码到output_frames文件夹
python ffab_decoder.py animation.ffab output_frames
```

## 输出说明

- 解码后的图片将以 `frame_0000.png`, `frame_0001.png`, `frame_0002.png`... 的命名格式保存在指定的输出文件夹中
- 所有图片都将以PNG格式保存，并保持原始的RGBA颜色通道

## FFAB 文件格式说明

FFAB文件是一种用于存储动画帧序列的自定义格式，其结构如下：

1. **文件头 (4字节)**
   - 魔数 (2字节): 0xFFAB，用于标识文件类型
   - 版本号 (2字节): 当前支持版本为0x0001

2. **Meta信息区 (8字节)**
   - 图片数量 (2字节): 动画的总帧数
   - 图片宽度 (2字节): 每帧图像的宽度
   - 图片高度 (2字节): 每帧图像的高度
   - ASTC格式代码 (2字节): 压缩格式标识符

3. **索引表**
   - 每个索引条目为12字节
   - 每个条目包含：数据偏移量 (8字节) 和数据长度 (4字节)
   - 索引表总大小 = 图片数量 × 12字节

4. **图像数据区**
   - 包含所有帧的ASTC压缩数据
   - 每个帧的数据位置和长度由索引表指定

## 常见问题

### 1. 找不到ASTC编码器

如果运行脚本时出现错误："未找到ASTC解码器 (astcenc)"，请确保：
- 已从 https://github.com/ARM-software/astc-encoder 下载并安装了ASTC编码器
- `astcenc` 命令已添加到系统环境变量中，或者直接放在与脚本相同的目录下

### 2. 无法解码FFAB文件

如果出现错误："无效的FFAB文件: 魔数不匹配"，说明输入文件可能不是有效的FFAB格式文件。请确认文件来源和完整性。

### 3. 图片解码失败

如果解码过程中出现ASTC解码失败的错误，可能是由于：
- FFAB文件中的压缩数据损坏
- ASTC编码器版本不兼容
- 尝试使用其他版本的astcenc工具

## 注意事项

- 解码过程会临时创建一些文件，这些文件会在处理完成后自动清理
- 解码后的PNG文件可能会比原始FFAB文件大很多，因为PNG是未压缩的格式
- 确保输出文件夹有足够的空间存储所有解码后的图片

## 许可证

与FlexFrameAnimation项目保持一致的许可证。

## 相关工具

- `ffab_encoder.py` - 用于将PNG/JPEG图片序列编码成FFAB格式的工具