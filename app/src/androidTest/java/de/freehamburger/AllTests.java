package de.freehamburger;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({AppTest.class, InstalledAppsTest.class, DataAndGuiTest.class})
public class AllTests {
}
