package com.survivorio.navalmonitor

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DxxFrame(
    val messageType: Int,
    val payloadSize: Int,
    val payload: ByteArray,
    val headerOffset: Int = 0
)

data class NavalBoardState(
    var boardNumber: Int = 0,
    var rows: Int = 0,
    var cols: Int = 0,
    var seed: ByteArray = byteArrayOf(),
    var selected: List<Int> = emptyList(),
    var totalPacketsReceived: Int = 0,
    var lastOpcode: Int = 0,
    var lastPacketSize: Int = 0,
    var lastPacketTime: String = "",
    var lastLogLine: String = ""
)

object DxxDecoder {

    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun getCurrentTime(): String = timeFormatter.format(Date())

    fun bytesToHex(bytes: ByteArray, limit: Int = 24): String {
        return bytes.take(limit).joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Processa um pacote recebido via UDP (Suporta tanto JSON do addon quanto Dxx binário)
     */
    fun processIncomingPacket(data: ByteArray, currentState: NavalBoardState): Boolean {
        currentState.totalPacketsReceived++
        currentState.lastPacketSize = data.size
        currentState.lastPacketTime = getCurrentTime()

        // 1. Tenta interpretar como JSON do addon PCAPdroid-mitm (naval_live_addon.py)
        if (data.isNotEmpty() && data[0] == '{'.toByte()) {
            try {
                val jsonStr = String(data, Charsets.UTF_8)
                val json = JSONObject(jsonStr)

                val msgType = json.optInt("type", 0)
                val boardNumber = json.optInt("board_number", 0)
                val rows = json.optInt("rows", 0)
                val cols = json.optInt("cols", 0)

                currentState.lastOpcode = msgType

                val matrixArray = json.optJSONArray("matrix")
                val seedBytes = if (matrixArray != null) {
                    ByteArray(matrixArray.length()) { i -> matrixArray.getInt(i).toByte() }
                } else currentState.seed

                val selectedArray = json.optJSONArray("selected")
                val selectedList = if (selectedArray != null) {
                    List(selectedArray.length()) { i -> selectedArray.getInt(i) }
                } else currentState.selected

                if (rows > 0 && cols > 0 && seedBytes.size == rows * cols) {
                    currentState.boardNumber = boardNumber
                    currentState.rows = rows
                    currentState.cols = cols
                    currentState.seed = seedBytes
                    currentState.selected = selectedList
                    currentState.lastLogLine = "JSON Addon: Board #$boardNumber (${rows}x${cols}), ${selectedList.size} tiros"
                    return true
                }
            } catch (e: Exception) {
                // Não é JSON válido, segue para o decoder binário Dxx
            }
        }

        // 2. Tenta interpretar como Frame Binário Dxx (uint16_le type + uint32_le size + Protobuf)
        val frame = decodeFrame(data)
        if (frame != null) {
            return processNavalFrame(frame, currentState)
        }

        currentState.lastLogLine = "UDP Payload (não reconhecido)"
        return false
    }

    /**
     * Procura e decodifica um frame Dxx no buffer de bytes binários.
     */
    fun decodeFrame(data: ByteArray): DxxFrame? {
        if (data.size < 6) return null

        for (offset in 0..(data.size - 6)) {
            try {
                val buffer = ByteBuffer.wrap(data, offset, data.size - offset).order(ByteOrder.LITTLE_ENDIAN)
                val messageType = buffer.short.toInt() and 0xFFFF
                val declaredSize = buffer.int

                if (declaredSize in 1..200000 && offset + 6 + declaredSize <= data.size) {
                    val isNavalOpcode = messageType in 19700..19720
                    val isGeneralOpcode = messageType in 10000..60000 && (offset + 6 + declaredSize == data.size)

                    if (isNavalOpcode || isGeneralOpcode) {
                        val payload = ByteArray(declaredSize)
                        buffer.get(payload)
                        return DxxFrame(messageType, declaredSize, payload, offset)
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int> {
        var value = 0L
        var shift = 0
        var currentOffset = offset
        for (i in 0 until 10) {
            if (currentOffset >= data.size) break
            val byte = data[currentOffset++].toLong()
            value = value or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0L) return Pair(value, currentOffset)
            shift += 7
        }
        return Pair(value, currentOffset)
    }

    fun parseProtobufFields(data: ByteArray): Map<Int, Any> {
        val result = mutableMapOf<Int, Any>()
        var offset = 0
        while (offset < data.size) {
            val (tag, nextOffset) = readVarint(data, offset)
            if (nextOffset == offset) break
            offset = nextOffset

            val fieldNumber = (tag shr 3).toInt()
            val wireType = (tag and 7).toInt()

            when (wireType) {
                0 -> {
                    val (v, o) = readVarint(data, offset)
                    offset = o
                    result[fieldNumber] = v
                }
                2 -> {
                    val (len, o) = readVarint(data, offset)
                    offset = o
                    val length = len.toInt()
                    if (offset + length <= data.size) {
                        val bytes = data.copyOfRange(offset, offset + length)
                        result[fieldNumber] = bytes
                        offset += length
                    }
                }
                5 -> offset += 4
                1 -> offset += 8
                else -> break
            }
        }
        return result
    }

    fun processNavalFrame(frame: DxxFrame, currentState: NavalBoardState): Boolean {
        currentState.lastOpcode = frame.messageType
        currentState.lastPacketSize = frame.payloadSize
        currentState.lastPacketTime = getCurrentTime()

        if (frame.messageType == 19702) {
            val root = parseProtobufFields(frame.payload)
            val configBytes = root[3] as? ByteArray ?: return true
            val config = parseProtobufFields(configBytes)

            val rows = (config[5] as? Long)?.toInt() ?: return true
            val cols = (config[6] as? Long)?.toInt() ?: return true
            val seed = config[7] as? ByteArray ?: return true

            if (seed.size == rows * cols) {
                currentState.boardNumber++
                currentState.rows = rows
                currentState.cols = cols
                currentState.seed = seed
                currentState.selected = emptyList()
                currentState.lastLogLine = "Novo mapa Dxx: ${rows}x${cols} (Board #${currentState.boardNumber})"
                return true
            }
        }

        if (frame.messageType == 19709 || frame.messageType == 19710) {
            val root = parseProtobufFields(frame.payload)
            val selectedBytes = root[2] as? ByteArray
            if (selectedBytes != null) {
                val newSelected = selectedBytes.map { it.toInt() and 0xFF }
                currentState.selected = newSelected
                currentState.lastLogLine = "Tiros Dxx: ${newSelected.size} selecionados"
                return true
            }
        }

        currentState.lastLogLine = "Opcode Dxx ${frame.messageType} lido"
        return true
    }
}
