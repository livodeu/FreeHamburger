Change Log
==========

## 1.0.1

_2020-09_

* New: Initial version

## 1.1

_2021-12_

* Changed: The app is now built for specific binary interfaces (ABIs) to reduce the APK size.
* Changed: The app targets API level 31 now.
* Fixed: Date sometimes overlapped with title text next to it
* Changed: Attached videos (at the bottom of some articles)
    * Long press of a shortened title of an attached video reveals it all
    * When watching an attached video, tapping the screen pauses/resumes the playback
* Changed: Improved handling of embedded external content which can now be opened in a browser
* New: Added a "Quick Settings" tile to toggle background updates  
* Changed: Added selected further hosts to the whitelist
* Changed: User certificates are now accepted  
* Upgrade: Several libraries have been updated:
    * appcompat 1.2.0 → 1.4.0 [release](https://developer.android.com/jetpack/androidx/releases/appcompat?hl=en#1.4.0)
    * conscrypt 2.5.1 → 2.5.2 [comparison](https://github.com/google/conscrypt/compare/2.5.1...2.5.2)
    * exoplayer 2.12.0 → 2.16.1 [release notes](https://github.com/google/ExoPlayer/blob/release-v2/RELEASENOTES.md)
    * material 1.2.1 → 1.4.0 [release](https://github.com/material-components/material-components-android/releases/tag/1.4.0)
    * okhttp 4.9.0 → 4.9.2 [change log](https://github.com/square/okhttp/blob/master/CHANGELOG.md)
    * picasso 2.71828 → 2.8 [release](https://github.com/square/picasso/releases/tag/2.8)
    * recyclerview 1.1.0 → 1.2.1 [release](https://developer.android.com/jetpack/androidx/releases/recyclerview?hl=en#recyclerview-1.2.1)

## 1.2

_2022-02_

* Changed: Better visibility in dark/night mode
* New: Integration into "global search" (if supported by the device)
* Changed: Menu drawer uses space better on larger/modern phones
* New: Notification contents can now be styled as in the app's news list
* New: Background updates may now load news from a selection of categories
* New: Number of columns in the app's news list can be adjusted  
* Changed: Added a further host to the whitelist
* New: Picture-in-picture playback mode added for fullscreen videos (Android 8 and newer) 
* Upgrade: Several libraries have been updated:
    * appcompat 1.4.0 → 1.4.1 [release](https://developer.android.com/jetpack/androidx/releases/appcompat?hl=en#1.4.1)
    * material 1.4.0 → 1.5.0 [release](https://github.com/material-components/material-components-android/releases/tag/1.5.0)
    * okhttp 4.9.2 → 4.9.3 [change log](https://github.com/square/okhttp/blob/master/CHANGELOG.md)
    * preference 1.1.1 → 1.2.0 [change log](https://developer.android.com/jetpack/androidx/releases/preference?hl=en#1.2.0)

## 1.3

_2022-0x_

* Changed: Theme based on Material3 flavour
* New: App widgets are now available
* New: News can be updated more frequently  
* Upgrade: A library has been updated:
  * exoplayer 2.16.1 → 2.17.0 [release notes](https://github.com/google/ExoPlayer/blob/release-v2/RELEASENOTES.md)
