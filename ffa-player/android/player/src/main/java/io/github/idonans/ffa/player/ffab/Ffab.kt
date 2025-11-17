package io.github.idonans.ffa.player.ffab

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.Configuration

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
        private const val FFAB_VERSION_0x0001 = 0x0001

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
         * 从字节数组中解析大端序的8位无符号整数
         * @param bytes 字节数组
         * @param offset 起始偏移量
         * @return 解析出的整数 (0-255)
         */
        private fun readUint8(bytes: ByteArray, offset: Int = 0): Int {
            return bytes[offset].toInt() and 0xFF
        }

        /**
         * 从字节数组中解析大端序的16位无符号整数
         * @param bytes 字节数组
         * @param offset 起始偏移量
         * @return 解析出的整数 (0-65535)
         */
        private fun readUint16(bytes: ByteArray, offset: Int = 0): Int {
            return (readUint8(bytes, offset) shl 8) or
                    //
                    readUint8(bytes, offset + 1)
        }

        /**
         * 从字节数组中解析大端序的32位无符号整数
         * @param bytes 字节数组
         * @param offset 起始偏移量
         * @return 解析出的长整数 (0-4294967295)
         */
        private fun readUint32(bytes: ByteArray, offset: Int = 0): Long {
            return (readUint8(bytes, offset).toLong() shl 24) or
                    //
                    (readUint8(bytes, offset + 1).toLong() shl 16) or
                    //
                    (readUint8(bytes, offset + 2).toLong() shl 8) or
                    //
                    readUint8(bytes, offset + 3).toLong()
        }

        /**
         * 从字节数组中解析大端序的64位无符号整数
         * @param bytes 字节数组
         * @param offset 起始偏移量
         * @return 解析出的长整数 (注意：对于超大数可能被解析为负数)
         */
        private fun readUint64(bytes: ByteArray, offset: Int = 0): Long {
            return (readUint8(bytes, offset).toLong() shl 56) or
                    //
                    (readUint8(bytes, offset + 1).toLong() shl 48) or
                    //
                    (readUint8(bytes, offset + 2).toLong() shl 40) or
                    //
                    (readUint8(bytes, offset + 3).toLong() shl 32) or
                    //
                    (readUint8(bytes, offset + 4).toLong() shl 24) or
                    //
                    (readUint8(bytes, offset + 5).toLong() shl 16) or
                    //
                    (readUint8(bytes, offset + 6).toLong() shl 8) or
                    //
                    readUint8(bytes, offset + 7).toLong()
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
    private fun prepare(context: Context) {
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
        val afd = context.resources.openRawResourceFd(ffabRawResId)
        try {
            prepareInner(context, afd)
        } finally {
            afd.close()
        }
    }

    private fun prepareInner(context: Context, afd: AssetFileDescriptor) {
        val inputStream = context.resources.openRawResource(ffabRawResId)
        try {
            // 读取文件头 (4字节)：FFAB_MAGIC (0xFFAB) + 版本号(0x0001)
            val header = ByteArray(4)
            inputStream.read(header)

            // 验证魔数和版本号
            val magic = readUint16(header, 0)
            val version = readUint16(header, 2)

            if (magic != FFAB_MAGIC || version != FFAB_VERSION_0x0001) {
                // 当前仅支持解析版本1(0x0001)
                throw IllegalArgumentException("Invalid FFAB file format")
            }

            // 读取Meta信息区 (8字节): 图片数量(2字节) + 图片宽度(2字节) + 图片高度(2字节) + ASTC格式代码(2字节)
            val metaInfo = ByteArray(8)
            inputStream.read(metaInfo)

            val imageCount = readUint16(metaInfo, 0)
            val width = readUint16(metaInfo, 2)
            val height = readUint16(metaInfo, 4)
            val formatCode = readUint16(metaInfo, 6)

            // 获取ASTC块大小
            val format = CODE_TO_ASTC_BLOCK[formatCode]
                ?: throw IllegalArgumentException("Invalid format code: $formatCode")

            // 读取索引表 (每项12字节)
            val frameIndexList = mutableListOf<FrameIndex>()
            val indexTableSize = imageCount * 12
            val indexTable = ByteArray(indexTableSize)
            inputStream.read(indexTable)

            for (i in 0 until imageCount) {
                val offsetIndex = i * 12
                val offset = readUint64(indexTable, offsetIndex)
                val dataLength = readUint32(indexTable, offsetIndex + 8)

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
        } finally {
            inputStream.close()
        }
    }

}