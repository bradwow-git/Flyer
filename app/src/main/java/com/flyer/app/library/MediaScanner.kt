package com.flyer.app.library

import android.content.Context
import android.provider.MediaStore
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.CanonicalTrack
import com.flyer.app.data.db.entities.TrackFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanner(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val trackDao = db.trackDao()

    suspend fun scanDevice(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null,
            "${MediaStore.Audio.Media.ARTIST} ASC"
        )?.use { cursor ->
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathCol) ?: continue
                val title = cursor.getString(titleCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val duration = cursor.getLong(durationCol)

                if (trackDao.getTrackByPath(path) != null) continue

                val canonicalId = trackDao.insertCanonicalTrack(
                    CanonicalTrack(
                        normalizedTitle = title.lowercase().trim(),
                        normalizedArtist = artist.lowercase().trim(),
                        normalizedAlbum = album.lowercase().trim(),
                        durationMs = duration
                    )
                )

                trackDao.insertTrackFile(
                    TrackFile(
                        canonicalTrackId = canonicalId,
                        filePath = path,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration
                    )
                )
                count++
            }
        }
        count
    }
}