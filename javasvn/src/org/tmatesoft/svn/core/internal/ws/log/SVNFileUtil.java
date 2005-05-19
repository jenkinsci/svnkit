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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

import org.tmatesoft.svn.util.PathUtil;

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
        if (src == null || dst == null) {
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        if (!src.exists()) {
            dst.delete();
            return;
        }
        if (dst.exists()) {
            dst.delete();
        }
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
        if (linkTarget.startsWith("link")) {
            linkTarget = linkTarget.substring("link".length()).trim();
        }
        Runtime.getRuntime().exec("ln -s '" + linkTarget + "' '" + src.getAbsolutePath() + "'");
        return isSymlink(src);
    }

    public static boolean detranslateSymlink(File src, File linkFile) throws IOException {
        if (isWindows) {
            return false;
        }
        if (!src.exists() || !isSymlink(src)) {
            throw new IOException("can't detranslate symlink '" + src.getAbsolutePath() + "' : file doesn't exists or not a symlink.");
        }
        String linkPath = src.getCanonicalPath();
        String locationPath = src.getAbsolutePath();
        if (linkPath.startsWith(locationPath)) {
            linkPath = PathUtil.removeLeadingSlash(linkPath.substring(locationPath.length()));
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(linkFile);
            os.write("link ".getBytes("UTF-8"));
            os.write(linkPath.getBytes("UTF-8"));
        } finally {
            if (os != null) {
                os.close();
            }
        }
        return true;
    }
    
    public static boolean compareFiles(File f1, File f2, MessageDigest digest) throws IOException {
        if (f1 == null || f2 == null) {
            throw new IOException("can't accept 'null' values in compareFiles method");
        }
        if (f1.equals(f2)) {
            return true;
        }
        boolean equals = true;
        if (f1.length() != f2.length()) {
            if (digest == null) {
                return false;
            }
            equals = false;
        }
        InputStream is1 = null;
        InputStream is2 = null;
        try {
            while(true) {
                int b1 = is1.read();
                int b2 = is2.read();
                if (b1 != b2) {
                    if (digest == null) {
                        return false;
                    }
                    equals = false;
                }                
                if (b1 < 0) {
                    break;
                }
                digest.update((byte) (b1 & 0xFF));
            }
        } finally {
            if (is1 != null) {
                is1.close();
            }
            if (is2 != null) {
                is2.close();
            }
        }
        return equals;
    }

    public static void setHidden(File file, boolean hidden) {
        if (!isWindows || file == null || !file.exists() || file.isHidden()) {
            return;
        }
        try {
            Runtime.getRuntime().exec("attrib " + (hidden ? "+" : "-") + "H \"" + file.getAbsolutePath() + "\"");
        } catch (Throwable th) {}
    }

    public static void deleteAll(File dir) {
        if (dir == null) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                deleteAll(child);
            }
        }
        dir.delete();
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

    public static String toHexDigest(MessageDigest digest) {
        if (digest == null) {
            return null;
        }
        byte[] result = digest.digest();
        String hexDigest = "";
        for (int i = 0; i < result.length; i++) {
            byte b = result[i];
            int lo = b & 0xf;
            int hi = (b >> 4) & 0xf;
            hexDigest += Integer.toHexString(hi) + Integer.toHexString(lo);
        }
        return hexDigest;
    }
}
