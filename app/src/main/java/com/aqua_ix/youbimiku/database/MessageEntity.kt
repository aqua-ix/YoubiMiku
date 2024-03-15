package com.aqua_ix.youbimiku.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val isRightMessage: Boolean,
    val text: String,
    val hideIcon: Boolean,
    val sendTime: Long?
)