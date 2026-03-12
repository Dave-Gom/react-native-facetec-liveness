#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(FaceTecLivenessModule, NSObject)

RCT_EXTERN_METHOD(initialize:(NSDictionary *)config
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
