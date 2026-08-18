package com.survivorio.navalmonitor

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcapFileReader {

    data class PcapResult(
        val totalPackets: Int,
        val dxxFrames: Int,
        val navalEvents: Int,
        val logSummary: String
    )

    fun readPcapFile(context: Context, uri: Uri, currentState: NavalBoardState): PcapResult {
        var inputStream: InputStream? = null
        var totalPackets = 0
        var dxxFramesCount = 0
        var navalEventsCount = 0

        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return PcapResult(0, 0, 0, "Error opening .pcap file")

            val globalHeader = ByteArray(24)
            val readGlobal = inputStream.read(globalHeader)
            if (readGlobal < 24) {
                return PcapResult(0, 0, 0, "Invalid .pcap file (header too short)")
            }

            val magic = ByteBuffer.wrap(globalHeader, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val isLittleEndian = (magic == 0xa1b2c3d4.toInt() || magic == 0x4d3cb2a1.toInt())
            val byteOrder = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

            val linkType = ByteBuffer.wrap(globalHeader, 20, 4).order(byteOrder).int

            val headerBuffer = ByteArray(16)
            while (true) {
                val readHeader = inputStream.read(headerBuffer)
                if (readHeader < 16) break

                val recordBuffer = ByteBuffer.wrap(headerBuffer).order(byteOrder)
                recordBuffer.int // tsSec
                recordBuffer.int // tsUsec
                val inclLen = recordBuffer.int
                recordBuffer.int // origLen

                if (inclLen <= 0 || inclLen > 2000000) break

                val packetData = ByteArray(inclLen)
                val readPacket = inputStream.read(packetData)
                if (readPacket < inclLen) break

                totalPackets++

                val ipOffset = when (linkType) {
                    1 -> 14  // Ethernet
                    113 -> 16 // Linux SLL
                    101, 228 -> 0 // Raw IP / IPv4
                    else -> 14
                }

                if (ipOffset < packetData.size) {
                    val ipData = packetData.copyOfRange(ipOffset, packetData.size)
                    val frame = DxxDecoder.decodeFrame(ipData)

                    if (frame != null) {
                        dxxFramesCount++
                        val changed = DxxDecoder.processNavalFrame(frame, currentState)
                        if (changed) {
                            navalEventsCount++
                        }
                    }
                }
            }

            val summary = if (navalEventsCount > 0) {
                "Success! Found $navalEventsCount board events"
            } else if (dxxFramesCount > 0) {
                "Read $dxxFramesCount Dxx frames, but no board events in file."
            } else {
                "Read $totalPackets packets, no unencrypted Dxx frames found."
            }

            return PcapResult(totalPackets, dxxFramesCount, navalEventsCount, summary)

        } catch (e: Exception) {
            e.printStackTrace()
            return PcapResult(totalPackets, dxxFramesCount, navalEventsCount, "Error reading .pcap: ${e.localizedMessage}")
        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
        }
    }
}
