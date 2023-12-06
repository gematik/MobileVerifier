package de.gematik.security.mobileverifier.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel: ViewModel() {
    private val tag = MainViewModel::class.java.name

    private val _verificationState = MutableStateFlow(VerificationState())
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    fun setVerificationState(verificationState: VerificationState){
        _verificationState.value = verificationState
        Log.d(tag, "verificationState set: ${verificationState}"  )
    }

    fun resetVerificationState(){
        _verificationState.value = VerificationState()
        Log.d(tag, "verificationState reset:")
    }
}