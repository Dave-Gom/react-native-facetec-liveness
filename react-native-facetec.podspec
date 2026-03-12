require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-facetec"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://facetec.com"
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "13.0" }
  s.source       = { :path => "." }

  s.source_files = "ios/FaceTecLiveness/**/*.{h,m,mm,swift}"
  s.swift_version = "5.0"

  # React Native dependency
  s.dependency "React-Core"

  # FaceTec SDK – the xcframework lives in the project root's ios/ directory.
  # CocoaPods does not support vendored_frameworks with paths outside the pod
  # tree, so we configure framework search paths manually per SDK.
  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
    "FRAMEWORK_SEARCH_PATHS[sdk=iphoneos*]"        => "$(inherited) \"${PODS_ROOT}/../../ios/FaceTecSDKForDevelopment.xcframework/ios-arm64\"",
    "FRAMEWORK_SEARCH_PATHS[sdk=iphonesimulator*]"  => "$(inherited) \"${PODS_ROOT}/../../ios/FaceTecSDKForDevelopment.xcframework/ios-arm64_x86_64-simulator\"",
    "OTHER_LDFLAGS"                                 => "$(inherited) -framework \"FaceTecSDK\""
  }
end
