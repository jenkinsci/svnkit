package org.tmatesoft.svn.test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;

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
}
