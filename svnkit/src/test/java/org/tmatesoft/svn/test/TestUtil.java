package org.tmatesoft.svn.test;

import java.io.File;

public class TestUtil {
    public static File createDirectory(File parentPath, String suggestedName) {
        File path = new File(parentPath, suggestedName);
        if (!path.exists()) {
            path.mkdirs();
            return path;
        }

        for (int attempt = 0; attempt < 100; attempt++) {
            final String name = suggestedName + "." + attempt;
            path = new File(parentPath, name);
            if (!path.exists()) {
                path.mkdirs();
                return path;
            }
        }

        throw new RuntimeException("Unable to create directory in " + parentPath);
    }
}
