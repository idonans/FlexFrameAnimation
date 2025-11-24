# player

## ffab
解析 `.ffab` 文件，提供读取 ffab 文件内容的能力，如：读取帧数量，宽高，读取指定帧内容等。

## FlexFrameLayer
定义动画层。一个动画播放视图可以包含多个层，每个层关联一个动画资源文件 `.ffab`。
FlexFrameLayer 的内容包括：
- 关联的动画资源文件 `.ffab`
- 位置信息（如在视图中的坐标）
- 动画播放参数（如播放速度、循环次数等）
- 动画播放状态 PlayState

每一个 FlexFrameLayer 都可以关联一个单独的 PlayState, 如果关联的 PlayState 为 null, 则会\
使用所属的 FlexFrameLayerGroup 上关联的 PlayState。

## FlexFrameLayerGroup
内部包含一组有序的 FlexFrameLayer 或 FlexFrameLayerGroup。
FlexFrameLayerGroup 可以嵌套，类似 Android View 树。

## FlexFrameAnimationView
动画视图，负责渲染一个 FlexFrameLayer 或者 一个 FlexFrameLayerGroup。\
当需要将多个动画层（FlexFrameLayer）渲染到同一个动画视图中时，需要将这\
些 FlexFrameLayer 组织成一个 FlexFrameLayerGroup。FlexFrameLayerGroup 可以\
嵌套，类似 Android View 树。
动画视图的渲染是在独立的动画渲染线程中运行，所有动画视图共享同一个动画渲染线程。
动画渲染线程支持提前初始化，即：动画渲染线程的运行不依赖动画视图的创建。
动画渲染线程称之为 FlexFrameRenderThread, 同时，这也是一个 OpenGL 线程。

## 动画时间
所有动画时间相关的值都基于 SystemClock.uptimeMillis() 计算。此值不受系统休眠时间影响，不受系统显示时间变更的影响。

## 技术方案

- OpenGL ES 3.2+
- 预编译 Shader 程序，SPIR-V 格式

### 线程模型

- FlexFrameRenderThread 动画渲染线程，这也是一个 OpenGL 线程。它负责所\
有 FlexFrameAnimationView 的内容渲染。包括在必要时解析动画资源。
- FlexFrameAsyncResourceThread 动画异步资源加载线程，这是一个后台线程，负责处理需要异步加载的\
动画资源，如：提前解析动画资源，预加载下一帧动画内容，缓存计算等。

## 附录

### 编译 Shader 程序

使用 glslc 编译器离线编译 Shader 程序，生成 SPIR-V 格式的二进制文件。
```
glslc -fshader-stage=vert vertex.glsl -o vert.spv
glslc -fshader-stage=frag fragment.glsl -o frag.spv
```
`GLSL` (OpenGL Shading Language) 是用于编写 GPU 着色器的编程语言
`SPIR-V` 是 Khronos Group 制定的二进制中间语言格式，作为高级着色器语言（如GLSL）与GPU驱动之间的桥梁
