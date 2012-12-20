package org.tmatesoft.svn.test;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc2.*;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.*;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

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

    public static void writeFileContentsString(File file, String contentsString) throws SVNException {
        final OutputStream fileOutputStream = SVNFileUtil.openFileForWriting(file);
        try {
            fileOutputStream.write(contentsString.getBytes());
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e));
        } finally {
            SVNFileUtil.closeFile(fileOutputStream);
        }
    }

    public static String readFileContentsString(File file) throws IOException {
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);

            while (true) {
                final int bytesRead = bufferedInputStream.read(buffer);
                if (bytesRead < 0)  {
                    break;
                }
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return new String(byteArrayOutputStream.toByteArray());
        } finally {
            SVNFileUtil.closeFile(bufferedInputStream);
            SVNFileUtil.closeFile(fileInputStream);
        }
    }

    public static SvnWcGeneration getDefaultWcGeneration() {
        return new SvnOperationFactory().getPrimaryWcGeneration();
    }

    static boolean isNewWorkingCopyTest() {
        return getDefaultWcGeneration() == SvnWcGeneration.V17;
    }

    static boolean isNewWorkingCopyOnly() {
        return getDefaultWcGeneration() == SvnWcGeneration.V17 &&
                new SvnOperationFactory().isPrimaryWcGenerationOnly();
    }

    public static Map<File, SvnStatus> getStatuses(SvnOperationFactory svnOperationFactory, File workingCopyDirectory) throws SVNException {
        final Map<File, SvnStatus> pathToStatus = new HashMap<File, SvnStatus>();
        final SvnGetStatus status = svnOperationFactory.createGetStatus();
        status.setDepth(SVNDepth.INFINITY);
        status.setRemote(false);
        status.setReportAll(true);
        status.setReportIgnored(true);
        status.setReportExternals(false);
        status.setApplicalbeChangelists(null);
        status.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
            public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                pathToStatus.put(status.getPath(), status);
            }
        });

        status.addTarget(SvnTarget.fromFile(workingCopyDirectory));
        status.run();
        return pathToStatus;
    }

    public static String md5(byte[] contents) {
        final byte[] tmp = new byte[1024];
        final SVNChecksumInputStream checksumStream = new SVNChecksumInputStream(new ByteArrayInputStream(contents), "md5");
        try {
            while (checksumStream.read(tmp) > 0) {
                //
            }
            return checksumStream.getDigest();
        } catch (IOException e) {
            //never happens
            e.printStackTrace();
            return null;
        } finally {
            SVNFileUtil.closeFile(checksumStream);
        }
    }


    public static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.bind(null);
            return socket.getLocalPort();
        }
        catch (IOException e) {
            return -1;
        }
        finally {
            if (socket != null) {
                try {
                    socket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String convertSlashesToDirect(String path) {
        return path.replace(File.separatorChar, '/');
    }

    public static String convertSlashesToSystem(String path) {
        return path.replace('/', File.separatorChar);
    }

    public static boolean areAllApacheOptionsSpecified(TestOptions testOptions) {
        return testOptions.getApacheRoot() != null && testOptions.getApacheCtlCommand() != null;
    }

    public static boolean areAllSvnserveOptionsSpecified(TestOptions testOptions) {
        return testOptions.getSvnserveCommand() != null;
    }

    public static SvnChecksum calculateSha1(byte[] contents) throws SVNException {
        final SVNChecksumInputStream inputStream = new SVNChecksumInputStream(new ByteArrayInputStream(contents), SvnChecksum.Kind.sha1.name());
        final byte[] buffer = new byte[1024];
        try {
            while (true) {
                final int read = inputStream.read(buffer);
                if (read < 0) {
                    break;
                }
            }

            final String digest = inputStream.getDigest();
            return new SvnChecksum(SvnChecksum.Kind.sha1, digest);
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.UNKNOWN);
            SVNErrorManager.error(errorMessage, e, SVNLogType.CLIENT);
            return null;
        } finally {
            SVNFileUtil.closeFile(inputStream);
        }
    }

    public static File getHookFile(File repositoryRoot, String hookName) {
        final File hooksDirectory = new File(repositoryRoot, "hooks");
        final String ext = SVNFileUtil.isWindows ? ".bat" : "";
        return new File(hooksDirectory, hookName + ext);
    }

    public static String getFailingHookContents() {
        return getTrivialHookContentsForExitCode(1);
    }

    public static String getSucceedingHookContents() {
        return getTrivialHookContentsForExitCode(0);
    }

    private static String getTrivialHookContentsForExitCode(int exitCode) {
        final String exitCodeString = String.valueOf(exitCode);
        if (SVNFileUtil.isWindows) {
            return "@echo off" + "\r\n" + "exit " + exitCodeString + "\r\n";
        } else {
            return "#!/bin/sh" + "\n" + "exit " + exitCodeString + "\n";
        }
    }

    public static int getTableSize(WorkingCopy workingCopy, String tableName) throws SqlJetException {
        final SqlJetDb db = SqlJetDb.open(workingCopy.getWCDbFile(), false);
        try {
            final ISqlJetTable table = db.getTable(tableName);
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = table.open();

            int count = 0;

            for (; !cursor.eof(); cursor.next()) {
                count++;
            }
            cursor.close();
            db.commit();

            return count;
        } finally {
            db.close();
        }
    }
}
