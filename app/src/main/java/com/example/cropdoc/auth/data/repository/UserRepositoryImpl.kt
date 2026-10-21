
package com.example.cropdoc.auth.data.repository

import android.util.Log
import com.example.cropdoc.auth.data.models.User
import com.example.cropdoc.auth.domain.Result
import com.example.cropdoc.auth.domain.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    companion object {
        private const val TAG = "UserRepository"
        private const val USERS_COLLECTION = "users"
        private const val USER_INFO_DOCUMENT = "info"
    }

    override suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): Flow<Result<Boolean>> = flow {
        try {
            val normalizedEmail = email.trim()

            // 1. Create the account in Firebase Authentication.
            val authResult = auth.createUserWithEmailAndPassword(
                normalizedEmail,
                password
            ).await()

            val uid = authResult.user?.uid
                ?: throw IllegalStateException(
                    "Account created, but Firebase UID is missing"
                )

            // 2. Create the user's Firestore profile.
            val user = User(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                email = normalizedEmail
            )

            saveUserToFirestore(uid, user)

            Log.d(TAG, "Sign-up and profile save successful for UID: $uid")
            emit(Result.Success(true))

        } catch (e: Exception) {
            Log.e(TAG, "Sign-up or Firestore profile save failed", e)
            emit(Result.Error(e))
        }
    }

    override suspend fun login(
        email: String,
        password: String
    ): Flow<Result<Boolean>> = flow {
        try {
            val normalizedEmail = email.trim()

            // 1. Authenticate the user.
            val authResult = auth.signInWithEmailAndPassword(
                normalizedEmail,
                password
            ).await()

            val firebaseUser = authResult.user
                ?: throw IllegalStateException(
                    "Sign-in succeeded, but Firebase user is missing"
                )

            val uid = firebaseUser.uid

            // 2. Check whether the Firestore profile exists.
            val userDocument = firestore
                .collection(USERS_COLLECTION)
                .document(uid)
                .collection(USER_INFO_DOCUMENT)
                .document(USER_INFO_DOCUMENT)
                .get()
                .await()

            // 3. Create a fallback profile if one does not exist.
            if (!userDocument.exists()) {
                val fallbackUser = User(
                    firstName = normalizedEmail.substringBefore("@"),
                    lastName = "",
                    email = normalizedEmail
                )

                saveUserToFirestore(uid, fallbackUser)

                Log.d(TAG, "Created missing profile for UID: $uid")
            }

            Log.d(TAG, "Login and profile check successful for UID: $uid")
            emit(Result.Success(true))

        } catch (e: Exception) {
            Log.e(TAG, "Login or Firestore profile check failed", e)
            emit(Result.Error(e))
        }
    }

    private suspend fun saveUserToFirestore(
        uid: String,
        user: User
    ) {
        val userData = mapOf(
            "firstName" to user.firstName,
            "lastName" to user.lastName,
            "email" to user.email
        )

        Log.d(TAG, "Writing Firestore document: $USERS_COLLECTION/$uid")

        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(USER_INFO_DOCUMENT)
            .document(USER_INFO_DOCUMENT)
            .set(userData)
            .await()

        Log.d(TAG, "Firestore write successful: $USERS_COLLECTION/$uid")
    }

    override suspend fun getCurrentUser(): Flow<Result<User>> = flow {
        try {
            val firebaseUser = auth.currentUser
                ?: throw IllegalStateException("User is not authenticated")

            val uid = firebaseUser.uid

            val document = firestore
                .collection(USERS_COLLECTION)
                .document(uid)
                .collection(USER_INFO_DOCUMENT)
                .document(USER_INFO_DOCUMENT)
                .get()
                .await()

            val user = if (document.exists()) {
                User(
                    firstName = document.getString("firstName") ?: "",
                    lastName = document.getString("lastName") ?: "",
                    email = document.getString("email")
                        ?: firebaseUser.email.orEmpty()
                )
            } else {
                // Return a fallback profile if the document is missing.
                User(
                    firstName = firebaseUser.email
                        ?.substringBefore("@") ?: "",
                    lastName = "",
                    email = firebaseUser.email.orEmpty()
                )
            }

            emit(Result.Success(user))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve current user", e)
            emit(Result.Error(e))
        }
    }

    override suspend fun logout(): Flow<Result<Boolean>> = flow {
        try {
            auth.signOut()

            Log.d(TAG, "User signed out successfully")
            emit(Result.Success(true))

        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
            emit(Result.Error(e))
        }
    }
}
