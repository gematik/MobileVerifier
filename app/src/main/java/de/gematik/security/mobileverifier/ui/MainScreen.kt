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

package de.gematik.security.mobileverifier.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel = viewModel(), onQrCodeScanned: (Invitation) -> Unit) {
    val verificationState by mainViewModel.verificationState.collectAsState()

    Box {

        Status(
            verificationState = verificationState,
            modifier = Modifier.align(TopCenter)
        )

        Content(
            verificationState = verificationState,
            modifier = Modifier.align(Center)
        ) {
            mainViewModel.setVerificationState(
                verificationState.copy(
                    progress = Progress.COMPLETED,
                    isPortraitVerified = true,
                ).apply {
                    message = "Access ${if (isSuccess()) "" else "not"} granted!"
                }
            )
        }

        ScanQrCodeButton(
            modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            onQrCodeScanned
        )
    }

    ProgressDialog(verificationState)
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)

@Composable
fun DefaultPreview() {
    MobileVerifierTheme {
        MainScreen(){}
    }
}