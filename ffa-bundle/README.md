# FFAB 文件格式

FFA Bundle (.ffab) 是一种二进制文件格式，用于存储序列帧图片资源。该格式设计简洁高效，支持快速随机访问任意帧，特别适合动画播放场景。

## 版本历史

| 版本号 | 发布日期 | 主要变化 |
|--------|----------|----------|
| 0x0001   |  2025-11-10  | 初始版本，定义文件头与内容区域 |

## 概述

FFAB 格式专为高性能帧动画设计，具有以下特点：

- 二进制结构，解析速度快
- 支持快速随机访问任意帧
- 使用大端序（Big-endian）字节序，与PNG、JPEG等主流图像格式保持一致

## 字节序规范

FFAB 文件格式采用**大端序（Big-endian）**字节序，所有多字节整数（如uint16、uint32、uint64）都按照大端序存储。

大端序是指数据的高位字节存储在内存的低地址，低位字节存储在内存的高地址。这与网络字节序相同，也是PNG、JPEG等主流图像格式使用的字节序。

### 字节序示例

以数值 `0xFFAB` 为例：
- 大端序存储：`0xFF 0xAB`
- 小端序存储：`0xAB 0xFF`

## 文件结构

FFAB 文件由两部分组成：

1. **文件头 (Header)**：包含文件标识和版本信息
2. **内容区域**：根据版本号不同，内容区域结构也不同

```
+---------------------+
|   文件头 (4字节)      |
+---------------------+
|   内容区域 (可变长度)  |
+---------------------+
```

## 文件头格式

文件头固定为 4 字节，包含以下字段（所有多字节值使用大端序）：

| 偏移量 | 长度 | 类型 | 描述 |
|--------|------|------|------|
| 0      | 2    | uint16 | 魔数 (0xFFAB) - 大端序 |
| 2      | 2    | uint16 | 版本号 (最大 0xFFFF) - 大端序 |

### 魔数

FFAB 文件的魔数为 `0xFFAB`，使用大端序存储为 `0xFF 0xAB`，用于快速识别文件格式。

### 版本号

当前版本号为 `0x0001`，用于未来格式升级。版本号使用2字节存储，最大支持 0xFFFF。

### 版本兼容性处理

FFAB 格式设计遵循以下版本兼容性原则：

1. **文件头固定不变**：所有版本共享相同的文件头结构（4字节）
2. **版本特定处理**：读取文件时，根据版本号选择对应的解析方式
3. **向前兼容**：新版本应能读取旧版本文件，旧版本可能无法读取新版本文件

### 版本1 (0x0001) 定义

版本1的内容区域定义了以下固定结构：
1. **Meta 信息区**：固定8字节
2. **索引表**：每项固定12字节
3. **图片数据区**：顺序存储压缩图片数据

```
+-----------------------------+
|   文件头 (4字节)              |
+-----------------------------+
|   Meta 信息区 (8字节)         |
+-----------------------------+
|   索引表 (图片总数*12字节)     |
+-----------------------------+
|   图片数据区 (可变长度)        |
+-----------------------------+
```


#### Meta 信息区格式

Meta 信息区固定为 8 字节，包含以下字段（所有多字节值使用大端序）：

| 偏移量 | 长度 | 类型 | 描述 |
|--------|------|------|------|
| 0      | 2    | uint16 | 图片总数 (最大 0xFFFF) - 大端序 |
| 2      | 2    | uint16 | 图片宽度 (最大 0xFFFF) - 大端序 |
| 4      | 2    | uint16 | 图片高度 (最大 0xFFFF) - 大端序 |
| 6      | 2    | uint16 | 图片压缩格式 (最大 0xFFFF) - 大端序 |


#### 图片总数

Meta 信息区中包含的图片总数，最大支持 65535 张 (0xFFFF)。


#### 图片宽度和高度

所有图片的宽高相同，宽度和高度最大支持 65535 像素 (0xFFFF)。


#### 图片压缩格式

指定图片数据的压缩格式：

