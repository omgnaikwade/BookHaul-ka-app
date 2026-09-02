package com.example.ui.tts.kokoro

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicBoolean

class KokoroEngine(
    private val context: Context,
    private val modelManager: KokoroModelManager
) {

    private val TAG = "KokoroEngine"

    private val SAMPLE_RATE = 24000
    private val MAX_TOKENS = 480

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    private val isInitialized = AtomicBoolean(false)

    private var mediaPlayer: MediaPlayer? = null

    // Cache voice embeddings so we don't read the .bin file every time
    private val voiceCache = mutableMapOf<String, FloatArray>()

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {

        if (isInitialized.get() && ortSession != null) {
            return@withContext true
        }

        if (!modelManager.isModelValid()) {
            Log.e(TAG, "Kokoro model is not installed")
            return@withContext false
        }

        try {

            Log.i(TAG, "Initializing Kokoro ONNX engine...")

            val env = OrtEnvironment.getEnvironment()

            val options = OrtSession.SessionOptions().apply {

                // Don't use insane thread count on mobile
                setIntraOpNumThreads(4)

                setOptimizationLevel(
                    OrtSession.SessionOptions.OptLevel.ALL_OPT
                )
            }

            val session = env.createSession(
                modelManager.modelFile.absolutePath,
                options
            )

            ortEnvironment = env
            ortSession = session

            isInitialized.set(true)

            Log.i(
                TAG,
                "Kokoro initialized successfully. Inputs: ${session.inputNames}"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to initialize Kokoro engine",
                e
            )

            isInitialized.set(false)

            false
        }
    }


    /**
     * Main synthesis function.
     *
     * Long text is automatically split into smaller chunks.
     */
    suspend fun synthesizeToWav(
        text: String,
        voiceId: String = "hf_alpha",
        speed: Float = 1.0f,
        languageCode: String = "en"
    ): File? = withContext(Dispatchers.Default) {

        val cleanText = text.trim()

        if (cleanText.isEmpty()) {
            return@withContext null
        }


        /*
         * Initialize only once.
         */

        if (!isInitialized.get() || ortSession == null) {

            val initialized = initialize()

            if (!initialized) {

                Log.e(
                    TAG,
                    "Kokoro initialization failed"
                )

                return@withContext null
            }
        }


        /*
         * Split long text.
         *
         * This is extremely important for PDF reading.
         */

        val chunks = splitTextIntoChunks(
            cleanText,
            languageCode
        )

        Log.d(
            TAG,
            "Text split into ${chunks.size} chunk(s)"
        )


        val allAudio = ArrayList<Float>()

        val startTotalTime =
            System.currentTimeMillis()


        for ((index, chunk) in chunks.withIndex()) {

            Log.d(
                TAG,
                "Processing chunk ${index + 1}/${chunks.size}"
            )

            val audio = synthesizeSingleChunk(
                text = chunk,
                voiceId = voiceId,
                speed = speed,
                languageCode = languageCode
            )


            if (audio != null && audio.isNotEmpty()) {

                /*
                 * Add small silence between chunks.
                 */

                allAudio.addAll(audio.toList())

                if (index < chunks.lastIndex) {

                    val silenceSamples =
                        SAMPLE_RATE / 5

                    repeat(silenceSamples) {

                        allAudio.add(0f)

                    }
                }
            }
        }


        if (allAudio.isEmpty()) {

            Log.e(
                TAG,
                "No audio generated"
            )

            return@withContext null
        }


        /*
         * Convert List<Float> to FloatArray.
         */

        val finalAudio =
            FloatArray(allAudio.size)

        for (i in allAudio.indices) {

            finalAudio[i] =
                allAudio[i]

        }


        val outputFile = File(
            context.cacheDir,
            "kokoro_${System.currentTimeMillis()}.wav"
        )


        writeWavFile(
            finalAudio,
            outputFile,
            SAMPLE_RATE
        )


        val totalTime =
            System.currentTimeMillis() -
                    startTotalTime


        Log.i(
            TAG,
            "Kokoro synthesis completed in ${totalTime}ms"
        )


        outputFile
    }


    /**
     * Synthesizes ONE chunk.
     *
     * Keeping inference isolated makes
     * long PDFs safer.
     */
    private suspend fun synthesizeSingleChunk(

        text: String,

        voiceId: String,

        speed: Float,

        languageCode: String

    ): FloatArray? {

        val session =
            ortSession ?: return null

        val env =
            ortEnvironment ?: return null


        var results:
                OrtSession.Result? =
            null


        val tensors =
            mutableListOf<OnnxTensor>()


        try {

            /*
             * Tokenize
             */

            val tokenIds =
                KokoroTokenizer.tokenize(
                    text,
                    languageCode,
                    modelManager.configFile
                )


            if (tokenIds.isEmpty()) {

                return null

            }


            /*
             * Safety limit.
             */

            if (tokenIds.size > MAX_TOKENS) {

                Log.w(
                    TAG,
                    "Too many tokens: ${tokenIds.size}"
                )

            }


            /*
             * Voice embedding.
             */

            val styleVector =
                loadVoiceStyle(
                    voiceId,
                    tokenIds.size
                )


            val inputs =
                mutableMapOf<String, OnnxTensor>()


            val seqLen =
                tokenIds.size.toLong()


            /*
             * Build ONNX inputs dynamically.
             */

            for (
                (inputName, nodeInfo)
                in session.inputInfo
            ) {


                val tensorInfo =
                    nodeInfo.info
                            as? ai.onnxruntime.TensorInfo


                val shape =
                    tensorInfo?.shape


                when {


                    /*
                     * Token input
                     */

                    inputName.contains(
                        "token",
                        ignoreCase = true
                    )

                            ||

                            inputName.contains(
                                "input_ids",
                                ignoreCase = true
                            )

                    -> {

                        val buffer =
                            LongBuffer.wrap(
                                tokenIds
                            )


                        val tensor =
                            OnnxTensor.createTensor(

                                env,

                                buffer,

                                longArrayOf(
                                    1,
                                    seqLen
                                )

                            )


                        inputs[inputName] =
                            tensor


                        tensors.add(
                            tensor
                        )
                    }


                    /*
                     * Voice style input
                     */

                    inputName.contains(
                        "style",
                        ignoreCase = true
                    )

                            ||

                            inputName.contains(
                                "ref",
                                ignoreCase = true
                            )

                    -> {

                        val buffer =
                            FloatBuffer.wrap(
                                styleVector
                            )


                        val styleShape =

                            when {

                                shape != null &&
                                        shape.size == 3

                                -> {

                                    longArrayOf(

                                        1,

                                        1,

                                        styleVector.size.toLong()

                                    )
                                }


                                shape != null &&
                                        shape.size == 2

                                -> {

                                    longArrayOf(

                                        1,

                                        styleVector.size.toLong()

                                    )
                                }


                                else -> {

                                    longArrayOf(

                                        1,

                                        styleVector.size.toLong()

                                    )
                                }
                            }


                        val tensor =
                            OnnxTensor.createTensor(

                                env,

                                buffer,

                                styleShape

                            )


                        inputs[inputName] =
                            tensor


                        tensors.add(
                            tensor
                        )
                    }


                    /*
                     * Speed input
                     */

                    inputName.contains(
                        "speed",
                        ignoreCase = true
                    )

                    -> {

                        val speedValue =
                            speed.coerceIn(
                                0.7f,
                                1.5f
                            )


                        val buffer =
                            FloatBuffer.wrap(

                                floatArrayOf(
                                    speedValue
                                )

                            )


                        val speedShape =

                            if (
                                shape != null &&
                                shape.size == 2
                            ) {

                                longArrayOf(
                                    1,
                                    1
                                )

                            } else {

                                longArrayOf(
                                    1
                                )

                            }


                        val tensor =
                            OnnxTensor.createTensor(

                                env,

                                buffer,

                                speedShape

                            )


                        inputs[inputName] =
                            tensor


                        tensors.add(
                            tensor
                        )
                    }


                    /*
                     * Unknown input.
                     *
                     * Use token input.
                     */

                    else -> {

                        val buffer =
                            LongBuffer.wrap(
                                tokenIds
                            )


                        val tensor =
                            OnnxTensor.createTensor(

                                env,

                                buffer,

                                longArrayOf(
                                    1,
                                    seqLen
                                )

                            )


                        inputs[inputName] =
                            tensor


                        tensors.add(
                            tensor
                        )
                    }
                }
            }


            Log.d(

                TAG,

                "Running inference: " +
                        "${tokenIds.size} tokens"

            )


            val startTime =
                System.currentTimeMillis()


            results =
                session.run(inputs)


            val inferenceTime =
                System.currentTimeMillis() -
                        startTime


            Log.d(

                TAG,

                "Inference completed in " +
                        "${inferenceTime}ms"

            )


            /*
             * Get first output.
             */

            val outputTensor =
                results[0]


            val output =
                outputTensor.value


            return extractAudio(
                output
            )


        } catch (
            c: CancellationException
        ) {

            throw c

        } catch (
            e: Exception
        ) {

            Log.e(

                TAG,

                "Kokoro inference error",

                e

            )


            return null

        } finally {


            /*
             * VERY IMPORTANT.
             *
             * Close tensors.
             */

            tensors.forEach {

                try {

                    it.close()

                } catch (
                    _: Exception
                ) {
                }
            }


            try {

                results?.close()

            } catch (
                _: Exception
            ) {
            }
        }
    }


    /**
     * Extract FloatArray from ONNX output.
     */
    private fun extractAudio(
        output: Any?
    ): FloatArray {


        return when (output) {


            is FloatArray -> {

                output

            }


            is Array<*> -> {

                extractAudioFromArray(
                    output
                )

            }


            else -> {

                Log.e(

                    TAG,

                    "Unknown output type: " +
                            output?.javaClass?.name

                )


                FloatArray(0)

            }
        }
    }


    private fun extractAudioFromArray(

        array: Array<*>

    ): FloatArray {


        if (array.isEmpty()) {

            return FloatArray(0)

        }


        val first =
            array[0]


        return when (first) {


            is FloatArray -> {

                first

            }


            is Array<*> -> {

                extractAudioFromArray(
                    first
                )

            }


            else -> {

                FloatArray(0)

            }
        }
    }


    /**
     * Split long text safely.
     *
     * First split by sentences.
     * Then split long sentences by words.
     */
    private fun splitTextIntoChunks(

        text: String,

        languageCode: String

    ): List<String> {


        /*
         * Approximate character limit.
         *
         * Hindi characters can produce
         * more phoneme tokens.
         */

        val maxCharacters =

            if (
                languageCode.lowercase()
                    .startsWith("hi")
            ) {

                180

            } else {

                250

            }


        val sentences =

            text.split(

                Regex(
                    "(?<=[.!?।॥])\\s+"
                )

            )


        val chunks =
            mutableListOf<String>()


        val current =
            StringBuilder()


        for (sentence in sentences) {


            val clean =
                sentence.trim()


            if (clean.isEmpty()) {

                continue

            }


            /*
             * Normal sentence.
             */

            if (
                clean.length <= maxCharacters
            ) {


                if (

                    current.length +
                            clean.length + 1

                    <= maxCharacters

                ) {

                    if (
                        current.isNotEmpty()
                    ) {

                        current.append(" ")

                    }


                    current.append(clean)


                } else {


                    if (
                        current.isNotEmpty()
                    ) {

                        chunks.add(
                            current.toString()
                        )

                        current.clear()

                    }


                    current.append(clean)

                }


            } else {


                /*
                 * Sentence is too long.
                 *
                 * Split by words.
                 */

                if (
                    current.isNotEmpty()
                ) {

                    chunks.add(
                        current.toString()
                    )

                    current.clear()

                }


                val words =
                    clean.split(
                        Regex("\\s+")
                    )


                val longChunk =
                    StringBuilder()


                for (word in words) {


                    if (

                        longChunk.length +
                                word.length + 1

                        > maxCharacters

                    ) {


                        if (
                            longChunk.isNotEmpty()
                        ) {

                            chunks.add(

                                longChunk.toString()

                            )

                            longChunk.clear()

                        }
                    }


                    if (
                        longChunk.isNotEmpty()
                    ) {

                        longChunk.append(" ")

                    }


                    longChunk.append(word)

                }


                if (
                    longChunk.isNotEmpty()
                ) {

                    chunks.add(

                        longChunk.toString()

                    )

                }
            }
        }


        if (
            current.isNotEmpty()
        ) {

            chunks.add(
                current.toString()
            )

        }


        /*
         * If split failed somehow.
         */

        if (
            chunks.isEmpty()
        ) {

            chunks.add(text)
                    }

        return chunks
    }
}
/**
     * Loads voice style embedding.
     *
     * Cached in RAM after first read.
     */
    private fun loadVoiceStyle(

        voiceId: String,

        tokenCount: Int

    ): FloatArray {


        /*
         * Check RAM cache.
         */

        voiceCache[voiceId]?.let {

            return it

        }


        val voiceFile =
            modelManager.getVoiceFile(
                voiceId
            )


        if (

            voiceFile.exists()

                    &&

                    voiceFile.length() >= 1024

        ) {


            try {


                val bytes =
                    voiceFile.readBytes()


                val byteBuffer =
                    ByteBuffer
                        .wrap(bytes)
                        .order(
                            ByteOrder.LITTLE_ENDIAN
                        )


                val floatCount =
                    bytes.size / 4


                val floats =
                    FloatArray(
                        floatCount
                    )


                byteBuffer
                    .asFloatBuffer()
                    .get(floats)


                if (

                    floats.size >= 256

                ) {


                    /*
                     * Kokoro voice files
                     * can contain multiple
                     * style vectors.
                     *
                     * Select one safely.
                     */

                    val rowCount =
                        floats.size / 256


                    val rowIndex =

                        tokenCount.coerceIn(

                            0,

                            rowCount - 1

                        )


                    val start =
                        rowIndex * 256


                    val vector =
                        floats.copyOfRange(

                            start,

                            start + 256

                        )


                    voiceCache[voiceId] =
                        vector


                    return vector
                }


            } catch (
                e: Exception
            ) {


                Log.e(

                    TAG,

                    "Failed to load voice $voiceId",

                    e

                )
            }
        }


        /*
         * Fallback.
         */

        Log.w(

            TAG,

            "Using fallback voice style"

        )


        return FloatArray(256) {

            if (
                it % 2 == 0
            ) {

                0.05f

            } else {

                -0.05f

            }
        }
    }


    /**
     * Writes PCM FloatArray as WAV.
     */
    private fun writeWavFile(

        floats: FloatArray,

        outputFile: File,

        sampleRate: Int

    ) {


        val numSamples =
            floats.size


        val numChannels = 1

        val bitsPerSample = 16


        val byteRate =

            sampleRate *
                    numChannels *
                    (bitsPerSample / 8)


        val blockAlign =

            numChannels *
                    (bitsPerSample / 8)


        val dataSize =

            numSamples *
                    (bitsPerSample / 8)


        val totalSize =
            36 + dataSize


        FileOutputStream(
            outputFile
        ).use { output ->


            val header =
                ByteBuffer
                    .allocate(44)
                    .order(
                        ByteOrder.LITTLE_ENDIAN
                    )


            header.put(
                "RIFF".toByteArray()
            )


            header.putInt(
                totalSize
            )


            header.put(
                "WAVE".toByteArray()
            )


            header.put(
                "fmt ".toByteArray()
            )


            header.putInt(16)


            header.putShort(
                1.toShort()
            )


            header.putShort(
                numChannels.toShort()
            )


            header.putInt(
                sampleRate
            )


            header.putInt(
                byteRate
            )


            header.putShort(
                blockAlign.toShort()
            )


            header.putShort(
                bitsPerSample.toShort()
            )


            header.put(
                "data".toByteArray()
            )


            header.putInt(
                dataSize
            )


            output.write(
                header.array()
            )


            val pcmBuffer =
                ByteBuffer
                    .allocate(
                        numSamples * 2
                    )
                    .order(
                        ByteOrder.LITTLE_ENDIAN
                    )


            for (sample in floats) {


                val clamped =
                    sample.coerceIn(
                        -1f,
                        1f
                    )


                val pcm =
                    (
                        clamped *
                                32767f
                    )
                        .toInt()
                        .toShort()


                pcmBuffer.putShort(
                    pcm
                )
            }


            output.write(
                pcmBuffer.array()
            )


            output.flush()
        }
    }


    /**
     * Audio playback.
     */
    fun playAudio(

        wavFile: File,

        onCompletion: (() -> Unit)? = null

    ) {


        try {


            stopAudio()


            mediaPlayer =
                MediaPlayer().apply {


                    setDataSource(
                        wavFile.absolutePath
                    )


                    prepare()


                    setOnCompletionListener {


                        onCompletion?.invoke()

                    }


                    start()
                }


        } catch (
            e: Exception
        ) {


            Log.e(
                TAG,
                "Audio playback error",
                e
            )
        }
    }


    fun stopAudio() {


        try {


            mediaPlayer?.stop()

            mediaPlayer?.release()

            mediaPlayer = null


        } catch (
            e: Exception
        ) {


            Log.e(
                TAG,
                "Stop audio error",
                e
            )
        }
    }


    fun release() {


        try {


            stopAudio()


            voiceCache.clear()


            ortSession?.close()

            ortSession = null


            /*
             * Do NOT close global
             * OrtEnvironment.
             *
             * ONNX Runtime manages it.
             */

            ortEnvironment = null


            isInitialized.set(false)


        } catch (
            e: Exception
        ) {


            Log.e(
                TAG,
                "Release error",
                e
            )
        }
    }


    companion object {


        @Volatile

        private var instance:
                KokoroEngine? = null


        fun getInstance(

            context: Context

        ): KokoroEngine {


            return instance
                ?: synchronized(this) {


                    instance
                        ?: KokoroEngine(

                            context.applicationContext,

                            KokoroModelManager
                                .getInstance(
                                    context
                                )

                        ).also {


                            instance = it

                        }
                }
        }
    }
}
      
