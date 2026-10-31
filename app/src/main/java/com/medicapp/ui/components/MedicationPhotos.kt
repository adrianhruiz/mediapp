package com.medicapp.ui.components

/**
 * CIMA solo devuelve la miniatura (200 px, unos 4 KB), pero guarda la foto grande (1024 px,
 * cientos de KB) en la misma ruta cambiando `thumbnails` por `full`. Se deriva aquí para
 * pedirla solo cuando alguien la amplía, y vale también para las URLs ya guardadas.
 */
object MedicationPhotos {
    private const val THUMBNAIL_SEGMENT = "/fotos/thumbnails/"
    private const val FULL_SEGMENT = "/fotos/full/"

    fun fullSize(thumbnailUrl: String): String = thumbnailUrl.replace(THUMBNAIL_SEGMENT, FULL_SEGMENT)
}
