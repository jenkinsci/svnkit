/*
 * Created on 16.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class SVNFileUtil {

    public final static boolean isWindows;

    static {
        String osName = System.getProperty("os.name");
        isWindows = osName != null && osName.toLowerCase().indexOf("windows") >= 0;
    }
    
    public static File createTempFile(File sibling) throws IOException {
        File parent = sibling.getParentFile();
        File result = null; 
        if (!parent.exists() || !parent.isDirectory()) {
            result = File.createTempFile("javasvn", "tmp");
        }  else {
            result = File.createTempFile("javasvn", "tmp", parent);
        }
        return result;
    }

    public static void rename(File src, File dst) throws IOException {
        if (!src.exists() || !src.isFile()) {
            throw new IOException("can't rename file '" + src.getAbsolutePath() + "' : file doesn't exist.");            
        }
        if (dst.isDirectory()) {
            throw new IOException("can't overwrite file '" + dst.getAbsolutePath() + "' : it is a directory.");            
        }
        boolean renamed = src.renameTo(dst);
        if (!renamed) {
            if (dst.exists()) {
                boolean deleted = dst.delete();
                if (!deleted || dst.exists()) {
                    throw new IOException("can't overwrite file '" + dst.getAbsolutePath() + "'.");            
                }
            }
            if (!src.renameTo(dst)) {
                throw new IOException("can't rename file '" + src.getAbsolutePath() + "'.");            
            }
        }
    }
    
    public static boolean setReadonly(File file, boolean readonly) throws IOException {
        if (!file.exists()) {
            throw new IOException("can't change file RO state '" + file.getAbsolutePath() + "' : file doesn't exist");            
        }
        if (readonly) {
            return file.setReadOnly();
        }
        File tmpFile = File.createTempFile("javasvn", "tmp", file.getParentFile());
        copy(file, tmpFile);
        rename(tmpFile, file);
        return true;
    }

    public static void setExecutable(File file, boolean executable) {
        if (isWindows || file == null || !file.exists()) {
            return;
        }
        try {
            Runtime.getRuntime().exec("chmod ugo" + (executable ? "+" : "-") + "x \"" + file.getAbsolutePath() + "\"");
        } catch (Throwable th) {}
    }
    
    public static boolean isSymlink(File file) {
        if (isWindows || file == null) {
            return false;
        }
        if (!file.exists()) {
            File parent = file.getParentFile();
            String[] children = parent.list();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    if (children[i].equals(file.getName())) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            try {
                return !file.getAbsolutePath().equals(file.getCanonicalPath());
            } catch (IOException e) {
            }
        }
        return false;
    }

    public static void copy(File src, File dst) throws IOException {
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        try {
            srcChannel = new FileInputStream(src).getChannel();
            dstChannel = new FileOutputStream(dst).getChannel();
            long count = srcChannel.size();
            while(count > 0) {
                count -= dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
            }
        } finally {
            if (dstChannel != null) {
                try {
                    dstChannel.close();
                } catch (IOException e) {}
            }
            if (srcChannel != null) {
                try {
                    srcChannel.close();
                } catch (IOException e) {}
            }
        }
    }

    public static boolean createSymlink(File src, File linkFile) throws IOException {
        if (isWindows) {
            return false;
        }
        if (src.exists() || isSymlink(src)) {
            throw new IOException("can't create symlink '" + src.getAbsolutePath() + "' : file already exists.");
        }
        String linkTarget = readSingleLine(linkFile);
        Runtime.getRuntime().exec("ln -s '" + linkTarget + "' '" + src.getAbsolutePath() + "'");
        return isSymlink(src);
    }
    
    private static String readSingleLine(File file) throws IOException {
        if (!file.isFile() || !file.canRead()) {
            throw new IOException("can't open file '" + file.getAbsolutePath() + "'");
        }
        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            line = reader.readLine();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {}
            }
        }
        return line;
    }
}
