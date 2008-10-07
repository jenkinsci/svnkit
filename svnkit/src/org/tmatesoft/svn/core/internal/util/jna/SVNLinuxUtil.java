/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util.jna;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNReturnValueCallback;
import org.tmatesoft.svn.core.SVNException;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * @version 1.2.0
 * @author TMate Software Ltd.
 */
public class SVNLinuxUtil {

    private static Memory ourSharedMemory;
    private static int ourStatStructureOffset;
    
    static {
        try {
            ourSharedMemory = new Memory(1024);
        } catch (Throwable th) {
            ourSharedMemory = null;
        }
    }

    public static SVNFileType getFileType(File file) {
        if (file == null || ourSharedMemory == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return null;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    if (file.exists() || file.isDirectory() || file.isFile()) {
                        return null;
                    }
                    return SVNFileType.NONE;
                }
                int mode = SVNFileUtil.isOSX || SVNFileUtil.isBSD ?
                        ourSharedMemory.getShort(getFileModeOffset()) : ourSharedMemory.getInt(getFileModeOffset());
                int type = mode & 0170000;
                if (type == 0120000) {
                    return SVNFileType.SYMLINK;
                } else if (type == 0040000) {
                    return SVNFileType.DIRECTORY;
                } else if (type == 0100000) {
                    return SVNFileType.FILE;
                } else {
                    if (file.exists() || file.isDirectory() || file.isFile()) {
                        return null;
                    }
                    return SVNFileType.NONE;
                }
            }
        } catch (Throwable th) {
            //
        }
        return null;
    }

    public static Boolean isExecutable(File file) {
        if (file == null || ourSharedMemory == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return null;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    return null;
                }

                int mode = ourSharedMemory.getInt(getFileModeOffset());
                int fuid = ourSharedMemory.getInt(getFileUserIDOffset());
                int fgid = ourSharedMemory.getInt(getFileGroupIDOffset());
                
                int access = mode & 0777;
                int mask = 0111;
                if (JNALibraryLoader.getUID() == fuid) {
                    mask = 0100; // check user
                } else if (JNALibraryLoader.getGID() == fgid) {
                    mask = 0010; // check group 
                } else {
                    mask = 0001; // check other.
                }
                return Boolean.valueOf((access & mask) != 0);
            }
        } catch (Throwable th) {
            //
        }
        return null;
    }

    public static String getLinkTarget(File file) {
        if (file == null || ourSharedMemory == null) {
            return null;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return null;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = cLibrary.readlink(path, ourSharedMemory, 1024);
                }
                if (rc <= 0) {
                    return null;
                }
                byte[] buffer = new byte[rc];
                ourSharedMemory.read(0, buffer, 0, rc);
                return new String(buffer, 0, rc);
            }
        } catch (Throwable th) {
            //
        }
        return null;
    }

    public static boolean setExecutable(File file, boolean set) {
        if (file == null || ourSharedMemory == null) {
            return false;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return false;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    return false;
                }

                int mode = ourSharedMemory.getInt(getFileModeOffset());
                int access = mode & 0777;
                int mask = 0;
                if ((access & 0400) != 0) {
                    mask |= 0100;
                }
                if ((access & 0040) != 0) {
                    mask |= 0010;
                }
                if ((access & 0004) != 0) {
                    mask |= 0001;
                }
                if (mask == 0) {
                    return false;
                }
                synchronized (cLibrary) {
                    rc = cLibrary.chmod(path, set ? mask | access : mask ^ access);
                }
                return rc < 0 ? false : true;
            }
        } catch (Throwable th) {
            //
        }
        return false;
    }

    public static boolean setWritable(File file) {
        if (file == null || ourSharedMemory == null) {
            return false;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return false;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.lstat(path, ourSharedMemory) : 
                            cLibrary.__lxstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    return false;
                }

                int mode = ourSharedMemory.getInt(getFileModeOffset());
                int access = mode & 0777;
                int mask = 0;
                if ((access & 0400) != 0) {
                    mask |= 0200;
                }
                if ((access & 0040) != 0) {
                    mask |= 0020;
                }
                if ((access & 0004) != 0) {
                    mask |= 0002;
                }
                if (mask == 0) {
                    return false;
                }
                synchronized (cLibrary) {
                    rc = cLibrary.chmod(path, mask | access);
                }
                return rc < 0 ? false : true;
            }
        } catch (Throwable th) {
            //
        }
        return false;
    }

    public static boolean setSGID(File file) {
        if (file == null || ourSharedMemory == null) {
            return false;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return false;
            }
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();
                int rc;
                synchronized (cLibrary) {
                    rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? 
                            cLibrary.stat(path, ourSharedMemory) : 
                            cLibrary.__xstat64(0, path, ourSharedMemory);
                }
                if (rc < 0) {
                    return false;
                }

                int mode = ourSharedMemory.getInt(getFileModeOffset());
                int access = mode & 07777;
                int mask = 02000;
                if ((access & mask) != 0) {
                    return false;
                }
                synchronized (cLibrary) {
                    rc = cLibrary.chmod(path, mask | access);
                }
                return rc < 0 ? false : true;
            }
        } catch (Throwable th) {
            //
        }
        return false;
    }

    public static boolean createSymlink(File file, String linkName) {
        if (file == null || linkName == null || ourSharedMemory == null) {
            return false;
        }
        String path = file.getAbsolutePath();
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        try {
            ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
            if (cLibrary == null) {
                return false;
            }
            int rc;
            synchronized (cLibrary) {
                rc = cLibrary.symlink(linkName, path);
            }
            return rc < 0 ? false : true;
        } catch (Throwable th) {
            //
        }
        return false;
    }

    public static void lstatDump(Memory memory) {
        System.out.println("Now we are using mode offset = " + getFileModeOffset() + "; user offset = " + getFileUserIDOffset() + "; group offset = " + getFileGroupIDOffset());
        System.out.println("Memory dump: " + memory.toString());
        System.out.println();
        printMemory(ourSharedMemory);
        System.out.println();
        lstatDump(ourSharedMemory, getFileModeOffset(), getFileUserIDOffset(), getFileGroupIDOffset());
    }

    private static void printMemory(Memory memory) {
        System.out.println("Binary view:");
        for (long i = 0; i < memory.getSize(); i++) {
            System.out.print(Integer.toBinaryString(memory.getByte(i)));
            System.out.print(' ');
        }

        System.out.println();
        System.out.println("Octal view:");
        for (long i = 0; i < memory.getSize(); i++) {
            System.out.print(Integer.toOctalString(memory.getByte(i)));
            System.out.print(' ');
        }

        System.out.println();
        System.out.println("Hex view:");
        for (long i = 0; i < memory.getSize(); i++) {
            System.out.print(Integer.toHexString(memory.getByte(i)));
            System.out.print(' ');
        }
        System.out.println();

    }

    private static void lstatDump(Memory memory, int modeOffset, int userOffset, int groupOffset) {
        int mode = memory.getInt(modeOffset);
        int fuid = memory.getInt(userOffset);
        int fgid = memory.getInt(groupOffset);
        int access = mode & 0777;

        boolean isUR = (access & 0400) != 0;
        boolean isUW = (access & 0200) != 0;
        boolean isUX = (access & 0100) != 0;
        boolean isGR = (access & 0040) != 0;
        boolean isGW = (access & 0020) != 0;
        boolean isGX = (access & 0010) != 0;
        boolean isOR = (access & 0004) != 0;
        boolean isOW = (access & 0002) != 0;
        boolean isOX = (access & 0001) != 0;

        StringBuffer buffer = new StringBuffer();
        buffer.append(isUR ? "r" : "-");
        buffer.append(isUW ? "w" : "-");
        buffer.append(isUX ? "x" : "-");
        buffer.append(isGR ? "r" : "-");
        buffer.append(isGW ? "w" : "-");
        buffer.append(isGX ? "x" : "-");
        buffer.append(isOR ? "r" : "-");
        buffer.append(isOW ? "w" : "-");
        buffer.append(isOX ? "x" : "-");

        buffer.append("  ");
        buffer.append(fuid);
        buffer.append(" ");
        buffer.append(fgid);

        System.out.println(buffer);
        System.out.println();
    }

    public static void defineStatStructureOffset() {
        if (ourSharedMemory == null) {
            return;
        }

        ISVNCLibrary cLibrary = JNALibraryLoader.getCLibrary();
        if (cLibrary == null) {
            return;
        }

        int uid = JNALibraryLoader.getUID();

        File file = null;
        File file2 = null;
        final boolean[] failed = new boolean[]{false};
        try {
            ISVNReturnValueCallback returnValueCallback = new ISVNReturnValueCallback() {
                public void handleReturnValue(int returnValue) throws SVNException {
                    if (returnValue != 0) {
                        failed[0] = true;
                    }
                }

                public void handleChar(char ch) throws SVNException {
                }

                public boolean isHandleProgramOutput() {
                    return false;
                }
            };
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            int expectedAccess = 0765;
            int expectedAccess2 = 0567;
            file = createTestFile(tmpDir, Integer.toOctalString(expectedAccess), returnValueCallback);
            file2 = createTestFile(tmpDir, Integer.toOctalString(expectedAccess2), returnValueCallback);
            String path = file.getAbsolutePath();
            String path2 = file2.getAbsolutePath();
            
            synchronized (ourSharedMemory) {
                ourSharedMemory.clear();

                Pointer pointer1 = ourSharedMemory;
                Pointer pointer2 = new Memory(256);

                boolean fault = lstat(path, cLibrary, pointer1);
                if (fault) {
                    failed[0] = true;
                    return;
                }

                fault = lstat(path2, cLibrary, pointer2);
                if (fault) {
                    failed[0] = true;
                    return;
                }

                int offset = -1;
                for (int i = 0; i < 128; i++) {
                    int mode;
                    int fuid;
                    int fgid;
                    if (SVNFileUtil.isOSX || SVNFileUtil.isBSD) {
                        mode = pointer1.getInt(i);
                        fuid = pointer1.getInt(i + 4);
                        fgid = pointer1.getInt(i + 4 + 4);
                    } else {
                        mode = pointer1.getInt(i);
                        fuid = pointer1.getInt(i + 4 + 4);
                        fgid = pointer1.getInt(i + 4 + 4 + 4);
                    }

                    int access = mode & 0777;

                    if (access == expectedAccess && fuid == uid) {
                        int mode2;
                        int fuid2;
                        int fgid2;

                        if (SVNFileUtil.isOSX || SVNFileUtil.isBSD) {
                            mode2 = pointer2.getInt(i);
                            fuid2 = pointer2.getInt(i + 4);
                            fgid2 = pointer2.getInt(i + 4 + 4);
                        } else {
                            mode2 = pointer2.getInt(i);
                            fuid2 = pointer2.getInt(i + 4 + 4);
                            fgid2 = pointer2.getInt(i + 4 + 4 + 4);
                        }

                        int access2 = mode2 & 0777;                        

                        if (access2 == expectedAccess2 && fuid2 == uid && fgid == fgid2) {
                            offset = i;
                            break;
                        }
                    }
                }

                if (offset != -1) {
                    ourStatStructureOffset = offset;
                } else {
                    failed[0] = true;
                }
                System.out.println("Stat Structure Offset = " + ourStatStructureOffset);
            }
        } catch (SVNException e) {
            failed[0] = true;
            e.printStackTrace();
        } finally {
            try {
                if (file != null) {
                    SVNFileUtil.deleteFile(file);
                }
            } catch (SVNException e) {
                //
            }
            try {
                if (file2 != null) {
                    SVNFileUtil.deleteFile(file2);
                }
            } catch (SVNException e) {
                //
            }
            if (failed[0]) {
                defineDefaultOffset();
            }
        }
    }

    private static File createTestFile(File tmpDir, String permissions, ISVNReturnValueCallback callback) throws SVNException {
        File file = SVNFileUtil.createUniqueFile(tmpDir, "svnkit", ".init.jna", false);
        SVNFileUtil.execCommand(new String[]{SVNFileUtil.getChmodCommand(), permissions, file.getAbsolutePath()}, true, callback);
        return file;
    }

    private static boolean lstat(String path, ISVNCLibrary cLibrary, Pointer pointer) {
        int rc;
        synchronized (cLibrary) {
            rc = SVNFileUtil.isOSX || SVNFileUtil.isBSD ? cLibrary.lstat(path, pointer) : cLibrary.__lxstat64(0, path, pointer);
        }
        return rc < 0;
    }

    private static void defineDefaultOffset() {
    }

    private static int getFileModeOffset() {
        if (SVNFileUtil.isLinux && SVNFileUtil.is64Bit) {
            return 24;
        }
        if (SVNFileUtil.isLinux && SVNFileUtil.is32Bit) {
            return 16;
        }
        if (SVNFileUtil.isOSX) {
            return 8;
        }
        if (SVNFileUtil.isSolaris) {
            return 20;
        }
        if (SVNFileUtil.isBSD) {
            return 8;
        }
        return 16;
    }

    private static int getFileUserIDOffset() {
        int modeOffset = getFileModeOffset();
        if (SVNFileUtil.isLinux && SVNFileUtil.is64Bit) {
            return modeOffset + 4;
        }
        if (SVNFileUtil.isLinux && SVNFileUtil.is32Bit) {
            return modeOffset + 8;
        }
        if (SVNFileUtil.isOSX) {
            return modeOffset + 4;
        }
        if (SVNFileUtil.isSolaris) {
            return modeOffset + 8;
        }
        if (SVNFileUtil.isBSD) {
            return modeOffset + 4;
        }

        return modeOffset + 8;
    }

    private static int getFileGroupIDOffset() {
        return getFileUserIDOffset() + 4;
    }
}