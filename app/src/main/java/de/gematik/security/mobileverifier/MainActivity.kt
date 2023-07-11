package de.gematik.security.mobileverifier

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.Invitation
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme
import java.net.URI
import java.net.URL
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

enum class State {
    UNKNOWN,
    APPROVED,
    DENIED
}

@Composable
fun QrCodeScan() {
    Box {
        val isVaccinationValid = remember { mutableStateOf(State.UNKNOWN) }

        Text(
            "Vaccination Certificate",
            Modifier
                .padding(20.dp)
                .align(Alignment.TopCenter),
            style = typography.headlineLarge
        )
        val scanLauncher = rememberLauncherForActivityResult(
            contract = ScanContract(),
            onResult = { result ->
                Log.i(TAG, "scanned code: ${result.contents}")
                val oob = URI.create(result.contents).query.substringAfter("oob=", "").substringBefore("&")
                if (oob.isNotEmpty()) {
                    controller.acceptInvitation(
                        invitation = json.decodeFromString<Invitation>(String(Base64.getDecoder().decode(oob))),
                        updateState = { state -> isVaccinationValid.value = state }
                    )
                }
            }
        )

        Image(
            painterResource(
                when (isVaccinationValid.value) {
                    State.APPROVED -> R.drawable.approved
                    State.UNKNOWN -> R.drawable.unknown
                    State.DENIED -> R.drawable.denied
                }
            ),
            "denied",
            Modifier
                .align(Alignment.Center)
        )

        Button(
            onClick = {
                isVaccinationValid.value = State.UNKNOWN
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