package io.github.idonans.ffa.player.ffab

import android.content.Context
import androidx.collection.LruCache
import com.google.common.cache.CacheBuilder
import io.github.idonans.ffa.player.Debug
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

        /**
         * key 为读目标 raw `.ffab` 内容时对应的在 apk 文件中的偏移量。当发生白天黑夜模式切换需要匹配
         * 不同的 `.ffab` 文件时，此偏移量会发生变化。通过对比此偏移量是否相同来替代对比 Configuration 是否相同。
         */
        private val mFfabInfoCache = FfabInfoCache(maxCacheSize = 200)

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
         * 从指定 context 上下文中解析目标 raw `.ffab` 文件内容，内部会充分复用缓存，仅在必要时才重新解析文件内容
         */
        @OptIn(ExperimentalStdlibApi::class)
        fun getOrCreateFfabInfo(ffabRawResId: Int, context: Context): FfabInfo? {
            val ffabInfo: FfabInfo?
            measureTimeIfDebug(
                { "prepareInner ${ffabRawResId.toHexString()}" },
            ) {
                ffabInfo = prepareInner(ffabRawResId, context)
            }
            return ffabInfo
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun prepareInner(ffabRawResId: Int, context: Context): FfabInfo? {
            context.resources.openRawResourceFd(ffabRawResId).use { afd ->
                val resourcesOffset = afd.startOffset
                val ffabInfo = mFfabInfoCache.getOrCreate(resourcesOffset) {
                    // 没有命中缓存，从原始资源解析 FfabInfo
                    val newFfabInfo: FfabInfo
                    LogUtil.d {
                        "prepareInner start init ffabRawResId:${ffabRawResId.toHexString()}," +
                                //
                                " resourcesOffset:$resourcesOffset"
                    }

                    afd.createInputStream().use { stream ->
                        stream.channel.use { channel ->
                            newFfabInfo = prepareInner(
                                ffabRawResId = ffabRawResId,
                                resourcesOffset = resourcesOffset,
                                channel = channel,
                            )
                        }
                    }
                    newFfabInfo
                }
                return ffabInfo

            }
        }

        private fun prepareInner(
            ffabRawResId: Int,
            resourcesOffset: Long,
            channel: FileChannel,
        ): FfabInfo {
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

            // 获取ASTC格式对象
            val format = FfabFormat.fromFormatCode(formatCode)
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
                ffabRawResId = ffabRawResId,
                resourcesOffset = resourcesOffset,
                version = version,
                imageCount = imageCount,
                width = width,
                height = height,
                format = format,
                frameIndexList = frameIndexList,
                frameDataBuffer = frameDataBuffer,
            )
        }
    }

    /**
     * 获取 FFAB 文件信息。这是一个多线程安全的方法。
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
class FfabInfo(
    private val ffabRawResId: Int,
    private val resourcesOffset: Long,
    private val version: Int,
    val imageCount: Int,
    val width: Int,
    val height: Int,
    val format: FfabFormat,
    private val frameIndexList: List<FrameIndex>,
    private val frameDataBuffer: ByteBuffer,
) {

    /**
     * 获取指定帧的内容
     */
    fun frameData(index: Int): ByteBuffer {
        val frameIndex = this.frameIndexList[index]
        // 创建原始缓冲区的副本，以避免影响原始缓冲区的状态
        val buffer = this.frameDataBuffer.duplicate()
        // 设置位置和限制来创建一个"切片"
        buffer.position(frameIndex.offset.toInt())
        buffer.limit(frameIndex.offset.toInt() + frameIndex.dataLength.toInt())
        // 创建切片
        return buffer.slice()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "FfabInfo(ffabRawResId=${ffabRawResId.toHexString()}," +
                //
                " resourcesOffset=$resourcesOffset, version=$version, imageCount=$imageCount," +
                //
                " width=$width, height=$height, format=$format)"
    }

    ////////////////////
    /**
     * resourcesOffset 是资源的唯一标识符，仅需要将 resourcesOffset 作为 equals 与 hash 的输入。
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FfabInfo

        return resourcesOffset == other.resourcesOffset
    }

    /**
     * resourcesOffset 是资源的唯一标识符，仅需要将 resourcesOffset 作为 equals 与 hash 的输入。
     */
    override fun hashCode(): Int {
        return resourcesOffset.hashCode()
    }
    ////////////////////

}

/**
 * FFAB 图片压缩格式
 */
sealed class FfabFormat {

    data object AstcLdr4x4 : FfabFormat()

    data object AstcLdr5x4 : FfabFormat()

