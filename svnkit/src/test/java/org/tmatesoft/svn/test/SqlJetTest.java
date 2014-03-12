package org.tmatesoft.svn.test;

import org.junit.Test;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetBusyHandler;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class SqlJetTest {

    @Test
    public void testLockingSucceedsWhenLockIsReleased() throws Exception {
        //SVNKIT-317
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLockingSucceedsWhenLockIsReleased", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File wcDbFile = workingCopy.getWCDbFile();

            final RandomAccessFile randomAccessFile = new RandomAccessFile(wcDbFile, "rw");
            try {
                final FileChannel channel = randomAccessFile.getChannel();
                final FileLock fileLock = channel.lock();

                final boolean[] shouldContinue = new boolean[1];
                final boolean[] unlocked = new boolean[1];
                shouldContinue[0] = false;
                unlocked[0] = false;

                // busyHandler will unlock the file, but SQLJet will ignore this success because an exception was thrown while the previous attempt

                final SVNSqlJetDb db = SVNSqlJetDb.open(wcDbFile, SVNSqlJetDb.Mode.ReadWrite);
                try {
                    db.getDb().setBusyHandler(new ISqlJetBusyHandler() {
                        public boolean call(int number) {

                            if (shouldContinue[0]) {
                                shouldContinue[0] = false;
                            } else if (!unlocked[0]) {
                                unlocked[0] = true;
                                try {
                                    if (fileLock.isValid()) {
                                        fileLock.release();
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                shouldContinue[0] = true;
                            }
                            return shouldContinue[0];
                        }
                    });
                    db.beginTransaction(SqlJetTransactionMode.WRITE);
                    db.commit();

                    // no exception should be thrown

                    if (fileLock.isValid()) {
                        fileLock.release();
                    }
                    channel.close();
                } finally {
                    db.close();
                }
            } finally {
                randomAccessFile.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    public String getTestName() {
        return getClass().getSimpleName();
    }
}
