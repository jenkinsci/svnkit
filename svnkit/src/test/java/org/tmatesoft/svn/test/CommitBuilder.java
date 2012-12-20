package org.tmatesoft.svn.test;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class CommitBuilder {

    private final SVNURL url;
    private String commitMessage;
    private final Map<String, byte[]> filesToAdd;
    private final Map<String, byte[]> filesToChange;
    private final Map<String, SVNProperties> filesToProperties;
    private final Map<String, SVNProperties> directoriesToProperties;
    private final Map<String, String> filesToCopyFromPath;
    private final Map<String, Long> filesToCopyFromRevision;
    private final Map<String, String> directoriesToCopyFromPath;
    private final Map<String, Long> directoriesToCopyFromRevision;
    private final Set<String> directoriesToAdd;
    private final Set<String> entriesToDelete;
    private ISVNAuthenticationManager authenticationManager;

    public CommitBuilder(SVNURL url) {
        this.filesToAdd = new HashMap<String, byte[]>();
        this.filesToChange = new HashMap<String, byte[]>();
        this.filesToCopyFromPath = new HashMap<String, String>();
        this.filesToCopyFromRevision = new HashMap<String, Long>();
        this.directoriesToCopyFromPath = new HashMap<String, String>();
        this.directoriesToCopyFromRevision = new HashMap<String, Long>();
        this.directoriesToAdd = new HashSet<String>();
        this.entriesToDelete = new HashSet<String>();
        this.filesToProperties = new HashMap<String, SVNProperties>();
        this.directoriesToProperties = new HashMap<String, SVNProperties>();
        this.url = url;

        setCommitMessage("");
    }

    public void setFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) {
        SVNProperties properties;
        if (!filesToProperties.containsKey(path)) {
            properties = new SVNProperties();
            filesToProperties.put(path, properties);
        } else {
            properties = filesToProperties.get(path);
        }

        properties.put(propertyName, propertyValue);
    }

    public void setDirectoryProperty(String path, String propertyName, SVNPropertyValue propertyValue) {
        SVNProperties properties;
        if (!directoriesToProperties.containsKey(path)) {
            properties = new SVNProperties();
            directoriesToProperties.put(path, properties);
        } else {
            properties = directoriesToProperties.get(path);
        }

        properties.put(propertyName, propertyValue);
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

    public CommitBuilder addDirectoryByCopying(String path, String copyFromPath) {
        return addDirectoryByCopying(path, copyFromPath, -1); //the latest revision is used in this case
    }

    public void addFileByCopying(String path, String copyFromPath) {
        addFileByCopying(path, copyFromPath, -1);
    }

    public void addFileByCopying(String path, String copyFromPath, long copyFromRevision) {
        filesToCopyFromPath.put(path, copyFromPath);
        filesToCopyFromRevision.put(path, copyFromRevision);
    }

    public CommitBuilder addDirectoryByCopying(String path, String copyFromPath, long copyFromRevision) {
        directoriesToCopyFromPath.put(path, copyFromPath);
        directoriesToCopyFromRevision.put(path, copyFromRevision);
        return this;
    }

    public void replaceFileByCopying(String path, String copyFromPath) {
        delete(path);
        addFileByCopying(path, copyFromPath);
    }

    public void replaceDirectoryByCopying(String path, String copyFromPath) {
        delete(path);
        addDirectoryByCopying(path, copyFromPath);
    }

    public void replaceFileByCopying(String path, String copyFromPath, long copyFromRevision) {
        delete(path);
        addFileByCopying(path, copyFromPath, copyFromRevision);
    }

    public void replaceDirectoryByCopying(String path, String copyFromPath, long copyFromRevision) {
        delete(path);
        addDirectoryByCopying(path, copyFromPath, copyFromRevision);
    }

    public SVNCommitInfo commit() throws SVNException {
        final SortedSet<String> directoriesToVisit = getDirectoriesToVisit();
        final SVNRepository svnRepository = createSvnRepository();
        final long latestRevision = svnRepository.getLatestRevision();

        final ISVNEditor commitEditor = svnRepository.getCommitEditor(commitMessage, null);
        commitEditor.openRoot(-1);

        String currentDirectory = "";
        for (String directory : directoriesToVisit) {
            if (directory.length() == 0) {
                continue;
            }
            closeUntilCommonAncestor(commitEditor, currentDirectory, directory);
            openOrAddDir(commitEditor, directory, latestRevision);
            setDirProperties(commitEditor, directory);
            currentDirectory = directory;

            addChildrensFiles(commitEditor, directory, latestRevision);
            deleteEntries(commitEditor, directory);
        }
        closeUntilCommonAncestor(commitEditor, currentDirectory, "");
        currentDirectory = "";

        setDirProperties(commitEditor, "");
        addChildrensFiles(commitEditor, "", latestRevision);
        deleteEntries(commitEditor, "");

        commitEditor.closeDir();
        return commitEditor.closeEdit();
    }

    private void setDirProperties(ISVNEditor commitEditor, String directory) throws SVNException {
        SVNProperties properties = directoriesToProperties.get(directory);
        if (properties == null) {
            return;
        }
        for (String propertyName : properties.nameSet()) {
            final SVNPropertyValue propertyValue = properties.getSVNPropertyValue(propertyName);
            commitEditor.changeDirProperty(propertyName, propertyValue);
        }
    }

    private void setFileProperties(ISVNEditor commitEditor, String file) throws SVNException {
        SVNProperties properties = filesToProperties.get(file);
        if (properties == null) {
            return;
        }
        for (String propertyName : properties.nameSet()) {
            final SVNPropertyValue propertyValue = properties.getSVNPropertyValue(propertyName);
            commitEditor.changeFileProperty(file, propertyName, propertyValue);
        }
    }

    private void addChildrensFiles(ISVNEditor commitEditor, String directory, long latestRevision) throws SVNException {
        for (String file : filesToAdd.keySet()) {
            String parent = getParent(file);
            if (parent == null) {
                parent = "";
            }
            if (directory.equals(parent)) {
                maybeDelete(commitEditor, file);
                addFile(commitEditor, file, filesToAdd.get(file), latestRevision);
            }
        }

        for (String file : filesToCopyFromPath.keySet()) {
            String parent = getParent(file);
            if (parent == null) {
                parent = "";
            }
            if (directory.equals(parent)) {
                maybeDelete(commitEditor, file);
                addFileByCopying(commitEditor, file, filesToCopyFromPath.get(file), filesToCopyFromRevision.get(file), latestRevision);
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

        for (String file : filesToProperties.keySet()) {
            String parent = getParent(file);
            if (parent == null) {
                parent = "";
            }
            if (directory.equals(parent)) {
                commitEditor.openFile(file, -1);
                setFileProperties(commitEditor, file);
                commitEditor.closeFile(file, null);
            }
        }
    }

    private void deleteEntries(ISVNEditor commitEditor, String directory) throws SVNException {
        for (String path : entriesToDelete) {
            String parent = getParent(path);
            if (parent == null) {
                parent = "";
            }
            if (directory.equals(parent)) {
                if (!filesToAdd.containsKey(path) && !filesToCopyFromPath.containsKey(path) && !directoriesToAdd.contains(path) && !directoriesToCopyFromPath.containsKey(path)) {
                    commitEditor.deleteEntry(path, -1);
                }
            }
        }

    }

    private void maybeDelete(ISVNEditor commitEditor, String file) throws SVNException {
        for (Iterator<String> iterator = entriesToDelete.iterator(); iterator.hasNext(); ) {
            String path = iterator.next();
            if (file.equals(path)) {
                commitEditor.deleteEntry(file, -1);
                iterator.remove();
                break;
            }
        }
    }

    private void addFileByCopying(ISVNEditor commitEditor, String file, String copySource, Long copyRevisionLong, long latestRevision) throws SVNException {
        final long copyRevisionSpecified = copyRevisionLong == null ? -1 : copyRevisionLong;
        final long copyRevision = copyRevisionSpecified == -1 ? latestRevision : copyRevisionSpecified;
        commitEditor.addFile(file, copySource, copyRevision);
        final byte[] originalContents = getOriginalContents(copySource, copyRevision);
        final String checksum = TestUtil.md5(originalContents);
        commitEditor.closeFile(file, checksum);
    }

    private void addFile(ISVNEditor commitEditor, String file, byte[] contents, long latestRevision) throws SVNException {
        final SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();

        commitEditor.addFile(file, null, -1);
        setFileProperties(commitEditor, file);
        commitEditor.applyTextDelta(file, null);
        final String checksum = deltaGenerator.sendDelta(file, new ByteArrayInputStream(contents), commitEditor, true);
        commitEditor.closeFile(file, checksum);
    }

    private void changeFile(ISVNEditor commitEditor, String file, byte[] newContents) throws SVNException {
        final SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        final byte[] originalContents = getOriginalContents(file, -1);

        if (newContents == null) {
            newContents = originalContents;
        }

        commitEditor.openFile(file, -1);
        setFileProperties(commitEditor, file);
        commitEditor.applyTextDelta(file, TestUtil.md5(originalContents));
        final String checksum = deltaGenerator.sendDelta(file,
                new ByteArrayInputStream(originalContents), 0, new ByteArrayInputStream(newContents),
                commitEditor, true);
        commitEditor.closeFile(file, checksum);
    }

    private byte[] getOriginalContents(String file, long revision) throws SVNException {
        final SVNRepository svnRepository = createSvnRepository();
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            svnRepository.getFile(file, revision, null, byteArrayOutputStream);
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

    private void openOrAddDir(ISVNEditor commitEditor, String directory, long latestRevision) throws SVNException {
        boolean exists = existsDirectory(directory);

        //process replacement
        for (Iterator<String> iterator = entriesToDelete.iterator(); iterator.hasNext(); ) {
            final String path = iterator.next();
            if (directory.equals(path)) {
                commitEditor.deleteEntry(path, -1);
                iterator.remove();
                exists = false;
                break;
            }
        }

        if (exists) {
            if (!directoriesToAdd.contains(directory)) {
                commitEditor.openDir(directory, -1);
            } else {
                commitEditor.addDir(directory, null, -1);
            }
        } else {
            final String copySource = directoriesToCopyFromPath.get(directory);
            final Long copyRevisionLong = directoriesToCopyFromRevision.get(directory);
            final long copyRevisionSpecified = copyRevisionLong == null ? -1 : copyRevisionLong;
            final long copyRevision = copyRevisionSpecified == -1 ? latestRevision : copyRevisionSpecified;
            if (copySource == null) {
                commitEditor.addDir(directory, null, -1);
            } else {
                commitEditor.addDir(directory, copySource, copyRevision);
            }
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
        for (String path: entriesToDelete) {
            String directory = getParent(path);
            if (directory != null) {
                addDirectoryToVisit(directory, directoriesToVisit);
            }
        }
        for (String directory : directoriesToProperties.keySet()) {
            addDirectoryToVisit(directory, directoriesToVisit);
        }
        for (String directoryToAdd : directoriesToAdd) {
            addDirectoryToVisit(directoryToAdd, directoriesToVisit);
        }

        addFilesParents(directoriesToVisit, filesToAdd.keySet());
        addFilesParents(directoriesToVisit, filesToChange.keySet());
        addFilesParents(directoriesToVisit, filesToProperties.keySet());
        addFilesParents(directoriesToVisit, filesToCopyFromPath.keySet());

        for (String directoryToCopy : directoriesToCopyFromPath.keySet()) {
            addDirectoryToVisit(directoryToCopy, directoriesToVisit);
        }

        return directoriesToVisit;
    }

    private void addFilesParents(SortedSet<String> directoriesToVisit, Set<String> files) {
        for (String file : files) {
            final String directory = getParent(file);
            if (directory != null) {
                addDirectoryToVisit(directory, directoriesToVisit);
            }
        }
    }

    private void addDirectoryToVisit(String directory, SortedSet<String> directoriesToVisit) {
        do {
            directoriesToVisit.add(directory);
            directory = getParent(directory);
        } while (directory != null);
    }

    private String getParent(String file) {
        if ("".equals(file)) {
            return null;
        }
        String parent = SVNPathUtil.removeTail(file);
        if ("".equals(parent)) {
            return null;
        }
        return parent;
    }

    private SVNRepository createSvnRepository() throws SVNException {
        final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        svnRepository.setAuthenticationManager(authenticationManager);
        return svnRepository;
    }

    public void delete(String path) {
        entriesToDelete.add(path);
    }

    public void setAuthenticationManager(ISVNAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }
}
