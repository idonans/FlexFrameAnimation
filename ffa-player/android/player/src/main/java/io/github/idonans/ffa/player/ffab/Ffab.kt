package io.github.idonans.ffa.player.ffab

import android.content.Context
import android.content.res.Configuration
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

        private fun FileChannel.mapWithOrder(position: Long, size: Long): MappedByteBuffer {
            val buffer = this.map(
                FileChannel.MapMode.READ_ONLY,
                position,
                size,
            )

            // .ffab 文件中的多字节整数都是按照大端序存储
            buffer.order(ByteOrder.BIG_ENDIAN)
            return buffer
        }

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
        val frameIndexList: List<FrameIndex>,
    )

    private val mConfiguration: Configuration = Configuration()
    private var mInfo: FfabInfo? = null

    /**
     * 获取 FFAB 文件信息
     *
     * @return FFAB 文件信息，如果文件未打开则返回 null
     */
    fun getInfo(): FfabInfo? = mInfo

    /**
     * 按需从 ffabRawResId 解析 ffab 文件内容。
     * 如果没有解析过，或者 Configuration 发生了变化，则重新解析 ffab 文件
     */
    fun prepare(context: Context) {
        synchronized(mConfiguration) {
            val newConfiguration = context.resources.configuration
            if (mConfiguration.diff(newConfiguration) != 0) {
                mConfiguration.setTo(newConfiguration)

                prepareInner(context)

                // 乐观逻辑：
                // 如果在解析结束后 configuration 发生了变化，则本次不再重新解析。
                // 但是需要将 mConfiguration 清空，以确保下一次再调用此方法时，能够触发重新解析。
                // 可能的极端情况识别不了：在解析过程中 configuration 发生了多次变化，最终又变回了初始状态。
                if (mConfiguration.diff(context.resources.configuration) != 0) {
                    mConfiguration.setToDefaults()
                }
            }
        }
    }

    private fun prepareInner(context: Context) {
        context.resources.openRawResourceFd(ffabRawResId).use { afd ->
            afd.createInputStream().use { stream ->
                stream.channel.use { channel ->
                    prepareInner(channel)
                }
            }
        }
    }

    private fun prepareInner(channel: FileChannel) {
        // 一次性读取文件头（4字节）与 Meta 信息 (8字节)
        val headerMetaBuffer = channel.mapWithOrder(
            position = 0,
            size = 12,
        )

        // 文件头 (4字节)：FFAB_MAGIC (0xFFAB) + 版本号(0x0001)
        // 验证魔数和版本号
        val magic = headerMetaBuffer.readUint16().toInt()
        val version = headerMetaBuffer.readUint16().toInt()

        if (magic != FFAB_MAGIC || version != FFAB_VERSION_0X0001) {
            // 当前仅支持解析版本1(0x0001)
            throw IllegalArgumentException("Invalid FFAB file format")
        }

        // Meta信息区 (8字节): 图片数量(2字节) + 图片宽度(2字节) + 图片高度(2字节) + ASTC格式代码(2字节)
        val imageCount = headerMetaBuffer.readUint16().toInt()
        val width = headerMetaBuffer.readUint16().toInt()
        val height = headerMetaBuffer.readUint16().toInt()
        val formatCode = headerMetaBuffer.readUint16().toInt()

        // 获取ASTC块大小
        val format = CODE_TO_ASTC_BLOCK[formatCode]
            ?: throw IllegalArgumentException("Invalid format code: $formatCode")

        // 读取索引表 (每项12字节)
        val frameIndexList = mutableListOf<FrameIndex>()
        val indexTableSize = imageCount * 12
        val indexTableBuffer = channel.mapWithOrder(
            position = 12L, // 索引表偏移量(文件头4字节 + Meta信息8字节)
            size = indexTableSize.toLong(),
        )

        for (i in 0 until imageCount) {
            // 依次读取每一个图片的偏移量（8字节）和数据长度（4字节）
            val offset = indexTableBuffer.readUint64().toLong()
            val dataLength = indexTableBuffer.readUint32().toLong()

            frameIndexList.add(FrameIndex(offset, dataLength))
        }

        // 创建FfabInfo对象
        mInfo = FfabInfo(
            version = version,
            imageCount = imageCount,
            width = width,
            height = height,
            format = format,
            formatCode = formatCode,
            frameIndexList = frameIndexList,
        )
    }

}