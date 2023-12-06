package de.gematik.security.mobileverifier

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.DidCommV2OverHttpConnection
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.connection.websocket.WsConnection
import de.gematik.security.credentialExchangeLib.protocols.*
import de.gematik.security.mobileverifier.Settings.localEndpoint
import de.gematik.security.mobileverifier.Settings.ownDid
import de.gematik.security.mobileverifier.Settings.ownServiceEndpoint
import de.gematik.security.mobileverifier.ui.MainViewModel
import de.gematik.security.mobileverifier.ui.Progress
import de.gematik.security.mobileverifier.ui.VerificationState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.URI
import java.security.InvalidParameterException
import java.util.*

class Controller(val mainActivity: MainActivity, val mainViewModel: MainViewModel) {
    private val tag = Controller::class.java.name

    suspend fun acceptInvitation(invitation: Invitation, updateState: (VerificationState) -> Unit) {
        val (connectionFactory, to, from) = when (invitation.from.scheme) {
            "ws", "wss" -> {
                Triple(
                    WsConnection,
                    invitation.from,
                    null
                )
            }

            "did" -> {
                Triple(
                    DidCommV2OverHttpConnection,
                    invitation.from,
                    ownDid
                )
            }

            else -> {
                throw InvalidParameterException("unsupported URI scheme: ${invitation.from.scheme}")
            }
        }
        mainActivity.lifecycleScope.launch {
            Log.i(tag, "invitation accepted from ${to}")
            updateState(VerificationState(progress = Progress.WAITING_FOR_OFFER))
            PresentationExchangeVerifierProtocol.connect(
                connectionFactory,
                to = to,
                from = from,
                invitationId = invitation.id
            ) {
                while (true) {
                    val message = runCatching {
                        it.receive()
                    }.onFailure { Log.d(tag, "exception: ${it.message}") }.getOrNull() ?: break
                    Log.i(tag, "received: ${message.type}")
                    if (!handleIncomingMessage(it, message, updateState)) break
                }
            }
        }
    }

    suspend fun start() {
        // start didcomm listener
        PresentationExchangeVerifierProtocol.listen(DidCommV2OverHttpConnection, ownServiceEndpoint) {
            listen(it)
        }

        // start WsSocket listener
        PresentationExchangeVerifierProtocol.listen(WsConnection, localEndpoint) {
            listen(it)
        }

    }

    private suspend fun listen(protocol: PresentationExchangeVerifierProtocol) {
        // invitation accept messages are always accepted and processed.
        mainViewModel.setVerificationState(VerificationState(progress = Progress.WAITING_FOR_OFFER))
        while (protocol.protocolState.state != PresentationExchangeVerifierProtocol.State.CLOSED) {
            val message = runCatching {
                protocol.receive()
            }.onFailure { Log.d(tag, "exception: ${it.message}") }.getOrNull() ?: break
            Log.i(tag, "received: ${message.type}")
            if (!handleIncomingMessage(protocol, message) { mainViewModel.setVerificationState(it) }) break
        }
    }
}

private suspend fun handleIncomingMessage(
    protocolInstance: PresentationExchangeVerifierProtocol,
    message: LdObject,
    updateState: (VerificationState) -> Unit
): Boolean {
    val type = message.type
    return when {
        type.contains("Close") -> handleClose(
            protocolInstance,
            message as Close,
            updateState
        ) // close connection
        type.contains("PresentationOffer") -> handlePresentationOffer(
            protocolInstance,
            message as PresentationOffer,
            updateState
        )

        type.contains("PresentationSubmit") -> handlePresentationSubmit(
            protocolInstance,
            message as PresentationSubmit,
            updateState
        )

        else -> true //ignore
    }
}

