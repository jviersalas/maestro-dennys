package apk.mavier.dguitar.domain.repository

import apk.mavier.dguitar.domain.model.Song

interface SongbookRepository {
    fun loadSongs(): List<Song>
    fun saveSongs(songs: List<Song>)
}
