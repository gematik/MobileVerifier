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

    private val TAG = "T4tHostApduService"

    companion object{
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

    private val NDEF_SELECT = byteArrayOf(
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

    private lateinit var NDEF_FILE_CONTENT : ByteArray

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return Service.START_STICKY // service got re-created by system after process was killed
        val ndefMessage = intent.getParcelableExtra<NdefMessage>(EXTRA_NDEF_MESSAGE)
        check(ndefMessage!=null){"Ndef message required"}
        NDEF_FILE_CONTENT = ndefMessage.toByteArray().let{it.size.toShort().toByteArray() + it}
        Log.i(TAG, "onStartCommand() NDEF-FILE: ${NDEF_FILE_CONTENT.toHex()}")
        return Service.START_STICKY
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
        //
        // The following flow is based on Appendix E "Example of Mapping Version 2.0 Command Flow"
        // in the NFC Forum specification
        //
        Log.i(TAG, "processCommandApdu() : " + commandApdu.toHex())

        //
        // First command: NDEF Tag Application select (Section 5.5.2 in NFC Forum spec)
        //
        if (NDEF_TAG_APP_SELECT.contentEquals(commandApdu)) {
            Log.i(TAG, "NDEF_TAG_APP selected: ${RESPONSE_OKAY.toHex()}")
            status.isNdefTagAppSelected = true
            return RESPONSE_OKAY
        }

        //
        // Second command: Capability Container select (Section 5.5.3 in NFC Forum spec)
        //
        if (CAPABILITY_CONTAINER_SELECT.contentEquals(commandApdu)) {
            Log.i(TAG, "CAPABILITY_CONTAINER selected: ${RESPONSE_OKAY.toHex()}")
            status.selectedFile = SelectedFile.CapabilityContainer
            return RESPONSE_OKAY
        }

        //
        // Fourth command: NDEF Select command (Section 5.5.5 in NFC Forum spec)
        //
        if (NDEF_SELECT.contentEquals(commandApdu)) {
            Log.i(TAG, "NDEF_FILE selected: ${RESPONSE_OKAY.toHex()}")
            status.selectedFile = SelectedFile.NdefFile
            return RESPONSE_OKAY
        }

        //
        // Third and fifth command: ReadBinary data (Section 5.5.4 in NFC Forum spec)
        //
        if (commandApdu.sliceArray(0..1).contentEquals(READ_BINARY)) {
            val offset = BigInteger(1, commandApdu.sliceArray(2..3)).toInt()
            val length = BigInteger(1, commandApdu.sliceArray(4..4)).toInt()
            val data = when (status.selectedFile) {
                SelectedFile.CapabilityContainer -> CAPABILITY_CONTAINER_FILE_CONTENT
                SelectedFile.NdefFile -> NDEF_FILE_CONTENT
                SelectedFile.Nothing -> null
            }
            data ?: return RESPONSE_ERROR_FILE_NOT_FOUND
            return if (offset + length <= data.size) {
                data.sliceArray(offset..offset + length - 1).let {
                    Log.i(TAG, "ReadBinary ${status.selectedFile.name}: ${(it + RESPONSE_OKAY).toHex()}")
                    it + RESPONSE_OKAY
                }
            } else {
                RESPONSE_ERROR_FILE_NOT_FOUND
            }
        }
        //
        // We're doing something outside our scope
        //
        Log.i(TAG, "unknown commandApdu: ${commandApdu.toHex()}")
        return RESPONSE_ERROR_FILE_NOT_FOUND
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "onDeactivated() Fired! Reason: $reason")
        status = Status()
    }

    private fun Short.toByteArray() : ByteArray {
        return ByteBuffer.allocate(Short.SIZE_BYTES).putShort(this).array()
    }

}