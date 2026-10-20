// JNI bridge to Bergamot. Adapted from RTranslator's BergamotTranslator.cpp
// (Copyright Luca Martino, Apache License 2.0, https://github.com/niedev/RTranslator).
#include <jni.h>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include "translator/parser.h"
#include "translator/response_options.h"
#include "translator/service.h"
#include "common/logging.h"

using namespace marian::bergamot;

static std::unique_ptr<BlockingService> service;
static std::map<std::string, std::shared_ptr<TranslationModel>> models; // by config text
static std::mutex lock;

static std::string str(JNIEnv *env, jstring s) {
  const char *c = env->GetStringUTFChars(s, nullptr);
  std::string r(c);
  env->ReleaseStringUTFChars(s, c);
  return r;
}

// Text crosses JNI as UTF-8 bytes: jstring's "modified UTF-8" breaks emoji and other characters outside the BMP.
static std::string utf8(JNIEnv *env, jbyteArray b) {
  std::string r(env->GetArrayLength(b), '\0');
  env->GetByteArrayRegion(b, 0, (jsize) r.size(), reinterpret_cast<jbyte *>(&r[0]));
  return r;
}

static jbyteArray bytes(JNIEnv *env, const std::string &s) {
  jbyteArray b = env->NewByteArray((jsize) s.size());
  env->SetByteArrayRegion(b, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
  return b;
}

// One direction: the model from its YAML config; kept in memory when [keep], else freed after the call.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_kafeg_inkpeek_mt_Engine_translateNative(JNIEnv *env, jobject, jstring cfg, jbyteArray text, jboolean keep) {
  std::lock_guard<std::mutex> guard(lock);
  try {
    if (!service) {
      marian::setThrowExceptionOnAbort(true); // a broken model throws instead of aborting the app
      BlockingService::Config sc;
      sc.cacheSize = 0;
      sc.logger.level = "off";
      service = std::make_unique<BlockingService>(sc);
    }
    std::string config = str(env, cfg);
    auto it = models.find(config);
    std::shared_ptr<TranslationModel> model = it != models.end() ? it->second
        : std::make_shared<TranslationModel>(parseOptionsFromString(config, /*validate=*/true, /*pathsDir=*/""));
    if (keep) models[config] = model; else models.erase(config);
    std::vector<std::string> in{utf8(env, text)};
    std::vector<ResponseOptions> opts(1);
    auto out = service->translateMultiple(model, std::move(in), opts);
    return bytes(env, out[0].target.text);
  } catch (const std::exception &e) {
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
    return nullptr;
  }
}

extern "C" JNIEXPORT void JNICALL Java_io_github_kafeg_inkpeek_mt_Engine_releaseNative(JNIEnv *, jobject) {
  std::lock_guard<std::mutex> guard(lock);
  models.clear();
}
