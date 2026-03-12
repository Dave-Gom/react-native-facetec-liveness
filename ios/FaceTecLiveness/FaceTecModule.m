#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(FaceTecModule, NSObject)

RCT_EXTERN_METHOD(initialize:(NSDictionary *)config
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
