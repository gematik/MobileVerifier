package de.gematik.security.mobileverifier

import android.util.Log
import androidx.lifecycle.lifecycleScope
import de.gematik.security.credentialExchangeLib.connection.WsConnection
import de.gematik.security.credentialExchangeLib.protocols.*
import kotlinx.coroutines.launch

class Controller(val mainActivity: MainActivity) {
    val TAG = Controller::class.java.name

    fun acceptInvitation(invitation: Invitation, updateState: (Boolean) -> Unit) {
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

    private suspend fun handleIncomingMessage(protocolInstance: PresentationExchangeVerifierProtocol, message: LdObject, updateState: (Boolean) -> Unit): Boolean {
        val type = message.type ?: return true //ignore
        return when {
            type.contains("Close") -> false // close connection
            type.contains("PresentationOffer") -> handlePresentationOffer(protocolInstance, message as PresentationOffer)
            type.contains("PresentationSubmit") -> handlePresentationSubmit(protocolInstance, message as PresentationSubmit, updateState)
            else -> true //ignore
        }
    }

    private suspend fun handlePresentationOffer(protocolInstance: PresentationExchangeVerifierProtocol, presentationOffer: PresentationOffer) : Boolean {
        protocolInstance.requestPresentation(
            PresentationRequest(
                inputDescriptor = presentationOffer.inputDescriptor
            )
        )
        return true
    }

    private suspend fun handlePresentationSubmit(protocolInstance: PresentationExchangeVerifierProtocol, presentationSubmit: PresentationSubmit, updateState: (Boolean) -> Unit) : Boolean {
        val credential = presentationSubmit.presentation.verifiableCredential.get(0)
        if(credential.type.contains("VaccinationCertificate")){
            //TODO: check credential
            updateState(true)
        }
        return true
    }

}