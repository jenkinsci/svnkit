package org.tmatesoft.svn.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

public class StressTest {

    static {
        SVNFileUtil.setSleepForTimestamp(false);
    }

    @Ignore("Temporarily ignored")
    @Test
    public void testWorkingCopy() throws Exception {
        final TestOptions testOptions = TestOptions.getInstance();
        Assume.assumeNotNull(testOptions.getRepositoryUrl());

        final Sandbox sandbox = Sandbox.createWithoutCleanup(getTestName() + ".testWorkingCopy", testOptions);
        try {
            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopy();

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

    @Test
    public void testUpdates() throws Exception {
        final TestOptions testOptions = TestOptions.getInstance();
        Assume.assumeNotNull(testOptions.getRepositoryUrl());

        final List<SVNRevision> revisionsToUpdate = getRevisionsToUpdate(testOptions, testOptions.getRepositoryUrl());
        if (revisionsToUpdate != null && revisionsToUpdate.size() == 0) {
            System.out.println("Nothing to do");
            return;
        }

        final Sandbox sandbox = Sandbox.createWithoutCleanup(getTestName() + ".testUpdates", testOptions);
        try {
            final long revisionToCheckout = revisionsToUpdate.get(0).getNumber();
            revisionsToUpdate.remove(0);

            final WorkingCopy workingCopy = sandbox.checkoutWorkingCopyOrUpdateTo(revisionToCheckout);

            runUpdates(workingCopy, revisionsToUpdate);
        } finally {
            sandbox.dispose();
        }
    }

    @Ignore("Temporarily ignored")
    @Test
    public void testCommits() throws Exception {
        final TestOptions testOptions = TestOptions.getInstance();
        Assume.assumeNotNull(testOptions.getRepositoryUrl());

        final Sandbox sandbox = Sandbox.createWithoutCleanup(getTestName() + ".testCommits", testOptions);
        try {
            final SVNURL originalRepositoryUrl = testOptions.getRepositoryUrl();
            final SVNURL targetRepositoryUrl = sandbox.createSvnRepository();

            translateRevisionByRevision(sandbox, originalRepositoryUrl, targetRepositoryUrl);
        } finally {
            sandbox.dispose();
        }
    }

    private void runUpdates(WorkingCopy workingCopy, List<SVNRevision> revisionsToUpdate) throws SVNException {
        for (SVNRevision revision : revisionsToUpdate) {
            workingCopy.updateToRevision(revision.getNumber());
        }
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

    private void translateRevisionByRevision(Sandbox sandbox, SVNURL originalRepositoryUrl, SVNURL targetRepositoryUrl) throws SVNException {
        final WorkingCopy workingCopy = sandbox.checkoutOrUpdateExistingWorkingCopy(targetRepositoryUrl, -1);

        final List<SVNLogEntry> logEntries = getLogEntries(originalRepositoryUrl);
        long previousRevision = -1;

        SVNRepository svnRepository = null;
        try {
            svnRepository = SVNRepositoryFactory.create(originalRepositoryUrl);

            for (SVNLogEntry logEntry : logEntries) {
                final long revision = logEntry.getRevision();

                final ISVNEditor editor = new WorkingCopyEditor(workingCopy);
                final ISVNReporterBaton reporter = new RevisionByRevisionReporter(previousRevision, revision);

                svnRepository.update(revision, "", SVNDepth.INFINITY, false, reporter, editor);

                final long committedRevision = workingCopy.commit(logEntry.getMessage());
                workingCopy.updateToRevision(committedRevision);

                previousRevision = revision;
            }
        } finally {
            if (svnRepository != null) {
                svnRepository.closeSession();
            }
        }
    }

    private List<SVNLogEntry> getLogEntries(SVNURL originalRepositoryUrl) throws SVNException {
        return getLogEntries(originalRepositoryUrl, 0, -1);
    }

    private List<SVNLogEntry> getLogEntries(SVNURL originalRepositoryUrl, long startRevision, long endRevision) throws SVNException {
        SVNRepository svnRepository = null;
        try {
            svnRepository = SVNRepositoryFactory.create(originalRepositoryUrl);

            if (endRevision < 0) {
                endRevision = svnRepository.getLatestRevision();
            }

            final SVNURL repositoryRoot = svnRepository.getRepositoryRoot(true);
            final String relativePath = SVNPathUtil.getRelativePath(repositoryRoot.toString(), originalRepositoryUrl.toString());

            svnRepository.setLocation(repositoryRoot, true);

            final List<SVNLogEntry> logEntries = new ArrayList<SVNLogEntry>();

            svnRepository.log(new String[]{"/"}, startRevision, endRevision, true, true, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    final Map<String,SVNLogEntryPath> changedPaths = logEntry.getChangedPaths();

                    if (relativePath.length() == 0) {
                        logEntries.add(logEntry);
                        return;
                    }

                    for (String path : changedPaths.keySet()) {
                        if (SVNPathUtil.isAncestor("/"+relativePath, path)) {
                            logEntries.add(logEntry);
                            break;
                        }
                    }
                }
            });

            return logEntries;
        } finally {
            if (svnRepository != null) {
                svnRepository.closeSession();
            }
        }
    }

    private List<SVNRevision> getRevisionsToUpdate(TestOptions testOptions, SVNURL repositoryUrl) throws SVNException {
        final List<SVNRevision> revisions = new ArrayList<SVNRevision>();

        final List<SVNRevisionRange> updateSchedule = testOptions.getUpdateSchedule();
        if (updateSchedule == null) {
            revisions.addAll(getRevisions(repositoryUrl));
            return revisions;
        }

        for (SVNRevisionRange svnRevisionRange : updateSchedule) {
            final SVNRevision startRevision = svnRevisionRange.getStartRevision();
            final SVNRevision endRevision = svnRevisionRange.getEndRevision();

            revisions.addAll(getRevisions(repositoryUrl, startRevision, endRevision));
        }

        return revisions;
    }

    private List<SVNRevision> getRevisions(SVNURL repositoryUrl, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        return getRevisions(getLogEntries(repositoryUrl, startRevision.getNumber(), endRevision.getNumber()));
    }

    private List<SVNRevision> getRevisions(SVNURL repositoryUrl) throws SVNException {
        return getRevisions(getLogEntries(repositoryUrl));
    }

    private List<SVNRevision> getRevisions(List<SVNLogEntry> logEntries) {
        final List<SVNRevision> revisions = new ArrayList<SVNRevision>();
        for (SVNLogEntry logEntry : logEntries) {
            revisions.add(SVNRevision.create(logEntry.getRevision()));
        }
        return revisions;
    }

    private class RevisionByRevisionReporter implements ISVNReporterBaton {

        private final long previousRevision;
        private final long revision;

        public RevisionByRevisionReporter(long previousRevision, long revision) {
            this.previousRevision = previousRevision;
            this.revision = revision;
        }

        public void report(ISVNReporter reporter) throws SVNException {
            final boolean startEmpty = previousRevision == -1;
            reporter.setPath("", null, startEmpty ? revision : previousRevision, SVNDepth.INFINITY, startEmpty);
            reporter.finishReport();
        }
    }
}
