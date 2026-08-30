The signing key does not live in this repo.

CI writes `nag.jks` here from the `KEYSTORE_B64` repository secret before building.

If you build locally in Android Studio, decode the same base64 into `signing/nag.jks`
yourself. Without it the release build is simply unsigned, and an unsigned APK cannot
be installed over one signed with the real key.
