package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class GoogleSignInOutcome {
    data object Success : GoogleSignInOutcome()
    data object Cancelled : GoogleSignInOutcome()
    data class Error(val message: String) : GoogleSignInOutcome()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Native Android Google Sign-In powered by Jetpack Credential Manager (`GetSignInWithGoogleOption`).
 * Launches the system Google account picker directly on Android devices without WebView popups.
 */
object GoogleAuthHelper {

    suspend fun attemptAutoSignIn(context: Context): GoogleSignInOutcome {
        val activityContext = context.findActivity() ?: context
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome.Error("Konfigurasi Google Sign-In belum tersedia.")
        }

        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activityContext, request)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
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
        val activityContext = context.findActivity() ?: context
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome.Error("Konfigurasi Google Sign-In tidak ditemukan.")
        }

        val credentialManager = CredentialManager.create(activityContext)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(firebaseCredential).await()
                GoogleSignInOutcome.Success
            } else {
                GoogleSignInOutcome.Error("Kredensial akun Google tidak dikenali. Silakan coba lagi.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Sign-in cancelled by user")
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google account available on device", e)
            GoogleSignInOutcome.Error("Tidak ada akun Google yang aktif di HP ini. Silakan tambahkan akun Google di Pengaturan Android terlebih dahulu.")
        } catch (e: FirebaseNetworkException) {
            Log.e("Auth", "Network error during Google sign-in", e)
            GoogleSignInOutcome.Error("Gagal terhubung ke server. Periksa koneksi internet Anda.")
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Google sign-in failed: ${e.type} - ${e.message}", e)
            GoogleSignInOutcome.Error(
                "Gagal membuka pemilih akun Google: ${e.localizedMessage ?: "Pastikan Google Play Services aktif dan koneksi internet lancar."}"
            )
        } catch (e: Exception) {
            Log.e("Auth", "Unexpected Google sign-in error", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Terjadi kesalahan saat masuk dengan akun Google.")
        }
    }

    suspend fun signOut(context: Context) {
        val activityContext = context.findActivity() ?: context
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(activityContext).clearCredentialState(ClearCredentialStateRequest())
            }
        }
        Firebase.auth.signOut()
    }
}
