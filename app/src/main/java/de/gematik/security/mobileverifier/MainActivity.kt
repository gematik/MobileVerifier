package de.gematik.security.mobileverifier

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme
import java.net.URI
import java.util.*

internal val TAG = MainActivity::class.java.name

internal lateinit var controller: Controller

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileVerifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QrCodeScan()
                }
            }
        }

        if (!::controller.isInitialized) {
            controller = Controller(this@MainActivity)
        }
    }
}

data class VerificationResult(
    var isVaccinationCertificate: Boolean = false,
    var isFullVaccinated: Boolean = false,
    var isInsuranceCertificate: Boolean = false,
    var portrait: String? = null,
    var isTrustedIssuer: Boolean = false,
    var isAssertionVerifiedSuccessfully: Boolean = false,
    var isAuthenticationVerifiedSuccessfully: Boolean = false,
    var message: String? = null
) {
    fun isSuccess() = isVaccinationCertificate &&
            isFullVaccinated &&
            isTrustedIssuer &&
            isAssertionVerifiedSuccessfully &&
            isAuthenticationVerifiedSuccessfully
}

@Composable
fun QrCodeScan() {
    Box {
        val verificationResult = remember { mutableStateOf<VerificationResult?>(null) }

        val scanLauncher = rememberLauncherForActivityResult(
            contract = ScanContract(),
            onResult = { result ->
                Log.i(TAG, "scanned code: ${result.contents}")
                result.contents?.let {
                    runCatching {
                        val oob = URI.create(it).query.substringAfter("oob=", "").substringBefore("&")
                        if (oob.isNotEmpty()) {
                            controller.acceptInvitation(
                                invitation = json.decodeFromString<Invitation>(String(Base64.getDecoder().decode(oob))),
                                updateState = { verificationResult.value = it }
                            )
                        }
                    }.onFailure { Log.i(TAG, "exception reading qr-code: ${it.message}") }
                }
            }
        )

        Column(
            Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Admission Control",
                style = typography.headlineLarge
            )
            if (verificationResult.value == null) {
                Text(
                    "(full vaccination required)",
                    style = typography.bodyLarge
                )
            } else {
                if (verificationResult.value?.isVaccinationCertificate == true) {
                    Text(
                        "vaccination certificate ✓",
                        style = typography.bodyLarge
                    )
                }
                if (verificationResult.value?.isFullVaccinated == true) {
                    Text(
                        "fully vaccinated (3/3) ✓",
                        style = typography.bodyLarge
                    )
                }
                if (verificationResult.value?.isInsuranceCertificate == true) {
                    Text(
                        "insurance certificate ✓",
                        style = typography.bodyLarge
                    )
                }
                if (verificationResult.value?.portrait != null) {
                    Text(
                        "portrait ✓",
                        style = typography.bodyLarge
                    )
                }
                if (verificationResult.value?.isTrustedIssuer == true) {
                    Text(
                        "trusted issuers ✓",
                        style = typography.bodyLarge
                    )
                }
                if (verificationResult.value?.isAssertionVerifiedSuccessfully == true) {
                    Text(
                        "assertions verified ✓",
                        style = typography.bodyLarge
                    )
                }
                if (verificationResult.value?.isVaccinationCertificate == true) {
                    Text(
                        "holder authenticated ✓",
                        style = typography.bodyLarge
                    )
                }
                verificationResult.value?.message?.let {
                    Text(
                        color = Color.Red,
                        text = it,
                        style = typography.bodyLarge
                    )
                }
            }
        }

        verificationResult.value?.portrait?.let {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Holder Portrait",
                    style = typography.headlineMedium
                )
                val imageData = Base64.getDecoder().decode(it)
                Image(
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size, null).asImageBitmap(),
                    contentDescription = "portrait",
                    modifier = Modifier
                        .size(300.dp)
                )
            }
        } ?: Image(
            painterResource(
                when {
                    verificationResult.value?.isSuccess() == true -> R.drawable.approved
                    verificationResult.value?.isSuccess() == false -> R.drawable.denied
                    else -> R.drawable.unknown
                }
            ),
            "status",
            Modifier
                .align(Alignment.Center)
        )

        Button(
            onClick = {
                verificationResult.value = null
                scanLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name)
                    },
                )
            }, Modifier
                .padding(20.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text(text = "Scan QR code")
        }

    }
}

@Preview(
    showBackground = true,
    widthDp = 40 * 10,
    heightDp = 40 * 16
)
@Composable
fun DefaultPreview() {
    MobileVerifierTheme {
        QrCodeScan()
    }
}