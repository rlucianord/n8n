#include <jni.h>
#include "llama.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_midoble_app_NativeLlamaBridge_nativeGetBackendInfo(JNIEnv *env, jobject /* receiver */) {
    llama_backend_init();
    const char *info = llama_print_system_info();
    if (info == nullptr) {
        return env->NewStringUTF("llama.cpp backend initialized; system information unavailable");
    }
    return env->NewStringUTF(info);
}