private suspend fun handlePresentationOffer(
    protocolInstance: PresentationExchangeVerifierProtocol,
    presentationOffer: PresentationOffer,
    updateState: (VerificationState) -> Unit
): Boolean {
    // vaccination status and personal id (portrait) are required
    val vaccinationDescriptor =
        presentationOffer.inputDescriptor.firstOrNull { it.frame.type.contains("VaccinationCertificate") }
    if (vaccinationDescriptor == null) {
        updateState(VerificationState(progress = Progress.COMPLETED, message = "no or wrong vaccination certificate"))
        return false
    }
    val vaccinationCredentialSubject = vaccinationDescriptor.frame.credentialSubject
    if ( // check if holder allows disclosure of vaccination status
        vaccinationCredentialSubject?.get("@explicit")?.jsonPrimitive?.boolean == true &&
        !vaccinationCredentialSubject.containsKey("order")
    ) {
        updateState(VerificationState(progress = Progress.COMPLETED, message = "vaccination status needs to be disclosed"))
        return false
    }
    val insuranceDescriptor =
        presentationOffer.inputDescriptor.firstOrNull { it.frame.type.contains("InsuranceCertificate") }
    if (insuranceDescriptor == null) {
        updateState(VerificationState(progress = Progress.COMPLETED, message = "no insurance certificate for identity proofing"))
        return false
    }
    val insuranceCredentialSubject = insuranceDescriptor.frame.credentialSubject?.jsonContent
    // check if holder allows disclosure of portrait
    if (insuranceCredentialSubject?.get("@explicit")?.jsonPrimitive?.boolean == true) {
        if (insuranceCredentialSubject.contains("insurant")) {
            val insurant = insuranceCredentialSubject["insurant"]?.jsonObject
            if (!(insurant?.get("@explicit")?.jsonPrimitive?.boolean == true) &&
                insurant?.contains("portrait") == false
            ) {
                updateState(VerificationState(progress = Progress.COMPLETED, message = "portrait needs to be disclosed"))
                return false
            }
        } else {
            updateState(VerificationState(progress = Progress.COMPLETED, message = "portrait needs to be disclosed"))
            return false
        }
    }
    val presentationRequest = PresentationRequest(
        inputDescriptor = listOf(
            Descriptor(
                id = UUID.randomUUID().toString(),
                frame = Credential(
                    // frame requesting vaccination status and holder id
                    atContext = Credential.DEFAULT_JSONLD_CONTEXTS + listOf(URI.create("https://w3id.org/vaccination/v1")),
                    type = Credential.DEFAULT_JSONLD_TYPES + listOf("VaccinationCertificate"),
                    credentialSubject = JsonLdObject(
                        mapOf(
                            "@explicit" to JsonPrimitive(true),
                            "@requireAll" to JsonPrimitive(true),
                            "type" to JsonArray(listOf(JsonPrimitive("VaccinationEvent"))),
                            "order" to JsonArray(listOf(JsonPrimitive("3/3"))),
                            "recipient" to JsonObject(
                                mapOf(
                                    "@explicit" to JsonPrimitive(true),
                                    "type" to JsonArray(listOf(JsonPrimitive("VaccineRecipient"))),
                                    "id" to JsonObject(mapOf()) // holder id
                                )
                            )
                        )
                    ),
                )
            ),
            Descriptor(
                id = UUID.randomUUID().toString(),
                frame = Credential(
                    // frame requesting portrait and holder id
                    atContext = Credential.DEFAULT_JSONLD_CONTEXTS + listOf(URI.create("https://gematik.de/vsd/v1")),
                    type = Credential.DEFAULT_JSONLD_TYPES + listOf("InsuranceCertificate"),
                    credentialSubject = JsonLdObject(
                        mapOf(
                            "@explicit" to JsonPrimitive(true),
                            "@requireAll" to JsonPrimitive(true),
                            "type" to JsonArray(listOf(JsonPrimitive("Insurance"))),
                            "id" to JsonObject(mapOf()), // holder id
                            "insurant" to JsonObject(
                                mapOf(
                                    "@explicit" to JsonPrimitive(true),
                                    "type" to JsonArray(listOf(JsonPrimitive("Insurant"))),
                                    "portrait" to JsonObject(mapOf())
                                )
                            )
                        )
                    ),
                )
            )
        )
    )
    protocolInstance.requestPresentation(
        presentationRequest
    )
    Log.i(tag, "sent: ${presentationRequest.type}")
    updateState(VerificationState( progress = Progress.WAITING_FOR_SUBMIT ))
    return true
}

private suspend fun handlePresentationSubmit(
    protocolInstance: PresentationExchangeVerifierProtocol,
    presentationSubmit: PresentationSubmit,
    updateState: (VerificationState) -> Unit
): Boolean {
    val verificationState = verifyPresentation(presentationSubmit.presentation)
    Log.i(tag, "presentation verified: ${verificationState}")
    updateState(verificationState)
    return false
}

