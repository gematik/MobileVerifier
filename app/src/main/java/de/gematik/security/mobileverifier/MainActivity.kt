package de.gematik.security.mobileverifier

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.protocols.GoalCode
import de.gematik.security.mobileverifier.Settings.ownDid
import de.gematik.security.mobileverifier.Settings.ownWsUri
import de.gematik.security.mobileverifier.t4thost.T4tHostApduService
import de.gematik.security.mobileverifier.t4thost.T4tHostApduService.Companion.EXTRA_NDEF_MESSAGE
import de.gematik.security.mobileverifier.ui.MainScreen
import de.gematik.security.mobileverifier.ui.MainViewModel
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

internal val TAG = MainActivity::class.java.name

internal lateinit var controller: Controller

class MainActivity : ComponentActivity() {

    val invitationId = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainViewModel by viewModels<MainViewModel>()
        setContent {
            MobileVerifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(mainViewModel)
                }
            }
        }

        // start type 4 tag (t4t) card emulation
        val intent = Intent(this, T4tHostApduService::class.java)
        val ndefMesage = NdefMessage(
            NdefRecord.createUri(
                Invitation(
                    id = invitationId,
                    label = "Summer Concert",
                    goal = "Checkin event with VaccinationCertificate",
                    goalCode = GoalCode.OFFER_PRESENTATION,
                    from = ownWsUri
                ).let{
                    "https://my-wallet.me/ssi?oob=${it.toBase64()}"
                }
            )
        )
        intent.putExtra(EXTRA_NDEF_MESSAGE, ndefMesage)
        startService(intent)

        // instantiate controller
        controller = Controller(this, mainViewModel)
        lifecycle.coroutineScope.launch {
            withContext(Dispatchers.IO){
                controller.start()
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)

@Composable
fun DefaultPreview() {
    MobileVerifierTheme {
        MainScreen()
    }
}