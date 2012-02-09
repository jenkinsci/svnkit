package org.tmatesoft.svn.test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
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

    public static void writeFileContentsString(File file, String contentsString) throws IOException {
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        try {
            fileOutputStream.write(contentsString.getBytes());
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
        }
    }

    static boolean isNewWorkingCopyTest() {
        final String propertyValue = System.getProperty("svnkit.wc.17", "true");
        return "true".equals(propertyValue);
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
}
