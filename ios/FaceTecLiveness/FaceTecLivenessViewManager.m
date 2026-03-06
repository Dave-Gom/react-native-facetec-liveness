#import <React/RCTViewManager.h>

@interface RCT_EXTERN_MODULE(FaceTecLivenessButtonManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(onResponse, RCTBubblingEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onError, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onStateChange, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(initializingText, NSString)
RCT_EXPORT_VIEW_PROPERTY(readyText, NSString)
RCT_EXPORT_VIEW_PROPERTY(errorText, NSString)
RCT_EXPORT_VIEW_PROPERTY(permissionDeniedText, NSString)

@end