    data object AstcLdr5x5 : FfabFormat()

    data object AstcLdr6x5 : FfabFormat()

    data object AstcLdr6x6 : FfabFormat()

    data object AstcLdr8x5 : FfabFormat()

    data object AstcLdr8x6 : FfabFormat()

    data object AstcLdr8x8 : FfabFormat()

    data object AstcLdr10x5 : FfabFormat()

    data object AstcLdr10x6 : FfabFormat()

    data object AstcLdr10x8 : FfabFormat()

    data object AstcLdr10x10 : FfabFormat()

    data object AstcLdr12x10 : FfabFormat()

    data object AstcLdr12x12 : FfabFormat()

    companion object {

        /**
         * 根据格式代码获取对应的图片压缩格式
         */
        fun fromFormatCode(formatCode: Int): FfabFormat? {
            return when (formatCode) {
                0x0001 -> AstcLdr4x4
                0x0002 -> AstcLdr5x4
                0x0003 -> AstcLdr5x5
                0x0004 -> AstcLdr6x5
                0x0005 -> AstcLdr6x6
                0x0006 -> AstcLdr8x5
                0x0007 -> AstcLdr8x6
                0x0008 -> AstcLdr8x8
                0x0009 -> AstcLdr10x5
                0x000A -> AstcLdr10x6
                0x000B -> AstcLdr10x8
                0x000C -> AstcLdr10x10
                0x000D -> AstcLdr12x10
                0x000E -> AstcLdr12x12
                else -> null
            }
        }

    }

}

/**
 * 对 FfabInfo 对象的缓存。缓存分为两层：
 * 第一层：基于 LruCache 的强缓存（strong cache），容量为 maxCacheSize。
 * 第二层：基于 WeakReference 缓存（weak cache），容量不限制。
 * 强缓存中的所有 FfabInfo 对象都有对应的 WeakReference 缓存项。
 *
 * 缓存的 key 为 resourcesOffset
 *
 * @param maxCacheSize 强缓存的数量上限
 */
private class FfabInfoCache(maxCacheSize: Int) {

    private val mLock = Any()

    /**
     * 强缓存的用途是保持 FfabInfo 对象不被回收，外部读取缓存总是从 weak cache 获取。
     * 强缓存的内容是 weak cache 的子集。
     */
    private val mStrongCache = LruCache<Long, FfabInfo>(maxCacheSize)

    /**
     * 当 weak cache 发生读写操作时，需要同时对 strong cache 进行读写，
     * 以保持最新访问或创建的 FfabInfo 最不容易被强缓存删除。
     */
    private val mWeakCache = CacheBuilder.newBuilder().weakValues().build<Long, FfabInfo>()

    fun getOrCreate(resourcesOffset: Long, lazyInit: () -> FfabInfo): FfabInfo? {
        synchronized(mLock) {
            val cacheFromWeakCache = mWeakCache.getIfPresent(resourcesOffset)
            if (cacheFromWeakCache != null) {
                // 刷新 strong cache，使得该 cache 不容易被淘汰
                mStrongCache.put(resourcesOffset, cacheFromWeakCache)
                return cacheFromWeakCache
            }

            if (Debug.enable) {
                // 当没有命中 weak cache 时，strong cache 必然也不会命中
                val cacheFromStrongCache = mStrongCache.get(resourcesOffset)
                if (cacheFromStrongCache != null) {
                    LogUtil.e {
                        "unexpected. FfabInfoCache getOrCreate not hit weak cache but got" +
                                //
                                " $cacheFromStrongCache from strong cache with" +
                                //
                                " resourcesOffset:$resourcesOffset"
                    }
                }
            }
        }

        // 在并发场景下，可能产生对同一个 resourcesOffset 同时执行 lazyInit
        // 允许这种冗余执行以提升整体的并发性能
        var createdValue: FfabInfo
        try {
            createdValue = lazyInit()
        } catch (e: Throwable) {
            LogUtil.e {
                "unexpected. FfabInfoCache getOrCreate with resourcesOffset$resourcesOffset" +
                        //
                        " lazyInit fail ${LogUtil.getStackTraceString(e)}"
            }
            return null
        }

        synchronized(mLock) {
            // 写入 weak cache
            mWeakCache.put(resourcesOffset, createdValue)
            // 同时写入 strong cache
            mStrongCache.put(resourcesOffset, createdValue)
        }

        return createdValue
    }

    fun trimToSize(maxCacheSize: Int) {
        mStrongCache.trimToSize(maxCacheSize)
    }

}