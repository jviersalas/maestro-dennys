package apk.mavier.dguitar.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.max

@SuppressLint("MissingPermission")
class PitchDetector(
    private val sampleRate: Int = 22050,
    private val bufferSize: Int = 4096
) {

    var onPitch: ((PitchAnalyzer.Result?) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    val isRunning: Boolean
        get() = running

    fun start(): Boolean {
        if (running) return true

        val record = createRecord() ?: return false
        audioRecord = record

        try {
            record.startRecording()
        } catch (_: Exception) {
            record.release()
            audioRecord = null
            return false
        }

        running = true
        thread = Thread({ loop(record) }, "DGuitarTuner").apply {
            priority = Thread.NORM_PRIORITY
            start()
        }
        return true
    }

    fun stop() {
        running = false

        val worker = thread
        thread = null
        try {
            worker?.join(400)
        } catch (_: InterruptedException) {
        }

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    private fun createRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null

        val bufferBytes = max(minBuffer, bufferSize * 2 * 2)
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val sources = intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            try {
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    return record
                }
                record.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun loop(record: AudioRecord) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (_: Exception) {
        }

        val samples = ShortArray(bufferSize)
        while (running) {
            val read = try {
                record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
            } catch (_: Exception) {
                break
            }
            if (read <= 0) continue

            val result = if (read >= samples.size / 2) {
                PitchAnalyzer.detect(samples.copyOf(read), sampleRate)
            } else {
                null
            }
            onPitch?.invoke(result)
        }
    }
}
