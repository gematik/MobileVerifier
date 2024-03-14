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

package de.gematik.security.mobileverifier

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.protocols.GoalCode
import de.gematik.security.mobileverifier.Settings.ownWsUri
import de.gematik.security.mobileverifier.t4thost.T4tHostApduService
import de.gematik.security.mobileverifier.t4thost.T4tHostApduService.Companion.EXTRA_NDEF_MESSAGE
import de.gematik.security.mobileverifier.ui.MainScreen
import de.gematik.security.mobileverifier.ui.MainViewModel
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.util.*

internal val tag = MainActivity::class.java.name

class MainActivity : ComponentActivity() {

    private val invitationId = UUID.randomUUID().toString()
    private lateinit var controller: Controller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainViewModel by viewModels<MainViewModel>()
        setContent {
            MobileVerifierTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(mainViewModel, ::onQrCodeScanned)
                }
            }
        }
        // allow check in by tapping the devices to each other using NFC
        activateNfcTag()

        // start controller listening for incoming request (invitation acceptances via web socket or DIDComm)
        startController(mainViewModel)

    }

    // called when user scans qr code
    private fun onQrCodeScanned(invitation: Invitation) {
        lifecycleScope.launch(Dispatchers.IO) {
            controller.acceptInvitation(invitation)
        }
    }

    private fun activateNfcTag() {
        // start type 4 tag (t4t) card emulation
        // goal: invites reader of tag to check in using vaccination certiticate
        // goalCode: expects reader of tag to send presentation offer
        val intent = Intent(this, T4tHostApduService::class.java)
        val ndefMesage = NdefMessage(
            NdefRecord.createUri(
                Invitation(
                    id = invitationId,
                    label = "Summer Concert",
                    goal = "Checkin event with VaccinationCertificate",
                    goalCode = GoalCode.OFFER_PRESENTATION,
                    from = ownWsUri
                ).let {
                    "https://my-wallet.me/ssi?oob=${it.toBase64()}"
                }
            )
        )
        intent.putExtra(EXTRA_NDEF_MESSAGE, ndefMesage)
        startService(intent)
        Log.i(tag, "T4T card emulation service started: $ndefMesage")
        val uriPrefix = ndefMesage.records[0].payload[0]
        val uriData = ndefMesage.records[0].payload.drop(1).toByteArray()
        Log.i(tag, "decoded payload: URI Prefix Code = $uriPrefix, URI Data = ${String(uriData)}")
        val oob = Base64.getDecoder().decode(
            URI.create(String(uriData)).query
                .substringAfter("oob=", "")
                .substringBefore("&")
        )
        Log.i(tag, "decoded oob: invitation = ${String(oob)}")
    }

    private fun startController(mainViewModel: MainViewModel) {
        controller = Controller(mainViewModel)
        lifecycleScope.launch(Dispatchers.IO) {
            controller.start()
        }
        Log.i(tag, "controller started")
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
        MainScreen() {}
    }
}