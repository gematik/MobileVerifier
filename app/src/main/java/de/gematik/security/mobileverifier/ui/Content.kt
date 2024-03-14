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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.gematik.security.mobileverifier.R
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme

@Composable
fun Content(verificationState: VerificationState, modifier: Modifier = Modifier, onVisualVerificationConfirmed: () -> Unit) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (verificationState.progress == Progress.PORTRAIT_VERIFICATION) {
            // show title and portrait
            Button(
                onClick = {
                    if(verificationState.progress == Progress.PORTRAIT_VERIFICATION) onVisualVerificationConfirmed()
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
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
}

@Preview
@Composable
private fun  ContentPreview() {
    val verificationState = VerificationState(Progress.COMPLETED)
    MobileVerifierTheme {
        Content(verificationState = verificationState){}
    }
}