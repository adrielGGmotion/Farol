# Farol

Farol is a minimalist Android launcher focused on functionality, productivity, and personalization.
It is an independently developed fork of [Stario](https://github.com/albu-razvan/Stario).

## Project Status

Farol is under active development. Release information and updated promotional images will be
added when it is ready for release.

## Fresh Setup

Farol installs as a separate app from Stario; it does not replace or update it. Stario settings
are not imported. Set up your layout and preferences again, select Farol as your default home
app, and grant any optional permissions you need. Your Stario installation remains untouched.

## Features

- **Material You Support**  
  Integrates seamlessly with Android’s Material You dynamic theming system, adapting colors based on
  your wallpaper and device settings.

- **Application Customization**  
  Customize your home screen with various icon packs and shapes to personalize your experience.

- **Built-In Weather Widget**  
  Check current weather conditions and forecasts right from your home screen.

- **Global Search Integration**  
  Perform fast, privacy-respecting searches using Kagi directly from the launcher.

- **Minimalistic Media Player Controls**  
  Manage your media playback easily with integrated controls.

- **Application Categories**  
  Organize your app drawer with customizable categories for better app management.

- **RSS/Atom Reader**  
  Stay up-to-date with news and blog feeds via the integrated RSS/Atom reader.

- **Page Sorting**  
  Easily reorder your home screen pages to suit your workflow.

## Compatibility

- Requires **Android SDK 29+** (Android 10.0 or later)
- Compatible with AOSP and most major OEM devices
- Should work with custom ROMs, though these are not officially tested — user feedback is welcome

## Development

You can quickly set up the development environment using the provided Dockerfile:

```bash
docker build --platform linux/amd64 -t farol-dev .

docker run --platform linux/amd64 --rm -it \
  -v </path/to/output>:/usr/local/farol/build \
  farol-dev
```

> Tip: Use `--rm` to automatically remove the container after use.

## Building

Should you wish to build the application yourself, run the build
script from within the development environment:

```bash
./build.sh
```

Alternatively, to also build a signed copy (APK and AAB), pass a keystore to the build script:

```bash
docker run --platform linux/amd64 --rm -it \
  -v </path/to/output>:/usr/local/farol/build \
  -v </path/to/keystore>:/usr/local/farol/keystore \
  farol-dev

./build.sh \
  -K /usr/local/farol/keystore/keystore.jks \
  -P keystore_password \
  -a key_alias \
  -p key_password
```

## Reproducible Builds

Check for RBs with the locally built unsigned APK and [apksigcopier](https://github.com/obfusk/apksigcopier). 

Firstly, copy the signature from the signed APK onto your built unsigned APK:

```bash
apksigcopier copy signed-from-source.apk unsigned-built-locally.apk out.apk
```

Then compare the two APKs:

```bash
apksigcopier compare farol-from-source.apk farol-built-locally.apk
```

> NOTE: `apksigcopier compare` requires [apksigner](https://developer.android.com/tools/apksigner).

## Contributing and Contact

Report bugs and suggest features through this repository's Issues tab.
Translations will be managed through Weblate; contribution details will be added when available.

Farol is maintained by [Adriel](https://adriel.men). You can also reach him on
[Telegram](https://t.me/adrielggmotion).

## Credits and License

Farol builds on [Stario](https://github.com/albu-razvan/Stario), created by Răzvan Albu,
and the work of its contributors. Upstream copyright and license notices are preserved.

Licensed under the [GNU GPL v3.0 or later](LICENSE.md).
