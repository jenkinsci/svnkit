package org.tmatesoft.svn.test;

import java.io.File;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

public class StressTest {

    @Test
    public void testWorkingCopy() throws Exception {
        final WorkingCopyTest workingCopyTest = WorkingCopyTest.createWithInitialization(getTestName());
        try {
            final long latestRevision = workingCopyTest.checkoutLatestRevision();

            runUpdates(workingCopyTest, latestRevision);

            runAdds(workingCopyTest);

            runRevert(workingCopyTest);

            runDeletes(workingCopyTest);

            runRevert(workingCopyTest);

            runSetProperties(workingCopyTest);

            runRevert(workingCopyTest);
        } finally {
            workingCopyTest.dispose();
        }
    }

    private void runUpdates(WorkingCopyTest workingCopyTest, long latestRevision) throws SVNException {
        for (long revision = latestRevision - 1; revision >= 1; revision--) {
            workingCopyTest.updateToRevision(revision);
        }

        for (long revision = 2; revision <= latestRevision; revision++) {
            workingCopyTest.updateToRevision(revision);
        }

        for (long revision = latestRevision - 1; revision >= 1; revision -= 10) {
            workingCopyTest.updateToRevision(revision);
        }

        for (long revision = 2; revision <= latestRevision; revision += 10) {
            workingCopyTest.updateToRevision(revision);
        }

        workingCopyTest.updateToRevision(latestRevision);
    }

    private void runAdds(WorkingCopyTest workingCopyTest) throws SVNException {
        final File originalDirectory = workingCopyTest.findAnyDirectory();
        File directory = originalDirectory;

        final File originalAnotherDirectory = workingCopyTest.findAnotherDirectory(directory);
        File anotherDirectory = originalAnotherDirectory;

        for (int i = 0; i < 5; i++) {
            SVNFileUtil.copyDirectory(directory, new File(anotherDirectory, directory.getName()), false, null);

            File tmp = directory;
            directory = anotherDirectory;
            anotherDirectory = tmp;
        }

        workingCopyTest.add(new File(originalDirectory, originalAnotherDirectory.getName()));
        workingCopyTest.add(new File(originalAnotherDirectory, originalDirectory.getName()));
    }

    private void runRevert(WorkingCopyTest workingCopyTest) throws SVNException {
        workingCopyTest.revert();
    }

    private void runDeletes(WorkingCopyTest workingCopyTest) throws SVNException {
        workingCopyTest.deleteChildren();
    }

    private void runSetProperties(WorkingCopyTest workingCopyTest) throws SVNException {
        final File workingCopyDirectory = workingCopyTest.getWorkingCopyDirectory();

        setProperties(workingCopyTest, workingCopyDirectory, 0);
    }

    private int setProperties(WorkingCopyTest workingCopyTest, File directory, int counter) throws SVNException {
        final File[] children = directory.isDirectory() ? SVNFileListUtil.listFiles(directory) : null;

        if (children != null) {
            for (File child : children) {
                if (!child.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
                    workingCopyTest.setProperty(child, "property" + counter, SVNPropertyValue.create(child.getAbsolutePath()));
                    counter++;
                    counter = setProperties(workingCopyTest, child, counter);
                }
            }
        }
        return counter;
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
