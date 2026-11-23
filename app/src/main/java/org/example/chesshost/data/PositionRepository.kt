package org.example.chesshost.data

import kotlinx.coroutines.flow.Flow

/** Eigene Stellungen (Stellungseditor). */
class PositionRepository(private val dao: SavedPositionDao) {

    fun observeAll(): Flow<List<SavedPosition>> = dao.observeAll()

    suspend fun find(id: Long): SavedPosition? = dao.find(id)

    suspend fun save(position: SavedPosition): Long = dao.upsert(position)

    suspend fun delete(id: Long) = dao.delete(id)
}
