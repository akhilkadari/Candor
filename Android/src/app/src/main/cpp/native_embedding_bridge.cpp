#include <jni.h>

#include <string>
#include <vector>

#include "sentencepiece_processor.h"

namespace {

jintArray ToJIntArray(JNIEnv *env, const std::vector<int> &values) {
  jintArray array = env->NewIntArray(static_cast<jsize>(values.size()));
  if (array == nullptr || values.empty()) {
    return array;
  }

  std::vector<jint> tmp(values.begin(), values.end());
  env->SetIntArrayRegion(array, 0, static_cast<jsize>(tmp.size()), tmp.data());
  return array;
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_google_ai_edge_gallery_data_recovery_SentencePieceBridge_nativeTokenize(
    JNIEnv *env, jobject /* this */, jstring model_path, jstring text) {
  if (model_path == nullptr || text == nullptr) {
    return env->NewIntArray(0);
  }

  const char *model_path_chars = env->GetStringUTFChars(model_path, nullptr);
  const char *text_chars = env->GetStringUTFChars(text, nullptr);
  if (model_path_chars == nullptr || text_chars == nullptr) {
    if (model_path_chars != nullptr) {
      env->ReleaseStringUTFChars(model_path, model_path_chars);
    }
    if (text_chars != nullptr) {
      env->ReleaseStringUTFChars(text, text_chars);
    }
    return env->NewIntArray(0);
  }

  sentencepiece::SentencePieceProcessor processor;
  const auto status = processor.Load(model_path_chars);
  std::vector<int> ids;
  if (status.ok()) {
    processor.Encode(text_chars, &ids);
  }

  env->ReleaseStringUTFChars(model_path, model_path_chars);
  env->ReleaseStringUTFChars(text, text_chars);
  return ToJIntArray(env, ids);
}
