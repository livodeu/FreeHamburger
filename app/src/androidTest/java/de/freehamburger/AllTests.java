package de.freehamburger;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({AppTest.class, InstalledAppsTest.class, DataAndGuiTest.class, WebViewTest.class, ArchiveTest.class, VersionTest.class, SearchTest.class})
public class AllTests {
}
