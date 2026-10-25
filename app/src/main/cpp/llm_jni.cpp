// AI translation with llama.cpp: one model held, greedy decoding, the prompt as the model's template made it.
#include <jni.h>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

static std::mutex mu;
static llama_model* model = nullptr;
static llama_context* ctx = nullptr;
static std::string loaded;
static const int CTX = 2048;

static void unload() {
    if (ctx) llama_free(ctx);
    if (model) llama_model_free(model);
    ctx = nullptr; model = nullptr; loaded.clear();
}

static jbyteArray bytes(JNIEnv* env, const std::string& s) {
    jbyteArray a = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(a, 0, (jsize) s.size(), (const jbyte*) s.data());
    return a;
}

static jbyteArray fail(JNIEnv* env, const std::string& why) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), why.c_str());
    return nullptr;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_kafeg_inkpeek_mt_Llm_translateNative(JNIEnv* env, jobject, jstring jpath, jbyteArray jprompt, jint maxTokens, jint threads) {
    std::lock_guard<std::mutex> g(mu);
    const char* p = env->GetStringUTFChars(jpath, nullptr); std::string path(p); env->ReleaseStringUTFChars(jpath, p);
    jsize n = env->GetArrayLength(jprompt); std::string prompt(n, '\0');
    env->GetByteArrayRegion(jprompt, 0, n, (jbyte*) prompt.data());
    if (loaded != path) {
        unload();
        llama_backend_init();
        llama_model_params mp = llama_model_default_params();
        mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        mp.use_extra_bufts = false; // repacked weights would be a second copy in memory (2.4 GB instead of 1.2)
        model = llama_model_load_from_file(path.c_str(), mp);
        if (!model) return fail(env, "Cannot load " + path);
        llama_context_params cp = llama_context_default_params();
        cp.n_ctx = CTX; cp.n_batch = 512; cp.n_threads = threads; cp.n_threads_batch = threads;
        ctx = llama_init_from_model(model, cp);
        if (!ctx) { unload(); return fail(env, "Cannot start the model"); }
        loaded = path;
    }
    const llama_vocab* vocab = llama_model_get_vocab(model);
    llama_memory_clear(llama_get_memory(ctx), true);
    // The template has the model's special tokens as text: parsed, no extra BOS.
    int nt = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, false, true);
    std::vector<llama_token> toks(nt);
    llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), toks.data(), nt, false, true);
    if (nt >= CTX - 8) return fail(env, "Text too long for the model");
    int room = std::min((int) maxTokens, CTX - nt - 1);
    if (llama_decode(ctx, llama_batch_get_one(toks.data(), (int32_t) toks.size()))) return fail(env, "Decoding failed");
    llama_sampler* smpl = llama_sampler_init_greedy();
    std::string out;
    for (int i = 0; i < room; i++) {
        llama_token t = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, t)) break;
        char buf[256];
        int k = llama_token_to_piece(vocab, t, buf, sizeof buf, 0, false);
        if (k > 0) out.append(buf, k);
        // A failed step would return half a translation as a whole one (and it is cached): an error instead.
        if (llama_decode(ctx, llama_batch_get_one(&t, 1))) { llama_sampler_free(smpl); return fail(env, "Decoding failed"); }
    }
    llama_sampler_free(smpl);
    return bytes(env, out);
}

extern "C" JNIEXPORT void JNICALL Java_io_github_kafeg_inkpeek_mt_Llm_releaseNative(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> g(mu);
    unload();
}
