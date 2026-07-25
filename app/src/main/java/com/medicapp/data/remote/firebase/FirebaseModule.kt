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
            // Con la caché sin límite, las consultas que se resuelven en local acaban recorriendo
            // todo lo guardado. Esto deja que Firestore se cree sus propios índices en el móvil
            // para las consultas que repite, que aquí son las de siempre: el historial por fecha,
            // las pautas activas, las dosis pendientes.
            persistentCacheIndexManager?.enableIndexAutoCreation()
        }
    }
}
