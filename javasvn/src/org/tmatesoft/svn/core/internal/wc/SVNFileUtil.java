/*
 * Created on 16.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.*;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.StringTokenizer;
import java.util.Date;

import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.core.io.SVNException;

public class SVNFileUtil {

    private static final String BINARY_MIME_TYPE = "application/octet-stream";

    public final static boolean isWindows;

    private static String ourGroupID;
    private static String ourUserID;

    static {
        String osName = System.getProperty("os.name");
        isWindows = osName != null && osName.toLowerCase().indexOf("windows") >= 0;
    }

    public static String getBasePath(File file) {
        File base = file.getParentFile();
        while (base != null) {
            if (base.isDirectory()) {
                File adminDir = new File(base, ".svn");
                if (adminDir.exists() && adminDir.isDirectory()) {
                    break;
                }
            }
            base = base.getParentFile();
        }
        String path = file.getAbsolutePath();
        if (base != null) {
            path = path.substring(base.getAbsolutePath().length());
        }
        path = path.replace(File.separatorChar, '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    public static File createUniqueFile(File parent, String name, String suffix) {
        File file = new File(parent, name + suffix);
        for (int i = 1; i < 99999; i++) {
            if (!file.exists()) {
                return file;
            }
            file = new File(parent, name + "." + i + suffix);
        }
        return file;
    }

    public static void rename(File src, File dst) throws IOException {
        if (!src.exists()) {
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
        copy(file, tmpFile, false);
        rename(tmpFile, file);
        return true;
    }

    public static void setExecutable(File file, boolean executable) {
        if (isWindows || file == null || !file.exists()) {
            return;
        }
        try {
            execCommand(new String[] {"chmod", executable ? "ugo+x" : "ugo-x", file.getAbsolutePath()});
        } catch (Throwable th) {
            DebugLog.error(th);
        }
    }

    public static boolean isSymlink(File file) {
        if (isWindows || file == null) {
            return false;
        }
        String line = execCommand(new String[] {"ls", "-ld", file.getAbsolutePath()});
        DebugLog.log("ls output: " + line);
        return line != null && line.startsWith("l");
    }

    public static void copy(File src, File dst, boolean safe) throws IOException {
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
        File tmpDst = dst;
        if (dst.exists()) {
            if (safe) {
                tmpDst = createUniqueFile(dst.getParentFile(), dst.getName(), ".tmp");
            } else {
                dst.delete();
            }
        }
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        try {
            srcChannel = new FileInputStream(src).getChannel();
            dstChannel = new FileOutputStream(tmpDst).getChannel();
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
        if (safe && tmpDst != dst) {
            rename(tmpDst, dst);
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

    public static String computeChecksum(File file) throws SVNException {
        if (file == null || !file.exists() || file.isDirectory()) {
            return null;
        }
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new SVNException(e.getMessage());
        }
        InputStream is = openFileForReading(file);
        try {
            while(true) {
                int b = is.read();
                if (b < 0) {
                    break;
                }
                digest.update((byte) (b & 0xFF));
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            closeFile(is);
        }
        return toHexDigest(digest);
    }

    public static boolean compareFiles(File f1, File f2, MessageDigest digest) throws SVNException {
        if (f1 == null || f2 == null) {
            throw new SVNException("can't accept 'null' values in compareFiles method");
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
        InputStream is1 = openFileForReading(f1);
        InputStream is2 = openFileForReading(f2);
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
                if (digest != null) {
                    digest.update((byte) (b1 & 0xFF));
                }
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            closeFile(is1);
            closeFile(is2);
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
        deleteAll(dir, true);
    }

    public static void deleteAll(File dir, boolean deleteDirs) {
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
        if (dir.isDirectory() && !deleteDirs) {
            return;
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

    public static long roundTimeStamp(long tstamp) {
        long remainder = tstamp - ((tstamp/1000) * 1000);
        if (remainder < 500) {
            return (tstamp / 1000) * 1000;
        }
        return (tstamp / 1000) * 1000 + 1000;
    }

    public static void sleepForTimestamp() {
        long time = System.currentTimeMillis();
        time = 1010 - (time - (time / 1000) * 1000);
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {}
    }

    public static String detectMimeType(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        byte[] buffer = new byte[1024];
        InputStream is = null;
        int read = 0;
        try {
            is = openFileForReading(file);
            read = is.read(buffer);
        } catch (IOException e) {
            return null;
        } catch (SVNException e) {
            return null;
        } finally {
            closeFile(is);
        }
        int binaryCount = 0;
        for (int i = 0; i < read; i++) {
            byte b = buffer[i];
            if (b == 0) {
                return BINARY_MIME_TYPE;
            }
            if (b < 0x07 || (b > 0x0d && b < 0x20) || b > 0x7F) {
                binaryCount++;
            }
        }
        if (read > 0 && binaryCount * 1000 / read > 850) {
            return BINARY_MIME_TYPE;
        }
        return null;
    }

    public static boolean isExecutable(File file) {
        if (isWindows) {
            return false;
        }
        DebugLog.log("checking file mod: " + file);
        String[] commandLine = new String[] {"ls", "-ln", file.getAbsolutePath()};
        String line = execCommand(commandLine);
        if (line == null || line.indexOf(' ') < 0) {
            return false;
        }
        int index = 0;

        String mod = null;
        String fuid = null;
        String fgid = null;
        for(StringTokenizer tokens = new StringTokenizer(line, " \t"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (index == 0) {
                mod = token;
            } else if (index == 2) {
                fuid = token;
            } else if (index == 3) {
                fgid = token;
            } else if (index > 3) {
                break;
            }
            index++;
        }
        if (mod == null) {
            return false;
        }
        DebugLog.log("fuid: " + fuid);
        DebugLog.log("guid: " + fgid);
        DebugLog.log("mod: " + mod);
        DebugLog.log("c.user: " + getCurrentUser());
        DebugLog.log("c.group: " + getCurrentGroup());
        if (getCurrentUser().equals(fuid)) {
            return mod.toLowerCase().indexOf('x') >= 0 &&
                mod.toLowerCase().indexOf('x') < 4;
        } else if (getCurrentGroup().equals(fgid)) {
            return mod.toLowerCase().indexOf('x', 4) >= 4 &&
                mod.toLowerCase().indexOf('x', 4) < 7;
        } else {
            return mod.toLowerCase().indexOf('x', 7) >= 7;
        }
    }

    public static void copyDirectory(File srcDir, File dstDir) throws IOException {
        if (!dstDir.exists()) {
            dstDir.mkdirs();
        }
        File[] files = srcDir.listFiles();
        for (int i = 0; files != null && i < files.length; i++) {
            File file = files[i];
            if (file.getName().equals("..") || file.getName().equals(".") || file.equals(dstDir)) {
                continue;
            }
            SVNFileType fileType = SVNFileType.getType(file);
            File dst = new File(dstDir, file.getName());

            if (fileType == SVNFileType.FILE) {
                boolean executable = isExecutable(file);
                copy(file, dst, false);
                if (executable) {
                    setExecutable(dst, executable);
                }
            } else if (fileType == SVNFileType.DIRECTORY) {
                copyDirectory(file, dst);
                if (file.isHidden()) {
                    setHidden(dst, true);
                }
            } else if (fileType == SVNFileType.SYMLINK) {
                // special case, copy of symlink.
            }
        }
    }

    public static OutputStream openFileForWriting(File file) throws SVNException  {
        return openFileForWriting(file, false);
    }

    public static OutputStream openFileForWriting(File file, boolean append) throws SVNException  {
        if (file == null) {
            return null;
        }
        if (file.exists() && (!file.isFile() || !file.canWrite())) {
            throw new SVNException("svn: Cannot write to '" + file + "': path refers to directory or write access denied");
        }
        try {
            return new BufferedOutputStream(new FileOutputStream(file, append));
        } catch (FileNotFoundException e) {
            throw new SVNException("svn: Cannot write to '" + file + "': " + e.getMessage());
        }
    }

    public static InputStream openFileForReading(File file) throws SVNException  {
        if (file == null) {
            return null;
        }
        if (!file.isFile() || !file.canRead()) {
            throw new SVNException("svn: Cannot read from '" + file + "': path refers to directory or read access denied");
        }
        if (!file.exists()) {
            return new ByteArrayInputStream(new byte[0]);
        }
        try {
            return new BufferedInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new SVNException("svn: Cannot read from '" + file + "': " + e.getMessage());
        }
    }

    public static void closeFile(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (IOException e) {
            //
        }
    }

    public static void closeFile(OutputStream os) {
        if (os == null) {
            return;
        }
        try {
            os.close();
        } catch (IOException e) {
            //
        }
    }

    private static String execCommand(String[] commandLine) {
        InputStream is = null;
        StringBuffer result = new StringBuffer();
        try {
            Process process = Runtime.getRuntime().exec(commandLine);
            is = process.getInputStream();
            int rc = process.waitFor();
            if (rc != 0) {
                return null;
            }
            int r;
            while((r = is.read()) >= 0) {
                result.append((char) (r & 0xFF));
            }
            return result.toString().trim();
        } catch (IOException e) {
            DebugLog.error(e);
        } catch (InterruptedException e) {
            DebugLog.error(e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
            }

        }
        return null;
    }

    private static String getCurrentUser() {
        if (isWindows) {
            return System.getProperty("user.name");
        }
        if (ourUserID == null) {
            ourUserID = execCommand(new String[] {"id", "-u"});
            if (ourUserID == null) {
                ourUserID = "0";
            }
        }
        return ourUserID;
    }



    private static String getCurrentGroup() {
        if (isWindows) {
            return System.getProperty("user.name");
        }
        if (ourGroupID == null) {
            ourGroupID = execCommand(new String[] {"id", "-g"});
            if (ourGroupID == null) {
                ourGroupID = "0";
            }
        }
        return ourGroupID;
    }


}
