package org.example.chesshost.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val dao: SavedGameDao) {

    fun observeGames(): Flow<List<SavedGame>> = dao.observeAll()

    suspend fun save(game: SavedGame): Long = dao.insert(game)

    suspend fun find(id: Long): SavedGame? = dao.findById(id)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()
}
