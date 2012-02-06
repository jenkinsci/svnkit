package org.tmatesoft.svn.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

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

    public static void log(String message) {
        System.out.println(message);
    }

    public static void writeFileContentsString(File file, String contentsString) throws IOException {
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        try {
            fileOutputStream.write(contentsString.getBytes());
        } finally {
            SVNFileUtil.closeFile(fileOutputStream);
        }
    }

    static boolean isNewWorkingCopyTest() {
        final String propertyValue = System.getProperty("svnkit.wc.17", "true");
        return "true".equals(propertyValue);
    }
}
