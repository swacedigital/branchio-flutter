#import "BranchioPlugin.h"
#import "Branch/Branch.h"

typedef NSString*const BranchFlutter NS_STRING_ENUM;

BranchFlutter _Nonnull BranchFlutterInitialize = @"init";
BranchFlutter _Nonnull BranchFlutterSetIdentity = @"setIdentity";
BranchFlutter _Nonnull BranchFlutterLogEvent = @"logEvent";
BranchFlutter _Nonnull BranchFlutterLatestReferringParams = @"latestReferringParams";
BranchFlutter _Nonnull BranchFlutterUnsetIdentity = @"unsetIdentity";

@interface BranchioPlugin() <FlutterApplicationLifeCycleDelegate, FlutterStreamHandler>

@property (nonatomic, strong) NSDictionary *launchOptions;
@property (nonatomic, strong) FlutterEventSink sink;

@end

@implementation BranchioPlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    FlutterMethodChannel* channel = [FlutterMethodChannel
                                     methodChannelWithName:@"plugins.swace.se/branchio"
                                     binaryMessenger:[registrar messenger]];
    

    FlutterEventChannel* eventChannel = [FlutterEventChannel
                                         eventChannelWithName:@"plugins.swace.se/branchio-events"
                                         binaryMessenger:[registrar messenger]];
    BranchioPlugin* instance = [BranchioPlugin new];
    [eventChannel setStreamHandler:instance];
    [registrar addApplicationDelegate:instance];
    [registrar addMethodCallDelegate:instance channel:channel];
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    if([call.method isEqualToString:BranchFlutterInitialize]) {
        [self initBranchWithParams:[call.arguments valueForKey:@"is_test"]
                             debug:[call.arguments valueForKey:@"debug"]];
        result(nil);
    } else if ([call.method isEqualToString:BranchFlutterSetIdentity]) {
        [self setIdentity:[call.arguments objectForKey:@"user_id"]];
        result(nil);
    } else if ([call.method isEqualToString:BranchFlutterLogEvent]) {
        [self logEvent:[call.arguments objectForKey:@"name"] withParams:[call.arguments objectForKey:@"params"]];
        result(nil);
    } else if ([call.method isEqualToString:BranchFlutterLatestReferringParams]) {
        result([self latestReferringParams]);
    } else if([call.method isEqualToString:BranchFlutterUnsetIdentity]) {
        [self unsetIdentity];
        result(nil);
    } else {
        result(FlutterMethodNotImplemented);
    }
}

// MARK: Stream handler

- (FlutterError *)onListenWithArguments:(id)arguments eventSink:(FlutterEventSink)events {
    self.sink = events;
    return nil;
}

- (FlutterError *)onCancelWithArguments:(id)arguments {
    self.sink = nil;
    return nil;
}

// MARK: Branch methods

- (void)initBranchWithParams:(BOOL)isTest debug:(BOOL)debug {
    [Branch setUseTestBranchKey:isTest];
    if(debug) [[Branch getInstance] setDebug];
    // listener for Branch Deep Link data
    [[Branch getInstance] initSessionWithLaunchOptions:self.launchOptions];
}

- (void)setIdentity:(NSString *)userId {
    [[Branch getInstance] setIdentity:userId];
}

- (void)unsetIdentity {
    [[Branch getInstance] logout];
}

- (void)logEvent:(NSString *)name withParams:(NSDictionary <NSString *, NSString *> *)params {
    BranchEvent *event = [[BranchEvent alloc] initWithName:name];
    [event setCustomData:params];
    [event logEvent];
}

- (nullable NSDictionary *)latestReferringParams {
    return [[Branch getInstance] getLatestReferringParams];
}


//MARK: FlutterApplicationLifeCycleDelegate

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    self.launchOptions = launchOptions;
    return true;
}

- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void (^)(NSArray * _Nullable))restorationHandler {
    // handler for Universal Links
    BOOL handled = [[Branch getInstance] continueUserActivity:userActivity];
    NSLog(@"application opened. sink: %@", self.sink);
    if(handled && self.sink) {
        self.sink([self latestReferringParams]);
    }
    return handled;
}


- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation {
    BOOL handled = [[Branch getInstance] application:application openURL:url sourceApplication:sourceApplication annotation:annotation];
    NSLog(@"application opened. sink: %@", self.sink);
    if(handled && self.sink) {
        self.sink([self latestReferringParams]);
    }
    return handled;
}

@end
