
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBillieCamera2Spec.h"

@interface BillieCamera2 : NSObject <NativeBillieCamera2Spec>
#else
#import <React/RCTBridgeModule.h>

@interface BillieCamera2 : NSObject <RCTBridgeModule>
#endif

@end
