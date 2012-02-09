package org.tmatesoft.svn.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

public class CommitBuilder {

    private final SVNURL url;
    private String commitMessage;
    private final Map<String, byte[]> filesToAdd;
    private final Map<String, byte[]> filesToChange;
    private final Set<String> directoriesToAdd;

    public CommitBuilder(SVNURL url) {
        this.filesToAdd = new HashMap<String, byte[]>();
        this.filesToChange = new HashMap<String, byte[]>();
        this.directoriesToAdd = new HashSet<String>();
        this.url = url;
        setCommitMessage("");
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public void addDirectory(String directory) {
        directoriesToAdd.add(directory);
    }

    public CommitBuilder addFile(String path) {
        return addFile(path, new byte[0]);
    }

    public CommitBuilder addFile(String path, byte[] contents) {
        filesToAdd.put(path, contents);
        return this;
    }

    public CommitBuilder changeFile(String path, byte[] contents) {
        filesToChange.put(path, contents);
        return this;
    }

    public SVNCommitInfo commit() throws SVNException {
        final SortedSet<String> directoriesToVisit = getDirectoriesToVisit();
        final SVNRepository svnRepository = createSvnRepository();

        final ISVNEditor commitEditor = svnRepository.getCommitEditor(commitMessage, null);
        commitEditor.openRoot(-1);

        String currentDirectory = "";
        for (String directory : directoriesToVisit) {
            closeUntilCommonAncestor(commitEditor, currentDirectory, directory);
            openOrAddDir(commitEditor, directory);
            currentDirectory = directory;

            addChildrensFiles(commitEditor, directory);
        }

        addChildrensFiles(commitEditor, "");

        closeUntilCommonAncestor(commitEditor, currentDirectory, "");

        commitEditor.closeDir();
        return commitEditor.closeEdit();
    }

    private void addChildrensFiles(ISVNEditor commitEditor, String directory) throws SVNException {
        for (String file : filesToAdd.keySet()) {
            String parent = getParent(file);
            if (parent == null) {
                parent = "";
            }
            if (directory.equals(parent)) {
                addFile(commitEditor, file, filesToAdd.get(file));
            }
        }

        for (String file : filesToChange.keySet()) {
            String parent = getParent(file);
            if (parent == null) {
                parent = "";
            }
            if (directory.equals(parent)) {
                changeFile(commitEditor, file, filesToChange.get(file));
            }
        }
    }

    private void addFile(ISVNEditor commitEditor, String file, byte[] contents) throws SVNException {
        final SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();

        commitEditor.addFile(file, null, -1);
        commitEditor.applyTextDelta(file, null);
        final String checksum = deltaGenerator.sendDelta(file, new ByteArrayInputStream(contents), commitEditor, true);
        commitEditor.closeFile(file, checksum);
    }

    private void changeFile(ISVNEditor commitEditor, String file, byte[] newContents) throws SVNException {
        final SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        final byte[] originalContents = getOriginalContents(file);

        if (newContents == null) {
            newContents = originalContents;
        }

        commitEditor.openFile(file, -1);
        commitEditor.applyTextDelta(file, TestUtil.md5(originalContents));
        final String checksum = deltaGenerator.sendDelta(file,
                new ByteArrayInputStream(originalContents), 0, new ByteArrayInputStream(newContents),
                commitEditor, true);
        commitEditor.closeFile(file, checksum);
    }

    private byte[] getOriginalContents(String file) throws SVNException {
        final SVNRepository svnRepository = createSvnRepository();
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            svnRepository.getFile(file, SVNRepository.INVALID_REVISION, null, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } finally {
            svnRepository.closeSession();
        }
    }

    private void closeUntilCommonAncestor(ISVNEditor commitEditor, String currentDirectory, String directory) throws SVNException {
        final String commonPathAncestor = getCommonPathAncestor(currentDirectory, directory);
        while (currentDirectory != null && !currentDirectory.equals(commonPathAncestor)) {
            commitEditor.closeDir();
            currentDirectory = getParent(currentDirectory);
        }
    }

    private String getCommonPathAncestor(String directory1, String directory2) {
        if (directory1 == null || directory1.length() == 0) {
            return "";
        }
        if (directory2 == null || directory2.length() == 0) {
            return "";
        }
        return SVNPathUtil.getCommonPathAncestor(directory1, directory2);
    }

    private void openOrAddDir(ISVNEditor commitEditor, String directory) throws SVNException {
        if (existsDirectory(directory)) {
            commitEditor.openDir(directory, -1);
        } else {
            commitEditor.addDir(directory, null, -1);
        }
    }

    private boolean existsDirectory(String directory) throws SVNException {
        final SVNRepository svnRepository = createSvnRepository();
        try {
            final SVNNodeKind nodeKind = svnRepository.checkPath(directory, SVNRepository.INVALID_REVISION);
            return nodeKind == SVNNodeKind.DIR;
        } finally {
            svnRepository.closeSession();
        }
    }

    private SortedSet<String> getDirectoriesToVisit() {
        final SortedSet<String> directoriesToVisit = new TreeSet<String>();
        for (String directory : directoriesToAdd) {
            addDirectoryToVisit(directory, directoriesToVisit);
        }
        directoriesToVisit.addAll(directoriesToAdd);
        for (String file : filesToAdd.keySet()) {
            final String directory = getParent(file);
            if (directory != null) {
                addDirectoryToVisit(directory, directoriesToVisit);
            }
        }
        for (String file : filesToChange.keySet()) {
            final String directory = getParent(file);
            if (directory != null) {
                addDirectoryToVisit(directory, directoriesToVisit);
            }
        }
        return directoriesToVisit;
    }

    private void addDirectoryToVisit(String directory, SortedSet<String> directoriesToVisit) {
        do {
            directoriesToVisit.add(directory);
            directory = getParent(directory);
        } while (directory != null);
    }

    private String getParent(String file) {
        return new File(file).getParent();
    }

    private SVNRepository createSvnRepository() throws SVNException {
        return SVNRepositoryFactory.create(url);
    }
}
