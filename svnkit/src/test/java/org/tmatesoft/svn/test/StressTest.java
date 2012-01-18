package org.tmatesoft.svn.test;

import java.io.File;
import java.util.List;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

public class StressTest {

    @Test
    public void testWorkingCopy() throws Exception {
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWorkingCopy");
        try {
            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy();
            final long latestRevision = workingCopy.getCurrentRevision();

            runUpdates(workingCopy, latestRevision);

            runAdds(workingCopy);

            runRevert(workingCopy);

            runDeletes(workingCopy);

            runRevert(workingCopy);

            runSetProperties(workingCopy);

            runRevert(workingCopy);
        } finally {
            sandbox.dispose();
        }
    }

    private void runUpdates(WorkingCopy workingCopy, long latestRevision) throws SVNException {
        for (long revision = latestRevision - 1; revision >= 1; revision--) {
            workingCopy.updateToRevision(revision);
        }

        for (long revision = 2; revision <= latestRevision; revision++) {
            workingCopy.updateToRevision(revision);
        }

        for (long revision = latestRevision - 1; revision >= 1; revision -= 10) {
            workingCopy.updateToRevision(revision);
        }

        for (long revision = 2; revision <= latestRevision; revision += 10) {
            workingCopy.updateToRevision(revision);
        }

        workingCopy.updateToRevision(latestRevision);
    }

    private void runAdds(WorkingCopy workingCopy) throws SVNException {
        final File originalDirectory = workingCopy.findAnyDirectory();
        File directory = originalDirectory;

        final File originalAnotherDirectory = workingCopy.findAnotherDirectory(directory);
        File anotherDirectory = originalAnotherDirectory;

        for (int i = 0; i < 5; i++) {
            SVNFileUtil.copyDirectory(directory, new File(anotherDirectory, directory.getName()), false, null);

            File tmp = directory;
            directory = anotherDirectory;
            anotherDirectory = tmp;
        }

        workingCopy.add(new File(originalDirectory, originalAnotherDirectory.getName()));
        workingCopy.add(new File(originalAnotherDirectory, originalDirectory.getName()));
    }

    private void runRevert(WorkingCopy workingCopy) throws SVNException {
        workingCopy.revert();
    }

    private void runDeletes(WorkingCopy workingCopy) throws SVNException {
        final List<File> childrenList = workingCopy.getChildren();

        for (File child : childrenList) {
            workingCopy.delete(child);
        }
    }

    private void runSetProperties(WorkingCopy workingCopy) throws SVNException {
        final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

        setProperties(workingCopy, workingCopyDirectory, 0);
    }

    private int setProperties(WorkingCopy workingCopy, File directory, int counter) throws SVNException {
        final File[] children = directory.isDirectory() ? SVNFileListUtil.listFiles(directory) : null;

        if (children != null) {
            for (File child : children) {
                if (!child.getName().equals(SVNFileUtil.getAdminDirectoryName())) {
                    workingCopy.setProperty(child, "property" + counter, SVNPropertyValue.create(child.getAbsolutePath()));
                    counter++;
                    counter = setProperties(workingCopy, child, counter);
                }
            }
        }
        return counter;
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
