package io.github.idonans.ffa.player.ffab

import android.content.Context
import io.github.idonans.ffa.player.util.LogUtil
import io.github.idonans.ffa.player.util.measureTimeIfDebug
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * 解析 `.ffab` 文件
 *
 * FFAB 文件格式 (版本 1):
 * 1. 文件头(4字节): FFAB_MAGIC (0xFFAB) + 版本号(0x0001)
 * 2. Meta信息区(8字节): 图片数量(2字节) + 图片宽度(2字节) + 图片高度(2字节) + ASTC格式代码(2字节)
 * 3. 索引表(每项12字节): 每个索引项包含数据偏移量(8字节) + 数据长度(4字节)
 * 4. 数据区: 连续存储所有图片的ASTC压缩数据，不包括 astc header (16字节)
 *
 * `.ffab` 文件存放在 res/raw 目录下
 *
 * @param ffabRawResId .ffab 文件的资源 id
 */
class Ffab(private val ffabRawResId: Int) {

    companion object {
        // FFAB 文件格式常量
        private const val FFAB_MAGIC = 0xFFAB
        private const val FFAB_VERSION_0X0001 = 0x0001

        // 压缩格式代码到 astc block 的映射
        private val CODE_TO_ASTC_BLOCK = mapOf(
            0x0001 to (4 to 4),
            0x0002 to (5 to 4),
            0x0003 to (5 to 5),
            0x0004 to (6 to 5),
            0x0005 to (6 to 6),
            0x0006 to (8 to 5),
            0x0007 to (8 to 6),
            0x0008 to (8 to 8),
            0x0009 to (10 to 5),
            0x000A to (10 to 6),
            0x000B to (10 to 8),
            0x000C to (10 to 10),
            0x000D to (12 to 10),
            0x000E to (12 to 12),
        )

        /**
         * key 为读目标 raw `.ffab` 内容时对应的在 apk 文件中的偏移量。当发生白天黑夜模式切换需要匹配
         * 不同的 `.ffab` 文件时，此偏移量会发生变化。通过对比此偏移量是否相同来替代对比 Configuration 是否相同。
         */
        private val mFfabInfoMap = mutableMapOf<Long, FfabInfo>()

        /**
         * 从 buffer 中读取接下来的 2 个字节，并解析为 16 位无符号整数
         * @return 解析出的整数 (0-0xFFFF)
         */
        private fun ByteBuffer.readUint16(): UShort {
            return this.short.toUShort()
        }

        /**
         * 从 buffer 中读取接下来的 4 个字节，并解析为 32 位无符号整数
         * @return 解析出的整数 (0-0xFFFFFFFF)
         */
        private fun ByteBuffer.readUint32(): UInt {
            return this.int.toUInt()
        }

        /**
         * 从 buffer 中读取接下来的 8 个字节，并解析为 64 位无符号整数
         * @return 解析出的整数 (0-0xFFFFFFFFFFFFFFFF)
         */
        private fun ByteBuffer.readUint64(): ULong {
            return this.long.toULong()
        }

        private fun FileChannel.mapWithOrder(positionOffset: Long, size: Long): MappedByteBuffer {
            val buffer = this.map(
                FileChannel.MapMode.READ_ONLY,
                this.position() + positionOffset,
                size,
            )

            // .ffab 文件中的多字节整数都是按照大端序存储
            buffer.order(ByteOrder.BIG_ENDIAN)
            return buffer
        }

        /**
         * 帧数据索引项
         */
        data class FrameIndex(
            val offset: Long,
            val dataLength: Long,
        )

        /**
         * FFAB 文件信息
         */
        data class FfabInfo(
            val version: Int,
            val imageCount: Int,
            val width: Int,
            val height: Int,
            val format: Pair<Int, Int>,
            val formatCode: Int,
        ) {
            private lateinit var mFrameIndexList: List<FrameIndex>
            private lateinit var mFrameDataBuffer: ByteBuffer

            constructor(
                version: Int,
                imageCount: Int,
                width: Int,
                height: Int,
                format: Pair<Int, Int>,
                formatCode: Int,
                mFrameIndexList: List<FrameIndex>,
                mFrameDataBuffer: ByteBuffer,
            ) : this(version, imageCount, width, height, format, formatCode) {
                this.mFrameIndexList = mFrameIndexList
                this.mFrameDataBuffer = mFrameDataBuffer
            }

            /**
             * 序列帧的帧数
             */
            fun frameCount() = this.mFrameIndexList.size

            /**
             * 获取指定帧的内容
             */
            fun frameData(index: Int): ByteBuffer {
                val frameIndex = this.mFrameIndexList[index]
                // 创建原始缓冲区的副本，以避免影响原始缓冲区的状态
                val buffer = this.mFrameDataBuffer.duplicate()
                // 设置位置和限制来创建一个"切片"
                buffer.position(frameIndex.offset.toInt())
                buffer.limit(frameIndex.offset.toInt() + frameIndex.dataLength.toInt())
                // 创建切片
                return buffer.slice()
            }

        }

        /**
         * 从指定 context 上下文中解析目标 raw `.ffab` 文件内容，内部会充分复用缓存，仅在必要时才重新解析文件内容
         */
        fun getOrCreateFfabInfo(ffabRawResId: Int, context: Context): FfabInfo {
            synchronized(mFfabInfoMap) {
                return prepareL(ffabRawResId, context)
            }
        }

        /**
         * 按需从 ffabRawResId 解析 ffab 文件内容。
         * 如果没有解析过，或者 Configuration 发生了变化，则重新解析 ffab 文件
         */
        @OptIn(ExperimentalStdlibApi::class)
        private fun prepareL(ffabRawResId: Int, context: Context): FfabInfo {
            val ffabInfo: FfabInfo
            measureTimeIfDebug(
                { "prepareInner ${ffabRawResId.toHexString()}" },
            ) {
                ffabInfo = prepareInner(ffabRawResId, context)
            }
            return ffabInfo
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun prepareInner(ffabRawResId: Int, context: Context): FfabInfo {
            context.resources.openRawResourceFd(ffabRawResId).use { afd ->
                val resourcesOffset = afd.startOffset
                val cache = mFfabInfoMap[resourcesOffset]
                if (cache != null) {
                    // 命中缓存
                    return cache
                }

                LogUtil.d {
                    "prepareInner ffabRawResId:${ffabRawResId.toHexString()}," +
                            //
                            " resourcesOffset:$resourcesOffset"
                }

                afd.createInputStream().use { stream ->
                    stream.channel.use { channel ->
                        val newFfabInfo = prepareInner(resourcesOffset, channel)
                        // 写入缓存
                        mFfabInfoMap[resourcesOffset] = newFfabInfo
                        return newFfabInfo
                    }
                }
            }
        }

        private fun prepareInner(resourcesOffset: Long, channel: FileChannel): FfabInfo {
            require(resourcesOffset == channel.position()) {
                "prepareInner resourcesOffset:$resourcesOffset," +
                        //
                        " channel.position:${channel.position()}"
            }

            // 一次性读取文件头（4字节）与 Meta 信息 (8字节)
            val headerMetaBuffer = channel.mapWithOrder(
                positionOffset = 0,
                size = 12,
            )

            // 文件头 (4字节)：FFAB_MAGIC (0xFFAB) + 版本号(0x0001)
            // 验证魔数和版本号
            val magic = headerMetaBuffer.readUint16().toInt()
            val version = headerMetaBuffer.readUint16().toInt()

            require(magic == FFAB_MAGIC) {
                "Invalid FFAB file format, magic: $magic, expected: $FFAB_MAGIC"
            }

            require(version == FFAB_VERSION_0X0001) {
                // 当前仅支持解析版本1(0x0001)
                "Invalid FFAB file format, version: $version, expected: $FFAB_VERSION_0X0001"
            }

            // Meta信息区 (8字节): 图片数量(2字节) + 图片宽度(2字节) + 图片高度(2字节) + ASTC格式代码(2字节)
            val imageCount = headerMetaBuffer.readUint16().toInt()
            val width = headerMetaBuffer.readUint16().toInt()
            val height = headerMetaBuffer.readUint16().toInt()
            val formatCode = headerMetaBuffer.readUint16().toInt()

            // 获取ASTC块大小
            val format = CODE_TO_ASTC_BLOCK[formatCode]
            requireNotNull(format) {
                "Invalid format code: $formatCode"
            }

            // 读取索引表 (每项12字节)
            val frameIndexList = ArrayList<FrameIndex>(imageCount)
            val indexTableSize = imageCount * 12
            val indexTableBuffer = channel.mapWithOrder(
                positionOffset = 12L, // 索引表偏移量(文件头4字节 + Meta信息8字节)
                size = indexTableSize.toLong(),
            )

            for (i in 0 until imageCount) {
                // 依次读取每一个图片的偏移量（8字节）和数据长度（4字节）
                val offset = indexTableBuffer.readUint64().toLong()
                val dataLength = indexTableBuffer.readUint32().toLong()

                frameIndexList.add(FrameIndex(offset, dataLength))
            }

            // 读取全部图片帧数据
            val frameDataBuffer = channel.mapWithOrder(
                // 索引表偏移量(文件头4字节 + Meta信息8字节) + 索引表大小
                positionOffset = 12L + indexTableSize.toLong(),
                // size 为最后一张图片的偏移量 + 数据长度
                size = frameIndexList.last().let { lastFrameIndex ->
                    lastFrameIndex.offset + lastFrameIndex.dataLength
                },
            )

            // 创建FfabInfo对象
            return FfabInfo(
                version = version,
                imageCount = imageCount,
                width = width,
                height = height,
                format = format,
                formatCode = formatCode,
                mFrameIndexList = frameIndexList,
                mFrameDataBuffer = frameDataBuffer,
            )
        }
    }

    /**
     * 获取 FFAB 文件信息
     *
     * @return FFAB 文件信息，如果文件未打开则返回 null
     */
    fun getInfo(context: Context): FfabInfo? {
        try {
            return getOrCreateFfabInfo(ffabRawResId, context)
        } catch (e: Throwable) {
            LogUtil.e {
                LogUtil.getStackTraceString(e)
            }
        }
        return null
    }

}