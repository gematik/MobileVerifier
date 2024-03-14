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