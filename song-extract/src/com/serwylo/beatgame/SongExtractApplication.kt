package com.serwylo.beatgame.desktop

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.serwylo.beatgame.audio.loadLevelDataFromDisk
import com.serwylo.beatgame.audio.saveLevelDataToDisk
import java.io.File

class SongExtract(private var arg: Array<String>): ApplicationAdapter() {

    override fun create() {

        if (arg.contains("--verbose")) {
            arg = arg.filter { it != "--verbose" }.toTypedArray()
            Gdx.app.logLevel = Application.LOG_DEBUG
        }

        if (arg.size != 2) {
            return usage()
        }

        val srcDir = File(arg[0])
        val destDir = File(arg[1])

        if (!srcDir.exists()) {
            return usage("Source audio directory $srcDir does not exist")
        }

        srcDir.listFiles()?.forEach {
            val ext = it.extension.lowercase()
            if (ext != "mp3" && ext != "flac") {
                Gdx.app.log(TAG, "Skipping non-audio file $it.")
            } else {
                processFile(it, destDir)
            }
        }

        Gdx.app.exit()

    }

    private fun processFile(audioFile: File, destDir: File) {

        val outPath = "${destDir.absolutePath}${File.separator}${audioFile.nameWithoutExtension}.json"
        val outFile = Gdx.files.absolute(outPath)

        if (outFile.exists()) {
            Gdx.app.log(TAG, "Skipping ${audioFile.name} as it already has a data file at $outPath.")
            return
        }

        Gdx.app.log(TAG, "Processing ${audioFile.name}, writing to ${outPath}.")

        val world = loadLevelDataFromDisk(Gdx.files.absolute(audioFile.path))
        saveLevelDataToDisk(outFile, world)

    }

    companion object {

        private const val TAG = "SongExtract"

        fun usage(error: String? = null) {
            if (error != null) {
                Gdx.app.error(TAG, error)
            }

            Gdx.app.error(TAG, "Usage: song-extract src-audio-dir/ dest-data-dir/")
            Gdx.app.exit()
        }

    }

}

