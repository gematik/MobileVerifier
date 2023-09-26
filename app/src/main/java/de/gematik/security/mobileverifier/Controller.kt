package de.gematik.security.mobileverifier

import android.util.Log
import androidx.lifecycle.lifecycleScope
import de.gematik.security.credentialExchangeLib.connection.websocket.WsConnection
import de.gematik.security.credentialExchangeLib.extensions.createUri
import de.gematik.security.credentialExchangeLib.protocols.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.URI
import java.util.*

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

    fun acceptInvitation(invitation: Invitation, updateState: (VerificationResult) -> Unit) {
        val serviceEndpoint = invitation.service[0].serviceEndpoint
        mainActivity.lifecycleScope.launch {
            Log.d(TAG, "invitation accepted from ${serviceEndpoint.host}")
            PresentationExchangeVerifierProtocol.connect(
                WsConnection,
                to = createUri(serviceEndpoint.host, serviceEndpoint.port),
                invitationId = UUID.fromString(invitation.id)
            ) {
                while (true) {
                    val message = runCatching {
                        it.receive()
                    }.onFailure { Log.d(TAG, "exception: ${it.message}") }.getOrNull() ?: break
                    Log.d(TAG, "received: ${message.type}")
                    if (!handleIncomingMessage(it, message, updateState)) break
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(
        protocolInstance: PresentationExchangeVerifierProtocol,
        message: LdObject,
        updateState: (VerificationResult) -> Unit
    ): Boolean {
        val type = message.type ?: return true //ignore
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
        updateState: (VerificationResult) -> Unit
    ): Boolean {
        if (  // only vaccination certificates are accepted
            !presentationOffer.inputDescriptor.frame.type.contains("VaccinationCertificate")
        ) {
            updateState(VerificationResult(message = "no vaccination certificate"))
            return false
        }
        val credentialSubject = presentationOffer.inputDescriptor.frame.credentialSubject
        if ( // vaccination needs to be disclosed
            credentialSubject?.get("@explicit")?.jsonPrimitive?.boolean == true &&
            !credentialSubject.containsKey("order")
        ) {
            updateState(VerificationResult(message = "vaccination status needs to be disclosed"))
            return false
        }
        val frame = Credential(
            // frame requesting vaccination status only
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
                            "id" to JsonObject(mapOf())
                        )
                    )
                )
            ),
        )
        protocolInstance.requestPresentation(
            PresentationRequest(
                inputDescriptor = Descriptor(
                    presentationOffer.inputDescriptor.id,
                    frame
                )
            )
        )
        return true
    }

    private suspend fun handlePresentationSubmit(
        protocolInstance: PresentationExchangeVerifierProtocol,
        presentationSubmit: PresentationSubmit,
        updateState: (VerificationResult) -> Unit
    ): Boolean {
        val verificationResult = verifyPresentation(presentationSubmit.presentation)
        updateState(verificationResult)
        return false
    }

    private fun handleClose(
        protocolInstance: PresentationExchangeVerifierProtocol,
        close: Close,
        updateState: (VerificationResult) -> Unit
    ): Boolean {
        updateState(VerificationResult(message = "no sufficient credential received"))
        return false
    }

    private fun verifyPresentation(presentation: Presentation): VerificationResult {
        val verificationResult = VerificationResult()
        val credential = presentation.verifiableCredential.get(0)
        if (!(credential.type.contains("VaccinationCertificate")))
            return verificationResult.apply {
                message = "no vaccination certificate"
            }
        verificationResult.isVaccinationCertificate = true
        if (!(credential.credentialSubject?.get("order")?.jsonPrimitive?.content == "3/3"))
            return verificationResult.apply {
                message = "patient isn't fully vaccinated"
            }
        verificationResult.isFullVaccinated = true
        if (credential.issuer.toString() != Settings.trustedIssuer.toString())
            return verificationResult.apply {
                message = "no trusted issuer"
            }
        verificationResult.isTrustedIssuer = true

        if (!(credential.verify()))
            return verificationResult.apply { message = "verification of assertion proof failed" }
        verificationResult.isAssertionVerifiedSuccessfully = true
        val holderId = credential
            .credentialSubject
            ?.get("recipient")?.jsonObject
            ?.get("id")?.jsonPrimitive?.content
            ?.let { URI.create(it) }
            ?: return verificationResult.apply {
                message = "holder id required"
            }
        val authProofCreator = presentation
            .proof
            ?.get(0)
            ?.creator
            ?: return verificationResult.apply {
                message = "auth proof creator missing"
            }
        val authProofVerificationMethod = presentation
            .proof
            ?.get(0)
            ?.verificationMethod
            ?: return verificationResult.apply {
                message = "auth proof verification method missing"
            }
        if (!(holderId == authProofCreator))
            return verificationResult.apply {
                message = "presentation wasn't created by holder"
            }
        if (!(holderId.schemeSpecificPart == authProofVerificationMethod.schemeSpecificPart))
            return verificationResult.apply {
                message = "invalid verification method - scheme mismatch"
            }
        if (!(presentation.verify()))
            return verificationResult.apply {
                message = "auth proof verification failed"
            }
        verificationResult.isAuthenticationVerifiedSuccessfully = true
        return verificationResult
    }

}