| 值 | 格式 | 描述 |
|----|------|------|
| 0x0001 | ASTC ldr 4x4 | ASTC ldr 4x4 数据格式 |
| 0x0002 | ASTC ldr 5x4 | ASTC ldr 5x4 数据格式 |
| 0x0003 | ASTC ldr 5x5 | ASTC ldr 5x5 数据格式 |
| 0x0004 | ASTC ldr 6x5 | ASTC ldr 6x5 数据格式 |
| 0x0005 | ASTC ldr 6x6 | ASTC ldr 6x6 数据格式 |
| 0x0006 | ASTC ldr 8x5 | ASTC ldr 8x5 数据格式 |
| 0x0007 | ASTC ldr 8x6 | ASTC ldr 8x6 数据格式 |
| 0x0008 | ASTC ldr 8x8 | ASTC ldr 8x8 数据格式 |
| 0x0009 | ASTC ldr 10x5 | ASTC ldr 10x5 数据格式 |
| 0x000A | ASTC ldr 10x6 | ASTC ldr 10x6 数据格式 |
| 0x000B | ASTC ldr 10x8 | ASTC ldr 10x8 数据格式 |
| 0x000C | ASTC ldr 10x10 | ASTC ldr 10x10 数据格式 |
| 0x000D | ASTC ldr 12x10 | ASTC ldr 12x10 数据格式 |
| 0x000E | ASTC ldr 12x12 | ASTC ldr 12x12 数据格式 |


#### 索引表格式

索引表是一个结构体数组，每个元素对应一张图片，包含该图片在文件中的位置和长度信息。


#### 索引项结构

每个索引项固定为 12 字节（所有多字节值使用大端序）：

| 偏移量 | 长度 | 类型 | 描述 |
|--------|------|------|------|
| 0      | 8    | uint64 | 图片数据偏移量 (相对于文件开始) - 大端序 |
| 8      | 4    | uint32 | 图片数据长度 (字节) - 大端序 |


#### 索引表总长度

索引表总长度 = 图片总数 × 12 字节


#### 图片数据区

图片数据区按顺序存储所有压缩后的图片数据。每张图片的数据可以独立解压，不影响其他图片。

```
+------------------+
|   图片1数据       |
+------------------+
|   图片2数据       |
+------------------+
|   ...             |
+------------------+
|   图片N数据       |
+------------------+
```

注意：当图片压缩格式为 ASTC 时，图片数据区中存储的每一张图片数据都是压缩后的 ASTC 格式数据，但是去除了前 16 字节的 ASTC header。astc header 的内容可以通过 Meta 信息完整的计算出来。

## 文件扩展名

FFAB 文件使用 `.ffab` 作为扩展名。
MIME 类型为 `application/x-ffa-bundle`。


### 版本处理流程

解析器处理FFAB文件时应遵循以下流程：

1. 读取文件头（4字节），验证魔数并获取版本号
   - 魔数 `0xFFAB` 应以大端序形式 `0xFF 0xAB` 出现在文件开头
   - 版本号 `0x0001` 应以大端序形式 `0x00 0x01` 出现在魔数之后
2. 根据版本号选择对应的解析方式
3. 解析所有多字节值时必须使用大端序
   - 文件头中的魔数和版本号
   - Meta信息区中的图片总数、宽度、高度和图片压缩格式
   - 索引表中的偏移量和数据长度
4. 如果遇到不支持的版本号，解析器应提供明确的错误信息


### 版本升级注意事项

- 版本升级不能改变文件头结构
- 新版本应保持对旧版本文件的读取能力
- 重大变更应考虑使用主版本号升级方式


## 工具支持

FFAB 格式提供以下工具支持：

1. **编码工具**：将图片序列编码为 FFAB 文件
2. **解码工具**：将 FFAB 文件解码为图片序列
3. **分析工具**：分析 FFAB 文件结构和内容


#### 依赖安装

```bash
pip install pillow numpy
```

```
安装 astcenc，参考地址：[astcenc](https://github.com/ARM-software/astc-encoder)。
注意：astcenc 是一个命令行工具，需要添加到系统 PATH 中。以 windows 版本为例，astc-encoder 中提供了多个版本的 astcenc-XXX.exe，可以选择其中一个重命名为 astcenc.exe 并添加到 PATH 中。
```

## ffab_encoder.py
这是FFAB文件格式的编码工具，用于将PNG或JPEG图片序列编码成FFAB格式文件。