private fun handleClose(
    protocolInstance: PresentationExchangeVerifierProtocol,
    close: Close,
    updateState: (VerificationState) -> Unit
): Boolean {
    updateState(VerificationState(message = "no sufficient credential received"))
    return false
}

private fun verifyPresentation(presentation: Presentation): VerificationState {
    val verificationState = VerificationState(progress = Progress.COMPLETED)
    if (presentation.verifiableCredential.size != 2) {
        return verificationState.apply {
            message = "two credentials expected, but ${presentation.verifiableCredential.size} received"
        }
    }
    val vaccinationCredential = presentation.verifiableCredential.get(0)
    val insuranceCredential = presentation.verifiableCredential.get(1)

    if (!(vaccinationCredential.type.contains("VaccinationCertificate")))
        return verificationState.apply {
            message = "vaccination credential wrong type"
        }
    verificationState.isVaccinationCertificate = true

    if (vaccinationCredential.credentialSubject?.get("order")?.jsonPrimitive?.content != "3/3")
        return verificationState.apply {
            message = "patient isn't fully vaccinated"
        }
    verificationState.isFullVaccinated = true

    if (!(insuranceCredential.type.contains("InsuranceCertificate")))
        return verificationState.apply {
            message = "insurance credential wrong type"
        }
    verificationState.isInsuranceCertificate = true

    if (vaccinationCredential.issuer.toString() != Settings.trustedIssuer.toString())
        return verificationState.apply {
            message = "vaccination trusted issuer verification failed"
        }
    if (insuranceCredential.issuer.toString() != Settings.trustedIssuer.toString())
        return verificationState.apply {
            message = "insurance trusted issuer verification failed"
        }
    verificationState.isTrustedIssuer = true
    verificationState.portrait = insuranceCredential.credentialSubject
        ?.get("insurant")?.jsonObject
        ?.get("portrait")?.jsonPrimitive?.content
        ?.let{Base64.getDecoder().decode(it)}
        ?.let{ BitmapFactory.decodeByteArray(it, 0, it.size, null).asImageBitmap()}
    if (verificationState.portrait == null) {
        verificationState.message = "portrait missing"
    }
    if (!(vaccinationCredential.verify()))
        return verificationState.apply {
            message = "verification of assertion proof (vaccination) failed"
        }
    if (!(vaccinationCredential.verify()))
        return verificationState.apply {
            message = "verification of assertion proof (insurance) failed"
        }
    verificationState.isAssertionVerifiedSuccessfully = true

    val vaccinationHolderId = vaccinationCredential
        .credentialSubject
        ?.get("recipient")?.jsonObject
        ?.get("id")?.jsonPrimitive?.content
        ?.let { URI.create(it) }
        ?: return verificationState.apply {
            message = "vaccination holder id required"
        }
    val insuranceHolderId = insuranceCredential
        .credentialSubject
        ?.get("insurant")?.jsonObject
        ?.get("id")?.jsonPrimitive?.content
        ?.let { URI.create(it) }
        ?: return verificationState.apply {
            message = "insurance holder id required"
        }
    if (vaccinationHolderId != insuranceHolderId)
        return verificationState.apply {
            message = "vaccination holder and insurance holder don't match"
        }
    val authProofCreator = presentation
        .proof
        ?.get(0)
        ?.creator
        ?: return verificationState.apply {
            message = "auth proof creator missing"
        }
    val authProofVerificationMethod = presentation
        .proof
        ?.get(0)
        ?.verificationMethod
        ?: return verificationState.apply {
            message = "auth proof verification method missing"
        }
    if (vaccinationHolderId != authProofCreator)
        return verificationState.apply {
            message = "presentation wasn't created by holder"
        }
    if (vaccinationHolderId.schemeSpecificPart != authProofVerificationMethod.schemeSpecificPart)
        return verificationState.apply {
            message = "invalid verification method - scheme mismatch"
        }
    if (!(presentation.verify()))
        return verificationState.apply {
            message = "auth proof verification failed"
        }
    verificationState.apply {
        isAuthenticationVerifiedSuccessfully = true
        progress = Progress.PORTRAIT_VERIFICATION
        message = "Tap portrait to confirm visual verification!"
    }
    return verificationState
}