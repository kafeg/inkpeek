// JNI bridge to fastText language identification (lid.176): the top languages of a text with probabilities.
#include <jni.h>
#include <map>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include "fasttext.h"

static std::map<std::string, std::unique_ptr<fasttext::FastText>> loaded; // by model path
static std::mutex lock;

// "de 0.981\nen 0.012\n…": up to [k] languages above [threshold]; the model stays loaded.
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_kafeg_inkpeek_mt_LangId_detectNative(JNIEnv *env, jobject, jstring model, jstring text, jint k, jfloat threshold) {
  std::lock_guard<std::mutex> guard(lock);
  const char *m = env->GetStringUTFChars(model, nullptr);
  const char *t = env->GetStringUTFChars(text, nullptr);
  std::string path(m), line(t);
  env->ReleaseStringUTFChars(model, m);
  env->ReleaseStringUTFChars(text, t);
  try {
    auto &ft = loaded[path];
    if (!ft) {
      ft = std::make_unique<fasttext::FastText>();
      ft->loadModel(path);
    }
    for (auto &c : line) if (c == '\n' || c == '\r') c = ' ';
    std::istringstream in(line + "\n");
    std::vector<std::pair<fasttext::real, std::string>> preds;
    ft->predictLine(in, preds, k, threshold);
    std::string out;
    for (auto &p : preds) {
      std::string label = p.second.rfind("__label__", 0) == 0 ? p.second.substr(9) : p.second;
      out += label + " " + std::to_string(p.first) + "\n";
    }
    return env->NewStringUTF(out.c_str());
  } catch (const std::exception &e) {
    loaded.erase(path);
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
    return nullptr;
  }
}
