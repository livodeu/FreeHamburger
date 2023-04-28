# ![Logo](app/src/main/res/mipmap-mdpi/ic_launcher.png) Free Hamburgers
Hamburger is a news app for Android phones - it displays the news issued by the primary German public television and radio broadcaster.

In contrast to the app provided by the broadcaster itself, Hamburger does **not** track the user in any way.

For information about the hidden activities of the broadcaster app, see [kuketz-blog.de](https://www.kuketz-blog.de/ard-zdf-apps-unter-der-lupe-tracking-und-verstoesse-gegen-das-ttdsg/)
and [mobilsicher.de](https://appcheck.mobilsicher.de/appchecks/tagesschau-aktuelle-nachrichten) (both in German).


## Get it

Either 

download the APK package that is available under ["Releases"](https://github.com/livodeu/FreeHamburger/releases/latest)

or 

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.svg" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/de.freehamburger/)

## Build

To build the release variant, you must add a file named "keystore.properties" in the root directory of the project.
It must contain the following entries:

    storeFile <file://absolute.path.to.keystore>
    storePassword <store_password>
    keyAlias <key_alias>
    keyPassword <key_password>

