#include <jni.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "llama.h"

namespace {
std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
const llama_vocab * g_vocab = nullptr;

void unload_model() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }
}

std::vector<llama_token> tokenize(const std::string & text) {
    std::vector<llama_token> tokens(text.size() + 16);
    int32_t count = llama_tokenize(
        g_vocab, text.data(), static_cast<int32_t>(text.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true
    );
    if (count < 0) {
        tokens.resize(static_cast<size_t>(-count));
        count = llama_tokenize(
            g_vocab, text.data(), static_cast<int32_t>(text.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true
        );
    }
    if (count < 0) return {};
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

std::string token_piece(llama_token token) {
    std::vector<char> buffer(256);
    int32_t count = llama_token_to_piece(g_vocab, token, buffer.data(),
                                         static_cast<int32_t>(buffer.size()), 0, false);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(g_vocab, token, buffer.data(),
                                     static_cast<int32_t>(buffer.size()), 0, false);
    }
    if (count <= 0) return {};
    return std::string(buffer.data(), static_cast<size_t>(count));
}
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_midoble_app_NativeLlamaBridge_nativeGetBackendInfo(JNIEnv * env, jobject) {
    llama_backend_init();
    const char * info = llama_print_system_info();
    return env->NewStringUTF(info != nullptr ? info : "llama.cpp inicializado");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_midoble_app_NativeLlamaBridge_nativeLoadModel(JNIEnv * env, jobject, jstring model_path) {
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return env->NewStringUTF("No se pudo leer la ruta del modelo.");

    std::lock_guard<std::mutex> lock(g_mutex);
    unload_model();
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (g_model == nullptr) {
        return env->NewStringUTF("No se pudo cargar el GGUF. Verifica que el archivo sea válido y esté completo.");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 2048;
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = std::max(2, static_cast<int>(std::thread::hardware_concurrency()) - 2);
    context_params.n_threads_batch = context_params.n_threads;
    g_context = llama_init_from_model(g_model, context_params);

    if (g_context == nullptr) {
        unload_model();
        return env->NewStringUTF("El modelo se abrió, pero no se pudo crear el contexto de inferencia.");
    }
    g_vocab = llama_model_get_vocab(g_model);
    return env->NewStringUTF("Modelo GGUF cargado correctamente. Motor local listo.");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_midoble_app_NativeLlamaBridge_nativeGenerate(
    JNIEnv * env, jobject, jstring prompt_text, jint max_tokens) {
    const char * prompt_chars = env->GetStringUTFChars(prompt_text, nullptr);
    if (prompt_chars == nullptr) return env->NewStringUTF("No se pudo leer el mensaje.");
    const std::string prompt(prompt_chars);
    env->ReleaseStringUTFChars(prompt_text, prompt_chars);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr || g_vocab == nullptr) {
        return env->NewStringUTF("Primero debes cargar un modelo GGUF.");
    }

    const auto tokens = tokenize(prompt);
    if (tokens.empty() || tokens.size() > 480) {
        return env->NewStringUTF("El mensaje supera el límite beta de contexto. Reduce el historial o el texto.");
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    llama_batch prompt_batch = llama_batch_get_one(
        const_cast<llama_token *>(tokens.data()), static_cast<int32_t>(tokens.size()));
    if (llama_decode(g_context, prompt_batch) != 0) {
        return env->NewStringUTF("No se pudo procesar el mensaje de entrada.");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    const int limit = std::max(1, std::min(static_cast<int>(max_tokens), 512));
    for (int i = 0; i < limit; ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(g_vocab, token)) break;
        output += token_piece(token);

        llama_batch next = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(g_context, next) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}
