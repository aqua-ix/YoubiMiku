package com.aqua_ix.youbimiku.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity)

    /**
     * 新しいものから[limit]件を返す。表示は古い順なので呼び出し側で並びを反転する。
     *
     * 並びは暗黙のrowid順に頼らずORDER BYで明示する。sendTimeが同じ場合
     * （列が無かった頃の履歴はすべて0）は挿入順になるようidで揃える。
     */
    @Query("SELECT * FROM MessageEntity ORDER BY sendTime DESC, id DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<MessageEntity>

    /**
     * [sendTime]・[id]より古いものから[limit]件を返す。
     *
     * OFFSETではなく読み込んだ位置を起点にするので、遡っている間に
     * 新しいメッセージが増えても読み飛ばしや重複が起きない。
     */
    @Query(
        "SELECT * FROM MessageEntity " +
                "WHERE sendTime < :sendTime OR (sendTime = :sendTime AND id < :id) " +
                "ORDER BY sendTime DESC, id DESC LIMIT :limit"
    )
    suspend fun getOlderThan(sendTime: Long, id: Int, limit: Int): List<MessageEntity>

    @Query("DELETE FROM MessageEntity")
    suspend fun deleteAll()
}
