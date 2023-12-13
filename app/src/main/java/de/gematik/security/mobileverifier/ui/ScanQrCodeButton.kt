package de.gematik.security.mobileverifier.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.mobileverifier.tag
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme
import java.net.URI
import java.util.*

@Composable
fun ScanQrCodeButton(modifier: Modifier = Modifier, onQrScanned: (Invitation) -> Unit) {
    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            result.contents?.let {
                runCatching {
                    val oob = URI.create(it).query.substringAfter("oob=", "").substringBefore("&")
                    if (oob.isNotEmpty()) {
                        Log.i(tag, "Qr code scanned: $oob")
                        val invitation = json.decodeFromString<Invitation>(
                            String(
                                Base64.getDecoder().decode(oob)
                            )
                        ).also {
                            Log.i(tag, "invitation = $it")
                        }
                        onQrScanned(invitation)
                    }
                }.onFailure { Log.i(tag, "exception reading qr-code: ${it.message}") }
            }
        }
    )
    Button(
        modifier = modifier,
        onClick = {
            scanLauncher.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name)
                },
            )
        },
    ) {
        Text(text = "Scan QR code")
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun ScanQrCodeButtonPreview() {
    MobileVerifierTheme {
        Box{
            ScanQrCodeButton(Modifier.align(Alignment.Center)) {}
        }
    }
}