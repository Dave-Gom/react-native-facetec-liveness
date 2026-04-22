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
  # FRAMEWORK_SEARCH_PATHS are set per build configuration in the Podfile
  # (post_install) to select dev vs prod xcframework automatically.
  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
    "OTHER_LDFLAGS"  => "$(inherited) -framework \"FaceTecSDK\""
  }
end
