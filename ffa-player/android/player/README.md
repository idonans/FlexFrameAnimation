# player

## ffab
解析 `.ffab` 文件，提供读取 ffab 文件内容的能力，如：读取帧数量，宽高，读取指定帧内容等。

## Layer
定义动画层。一个动画播放视图可以包含多个层，每个层关联一个动画资源文件 `.ffab`。
Layer 的内容包括：
- 关联的动画资源文件 `.ffab`
- 位置信息（如在视图中的坐标）
- 动画播放参数（如播放速度、循环次数等）

## 技术方案

- OpenGL ES 3.2+
- 预编译 Shader 程序，SPIR-V 格式

## 附录

### 编译 Shader 程序

使用 glslc 编译器离线编译 Shader 程序，生成 SPIR-V 格式的二进制文件。
```
glslc -fshader-stage=vert vertex.glsl -o vert.spv
glslc -fshader-stage=frag fragment.glsl -o frag.spv
```
`GLSL (OpenGL Shading Language) 是用于编写GPU着色器的编程语言`
`SPIR-V是Khronos Group制定的二进制中间语言格式，作为高级着色器语言（如GLSL）与GPU驱动之间的桥梁`
