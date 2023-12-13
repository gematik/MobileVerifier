package de.gematik.security.mobileverifier.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import de.gematik.security.mobileverifier.ui.theme.MobileVerifierTheme

@Composable
fun Status(verificationState: VerificationState, modifier: Modifier = Modifier){
    Column(
        modifier,
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
}

@Preview(
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)

@Composable
private fun  StatusPreview() {
    val verificationState = VerificationState(Progress.WAITING_FOR_INVITATION)
    MobileVerifierTheme {
        Box{
            Status(modifier = Modifier.align(Alignment.TopCenter), verificationState = verificationState)
        }
    }
}