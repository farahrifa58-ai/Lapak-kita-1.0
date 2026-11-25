package com.example.data.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
): Nothing {
    val auth = Firebase.auth
    val user = auth.currentUser

    val providerArray = JSONArray()
    user?.providerData?.forEach { info ->
        providerArray.put(
            JSONObject().apply {
                put("providerId", info.providerId)
                put("displayName", info.displayName ?: JSONObject.NULL)
                put("email", info.email ?: JSONObject.NULL)
                put("photoUrl", info.photoUrl?.toString() ?: JSONObject.NULL)
            }
        )
    }

    val payload = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.value)
        put("path", path ?: JSONObject.NULL)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid ?: JSONObject.NULL)
                put("email", user?.email ?: JSONObject.NULL)
                put("emailVerified", user?.isEmailVerified ?: false)
                put("isAnonymous", user?.isAnonymous ?: false)
                put("tenantId", user?.tenantId ?: JSONObject.NULL)
                put("providerInfo", providerArray)
            }
        )
    }

    val message = payload.toString()
    Log.e("FirestoreError", message, error)
    throw IllegalStateException("Firestore Error: $message", error)
}
