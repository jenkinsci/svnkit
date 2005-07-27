/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExportEditor;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNUpdateClient extends SVNBasicClient {

    public SVNUpdateClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNUpdateClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }

    public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {
        long revNumber = getRevisionNumber(file, revision);
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, true, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, null, recursive, isLeaveConflictsUnresolved());
            SVNRepository repos = createRepository(wcAccess.getAnchor()
                    .getEntries().getEntry("", true).getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            repos.update(revNumber, target, recursive, reporter, editor);

            if (editor.getTargetRevision() >= 0) {
                if (recursive && !isIgnoreExternals()) {
                    handleExternals(wcAccess);
                }
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close(true);
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }
        }
    }

    public long doSwitch(File file, SVNURL url, SVNRevision revision, boolean recursive) throws SVNException {
        long revNumber = getRevisionNumber(file, revision);
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, true, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, url.toString(), recursive, isLeaveConflictsUnresolved());
            SVNRepository repos = createRepository(wcAccess.getAnchor()
                    .getEntries().getEntry("", true).getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null
                    : wcAccess.getTargetName();
            repos.update(url, revNumber, target, recursive, reporter, editor);

            if (editor.getTargetRevision() >= 0 && recursive
                    && !isIgnoreExternals()) {
                handleExternals(wcAccess);
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(
                        wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close(true);
            if (!isDoNotSleepForTimeStamp()) {
                SVNFileUtil.sleepForTimestamp();
            }
        }
    }

    public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        if (dstPath == null) {
            dstPath = new File(".", SVNPathUtil.tail(url.getPath()));
        }
        if (!revision.isValid() && !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
            revision = SVNRevision.HEAD;
        } else if (!revision.isValid()) {
            revision = pegRevision;
        } else if (!pegRevision.isValid()) {
            pegRevision = revision;
        }
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        long revNumber = getRevisionNumber(revision, repos, null);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        String uuid = repos.getRepositoryUUID();
        if (targetNodeKind == SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: URL '" + url + "' refers to a file not a directory");
        } else if (targetNodeKind == SVNNodeKind.NONE) {
            SVNErrorManager.error("svn: URL '" + url + "' doesn't exist");
        }
        setDoNotSleepForTimeStamp(true);
        long result = -1;
        try {
            if (!dstPath.exists()
                    || (dstPath.isDirectory() && !SVNWCAccess
                            .isVersionedDirectory(dstPath))) {
                createVersionedDirectory(dstPath, url, uuid, revNumber);
                result = doUpdate(dstPath, revision, recursive);
            } else if (dstPath.isDirectory()
                    && SVNWCAccess.isVersionedDirectory(dstPath)) {
                SVNWCAccess wcAccess = SVNWCAccess.create(dstPath);
                if (url
                        .equals(wcAccess
                                .getTargetEntryProperty(SVNProperty.URL))) {
                    result = doUpdate(dstPath, revision, recursive);
                } else {
                    SVNErrorManager
                            .error("svn: working copy with different URL '"
                                    + wcAccess
                                            .getTargetEntryProperty(SVNProperty.URL)
                                    + "' already exists at checkout destination");
                }
            } else {
                SVNErrorManager.error("svn: '" + dstPath + "' already exists and it is a file");
            }
        } finally {
            if (!isCommandRunning()) {
                SVNFileUtil.sleepForTimestamp();
            }
            setDoNotSleepForTimeStamp(false);
        }
        return result;
    }

    public long doExport(SVNURL url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        SVNRepository repository = createRepository(url, null, pegRevision, revision);
        long exportedRevision = doRemoteExport(repository, repository.getPegRevision(), dstPath, eolStyle, force, recursive);
        dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, exportedRevision));
        return exportedRevision;
    }

    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle,
            final boolean force, boolean recursive) throws SVNException {
        long exportedRevision = -1;
        if (revision != SVNRevision.BASE && revision != SVNRevision.WORKING && revision != SVNRevision.COMMITTED && revision != SVNRevision.UNDEFINED) {
            SVNRepository repository = createRepository(null, srcPath, pegRevision, revision);
            exportedRevision = doRemoteExport(repository, repository.getPegRevision(), dstPath, eolStyle, force, recursive); 
        } else {
            if (revision == SVNRevision.UNDEFINED) {
                revision = SVNRevision.WORKING;
            }
            copyVersionedDir(srcPath, dstPath, revision, eolStyle, force, recursive);
        }
        dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, exportedRevision));
        return exportedRevision;
    }
    
    private void copyVersionedDir(File from, File to, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(from);
        wcAccess.open(false, false);
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry == null) {
            SVNErrorManager.error("svn: '" + from + "' is not under version control or doesn't exist");
        }
        if (revision == SVNRevision.WORKING && targetEntry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && targetEntry.isScheduledForAddition()) {
            return;
        }
        if (targetEntry.isDirectory()) {
            // create dir
            boolean dirCreated = to.mkdirs();
            if (!to.exists() || to.isFile()) {
                SVNErrorManager.error("svn: Cannot create destination directory");
            }
            if (!dirCreated && to.isDirectory() && !force) {
                SVNErrorManager.error("svn: Destination directory exists, and will not be overwritten unless forced");
            }
            // read entries
            SVNEntries entries = wcAccess.getTarget().getEntries();
            for (Iterator ents = entries.entries(false); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if (entry.isDirectory()) {
                    if ("".equals(entry.getName())) {
                        continue;
                    } else if (recursive) {
                        File childTo = new File(to, entry.getName());
                        File childFrom = new File(from, entry.getName());
                        copyVersionedDir(childFrom, childTo, revision, eolStyle, force, recursive);
                    }
                } else if (entry.isFile()) {
                    File childTo = new File(to, entry.getName());
                    copyVersionedFile(childTo, wcAccess.getTarget(), entry.getName(), revision, force, eolStyle);
                }
            }
        } else if (targetEntry.isFile()) {
            copyVersionedFile(to, wcAccess.getTarget(), wcAccess.getTargetName(), revision, force, eolStyle);
        }
    }

    private void copyVersionedFile(File dstPath, SVNDirectory dir, String fileName, SVNRevision revision, boolean force, String eol) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(fileName, false);
        if (entry == null) {
            SVNErrorManager.error("svn: '" + dir.getFile(fileName) + "' is not under version control or doesn't exist");
        }
        if (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) {
            return;
        }
        if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
            return;
        }
        boolean modified = false;
        SVNProperties props = null;
        long timestamp;
        if (revision != SVNRevision.WORKING) {
            props = dir.getBaseProperties(fileName, false);
        } else {
            props = dir.getProperties(fileName, false);
            modified = dir.hasTextModifications(fileName, false);
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        byte[] eols = eol != null ? SVNTranslator.getEOL(eol) : null;
        if (eols == null) {
            eol = props.getPropertyValue(SVNProperty.EOL_STYLE);
            eols = SVNTranslator.getWorkingEOL(eol);
        }
        if (modified && !special) {
            timestamp = dir.getFile(fileName).lastModified();
        } else {
            timestamp = SVNTimeUtil.parseDateAsLong(entry.getCommittedDate());
        }
        Map keywordsMap = null;
        if (keywords != null) {
            String rev = Long.toString(entry.getCommittedRevision());
            String author;
            if (modified) {
                author = "(local)";
                rev += "M";
            } else {
                author = entry.getAuthor();                
            }
            keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), author, entry.getCommittedDate(), rev);            
        }
        File srcFile = revision == SVNRevision.WORKING ? dir.getFile(fileName) : dir.getBaseFile(fileName, false);
        SVNFileType fileType = SVNFileType.getType(srcFile);
        if (fileType == SVNFileType.SYMLINK && revision == SVNRevision.WORKING) {
            // base will be translated OK, but working not.
            File tmpBaseFile = dir.getBaseFile(fileName, true);
            try {
                SVNTranslator.translate(srcFile, tmpBaseFile, eols, keywordsMap, special, false);
                SVNTranslator.translate(tmpBaseFile, dstPath, eols, keywordsMap, special, true);
            } finally {
                tmpBaseFile.delete();
            }
        } else {
            SVNTranslator.translate(srcFile, dstPath, eols, keywordsMap, special, true);
        }
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        if (!special && timestamp > 0) {
            dstPath.setLastModified(timestamp);
        }
    }

    private long doRemoteExport(SVNRepository repository, final long revNumber, File dstPath, String eolStyle, boolean force, boolean recursive) throws SVNException {
        SVNNodeKind dstKind = repository.checkPath("", revNumber);
        if (dstKind == SVNNodeKind.DIR) {
            SVNExportEditor editor = new SVNExportEditor(this, repository.getLocation().toString(), dstPath,  force, eolStyle);
            repository.update(revNumber, null, recursive, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revNumber, true);
                    reporter.finishReport();
                }
            }, editor);
            // nothing may be created.
            SVNFileType fileType = SVNFileType.getType(dstPath);
            if (fileType == SVNFileType.NONE) {
                editor.openRoot(revNumber);
            }
            if (!isIgnoreExternals() && recursive) {
                Map externals = editor.getCollectedExternals();
                for (Iterator files = externals.keySet().iterator(); files.hasNext();) {
                    File rootFile = (File) files.next();
                    String propValue = (String) externals.get(rootFile);
                    if (propValue == null) {
                        continue;
                    }
                    SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", propValue);
                    for (int i = 0; i < infos.length; i++) {
                        File targetDir = new File(rootFile, infos[i].getPath());
                        SVNURL srcURL = infos[i].getOldURL();
                        SVNRevision srcRevision = SVNRevision.create(infos[i].getOldRevision());
                        String relativePath =  targetDir.equals(dstPath) ? "" : targetDir.getAbsolutePath().substring(dstPath.getAbsolutePath().length() + 1);
                        relativePath = relativePath.replace(File.separatorChar, '/');
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(null, relativePath));
                        try {
                            setEventPathPrefix(relativePath);
                            doExport(srcURL, targetDir, srcRevision, srcRevision, eolStyle, force, recursive);
                        } catch (Throwable th) {
                            dispatchEvent(new SVNEvent(th.getMessage()));
                        } finally {
                            setEventPathPrefix(null);
                        }
                    }
                }
            }
        } else if (dstKind == SVNNodeKind.FILE) {
            String url = repository.getLocation().toString();
            if (dstPath.isDirectory()) {
                dstPath = new File(dstPath, SVNEncodingUtil.uriDecode(SVNPathUtil.tail(url)));
            }
            if (dstPath.exists()) {
                if (!force) {
                    SVNErrorManager.error("svn: '" + dstPath + "' already exists");
                }
            } else {
                dstPath.getParentFile().mkdirs();
            }
            Map properties = new HashMap();
            OutputStream os = null;
            File tmpFile = SVNFileUtil.createUniqueFile(dstPath.getParentFile(), dstPath.getName(), ".tmp");
            os = SVNFileUtil.openFileForWriting(tmpFile);
            try {
                repository.getFile("", revNumber, properties, os);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            if (force && dstPath.exists()) {
                SVNFileUtil.deleteAll(dstPath);
            }
            Map keywords = SVNTranslator.computeKeywords((String) properties.get(SVNProperty.KEYWORDS), url,
                            (String) properties.get(SVNProperty.LAST_AUTHOR),
                            (String) properties.get(SVNProperty.COMMITTED_DATE),
                            (String) properties.get(SVNProperty.COMMITTED_REVISION));
            if (eolStyle == null) {
                eolStyle = (String) properties.get(SVNProperty.EOL_STYLE);
            }
            byte[] eols = SVNTranslator.getWorkingEOL(eolStyle);
            SVNTranslator.translate(tmpFile, dstPath, eols, keywords, properties.get(SVNProperty.SPECIAL) != null, true);
            tmpFile.delete();
            if (properties.get(SVNProperty.EXECUTABLE) != null) {
                SVNFileUtil.setExecutable(dstPath, true);
            }
            dispatchEvent(SVNEventFactory.createExportAddedEvent(dstPath.getParentFile(), dstPath, SVNNodeKind.FILE));            
        }
        return revNumber;
    }

    public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL, boolean recursive) throws SVNException {
        SVNRepository repos = createRepository(newURL);
        repos.testConnection();
        String uuid = repos.getRepositoryUUID();
        SVNWCAccess wcAccess = createWCAccess(dst);
        try {
            wcAccess.open(true, recursive);
            String oldUUID = wcAccess.getTargetEntryProperty(SVNProperty.UUID);
            if (!oldUUID.equals(uuid)) {
                SVNErrorManager.error("The repository at '" + newURL
                        + "' has uuid '" + uuid + "', but the WC has '"
                        + oldUUID + "'");
            }
            doRelocate(wcAccess.getAnchor(), wcAccess.getTargetName(), oldURL.toString(), newURL.toString(), recursive);
        } finally {
            wcAccess.close(true);

        }
    }

    private void doRelocate(SVNDirectory dir, String targetName, String oldURL, String newURL, boolean recursive) throws SVNException {
        SVNEntries entries = dir.getEntries();
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (targetName != null && !"".equals(targetName)) {
                if (!targetName.equals(entry.getName())) {
                    continue;
                }
            }
            String copyFromURL = entry.getCopyFromURL();
            
            if (copyFromURL != null && copyFromURL.startsWith(oldURL)) {
                copyFromURL = copyFromURL.substring(oldURL.length());
                copyFromURL = SVNPathUtil.append(newURL, copyFromURL);
                copyFromURL = validateURL(copyFromURL);
                entry.setCopyFromURL(copyFromURL);
            }
            if (recursive && entry.isDirectory() && !"".equals(entry.getName())) {
                if (dir.getChildDirectory(entry.getName()) != null) {
                    doRelocate(dir.getChildDirectory(entry.getName()), null,
                            oldURL, newURL, recursive);
                }
            } else if (entry.isFile() || "".equals(entry.getName())) {
                String url = entry.getURL();
                if (url.startsWith(oldURL)) {
                    url = url.substring(oldURL.length());
                    url = SVNPathUtil.append(newURL, url);
                    url = validateURL(url);
                    entry.setURL(url);
                }
            }
        }
        dir.getEntries().save(true);
    }

    private void handleExternals(SVNWCAccess wcAccess) {
        setDoNotSleepForTimeStamp(true);
        try {
            for (Iterator externals = wcAccess.externals(); externals.hasNext();) {
                SVNExternalInfo external = (SVNExternalInfo) externals.next();
                if (external.getOldURL() == null && external.getNewURL() == null) {
                    continue;
                }
                long revNumber = external.getNewRevision();
                SVNRevision revision = revNumber >= 0 ? SVNRevision.create(revNumber) : SVNRevision.HEAD;
                setEventPathPrefix(external.getPath());
                try {
                    if (external.getOldURL() == null) {
                        external.getFile().mkdirs();
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                        doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                    } else if (external.getNewURL() == null) {
                        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                            SVNWCAccess externalAccess = createWCAccess(external.getFile());
                            externalAccess.open(true, true);
                            externalAccess.getAnchor().destroy("", true);
                            externalAccess.close(true);
                        }
                    } else if (external.isModified()) {
                        deleteExternal(external);
                        external.getFile().mkdirs();
                        dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                        doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                    } else {
                        if (!external.getFile().isDirectory()) {
                            external.getFile().mkdirs();
                            doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                        } else {
                            String url = null;
                            if (SVNWCAccess.isVersionedDirectory(external
                                    .getFile())) {
                                SVNWCAccess externalAccess = createWCAccess(external.getFile());
                                url = externalAccess.getTargetEntryProperty(SVNProperty.URL);
                            }
                            if (!external.getNewURL().equals(url)) {
                                deleteExternal(external);
                            }
                            // update or checkout.
                            external.getFile().mkdirs();
                            dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                            doCheckout(external.getNewURL(), external.getFile(), revision, revision, true);
                        }
                    }
                } catch (Throwable th) {
                    dispatchEvent(new SVNEvent(th.getMessage()));
                    SVNDebugLog.log(th);
                } finally {
                    setEventPathPrefix(null);
                }
            }
        } finally {
            setEventPathPrefix(null);
            setDoNotSleepForTimeStamp(false);
        }
    }

    private void deleteExternal(SVNExternalInfo external) throws SVNException {
        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
            SVNWCAccess externalAccess = createWCAccess(external.getFile());

            try {
                externalAccess.open(true, true);
                externalAccess.getAnchor().destroy("", true);
            } catch (Throwable th) {
                SVNDebugLog.log(th);
            } finally {
                externalAccess.close(true);
            }
        }
        if (external.getFile().exists()) {
            external.getFile().getParentFile().mkdirs();
            File newLocation = SVNFileUtil.createUniqueFile(external.getFile()
                    .getParentFile(), external.getFile().getName(), ".OLD");
            SVNFileUtil.rename(external.getFile(), newLocation);
        }
    }
}
