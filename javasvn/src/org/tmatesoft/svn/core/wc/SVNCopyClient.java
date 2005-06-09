package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNLog;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 09.06.2005
 * Time: 19:44:56
 * To change this template use File | Settings | File Templates.
 */
public class SVNCopyClient extends SVNBasicClient {

    public SVNCopyClient() {
    }

    public SVNCopyClient(ISVNEventListener eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNCopyClient(ISVNCredentialsProvider credentialsProvider) {
        super(credentialsProvider);
    }

    public SVNCopyClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, eventDispatcher);
    }

    public SVNCopyClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNCopyClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void doCopy(String srcURL, SVNRevision pegRevision, SVNRevision revision, File dstPath) throws SVNException {
        srcURL = validateURL(srcURL);
        srcURL = getURL(srcURL, pegRevision, revision);
        long revNumber = getRevisionNumber(srcURL, revision);

        SVNRepository repos = createRepository(srcURL);
        SVNNodeKind srcKind = repos.checkPath("", revision.getNumber());
        if (srcKind == SVNNodeKind.NONE) {
            if (revision == SVNRevision.HEAD) {
                SVNErrorManager.error(0, null);
            }
            SVNErrorManager.error(0, null);
        }
        String srcUUID = repos.getRepositoryUUID();
        if (dstPath.isDirectory()) {
            dstPath = new File(dstPath, PathUtil.decode(PathUtil.tail(srcURL)));
            if (dstPath.exists()) {
                SVNErrorManager.error(0, null);
            }
        } else if (dstPath.exists()) {
            SVNErrorManager.error(0, null);
        }

        boolean sameRepositories;
        SVNWCAccess wcAccess = createWCAccess(dstPath);
        try {
            wcAccess.open(true, false);
            // check for missing entry.
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName());
            if (entry != null && !entry.isDirectory()) {
                SVNErrorManager.error(0, null);
            }
            String uuid = wcAccess.getTargetEntryProperty(SVNProperty.UUID);
            sameRepositories = uuid.equals(srcUUID);
            if (srcKind == SVNNodeKind.DIR) {
                String dstURL = wcAccess.getAnchor().getEntries().getPropertyValue("", SVNProperty.URL);
                dstURL = PathUtil.append(dstURL, PathUtil.encode(dstPath.getName()));
                SVNDirectory targetDir = createVersionedDirectory(dstPath, dstURL, uuid, revNumber);
                SVNWCAccess wcAccess2 = new SVNWCAccess(targetDir, targetDir, "");
                wcAccess2.open(true, true);
                setDoNotSleepForTimeStamp(true);
                try {
                    SVNReporter reporter = new SVNReporter(wcAccess2, true);
                    SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess2, null, true);

                    repos.update(revNumber, null, true, reporter, editor);
                    dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
                    if (sameRepositories) {
                        addDir(wcAccess.getAnchor(), dstPath.getName(), srcURL, editor.getTargetRevision());
                        addDir(wcAccess2.getAnchor(), "", srcURL, editor.getTargetRevision());
                        // fire added event.
                        dispatchEvent(SVNEventFactory.createAddedEvent(wcAccess, wcAccess.getAnchor(),
                                wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
                    } else {
                        SVNErrorManager.error(0, null);
                    }
                } finally {
                    setDoNotSleepForTimeStamp(false);
                    wcAccess2.close(true, true);
                }
            } else {
                Map properties = new HashMap();
                File tmpFile = null;
                OutputStream os = null;
                try {
                    File baseTmpFile = wcAccess.getAnchor().getBaseFile(dstPath.getName(), true);
                    tmpFile = SVNFileUtil.createUniqueFile(baseTmpFile.getParentFile(), dstPath.getName(), ".tmp");
                    os = new FileOutputStream(tmpFile);
                    repos.getFile("", revNumber, properties, os);
                    os.close();
                    os = null;
                    SVNFileUtil.rename(tmpFile, baseTmpFile);
                } catch (IOException e) {
                    SVNErrorManager.error(0, e);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            //
                        }
                    }
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                }
                addFile(wcAccess.getAnchor(), dstPath.getName(), properties, sameRepositories ? srcURL : null, revNumber);
                wcAccess.getAnchor().runLogs();
                // fire added event.
                dispatchEvent(SVNEventFactory.createAddedEvent(wcAccess, wcAccess.getAnchor(),
                        wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
            }
        } finally {
            if (wcAccess != null) {
                wcAccess.close(true, false);
            }
            SVNFileUtil.sleepForTimestamp();
        }
    }

    private void addDir(SVNDirectory dir, String name, String copyFromURL, long copyFromRev) throws SVNException {
        SVNEntry entry = dir.getEntries().getEntry(name);
        if (entry == null) {
            entry = dir.getEntries().addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        if (copyFromURL != null) {
            entry.setCopyFromRevision(copyFromRev);
            entry.setCopyFromURL(copyFromURL);
            entry.setCopied(true);
        }
        entry.scheduleForAddition();
        if ("".equals(name) && copyFromURL != null) {
            updateCopiedDirectory(dir, name);
        }
        dir.getEntries().save(true);
    }

    private void updateCopiedDirectory(SVNDirectory dir, String name) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name);
        if (entry != null) {
            entry.setCopied(true);
            if (entry.isFile()) {
                dir.getWCProperties(name).delete();
            }
            if (!"".equals(name) && entry.isDirectory()) {
                SVNDirectory childDir = dir.getChildDirectory(name);
                updateCopiedDirectory(childDir, "");
            } else if ("".equals(name)) {
                dir.getWCProperties("").delete();
                for (Iterator ents = entries.entries(); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    updateCopiedDirectory(dir, childEntry.getName());
                }
                entries.save(true);
            }
        }
    }

    private void addFile(SVNDirectory dir, String fileName, Map properties, String copyFromURL, long copyFromRev) throws SVNException {
        SVNLog log = dir.getLog(0);
        Map regularProps = new HashMap();
        Map entryProps = new HashMap();
        Map wcProps = new HashMap();
        for (Iterator names = properties.keySet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            String propValue = (String) properties.get(propName);
            if (propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                entryProps.put(SVNProperty.shortPropertyName(propName), propValue);
            } else if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                wcProps.put(propName, propValue);
            } else {
                regularProps.put(propName, propValue);
            }
        }
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), "0");
        entryProps.put(SVNProperty.shortPropertyName(SVNProperty.SCHEDULE), SVNProperty.SCHEDULE_ADD);
        if (copyFromURL != null) {
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_REVISION), Long.toString(copyFromRev));
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPYFROM_URL), copyFromURL);
            entryProps.put(SVNProperty.shortPropertyName(SVNProperty.COPIED), Boolean.TRUE.toString());
        }

        log.logChangedEntryProperties(fileName, entryProps);
        log.logChangedWCProperties(fileName, wcProps);
        dir.mergeProperties(fileName, regularProps, null, true, log);

        Map command = new HashMap();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, true)));
        command.put(SVNLog.DEST_ATTR, fileName);
        log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
        command.clear();
        command.put(SVNLog.NAME_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, true)));
        command.put(SVNLog.DEST_ATTR, SVNFileUtil.getBasePath(dir.getBaseFile(fileName, false)));
        log.addCommand(SVNLog.MOVE, command, false);
        log.save();
    }

}
