package de.gematik.security.mobileverifier.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.mobileverifier.R
import de.gematik.security.mobileverifier.controller
import de.gematik.security.mobileverifier.tag
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    Box {
        val verificationState by mainViewModel.verificationState.collectAsState()
        val scanLauncher = rememberLauncherForActivityResult(
            contract = ScanContract(),
            onResult = { result ->
                result.contents?.let {
                    runCatching {
                        val oob = URI.create(it).query.substringAfter("oob=", "").substringBefore("&")
                        if (oob.isNotEmpty()) {
                            Log.i(tag, "Qr code scanned: $oob")
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    controller.acceptInvitation(
                                        invitation = json.decodeFromString<Invitation>(
                                            String(
                                                Base64.getDecoder().decode(oob)
                                            )
                                        ).also {
                                            Log.i(tag, "invitation = $it")
                                        },
                                        updateState = { mainViewModel.setVerificationState(it) }
                                    )
                                }
                            }
                        }
                    }.onFailure { Log.i(tag, "exception reading qr-code: ${it.message}") }
                }
            }
        )

        Column(
            Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Admission Control",
                style = MaterialTheme.typography.headlineLarge
            )
            if (verificationState.progress == Progress.WAITING_FOR_INVITATION) {
                Text(
                    "full vaccination required (3/3)",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                if (verificationState.isVaccinationCertificate) {
                    Text(
                        "vaccination certificate ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.isFullVaccinated) {
                    Text(
                        "fully vaccinated (3/3) ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.isInsuranceCertificate) {
                    Text(
                        "insurance certificate ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.portrait != null) {
                    Text(
                        "portrait ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.isTrustedIssuer) {
                    Text(
                        "trusted issuers ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.isAssertionVerifiedSuccessfully) {
                    Text(
                        "assertions verified ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.isAuthenticationVerifiedSuccessfully) {
                    Text(
                        "holder authenticated ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                if (verificationState.isPortraitVerified) {
                    Text(
                        "portrait verified ✓",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                verificationState.message?.let {
                    Text(
                        color = if (verificationState.isSuccess()) Color(0, 0xC0, 0) else Color.Red,
                        text = it,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Box(
            Modifier.align(Center),
            contentAlignment = Center
        ) {
            if (verificationState.progress == Progress.PORTRAIT_VERIFICATION) {
                // show title and portrait
                Button(
                    onClick = {
                        mainViewModel.setVerificationState(
                            verificationState.copy(
                                progress = Progress.COMPLETED,
                                isPortraitVerified = true,
                            ).apply {
                                message = " Access ${if (isSuccess()) "" else "not"} granted!"
                            }
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = White
                    ),
                ) {
                    verificationState.portrait?.let {
                        Image(
                            it,
                            contentDescription = "portrait",
                            modifier = Modifier.size(250.dp)
                        )
                    } ?: Image(
                        painterResource(R.drawable.portrait_blau_gematik),
                        "unknown portrait",
                        modifier = Modifier.size(250.dp)
                    )
                }
            } else {
                Image(
                    painterResource(
                        if (verificationState.progress != Progress.COMPLETED) {
                            R.drawable.unknown
                        } else {
                            if (verificationState.isSuccess()) R.drawable.approved else R.drawable.denied
                        }
                    ),
                    "status",
                    modifier = Modifier.size(250.dp)
                )
            }
        }

        Button(
            onClick = {
                mainViewModel.resetVerificationState()
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

        if (verificationState.progress == Progress.WAITING_FOR_OFFER || verificationState.progress == Progress.WAITING_FOR_SUBMIT) {
            Dialog(
                onDismissRequest = { },
                DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Box(
                    contentAlignment = Center,
                    modifier = Modifier
                        .size(300.dp)
                        .background(White, shape = RoundedCornerShape(8.dp))
                ) {
                    CircularProgressIndicator(
                        Modifier.align(Center)
                    )
                    Text(
                        modifier = Modifier
                            .align(BottomCenter)
                            .padding(20.dp),
                        text = verificationState.progress.message
                    )
                }
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