#include <jni.h>
#include <vector>
#include <cstdio>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "AudioConverter"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// WAV header structure
struct WavHeader {
    char riff[4];           // "RIFF"
    uint32_t file_size;     // File size - 8
    char wave[4];           // "WAVE"
    char fmt[4];            // "fmt "
    uint32_t fmt_size;      // Format chunk size
    uint16_t audio_format;  // Audio format (1 = PCM)
    uint16_t num_channels;  // Number of channels
    uint32_t sample_rate;   // Sample rate
    uint32_t byte_rate;     // Byte rate
    uint16_t block_align;   // Block align
    uint16_t bits_per_sample; // Bits per sample
    char data[4];           // "data"
    uint32_t data_size;     // Data size
};

/**
 * Read WAV file and extract PCM samples as float32
 * Returns true on success, false on failure
 */
bool read_wav(const char* filename, std::vector<float>& pcm_data, int& sample_rate) {
    FILE* file = fopen(filename, "rb");
    if (!file) {
        LOGE("Failed to open WAV file: %s", filename);
        return false;
    }

    // Read WAV header
    WavHeader header;
    if (fread(&header, sizeof(WavHeader), 1, file) != 1) {
        LOGE("Failed to read WAV header");
        fclose(file);
        return false;
    }

    // Validate WAV format
    if (memcmp(header.riff, "RIFF", 4) != 0 ||
        memcmp(header.wave, "WAVE", 4) != 0) {
        LOGE("Invalid WAV file format");
        fclose(file);
        return false;
    }

    // Log WAV info
    LOGI("WAV Info: %d Hz, %d channels, %d bits, format %d",
         header.sample_rate, header.num_channels, header.bits_per_sample, header.audio_format);

    sample_rate = header.sample_rate;

    // Calculate number of samples
    const size_t num_samples = header.data_size / (header.bits_per_sample / 8);
    pcm_data.resize(num_samples);

    // Read and convert PCM data based on bit depth
    if (header.bits_per_sample == 16) {
        std::vector<int16_t> samples(num_samples);
        if (fread(samples.data(), sizeof(int16_t), num_samples, file) != num_samples) {
            LOGE("Failed to read PCM data");
            fclose(file);
            return false;
        }

        // Convert int16 to float32 normalized to [-1, 1]
        for (size_t i = 0; i < num_samples; i++) {
            pcm_data[i] = static_cast<float>(samples[i]) / 32768.0f;
        }
    } else if (header.bits_per_sample == 32) {
        std::vector<int32_t> samples(num_samples);
        if (fread(samples.data(), sizeof(int32_t), num_samples, file) != num_samples) {
            LOGE("Failed to read PCM data");
            fclose(file);
            return false;
        }

        // Convert int32 to float32 normalized to [-1, 1]
        for (size_t i = 0; i < num_samples; i++) {
            pcm_data[i] = static_cast<float>(samples[i]) / 2147483648.0f;
        }
    } else {
        LOGE("Unsupported bit depth: %d", header.bits_per_sample);
        fclose(file);
        return false;
    }

    // Convert stereo to mono if needed
    if (header.num_channels == 2) {
        LOGI("Converting stereo to mono");
        std::vector<float> mono_data(num_samples / 2);
        for (size_t i = 0; i < mono_data.size(); i++) {
            mono_data[i] = (pcm_data[i * 2] + pcm_data[i * 2 + 1]) / 2.0f;
        }
        pcm_data = std::move(mono_data);
    }

    fclose(file);
    LOGI("Successfully loaded %zu samples from WAV file", pcm_data.size());
    return true;
}
