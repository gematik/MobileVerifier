/*
 * Copyright 2022-2024, gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package de.gematik.security.mobileverifier.t4thost

import android.app.Service
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import de.gematik.security.credentialExchangeLib.extensions.toHex
import java.math.BigInteger
import java.nio.ByteBuffer

class T4tHostApduService : HostApduService() {

    private val tag = T4tHostApduService::class.java.name

    companion object {
        const val EXTRA_NDEF_MESSAGE = "extraNdefMessage"
    }

    private val NDEF_TAG_APP_SELECT = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xA4.toByte(), // INS	- Instruction - Instruction code
        0x04.toByte(), // P1	- Parameter 1 - Instruction parameter 1
        0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
        0x07.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xD2.toByte(),
        0x76.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x85.toByte(),
        0x01.toByte(),
        0x01.toByte(), // NDEF Tag Application name
        0x00.toByte(), // Le field	- Maximum number of bytes expected in the data field of the response to the command
    )

    private val CAPABILITY_CONTAINER_SELECT = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xa4.toByte(), // INS	- Instruction - Instruction code
        0x00.toByte(), // P1	- Parameter 1 - Instruction parameter 1
        0x0c.toByte(), // P2	- Parameter 2 - Instruction parameter 2
        0x02.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xe1.toByte(),
        0x03.toByte(), // file identifier of the CC file
    )

    private val CAPABILITY_CONTAINER_FILE_CONTENT = byteArrayOf(
        0x00.toByte(), 0x0F.toByte(), // CCLEN length of the CC file
        0x20.toByte(), // Mapping Version 2.0
        0xFF.toByte(), 0xFF.toByte(), // MLe maximum
        0xFF.toByte(), 0xFF.toByte(), // MLc maximum
        0x04.toByte(), // T field of the NDEF File Control TLV
        0x06.toByte(), // L field of the NDEF File Control TLV
        0xE1.toByte(), 0x04.toByte(), // File Identifier of NDEF file
        0xFF.toByte(), 0xFE.toByte(), // Maximum NDEF file size of 65534 bytes
        0x00.toByte(), // Read access without any security
        0xFF.toByte(), // Write access without any security
    )

    private val NDEF_FILE_SELECT = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xa4.toByte(), // Instruction byte (INS) for Select command
        0x00.toByte(), // Parameter byte (P1), select by identifier
        0x0c.toByte(), // Parameter byte (P1), select by identifier
        0x02.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xE1.toByte(),
        0x04.toByte(), // file identifier of the NDEF file retrieved from the CC file
    )

    private val READ_BINARY = byteArrayOf(
        0x00.toByte(), // Class byte (CLA)
        0xb0.toByte(), // Instruction byte (INS) for ReadBinary command
        // (P1, P2) offset inside the CC file
        // (Le) length of data to read
    )

    private val RESPONSE_OKAY = byteArrayOf(
        0x90.toByte(), // SW1	Status byte 1 - Command processing status
        0x00.toByte(), // SW2	Status byte 2 - Command processing qualifier
    )

    private val RESPONSE_ERROR_FILE_NOT_FOUND = byteArrayOf(
        0x6A.toByte(), // SW1	Status byte 1 - Command processing status
        0x82.toByte(), // SW2	Status byte 2 - Command processing qualifier
    )

    private lateinit var NDEF_FILE_CONTENT: ByteArray

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        check(intent != null) {"intent == null - should never happen (see START_REDELIVER_INTENT)"}
        val ndefMessage = intent.getParcelableExtra<NdefMessage>(EXTRA_NDEF_MESSAGE)
        check(ndefMessage != null) { "Ndef message required" }
        NDEF_FILE_CONTENT = ndefMessage.toByteArray().let { it.size.toShort().toByteArray() + it }
        return Service.START_REDELIVER_INTENT
    }

    enum class SelectedFile {
        Nothing,
        CapabilityContainer,
        NdefFile
    }

    data class Status(
        var isNdefTagAppSelected: Boolean = false,
        var selectedFile: SelectedFile = SelectedFile.Nothing
    )

    var status = Status()

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {

        Log.d(tag, "processCommandApdu() : " + commandApdu.toHex())
        return when {

            NDEF_TAG_APP_SELECT.contentEquals(commandApdu) -> {
                // NDEF Tag Application select
                status.isNdefTagAppSelected = true
                Log.d(tag, "NDEF_TAG_APP selected: ${RESPONSE_OKAY.toHex()}")
                RESPONSE_OKAY
            }

            CAPABILITY_CONTAINER_SELECT.contentEquals(commandApdu) -> {
                // Capability Container select
                status.selectedFile = SelectedFile.CapabilityContainer
                Log.d(tag, "CAPABILITY_CONTAINER selected: ${RESPONSE_OKAY.toHex()}")
                RESPONSE_OKAY
            }

            NDEF_FILE_SELECT.contentEquals(commandApdu) -> {
                // NDEF File Select command
                status.selectedFile = SelectedFile.NdefFile
                Log.d(tag, "NDEF_FILE selected: ${RESPONSE_OKAY.toHex()}")
                RESPONSE_OKAY
            }

            READ_BINARY.contentEquals(commandApdu.sliceArray(0..1)) -> {
                // ReadBinary from selected file
                val offset = BigInteger(1, commandApdu.sliceArray(2..3)).toInt()
                val length = BigInteger(1, commandApdu.sliceArray(4..4)).toInt()
                val data = when (status.selectedFile) {
                    SelectedFile.CapabilityContainer -> CAPABILITY_CONTAINER_FILE_CONTENT
                    SelectedFile.NdefFile -> NDEF_FILE_CONTENT
                    SelectedFile.Nothing -> null
                }
                if (data == null) {
                    Log.d(tag, "no file selected: ${RESPONSE_ERROR_FILE_NOT_FOUND.toHex()}")
                    RESPONSE_ERROR_FILE_NOT_FOUND
                } else {
                    if (offset + length <= data.size) {
                        data.sliceArray(offset..offset + length - 1).let {
                            Log.d(tag, "ReadBinary ${status.selectedFile.name} offset:$offset len:$length: ${(it + RESPONSE_OKAY).toHex()}")
                            it + RESPONSE_OKAY
                        }
                    } else {
                        Log.d(tag, "offset + length out of bound: ${RESPONSE_ERROR_FILE_NOT_FOUND.toHex()}")
                        RESPONSE_ERROR_FILE_NOT_FOUND
                    }
                }
            }

            else -> {
                Log.d(tag, "unknown commandApdu: ${RESPONSE_ERROR_FILE_NOT_FOUND.toHex()}")
                RESPONSE_ERROR_FILE_NOT_FOUND
            }

        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(tag, "nfc link deactivated/lost or other AID selected: $reason")
        status = Status()
    }

    private fun Short.toByteArray(): ByteArray {
        return ByteBuffer.allocate(Short.SIZE_BYTES).putShort(this).array()
    }

}