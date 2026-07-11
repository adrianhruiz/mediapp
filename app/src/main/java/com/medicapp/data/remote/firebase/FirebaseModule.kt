package com.medicapp.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

object FirebaseModule {

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(
                    persistentCacheSettings {
                        setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    },
                )
            }
        }
    }
}
