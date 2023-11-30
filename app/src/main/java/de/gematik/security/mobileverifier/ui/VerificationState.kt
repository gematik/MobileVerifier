package de.gematik.security.mobileverifier.ui

import androidx.compose.ui.graphics.ImageBitmap

data class VerificationState(
    var progress: Progress = Progress.WAITING_FOR_INVITATION,
    var isVaccinationCertificate: Boolean = false,
    var isFullVaccinated: Boolean = false,
    var isInsuranceCertificate: Boolean = false,
    var portrait: ImageBitmap? = null,
    var isTrustedIssuer: Boolean = false,
    var isAssertionVerifiedSuccessfully: Boolean = false,
    var isAuthenticationVerifiedSuccessfully: Boolean = false,
    var isPortraitVerified: Boolean = false,
    var message: String? = null
) {
    fun isSuccess() = isVaccinationCertificate &&
            isFullVaccinated &&
            isInsuranceCertificate &&
            isTrustedIssuer &&
            isAssertionVerifiedSuccessfully &&
            isAuthenticationVerifiedSuccessfully &&
            isPortraitVerified
}

enum class Progress(val message: String) {
    WAITING_FOR_INVITATION ("Wait for invitation"),
    WAITING_FOR_OFFER ("Wait for offer"),
    WAITING_FOR_SUBMIT ("Wait for credential"),
    PORTRAIT_VERIFICATION("Verification of portrait"),
    COMPLETED ("Exchange completed")
}