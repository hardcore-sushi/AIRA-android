# AIRA Android
AIRA is peer-to-peer encrypted communication tool for local networks built on the [PSEC protocol](https://forge.chapril.org/hardcoresushi/PSEC). It allows to securely send text messages and files without any server or Internet access. AIRA automatically discovers and connects to other peers on your network, so you don't need any prior configuration to start communicating.

Here is the Android version. You can find the original AIRA desktop version [here](https://forge.chapril.org/hardcoresushi/AIRA).

<p align="center">
<img alt="Screenshot of the main screen of AIRA-android, with Bob online and Angerfist and Barack Obama as contacts" src="https://forge.chapril.org/hardcoresushi/AIRA-android/raw/branch/master/screenshots/1.png" height="550"/>
<img alt="Screenshot of a conversation between Alice and Bob about AIRA" src="https://forge.chapril.org/hardcoresushi/AIRA-android/raw/branch/master/screenshots/2.png" height="550"/>
<img alt="Screenshot of the settings screen of AIRA-android" src="https://forge.chapril.org/hardcoresushi/AIRA-android/raw/branch/master/screenshots/3.png" height="550"/>
</p>

# Disclaimer
AIRA is still under developement and is not ready for production usage yet. Not all features have been implemented and bugs are expected. Neither the code or the PSEC protocol received any security audit and therefore shouldn't be considered fully secure. AIRA is provided "as is", without any warranty of any kind.

# Features
- End-to-End encryption using the [PSEC protocol](https://forge.chapril.org/hardcoresushi/PSEC)
- Automatic peer discovery using mDNS
- Manual peer connection
- File transferts
- Notifications
- Encrypted database
- Contact verification
- IPv4/v6 compatibility
- Free/Libre and Open Source

# Download
AIRA releases are availables in the "Release" section. All APKs are signed with my PGP key available on keyservers. To download it:

`gpg --keyserver hkp://pool.sks-keyservers.net --recv-keys 007F84120107191E` \
Fingerprint: `BD56 2147 9E7B 74D3 6A40  5BE8 007F 8412 0107 191E` \
Email: `Hardcore Sushi <hardcore.sushi@disroot.org>`

Then, verify APK: `gpg --verify AIRA.apk.asc AIRA.apk`

__Don't install the APK if the verification fails!__

# Build
### Install Rust
AIRA android uses some code from the desktop version which is written in Rust. Therefore, you need to compile this Rust code first.
```
curl --proto '=https' --tlsv1.3 -sSf https://sh.rustup.rs | sh
rustup target add aarch64-linux-android armv7-linux-androideabi
```
### Install NDK
The Rust code uses a crate called "rusqlite" to store data in SQLite databases. This crates uses the original SQLite3 library written in C. Therefore, to compile it you need the Android NDK. You can find instructions to install the NDK here: https://developer.android.com/ndk/guides

Once installed, you need to define the $ANDROID_NDK_HOME environment variable (if not already set):
```
export ANDROID_NDK_HOME=/home/<user>/Android/SDK/ndk/<NDK version>"
```
### Download AIRA
```
git clone --depth=1 https://forge.chapril.org/hardcoresushi/AIRA-android.git && cd AIRA-android
```
### Verify commit
```
git verify-commit HEAD
```
### Build AIRA Rust code
```
cd app/src/main/native
./build.sh
```
### Build final APK
If you have AndroidStudio installed, you can just open the project directory and then start the build process. Otherwise, you can use Gradle from the command line:

Generate a signed APK with your keystore:
```
# From the project root directory:
./gradlew assembleRelease -Pandroid.injected.signing.store.file=<KEYFILE> -Pandroid.injected.signing.store.password=<STORE_PASSWORD> -Pandroid.injected.signing.key.alias=<KEY_ALIAS> -Pandroid.injected.signing.key.password=<KEY_PASSWORD>
```
Generate an unsigned APK:
```
./gradlew assembleRelease
```
Once completed, the APKs will be located under `app/build/outputs/apk/release/`.

If you generate an unsigned APK you won't be able to install it as-is on your device. You will need to force install it with ADB:
```
adb install app-release-unsigned.apk
```
