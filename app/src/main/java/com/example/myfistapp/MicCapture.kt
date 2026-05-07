package com.example.myfistapp

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.myfistapp.audio.StreamingAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MicCapture(private val analyzer: StreamingAnalyzer) {

    private val sampleRate = 44100
    private val channelCfg = AudioFormat.CHANNEL_IN_MONO
    private val encoding   = AudioFormat.ENCODING_PCM_16BIT

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        analyzer.beginStreaming(sampleRate, 1)
        job = scope.launch(Dispatchers.IO) {
            val minBuf  = AudioRecord.getMinBufferSize(sampleRate, channelCfg, encoding)
            val bufSize = maxOf(minBuf, 4096) * 2
            val record  = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelCfg, encoding, bufSize,
            )
            try {
                record.startRecording()
                val buf = ShortArray(bufSize / 2)
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0) analyzer.pushSamples(buf, read)
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                analyzer.endStreaming()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
