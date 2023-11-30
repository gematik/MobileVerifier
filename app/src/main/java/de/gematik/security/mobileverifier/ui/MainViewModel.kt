package de.gematik.security.mobileverifier.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: ViewModel() {
    private val _verificationState = MutableStateFlow(VerificationState())
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    fun setVerificationState(verificationState: VerificationState){
        _verificationState.value = verificationState
    }

    fun resetVerificationState(){
        _verificationState.value = VerificationState()
    }
}