### 使用方法

#### 基本语法
```bash
python ffab_encoder.py <input_folder> <output_file> [options]
```

#### 参数说明
- `input_folder`: 包含PNG或JPEG图片的输入文件夹路径
- `output_file`: 输出的FFAB文件路径
- `--format`: ASTC压缩格式，可选值：4x4, 5x4, 5x5, 6x5, 6x6, 8x5, 8x6, 8x8, 10x5, 10x6, 10x8, 10x10, 12x10, 12x12（默认：6x6），压缩格式值越大，压缩率越高，但是细节还原效果越差。
- `--quality`: ASTC压缩质量，范围0.0-100.0（默认：50），质量参数影响压缩速度，不影响最终生成的文件大小。质量值越大，则压缩速度越慢，细节还原效果越好。

#### 使用示例

1. 基本使用（使用默认6x6格式和质量50）：
```bash
python ffab_encoder.py ./frames ./output.ffab
```

2. 指定ASTC格式和质量：
```bash
python ffab_encoder.py ./frames ./output.ffab --format 8x8 --quality 75
```

#### 注意事项

1. 输入文件夹中的所有图片必须具有相同的尺寸
2. 图片将按照文件名字母顺序进行处理
3. 支持的图片格式：PNG, JPG, JPEG
4. 所有图片将被转换为RGBA格式以保持透明度
5. 需要安装astcenc工具并添加到系统PATH中


## ffab_decoder.py
这是FFAB文件格式的解码工具，用于将FFAB文件解码为图片序列。


## 二维静态图片压缩格式对比

| 格式 | 压缩方式 | 透明支持 | 典型压缩率 | GPU直接支持 | 适用场景 |
|------|----------|----------|------------|------------|----------|
| PNG | 无损压缩 | 支持 | 2:1 - 5:1 | 否 | 需要精确保留原始内容的图像 |
| JPEG | 有损压缩 | 不支持 | 10:1 - 20:1 | 否 | 照片等连续色调图像 |
| ASTC ldr 4×4 | 有损压缩 | 支持 | 3:1 | 是 | 高质量要求的动画帧，细节丰富的UI元素 |
| ASTC ldr 5×4 | 有损压缩 | 支持 | 3.75:1 | 是 | 质量与压缩率平衡的UI元素 |
| ASTC ldr 5×5 | 有损压缩 | 支持 | 4.75:1 | 是 | 中等质量要求的动画帧 |
| ASTC ldr 6×5 | 有损压缩 | 支持 | 5.7:1 | 是 | 质量与压缩率平衡的动画场景 |
| ASTC ldr 6×6 | 有损压缩 | 支持 | 6.7:1 | 是 | 大多数动画场景（推荐） |
| ASTC ldr 8×5 | 有损压缩 | 支持 | 7.5:1 | 是 | 一般动画背景 |
| ASTC ldr 8×6 | 有损压缩 | 支持 | 8.7:1 | 是 | 一般动画背景 |
| ASTC ldr 8×8 | 有损压缩 | 支持 | 12:1 | 是 | 背景等对细节要求不高的内容 |
| ASTC ldr 10×5 | 有损压缩 | 支持 | 9.6:1 | 是 | 次要背景元素 |
| ASTC ldr 10×6 | 有损压缩 | 支持 | 11.2:1 | 是 | 次要背景元素 |
| ASTC ldr 10×8 | 有损压缩 | 支持 | 14.4:1 | 是 | 远距离背景或模糊效果 |
| ASTC ldr 10×10 | 有损压缩 | 支持 | 19.2:1 | 是 | 远距离背景或模糊效果 |
| ASTC ldr 12×10 | 有损压缩 | 支持 | 20:1 | 是 | 非常远的背景或装饰性元素 |
| ASTC ldr 12×12 | 有损压缩 | 支持 | 25.6:1 | 是 | 极简风格背景或纹理 |


## 附录

