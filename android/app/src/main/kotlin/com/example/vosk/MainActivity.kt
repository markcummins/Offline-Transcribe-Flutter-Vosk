package com.example.vosk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.speaker_recognition/recognize"
    private val EVENT_CHANNEL = "com.example.speaker_recognition/events"
    private lateinit var speechService: SpeechService
    private lateinit var recognizer: Recognizer
    private lateinit var model: Model
    private lateinit var spkModel: SpeakerModel
    private val TAG = "VoskDemo"
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    private var pendingResult: MethodChannel.Result? = null
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecognition" -> {
                    pendingResult = result
                    if (checkAudioPermission()) {
                        startRecognition()
                    } else {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
                    }
                }
                "stopRecognition" -> {
                    stopRecognition()
                    result.success("Recognition stopped")
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            }
        )
    }

    private val knownSpeakerProfiles = mutableMapOf<String, MutableList<FloatArray>>()
    private var speakerCount = 0
    private val similarityThreshold = 0.45f

    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        val dotProduct = vec1.zip(vec2).sumByDouble { it.first * it.second.toDouble() }
        val magnitude1 = Math.sqrt(vec1.sumByDouble { it * it.toDouble() })
        val magnitude2 = Math.sqrt(vec2.sumByDouble { it * it.toDouble() })
        return (dotProduct / (magnitude1 * magnitude2)).toFloat()
    }

    private fun normalizeVector(vector: FloatArray): FloatArray {
        val magnitude = Math.sqrt(vector.sumByDouble { it * it.toDouble() })
        return vector.map { (it / magnitude).toFloat() }.toFloatArray()
    }

    private fun averageVectors(vectors: List<FloatArray>): FloatArray {
        val length = vectors[0].size
        val avgVector = FloatArray(length)
        for (vector in vectors) {
            for (i in vector.indices) {
                avgVector[i] += vector[i]
            }
        }
        return avgVector.map { it / vectors.size }.toFloatArray()
    }

    private fun identifyOrCreateSpeaker(speakerFeatures: FloatArray): String {
        val normalizedFeatures = normalizeVector(speakerFeatures)
        var bestMatch = ""
        var highestSimilarity = Float.MIN_VALUE

        for ((speaker, profiles) in knownSpeakerProfiles) {
            val avgProfile = averageVectors(profiles)
            val similarity = cosineSimilarity(normalizedFeatures, avgProfile)
            if (similarity > highestSimilarity) {
                highestSimilarity = similarity
                bestMatch = speaker
            }
        }

        return if (highestSimilarity > similarityThreshold) {
            knownSpeakerProfiles[bestMatch]?.add(normalizedFeatures)
            bestMatch
        } else {
            val newSpeaker = "Speaker ${++speakerCount}"
            knownSpeakerProfiles[newSpeaker] = mutableListOf(normalizedFeatures)
            newSpeaker
        }
    }

    private fun startRecognition() {
        try {
            val modelPath = getModelPath("vosk-model-small-en-us-0.15")
            val spkModelPath = getModelPath("vosk-model-spk-0.4")
            model = Model(modelPath)
            spkModel = SpeakerModel(spkModelPath)
            recognizer = Recognizer(model, 16000.0f, spkModel)
            speechService = SpeechService(recognizer, 16000.0f)
            eventSink?.success("Recognition started")
            startListening()
        } catch (e: IOException) {
            pendingResult?.error("ERROR", e.message, null)
            pendingResult = null
        }
    }

    private fun processPartialResult(hypothesis: String) {
        val resultJson = JSONObject(hypothesis)
        if (resultJson.has("partial")) {
            val text = resultJson.getString("partial")
            val event = mapOf("type" to "partial", "result" to text)

            eventSink?.success(event)
        } else {
            Log.d(TAG, "No 'text' field in partial result: $hypothesis")
        }
    }

    private fun processFinalResult(hypothesis: String) {
        val resultJson = JSONObject(hypothesis)
        if (resultJson.has("text")) {
            val text = resultJson.getString("text")
            val speakerList = resultJson.optJSONArray("spk")?.let { jsonArray ->
                FloatArray(jsonArray.length()) { jsonArray.getDouble(it).toFloat() }
            } ?: FloatArray(0)

            val speaker = if (speakerList.isNotEmpty()) identifyOrCreateSpeaker(speakerList) else "Speaker"
            val finalEvent = mapOf("type" to "final", "result" to text,"speaker" to speaker)
            val partialEvent = mapOf("type" to "partial", "result" to text)

            eventSink?.success(finalEvent)
            pendingResult?.success(partialEvent)
            pendingResult = null
        } else {
            Log.d(TAG, "No 'text' field in final result: $hypothesis")
        }
    }

    private fun startListening() {
        speechService.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                processPartialResult(hypothesis)
            }

            override fun onResult(hypothesis: String) {
                processFinalResult(hypothesis)
            }

            override fun onFinalResult(hypothesis: String) {
                // Optionally handle final result if needed
            }

            override fun onError(e: Exception) {
                pendingResult?.error("ERROR", e.message, null)
                eventSink?.success("Recognition error")
                pendingResult = null
                startListening()
            }

            override fun onTimeout() {
                pendingResult?.success("Timeout")
                eventSink?.success("Recognition timeout")
                pendingResult = null
                startListening()
            }
        })
    }

    private fun stopRecognition() {
        try {
            speechService.stop()
            eventSink?.success("Recognition stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop speech service", e)
        }
    }

    private fun getModelPath(modelName: String): String {
        val modelDir = File(applicationContext.filesDir, modelName)
        if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() == true) {
            Log.d(TAG, "Model directory does not exist or is empty: ${modelDir.absolutePath}")
            copyAndUnzipAssets("assets/models/$modelName.zip", modelDir)
        } else {
            Log.d(TAG, "Model directory already exists: ${modelDir.absolutePath}")
        }
        return modelDir.absolutePath
    }

    private fun copyAndUnzipAssets(assetFileName: String, destDir: File) {
        try {
            val assetManager = assets
            val assetPath = "flutter_assets/$assetFileName"
            Log.d(TAG, "Attempting to open asset: $assetPath")

            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            val inStream: InputStream = assetManager.open(assetPath)
            val tempZipFile = File(destDir, "temp_model.zip")
            inStream.use { input ->
                tempZipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Successfully copied asset to temp file: ${tempZipFile.absolutePath}")

            unzip(tempZipFile.inputStream(), destDir)
            tempZipFile.delete()
            Log.d(TAG, "Successfully unzipped $assetFileName to ${destDir.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy and unzip asset file $assetFileName.", e)
        }
    }

    private fun unzip(zipFile: InputStream, targetDirectory: File) {
        ZipInputStream(zipFile).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val file = File(targetDirectory, entry!!.name)
                if (entry!!.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecognition()
        } else {
            pendingResult?.error("ERROR", "Permission denied", null)
            pendingResult = null
        }
    }
}
