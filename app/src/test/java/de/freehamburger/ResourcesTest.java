package de.freehamburger;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class ResourcesTest {

    /** Tests the existence of some qualified resource directories */
    @Test
    public void testQualifiers() {
        File appDir = new File(new File("").getAbsolutePath() + "/");
        assertTrue("Not a directory: \"" + appDir.getAbsolutePath() + "\"", appDir.isDirectory());
        File resDir = new File(appDir.getAbsolutePath() + "/src/main/res/");
        assertTrue(resDir.isDirectory());
        final String[] subdirs = new String[] {
                "layout-v31",
                "values-de", "values-h600dp-port", "values-night", "values-v24", "values-v31", "values-w1000dp",
                "xml-v31"
        };
        for (String subdir : subdirs) {
            File qualifiedResDir = new File(resDir, subdir);
            assertTrue("Not a directory: \"" + qualifiedResDir.getAbsolutePath() + "\"", qualifiedResDir.isDirectory());
        }
    }
}
