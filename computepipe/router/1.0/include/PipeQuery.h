/**
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_AUTOMOTIVE_COMPUTEPIPE_ROUTER_V1_0_PIPEQUERY
#define ANDROID_AUTOMOTIVE_COMPUTEPIPE_ROUTER_V1_0_PIPEQUERY

#include <android/automotive/computepipe/registry/BnPipeQuery.h>

#include "PipeRunner.h"
#include "Registry.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace router {
namespace V1_0 {
namespace implementation {

class PipeQuery : public android::automotive::computepipe::registry::BnPipeQuery {
  public:
    explicit PipeQuery(std::shared_ptr<PipeRegistry<PipeRunner>> r) : mRegistry(r) {
    }
    binder::Status getGraphList(::std::vector<std::string>* outNames) override;
    binder::Status getPipeRunner(
        const std::string& graphName,
        const sp<::android::automotive::computepipe::registry::IClientInfo>& info,
        sp<::android::automotive::computepipe::runner::IPipeRunner>* outRunner) override;

  private:
    std::shared_ptr<PipeRegistry<PipeRunner>> mRegistry;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace router
}  // namespace computepipe
}  // namespace automotive
}  // namespace android
#endif