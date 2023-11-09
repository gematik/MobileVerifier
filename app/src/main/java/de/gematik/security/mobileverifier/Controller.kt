package de.gematik.security.mobileverifier

import android.util.Log
import androidx.lifecycle.lifecycleScope
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.DidCommV2OverHttpConnection
import de.gematik.security.credentialExchangeLib.connection.DidCommV2OverHttp.createPeerDID
import de.gematik.security.credentialExchangeLib.connection.Invitation
import de.gematik.security.credentialExchangeLib.connection.websocket.WsConnection
import de.gematik.security.credentialExchangeLib.json
import de.gematik.security.credentialExchangeLib.protocols.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.security.InvalidParameterException
import java.util.UUID

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

    val networkInterface =
        NetworkInterface.getNetworkInterfaces().toList().first { it.name.lowercase().startsWith("wlan") }
    val address = networkInterface.inetAddresses.toList().first { it is Inet4Address }

    fun acceptInvitation(invitation: Invitation, updateState: (VerificationResult) -> Unit) {
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
                    URI.create(
                        createPeerDID(
                            serviceEndpoint = URI(
                                "http",
                                null,
                                address.hostAddress,
                                9095,
                                "/didcomm",
                                null,
                                null
                            ).toString()
                        )
                    )
                )
            }

            else -> {
                throw InvalidParameterException("unsupported URI scheme: ${invitation.from.scheme}")
            }
        }
        mainActivity.lifecycleScope.launch {
            Log.d(TAG, "invitation accepted from ${to}")
            PresentationExchangeVerifierProtocol.connect(
                connectionFactory,
                to = to,
                from = from,
                invitationId = invitation.id
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
        updateState: (VerificationResult) -> Unit
    ): Boolean {
        // vaccination status and personal id (portrait) are required
        val vaccinationDescriptor =
            presentationOffer.inputDescriptor.firstOrNull { it.frame.type.contains("VaccinationCertificate") }
        if (vaccinationDescriptor == null) {
            updateState(VerificationResult(message = "no vaccination certificate descriptor"))
            return false
        }
        val vaccinationCredentialSubject = vaccinationDescriptor.frame.credentialSubject
        if ( // check if holder allows disclosure of vaccination status
            vaccinationCredentialSubject?.get("@explicit")?.jsonPrimitive?.boolean == true &&
            !vaccinationCredentialSubject.containsKey("order")
        ) {
            updateState(VerificationResult(message = "vaccination status needs to be disclosed"))
            return false
        }
        val insuranceDescriptor =
            presentationOffer.inputDescriptor.firstOrNull { it.frame.type.contains("InsuranceCertificate") }
        if (insuranceDescriptor == null) {
            updateState(VerificationResult(message = "no insurance certificate descriptor"))
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
                    updateState(VerificationResult(message = "portrait needs to be disclosed"))
                    return false
                }
            } else {
                updateState(VerificationResult(message = "portrait needs to be disclosed"))
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
        Log.d(
            TAG, "sent: ${presentationRequest.type}"
        )
        return true
    }

    private suspend fun handlePresentationSubmit(
        protocolInstance: PresentationExchangeVerifierProtocol,
        presentationSubmit: PresentationSubmit,
        updateState: (VerificationResult) -> Unit
    ): Boolean {
        val verificationResult = verifyPresentation(presentationSubmit.presentation)
        Log.d(TAG, "presentation verified: ${verificationResult}")
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
        if(presentation.verifiableCredential.size != 2){
            return verificationResult.apply {
                message = "two credentials expected, but ${presentation.verifiableCredential.size} received"
            }
        }
        val vaccinationCredential = presentation.verifiableCredential.get(0)
        val insuranceCredential = presentation.verifiableCredential.get(1)

        if (!(vaccinationCredential.type.contains("VaccinationCertificate")))
            return verificationResult.apply {
                message = "vaccination credential wrong type"
            }
        verificationResult.isVaccinationCertificate = true

        if (vaccinationCredential.credentialSubject?.get("order")?.jsonPrimitive?.content != "3/3")
            return verificationResult.apply {
                message = "patient isn't fully vaccinated"
            }
        verificationResult.isFullVaccinated = true

        if (!(insuranceCredential.type.contains("InsuranceCertificate")))
            return verificationResult.apply {
                message = "insurance credential wrong type"
            }
        verificationResult.isInsuranceCertificate = true

        if (vaccinationCredential.issuer.toString() != Settings.trustedIssuer.toString())
            return verificationResult.apply {
                message = "vaccination trusted issuer verification failed"
            }
        if (insuranceCredential.issuer.toString() != Settings.trustedIssuer.toString())
            return verificationResult.apply {
                message = "insurance trusted issuer verification failed"
            }
        verificationResult.isTrustedIssuer = true
        verificationResult.portrait = insuranceCredential.credentialSubject
            ?.get("insurant")?.jsonObject
            ?.get("portrait")?.jsonPrimitive?.content
        if(verificationResult.portrait == null){
            verificationResult.message = "portrait missing"
        }
        if (!(vaccinationCredential.verify()))
            return verificationResult.apply {
                message = "verification of assertion proof (vaccination) failed"
            }
        if (!(vaccinationCredential.verify()))
            return verificationResult.apply {
                message = "verification of assertion proof (insurance) failed"
            }
        verificationResult.isAssertionVerifiedSuccessfully = true

        val vaccinationHolderId = vaccinationCredential
            .credentialSubject
            ?.get("recipient")?.jsonObject
            ?.get("id")?.jsonPrimitive?.content
            ?.let { URI.create(it) }
            ?: return verificationResult.apply {
                message = "vaccination holder id required"
            }
        val insuranceHolderId = insuranceCredential
            .credentialSubject
            ?.get("insurant")?.jsonObject
            ?.get("id")?.jsonPrimitive?.content
            ?.let { URI.create(it) }
            ?: return verificationResult.apply {
                message = "insurance holder id required"
            }
        if (vaccinationHolderId != insuranceHolderId)
            return verificationResult.apply {
                message = "vaccination holder and insurance holder don't match"
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
        if (vaccinationHolderId != authProofCreator)
            return verificationResult.apply {
                message = "presentation wasn't created by holder"
            }
        if (vaccinationHolderId.schemeSpecificPart != authProofVerificationMethod.schemeSpecificPart)
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