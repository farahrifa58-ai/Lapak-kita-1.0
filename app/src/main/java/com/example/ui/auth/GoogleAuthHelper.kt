package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class GoogleSignInOutcome {
    data object Success : GoogleSignInOutcome()
    data object Cancelled : GoogleSignInOutcome()
    data class Error(val message: String) : GoogleSignInOutcome()
}

object GoogleAuthHelper {

    suspend fun attemptAutoSignIn(context: Context): GoogleSignInOutcome {
        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome.Error("Konfigurasi Google Sign-In belum tersedia.")
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(firebaseCredential).await()
                GoogleSignInOutcome.Success
            } else {
                GoogleSignInOutcome.Cancelled
            }
        } catch (e: NoCredentialException) {
            // No previously authorized account — stay calmly on AuthScreen
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Auto sign-in cancelled by user")
            GoogleSignInOutcome.Cancelled
        } catch (e: Exception) {
            Log.w("Auth", "Auto sign-in skipped: ${e.message}")
            GoogleSignInOutcome.Cancelled
        }
    }

    suspend fun signInWithGoogle(context: Context): GoogleSignInOutcome {
        val webClientId = context.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome.Error("Konfigurasi Google Sign-In tidak ditemukan.")
        }

        val credentialManager = CredentialManager.create(context)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(firebaseCredential).await()
                GoogleSignInOutcome.Success
            } else {
                GoogleSignInOutcome.Error("Kredensial akun Google tidak dikenali.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Sign-in cancelled by user")
            GoogleSignInOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Google sign-in failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Gagal masuk dengan akun Google.")
        } catch (e: Exception) {
            Log.e("Auth", "Unexpected Google sign-in error", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Terjadi kesalahan saat masuk.")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
        }
        Firebase.auth.signOut()
    }
}
