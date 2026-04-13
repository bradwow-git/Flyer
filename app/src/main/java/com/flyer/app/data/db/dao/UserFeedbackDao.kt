package com.flyer.app.data.db.dao

import androidx.room.*
import com.flyer.app.data.db.entities.UserFeedback

@Dao
interface UserFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(feedback: UserFeedback)

    @Query("SELECT * FROM user_feedback WHERE canonicalTrackId = :trackId LIMIT 1")
    suspend fun getFeedbackForTrack(trackId: Long): UserFeedback?

    @Query("SELECT * FROM user_feedback WHERE feedbackType = :type")
    suspend fun getAllByType(type: String): List<UserFeedback>

    @Query("DELETE FROM user_feedback WHERE canonicalTrackId = :trackId")
    suspend fun clearFeedback(trackId: Long)

    @Query("DELETE FROM user_feedback WHERE canonicalTrackId = :trackId")
    suspend fun deleteFeedbackForTrack(trackId: Long)
}