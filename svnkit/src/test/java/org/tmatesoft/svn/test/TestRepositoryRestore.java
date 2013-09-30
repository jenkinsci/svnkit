package org.tmatesoft.svn.test;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc17.db.*;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbSchema;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc2.*;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class TestRepositoryRestore {

    private final File sourceWCDbFile;
    private final File targetSandboxDirectory;

    //working copy area
    private final File workingCopyDirectory;
    private final File adminDirectory;
    private final File pristineDirectory;
    private final File tmpDirectory;
    private final File targetWCDbFile;
    private final File repositoryDirectory;

    private final SVNURL url;

    public TestRepositoryRestore(File sourceWCDbFile, File targetSandboxDirectory) {
        this.sourceWCDbFile = sourceWCDbFile;
        this.targetSandboxDirectory = targetSandboxDirectory;

        this.workingCopyDirectory = SVNFileUtil.createFilePath(targetSandboxDirectory, "wc");
        this.adminDirectory = SVNFileUtil.createFilePath(workingCopyDirectory, SVNFileUtil.getAdminDirectoryName());
        this.pristineDirectory = SVNFileUtil.createFilePath(adminDirectory, ISVNWCDb.PRISTINE_STORAGE_RELPATH);
        this.tmpDirectory = SVNFileUtil.createFilePath(adminDirectory, ISVNWCDb.WCROOT_TEMPDIR_RELPATH);
        this.targetWCDbFile = SVNFileUtil.createFilePath(adminDirectory, ISVNWCDb.SDB_FILE);
        this.repositoryDirectory = SVNFileUtil.createFilePath(targetSandboxDirectory, "svn.repo");
        try {
            this.url = SVNURL.fromFile(repositoryDirectory);
        } catch (SVNException e) {
            throw new RuntimeException(e);
        }
    }

    public void cleanup() throws SVNException {
        SVNFileUtil.deleteAll(targetSandboxDirectory, null);
    }

   public void run() throws SVNException {
        //1. Create empty SVN repository
        createEmptySvnRepository();

        //2. Copy wc.db to the target place, create necessary directories
        prepareWorkingCopy();

        //3. Fully read NODES table
        List<NodesRecord> nodesRecords = readAllNodesRecords();

        //4. Get all revisions ever mentioned in ascending order
        long[] allRevisions = getAllRevisions(nodesRecords);

        //5. Fully read PRISTINE table, map SHA1->MD5
        Map<SvnChecksum, SvnChecksum> pristineRecords = readAllPristineRecords();

        //6. Generate pristineRecords.size() different pristine files (new SHA1->byte[])
        Map<SvnChecksum, byte[]> content = generatePristineFiles(pristineRecords.size());

        //7. Calculate SHA1->MD5 map for 'content' map:
        Map<SvnChecksum, SvnChecksum> newPristineRecords = calculateNewPristineRecords(content);

        //8. Calculate old SHA1->new SHA1 correspondence
        Map<SvnChecksum, SvnChecksum> oldToNewChecksums = calculateOldToNewChecksums(pristineRecords.keySet(), newPristineRecords.keySet());

        //9. Update PRISTINE table using oldToNewChecksums mapping
        updatePristineTable(oldToNewChecksums, newPristineRecords, content);

        //10. Update NODES table using allRevision as mapping (newRevision = arrayIndex + 1) and oldToNewChecksums
        updateNodesTable(allRevisions, oldToNewChecksums);

        //11. Update REPOSITORY table to update URL where id = 1
        updateReposTable();

        //12. Create files in the working copy
        restoreWorkingCopyFiles(nodesRecords, oldToNewChecksums, content);

        //13. Populate repository
        populateRepository(allRevisions, nodesRecords, oldToNewChecksums, content);
    }

    private void restoreWorkingCopyFiles(List<NodesRecord> nodesRecords, Map<SvnChecksum, SvnChecksum> oldToNewChecksums, Map<SvnChecksum, byte[]> content) throws SVNException {
        for (NodesRecord nodesRecord : nodesRecords) {
            String localRelPath = nodesRecord.getLocalRelPath();
            SvnChecksum checksum = nodesRecord.getChecksum();

            File path = new File(workingCopyDirectory, localRelPath);
            SVNFileUtil.ensureDirectoryExists(SVNFileUtil.getFileDir(path));
            if (nodesRecord.getKind() == SVNNodeKind.FILE && checksum != null) {
                byte[] contentBytes = content.get(oldToNewChecksums.get(checksum));

                OutputStream outputStream = SVNFileUtil.openFileForWriting(path);
                try {
                    outputStream.write(contentBytes);
                } catch (IOException e) {
                    SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
                    SVNErrorManager.error(errorMessage, SVNLogType.WC);
                } finally {
                    SVNFileUtil.closeFile(outputStream);
                }
            } else if (nodesRecord.getKind() == SVNNodeKind.DIR) {
                SVNFileUtil.ensureDirectoryExists(path);
            }
        }
    }

    private void updateReposTable() throws SVNException {
        String uuid = getRepositoryUuid();
        assert targetWCDbFile.exists();

        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(targetWCDbFile, true);
            final ISqlJetTable nodesTable = db.getTable(SVNWCDbSchema.REPOSITORY.name());
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            final ISqlJetCursor cursor = nodesTable.open();

            for (; !cursor.eof(); cursor.next()) {
                long reposId = cursor.getInteger(SVNWCDbSchema.REPOSITORY__Fields.id.name());
                if (reposId != 1) {
                    continue;
                }

                Map<String, Object> values = new HashMap<String, Object>();
                values.put(SVNWCDbSchema.REPOSITORY__Fields.root.name(), url.toString());
                values.put(SVNWCDbSchema.REPOSITORY__Fields.uuid.name(), uuid);
                cursor.updateByFieldNames(values);
            }
            cursor.close();
        } catch (SqlJetException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, e);
            SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
        } finally {
            try {
                if (db != null) {
                    db.commit();
                    db.close();
                }
            } catch (SqlJetException ignore) {
            }
        }
    }

    private String getRepositoryUuid() throws SVNException {
        SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        try {
            return svnRepository.getRepositoryUUID(true);
        } finally {
            svnRepository.closeSession();
        }
    }

    private void populateRepository(long[] allRevisions, List<NodesRecord> nodesRecords, Map<SvnChecksum, SvnChecksum> oldToNewChecksums, Map<SvnChecksum, byte[]> content) throws SVNException {
        long newRevision = 1;
        for (long oldRevision : allRevisions) {
            populateRepositoryForRevision(oldRevision, newRevision, nodesRecords, oldToNewChecksums, content);
            newRevision++;
        }
    }

    private void populateRepositoryForRevision(long oldRevision, long newRevision, List<NodesRecord> nodesRecords, Map<SvnChecksum, SvnChecksum> oldToNewChecksums, Map<SvnChecksum, byte[]> content) throws SVNException {
        SVNRepository svnRepository = SVNRepositoryFactory.create(url);
        try {
            List<NodesRecord> changedRecords = new ArrayList<NodesRecord>();
            for (NodesRecord nodesRecord : nodesRecords) {
                String reposPath = nodesRecord.getReposPath();
                if (reposPath == null) {
                    continue;
                }
                long changedRevision = nodesRecord.getChangedRevision();
                long revision = nodesRecord.getRevision();
                if (changedRevision == oldRevision || (nodesRecord.getOpDepth() > 0 && revision == oldRevision)) {
                    changedRecords.add(nodesRecord);
                }
            }
            CommitBuilder commitBuilder = new CommitBuilder(url);
            for (NodesRecord changedRecord : changedRecords) {
                String reposPath = changedRecord.getReposPath();

                SVNNodeKind reposKind = svnRepository.checkPath(reposPath, newRevision - 1);
                SVNNodeKind workingCopyKind = changedRecord.getKind();

                if (reposKind == SVNNodeKind.NONE) {
                    if (workingCopyKind == SVNNodeKind.FILE) {
                        commitBuilder.addFile(reposPath, content.get(oldToNewChecksums.get(changedRecord.getChecksum())));
                    } else {
                        commitBuilder.addDirectory(reposPath);
                    }
                } else if (reposKind == SVNNodeKind.FILE) {
                    if (workingCopyKind == SVNNodeKind.FILE) {
                        commitBuilder.changeFile(reposPath, content.get(oldToNewChecksums.get(changedRecord.getChecksum())));
                    } else {
                        commitBuilder.delete(reposPath);
                        commitBuilder.addDirectory(reposPath);
                    }
                } else if (reposKind == SVNNodeKind.DIR) {
                    if (workingCopyKind == SVNNodeKind.DIR) {
                        //do nothing
                    } else {
                        commitBuilder.delete(reposPath);
                        commitBuilder.addFile(reposPath);
                    }
                }

                SVNProperties properties = changedRecord.getProperties();
                if (properties != null) {
                    Map propertiesMap = properties.asMap();
                    for (Object o : propertiesMap.entrySet()) {
                        Map.Entry<String, SVNPropertyValue> entry = (Map.Entry<String, SVNPropertyValue>) o;
                        String propertyName = entry.getKey();
                        SVNPropertyValue propertyValue = entry.getValue();

                        if (workingCopyKind == SVNNodeKind.FILE) {
                            commitBuilder.setFileProperty(reposPath, propertyName, propertyValue);
                        } else {
                            assert workingCopyKind == SVNNodeKind.DIR;
                            commitBuilder.setDirectoryProperty(reposPath, propertyName, propertyValue);
                        }
                    }
                }
            }
            commitBuilder.commit();

        } finally {
            svnRepository.closeSession();
        }
    }

    private void updateNodesTable(long[] allRevisions, Map<SvnChecksum, SvnChecksum> oldToNewChecksums) throws SVNException {
        assert targetWCDbFile.exists();

        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(targetWCDbFile, true);
            final ISqlJetTable nodesTable = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            final ISqlJetCursor cursor = nodesTable.open();

            for (; !cursor.eof(); cursor.next()) {
                long revision = cursor.getInteger(SVNWCDbSchema.NODES__Fields.revision.name());
                long changedRevision = cursor.getInteger(SVNWCDbSchema.NODES__Fields.changed_revision.name());
                SvnChecksum checksum = SvnChecksum.fromString(cursor.getString(SVNWCDbSchema.NODES__Fields.checksum.name()));

                long newRevision = lookupNewRevision(allRevisions, revision);
                long newChangedRevision = lookupNewRevision(allRevisions, changedRevision);
                SvnChecksum newChecksum = checksum == null ? null : oldToNewChecksums.get(checksum);

                Map<String, Object> values = new HashMap<String, Object>();
                values.put(SVNWCDbSchema.NODES__Fields.revision.name(), newRevision);
                values.put(SVNWCDbSchema.NODES__Fields.changed_revision.name(), newChangedRevision);
                if (newChecksum != null) {
                    values.put(SVNWCDbSchema.NODES__Fields.checksum.name(), newChecksum.toString());
                }
                cursor.updateByFieldNames(values);
            }
            cursor.close();
        } catch (SqlJetException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, e);
            SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
        } finally {
            try {
                if (db != null) {
                    db.commit();
                    db.close();
                }
            } catch (SqlJetException ignore) {
            }
        }
    }

    private long lookupNewRevision(long[] allRevisions, long revision) {
        int index = Arrays.binarySearch(allRevisions, revision);
        assert allRevisions[index] == revision;
        return index + 1;
    }

    private void updatePristineTable(Map<SvnChecksum, SvnChecksum> oldToNewChecksums, Map<SvnChecksum, SvnChecksum> newPristineRecords, Map<SvnChecksum, byte[]> content) throws SVNException {
        assert targetWCDbFile.exists();

        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(targetWCDbFile, true);
            final ISqlJetTable nodesTable = db.getTable(SVNWCDbSchema.PRISTINE.name());
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            final ISqlJetCursor cursor = nodesTable.open();

            for (; !cursor.eof(); cursor.next()) {
                SvnChecksum oldChecksum = SvnChecksum.fromString(cursor.getString(SVNWCDbSchema.PRISTINE__Fields.checksum.name()));

                SvnChecksum newChecksum = oldToNewChecksums.get(oldChecksum);
                assert newChecksum != null;

                SvnChecksum md5Checksum = newPristineRecords.get(newChecksum);
                byte[] contentBytes = content.get(newChecksum);

                Map<String, Object> values = new HashMap<String, Object>();
                values.put(SVNWCDbSchema.PRISTINE__Fields.checksum.name(), newChecksum.toString());
                values.put(SVNWCDbSchema.PRISTINE__Fields.md5_checksum.name(), md5Checksum.toString());
                values.put(SVNWCDbSchema.PRISTINE__Fields.size.name(), contentBytes.length);
                cursor.updateByFieldNames(values);
            }
            cursor.close();
        } catch (SqlJetException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, e);
            SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
        } finally {
            try {
                if (db != null) {
                    db.commit();
                    db.close();
                }
            } catch (SqlJetException ignore) {
            }
        }
    }

    private Map<SvnChecksum, SvnChecksum> calculateOldToNewChecksums(Collection<SvnChecksum> oldChecksums, Collection<SvnChecksum> newChecksums) {
        assert oldChecksums.size() == newChecksums.size();

        Iterator<SvnChecksum> oldIterator = oldChecksums.iterator();
        Iterator<SvnChecksum> newIterator = newChecksums.iterator();

        Map<SvnChecksum, SvnChecksum> oldToNewChecksums = new HashMap<SvnChecksum, SvnChecksum>();

        for (int i = 0; i < oldChecksums.size(); i++) {
            SvnChecksum oldChecksum = oldIterator.next();
            SvnChecksum newChecksum = newIterator.next();

            oldToNewChecksums.put(oldChecksum, newChecksum);
        }
        return oldToNewChecksums;
    }

    private Map<SvnChecksum, SvnChecksum> calculateNewPristineRecords(Map<SvnChecksum, byte[]> content) throws SVNException {
        Map<SvnChecksum, SvnChecksum> sha1ToMd5Checksum = new HashMap<SvnChecksum, SvnChecksum>();
        for (Map.Entry<SvnChecksum, byte[]> entry : content.entrySet()) {
            SvnChecksum sha1Checksum = entry.getKey();
            byte[] contentBytes = entry.getValue();
            SvnChecksum md5Checksum = calculateChecksum(contentBytes, SvnChecksum.Kind.md5);

            sha1ToMd5Checksum.put(sha1Checksum, md5Checksum);
        }
        return sha1ToMd5Checksum;
    }

    private Map<SvnChecksum, byte[]> generatePristineFiles(int count) throws SVNException {
        Map<SvnChecksum, byte[]> content = new HashMap<SvnChecksum, byte[]>();
        SVNWCDb db = new SVNWCDb();
        try {
            db.open(ISVNWCDb.SVNWCDbOpenMode.ReadOnly, null, false, false);
            SVNWCDb.DirParsedInfo parsed = db.parseDir(workingCopyDirectory, SVNSqlJetDb.Mode.ReadOnly);
            SVNWCDbRoot wcRoot = parsed.wcDbDir.getWCRoot();
            for (int i = 0; i < count; i++) {
                generatePristineFileTo(wcRoot, content, i);
            }
        } finally {
            db.close();
        }
        return content;
    }

    private void createEmptySvnRepository() throws SVNException {
        SVNFileUtil.ensureDirectoryExists(repositoryDirectory);
        createSvnRepository(this.repositoryDirectory);
    }

    private void prepareWorkingCopy() throws SVNException {
        SVNFileUtil.ensureDirectoryExists(tmpDirectory);
        SVNFileUtil.ensureDirectoryExists(pristineDirectory);
        SVNFileUtil.ensureDirectoryExists(adminDirectory);
        SVNFileUtil.copyFile(sourceWCDbFile, targetWCDbFile, false);
    }

    private List<NodesRecord> readAllNodesRecords() throws SVNException {
        assert targetWCDbFile.exists();

        List<NodesRecord> nodesRecords = new ArrayList<NodesRecord>();

        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(targetWCDbFile, false);
            final ISqlJetTable nodesTable = db.getTable(SVNWCDbSchema.NODES.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = nodesTable.open();

            for (; !cursor.eof(); cursor.next()) {
                String localRelPath = cursor.getString(SVNWCDbSchema.NODES__Fields.local_relpath.name());
                String reposPath = cursor.getString(SVNWCDbSchema.NODES__Fields.repos_path.name());
                long opDepth = cursor.getInteger(SVNWCDbSchema.NODES__Fields.op_depth.name());
                long revision = cursor.getInteger(SVNWCDbSchema.NODES__Fields.revision.name());
                long changedRevision = cursor.getInteger(SVNWCDbSchema.NODES__Fields.changed_revision.name());
                SVNNodeKind kind = (SvnWcDbStatementUtil.parseKind(cursor.getString(SVNWCDbSchema.NODES__Fields.kind.name())) == ISVNWCDb.SVNWCDbKind.Dir) ? SVNNodeKind.DIR : SVNNodeKind.FILE;
                SvnChecksum checksum = SvnChecksum.fromString(cursor.getString(SVNWCDbSchema.NODES__Fields.checksum.name()));
                SVNProperties properties = SVNSqlJetStatement.parseProperties(cursor.getBlobAsArray(SVNWCDbSchema.NODES__Fields.properties.name()));
                nodesRecords.add(new NodesRecord(localRelPath, reposPath, revision, opDepth, kind, changedRevision, checksum, properties));
            }
            cursor.close();
        } catch (SqlJetException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, e);
            SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
        } finally {
            try {
                if (db != null) {
                    db.commit();
                    db.close();
                }
            } catch (SqlJetException ignore) {
            }
        }
        return nodesRecords;
    }

    private long[] getAllRevisions(List<NodesRecord> nodesRecords) {
        SortedSet<Long> allRevisions = new TreeSet<Long>();
        for (NodesRecord nodesRecord : nodesRecords) {
            allRevisions.add(nodesRecord.getRevision());
            allRevisions.add(nodesRecord.getChangedRevision());
        }
        long[] result = new long[allRevisions.size()];
        int index = 0;
        for (Long revision : allRevisions) {
            result[index] = revision;
            index++;
        }
        return result;
    }

    private Map<SvnChecksum, SvnChecksum> readAllPristineRecords() throws SVNException {
        assert targetWCDbFile.exists();

        Map<SvnChecksum, SvnChecksum> pristineRecords = new HashMap<SvnChecksum, SvnChecksum>();

        SqlJetDb db = null;
        try {
            db = SqlJetDb.open(targetWCDbFile, false);
            final ISqlJetTable nodesTable = db.getTable(SVNWCDbSchema.PRISTINE.name());
            db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
            final ISqlJetCursor cursor = nodesTable.open();

            for (; !cursor.eof(); cursor.next()) {
                String checksum = cursor.getString(SVNWCDbSchema.PRISTINE__Fields.checksum.name());
                String md5Checksum = cursor.getString(SVNWCDbSchema.PRISTINE__Fields.md5_checksum.name());

                pristineRecords.put(SvnChecksum.fromString(checksum), SvnChecksum.fromString(md5Checksum));
            }
            cursor.close();
        } catch (SqlJetException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.WC_DB_ERROR, e);
            SVNErrorManager.error(errorMessage, e, SVNLogType.WC);
        } finally {
            try {
                if (db != null) {
                    db.commit();
                    db.close();
                }
            } catch (SqlJetException ignore) {
            }
        }
        return pristineRecords;
    }

    private void generatePristineFileTo(SVNWCDbRoot wcRoot, Map<SvnChecksum, byte[]> outputContent, int index) throws SVNException {
        byte[] content = new byte[index];
        Arrays.fill(content, (byte)'.');

        SvnChecksum sha1Checksum = calculateChecksum(content, SvnChecksum.Kind.sha1);

        installPristine(wcRoot, sha1Checksum, content);
        outputContent.put(sha1Checksum, content);
    }

    private void installPristine(SVNWCDbRoot wcRoot, SvnChecksum sha1Checksum, byte[] content) throws SVNException {
        File pristinePath = SvnWcDbPristines.getPristineFuturePath(wcRoot, sha1Checksum);
        SVNFileUtil.ensureDirectoryExists(SVNFileUtil.getFileDir(pristinePath));

        OutputStream outputStream = SVNFileUtil.openFileForWriting(pristinePath);
        try {
            outputStream.write(content);
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(outputStream);
        }
    }

    private SvnChecksum calculateChecksum(byte[] content, SvnChecksum.Kind checksumKind) throws SVNException {
        SVNChecksumInputStream checksumInputStream = new SVNChecksumInputStream(new ByteArrayInputStream(content), checksumKind.name());
        try {
            SVNFileUtil.readFile(checksumInputStream);
            return new SvnChecksum(checksumKind, checksumInputStream.getDigest());
        } catch (IOException e) {
            SVNErrorMessage errorMessage = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e);
            SVNErrorManager.error(errorMessage, SVNLogType.WC);
        } finally {
            SVNFileUtil.closeFile(checksumInputStream);
        }
        return null;
    }

    private void createSvnRepository(File repositoryDirectory) throws SVNException {
        final SVNClientManager clientManager = SVNClientManager.newInstance();
        try {
            SVNAdminClient adminClient = clientManager.getAdminClient();
            adminClient.doCreateRepository(repositoryDirectory, null, true, false);

        } finally {
            clientManager.dispose();
        }
    }

    private static class NodesRecord {
        private final String localRelPath;
        private final String reposPath;
        private final long revision;
        private final long opDepth;
        private final SVNNodeKind kind;

        private final long changedRevision;
        private final SvnChecksum checksum;
        private final SVNProperties properties;

        private NodesRecord(String localRelPath, String reposPath, long revision, long opDepth, SVNNodeKind kind, long changedRevision, SvnChecksum checksum, SVNProperties properties) {
            this.localRelPath = localRelPath;
            this.reposPath = reposPath;
            this.revision = revision;
            this.opDepth = opDepth;
            this.kind = kind;
            this.changedRevision = changedRevision;
            this.checksum = checksum;
            this.properties = properties;
        }

        private String getLocalRelPath() {
            return localRelPath;
        }

        private String getReposPath() {
            return reposPath;
        }

        private long getRevision() {
            return revision;
        }

        private long getOpDepth() {
            return opDepth;
        }

        private SVNNodeKind getKind() {
            return kind;
        }

        private long getChangedRevision() {
            return changedRevision;
        }

        public SvnChecksum getChecksum() {
            return checksum;
        }

        private SVNProperties getProperties() {
            return properties;
        }
    }
}
