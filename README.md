# Free Hamburgers
<p>FreeHamburger is a news app for Android phones - it displays the news issued by the primary German public television and radio broadcaster.</p>
<p>In contrast to the app provided by the broadcaster itself, FreeHamburger does not track the user in any way.</p>

## Build

<p>To build the release variant, you must add a file named "keystore.properties" in the root directory of the project.
It must contain the following entries:</p>

    storeFile <file://absolute.path.to.keystore>
    storePassword <store_password>
    keyAlias <key_alias>
    keyPassword <key_password>

