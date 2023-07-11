package de.gematik.security.mobileverifier

import android.util.Log
import androidx.lifecycle.lifecycleScope
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.protocols.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.net.URI

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

    fun acceptInvitation(invitation: Invitation, updateState: (State) -> Unit) {
        mainActivity.lifecycleScope.launch {
            invitation.service[0].serviceEndpoint?.let { serviceEndpoint ->
                Log.d(TAG, "invitation accepted from ${serviceEndpoint.host}")
                PresentationExchangeVerifierProtocol.connect(
                    WsConnection,
                    host = serviceEndpoint.host,
                    serviceEndpoint.port,
                ) {
                    it.sendInvitation(invitation)
                    while (true) {
                        val message = it.receive()
                        Log.d(TAG, "received: ${message.type}")
                        if (!handleIncomingMessage(it, message, updateState)) break
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(
        protocolInstance: PresentationExchangeVerifierProtocol,
        message: LdObject,
        updateState: (State) -> Unit
    ): Boolean {
        val type = message.type ?: return true //ignore
        return when {
            type.contains("Close") -> false // close connection
            type.contains("PresentationOffer") -> handlePresentationOffer(
                protocolInstance,
                message as PresentationOffer
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
        presentationOffer: PresentationOffer
    ): Boolean {
        if (  // only vaccination certificates are accepted
            !presentationOffer.inputDescriptor.frame.type.contains("VaccinationCertificate")
        ) return false
        val credentialSubject = presentationOffer.inputDescriptor.frame.credentialSubject
        if ( // vaccination needs to be disclosed
            credentialSubject?.get("@explicit")?.jsonPrimitive?.boolean == true &&
            !credentialSubject.containsKey("order")
        ) return false
        val frame = Credential( // frame requesting vaccination status only
            atContext = Credential.DEFAULT_JSONLD_CONTEXTS + listOf(URI.create("https://w3id.org/vaccination/v1")),
            type = Credential.DEFAULT_JSONLD_TYPES + listOf("VaccinationCertificate"),
            credentialSubject = JsonObject(
                mapOf(
                    "@explicit" to JsonPrimitive(true),
                    "type" to JsonArray(listOf(JsonPrimitive("VaccinationEvent"))),
                    "order" to JsonObject(mapOf()),
                    "recipient" to JsonObject(mapOf(
                        "@explicit" to JsonPrimitive(true),
                        "type" to JsonArray(listOf(JsonPrimitive("VaccineRecipient"))),
                        "id" to JsonObject(mapOf())
                    ))
                )
            )
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
        updateState: (State) -> Unit
    ): Boolean {
        val isSuccess = verifyPresentation(presentationSubmit.presentation)
        updateState(if (isSuccess) State.APPROVED else State.DENIED )
        return false
    }

    private fun verifyPresentation(presentation: Presentation) : Boolean{
        val credential = presentation.verifiableCredential.get(0)
        if (!(credential.type.contains("VaccinationCertificate"))) return false // is vaccination certificate?
        if (!(credential.credentialSubject?.get("order")?.jsonPrimitive?.content == "3/3")) return false // is patient fully vaccinated?
        val holderId = credential
            .credentialSubject
            ?.get("recipient")?.jsonObject
            ?.get("id")?.jsonPrimitive?.content
            ?.let { URI.create(it) }
            ?: return false // holder id required
        val authProofCreator = presentation
            .proof
            ?.get(0)
            ?.creator
            ?: return false
        val authProofVerificationMethod = presentation
            .proof
            ?.get(0)
            ?.verificationMethod
            ?: return false
        if (!(holderId == authProofCreator)) return false
        if (!(holderId.schemeSpecificPart == authProofVerificationMethod.schemeSpecificPart)) return false
        if (!(credential.verify())) return false // verify assertion proof
        if (!(presentation.verify())) return false // verify authentication proof
        return true
    }

}