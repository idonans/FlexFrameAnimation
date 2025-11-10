# FFAB 编码工具

这是FFAB文件格式的编码工具，用于将PNG或JPEG图片序列编码成FFAB格式文件。

## 功能特性

- 支持PNG和JPEG图片输入
- 支持三种ASTC压缩格式（4x4、6x6、8x8）
- 自动生成索引表
- 支持批量处理

## 使用方法

```bash
python ffab_encoder.py input_folder output_file.ffab [--format FORMAT]
```

参数说明：
- `input_folder`: 包含PNG或JPEG图片的输入文件夹
- `output_file.ffab`: 输出的FFAB文件路径
- `--format`: ASTC压缩格式（可选，默认为6x6）
  - 4x4: ASTC LDR 4x4
  - 6x6: ASTC LDR 6x6（默认）
  - 8x8: ASTC LDR 8x8

## 依赖

- Python 3.6+
- Pillow (PIL)
- numpy
- astcenc

## 安装依赖

```bash
pip install pillow numpy
```
注意：astcenc需要单独安装，不是Python包。相关下载信息参考 https://github.com/ARM-software/astc-encoder 。
windows 下有多个可运行的版本，选择合适的版本重命名为 astcenc.exe，并添加到 path 环境变量中。
例如：将 astcenc-avx2.exe 重命名为 astcenc.exe 并添加到 path 环境变量中。