### astc header
使用 astcenc 工具生成 `.astc` 格式文件，文件的前 16 字节为 ASTC header。
astcenc 工具可以从 [ARM-software/astc-encoder](https://github.com/ARM-software/astc-encoder) 项目中获取。

摘录 ARM-software/astc-encoder 项目中关于 `.astc` 文件格式的 header 说明
`.astc` header 格式定义如下：
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
};
```

`.astc` header 魔数:
```
magic[0] = 0x13;
magic[1] = 0xAB;
magic[2] = 0xA1;
magic[3] = 0x5C;
```

`.astc` header 块分辨率:
block_x, block_y, block_z 字段存储 ASTC 块的宽度、高度、深度（以像素为单位）。
对于 2D 图像，Z 维度必须设置为 1。

`.astc` header 图片分辨率:
dim_x, dim_y, dim_z 字段存储 ASTC 图像的宽度、高度、深度（以像素为单位）。
对于 2D 图像，Z 维度必须设置为 1。

注意：图片数据不是必须是压缩块大小的整数倍；压缩数据可能包含在解压时被丢弃的填充字节。

图片分辨率计算方式为：
```
图片宽度 = dim_x[0] + (dim_x[1] << 8) + (dim_x[2] << 16);
图片高度 = dim_y[0] + (dim_y[1] << 8) + (dim_y[2] << 16);
图片深度 = dim_z[0] + (dim_z[1] << 8) + (dim_z[2] << 16);
```

当 ffab 的图片压缩格式为 ASTC 时，图片数据区中存储的每一帧 astc 格式数据都没有包含 16 个字节的 astc header。
当使用 astcenc 解码图片帧时，需要先将 ASTC header 与图片数据合并，然后再调用 astcenc 解码。
参数上述 astc header 的格式定义，结合 Meta 信息区中的图片宽度、高度、块分辨率等参数，完整的计算出 astc header 的内容。
对于 ffab 的二位图片，计算方式为：
```
astc_header.magic = [0x13, 0xAB, 0xA1, 0x5C]
astc_header.block_x = 块分辨率宽度
astc_header.block_y = 块分辨率高度
astc_header.block_z = 1
astc_header.dim_x = [图片宽度 & 0xFF, (图片宽度 >> 8) & 0xFF, (图片宽度 >> 16) & 0xFF]
astc_header.dim_y = [图片高度 & 0xFF, (图片高度 >> 8) & 0xFF, (图片高度 >> 16) & 0xFF]
astc_header.dim_z = 1
```

astc 压缩格式与 block_x, block_y, block_z 字段的关系：
| 格式 | block_x | block_y | block_z |
|------|---------|---------|---------|
| ASTC ldr 4×4 | 4 | 4 | 1 |
| ASTC ldr 5×4 | 5 | 4 | 1 |
| ASTC ldr 5×5 | 5 | 5 | 1 |
| ASTC ldr 6×5 | 6 | 5 | 1 |
| ASTC ldr 6×6 | 6 | 6 | 1 |
| ASTC ldr 8×5 | 8 | 5 | 1 |
| ASTC ldr 8×6 | 8 | 6 | 1 |
| ASTC ldr 8×8 | 8 | 8 | 1 |
| ASTC ldr 10×5 | 10 | 5 | 1 |
| ASTC ldr 10×6 | 10 | 6 | 1 |
| ASTC ldr 10×8 | 10 | 8 | 1 |
| ASTC ldr 10×10 | 10 | 10 | 1 |
| ASTC ldr 12×10 | 12 | 10 | 1 |
| ASTC ldr 12×12 | 12 | 12 | 1 |

更多 astc 文件格式信息，参考官方文档 https://github.com/ARM-software/astc-encoder/blob/main/Docs/FileFormat.md

### astcenc 工具
astcenc 使用参数说明
```
QUICK REFERENCE

       To compress an image use:
           astcenc {-cl|-cs|-ch|-cH} <in> <out> <blockdim> <quality> [options]

       To decompress an image use:
           astcenc {-dl|-ds|-dh|-dH} <in> <out>

       To perform a quality test use:
           astcenc {-tl|-ts|-th|-tH} <in> <out> <blockdim> <quality> [options]

       Mode -*l = linear LDR, -*s = sRGB LDR, -*h = HDR RGB/LDR A, -*H = HDR.
       Quality = -fastest/-fast/-medium/-thorough/-verythorough/-exhaustive/a float [0-100].
```

astcenc 工具帮助命令
```
astcenc -help
```
