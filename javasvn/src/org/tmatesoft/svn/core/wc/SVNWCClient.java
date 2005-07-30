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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNWCClient extends SVNBasicClient {

    public SVNWCClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNWCClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }

    public void doGetFileContents(File path, SVNRevision pegRevision,
            SVNRevision revision, boolean expandKeywords, OutputStream dst)
            throws SVNException {
        if (dst == null) {
            return;
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.WORKING;
        }
        if (revision == SVNRevision.COMMITTED) {
            revision = SVNRevision.BASE;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        if ("".equals(wcAccess.getTargetName())
                || wcAccess.getTarget() != wcAccess.getAnchor()) {
            SVNErrorManager.error("svn: '" + path + "' refers to a directory");
        }
        String name = wcAccess.getTargetName();
        if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            File file = wcAccess.getAnchor().getBaseFile(name, false);
            boolean delete = false;
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(wcAccess.getTargetName(), false);
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path
                        + "' is not under version control or doesn't exist");
            }
            try {

                if (revision == SVNRevision.BASE) {
                    if (expandKeywords) {
                        delete = true;
                        file = wcAccess.getAnchor().getBaseFile(name, true).getParentFile();
                        file = SVNFileUtil.createUniqueFile(file, name, ".tmp");
                        SVNTranslator.translate(wcAccess.getAnchor(), name,
                                SVNFileUtil.getBasePath(wcAccess.getAnchor()
                                        .getBaseFile(name, false)), SVNFileUtil
                                        .getBasePath(file), true, false);
                    }
                } else {
                    if (!expandKeywords) {
                        delete = true;
                        file = wcAccess.getAnchor().getBaseFile(name, true).getParentFile();
                        file = SVNFileUtil.createUniqueFile(file, name, ".tmp");
                        SVNTranslator.translate(wcAccess.getAnchor(), name,
                                name, SVNFileUtil.getBasePath(file), false,
                                false);
                    } else {
                        file = wcAccess.getAnchor().getFile(name);
                    }
                }
            } finally {
                if (file != null && file.exists()) {
                    InputStream is = SVNFileUtil.openFileForReading(file);
                    try {
                        int r;
                        while ((r = is.read()) >= 0) {
                            dst.write(r);
                        }
                    } catch (IOException e) {
                        SVNDebugLog.logInfo(e);
                    } finally {
                        SVNFileUtil.closeFile(is);
                        if (delete) {
                            file.delete();
                        }
                    }
                }
            }
        } else {
            SVNEntry entry = wcAccess.getTargetEntry();
            SVNURL url = entry.getSVNURL();
            SVNRepository repos = createRepository(url, path, pegRevision, revision);
            url = repos.getLocation();
            long revNumber = getRevisionNumber(revision, repos, path);
            long pegRevisionNumber = repos.getPegRevision();
            if (!expandKeywords) {
                doGetFileContents(url, SVNRevision.create(pegRevisionNumber), SVNRevision.create(revNumber), expandKeywords, dst);
            } else {
                File tmpFile = SVNFileUtil.createUniqueFile(new File(path
                        .getParentFile(), ".svn/tmp/text-base"),
                        path.getName(), ".tmp");
                File tmpFile2 = null;
                OutputStream os = null;
                InputStream is = null;
                try {
                    os = SVNFileUtil.openFileForWriting(tmpFile);
                    doGetFileContents(url, SVNRevision.create(pegRevisionNumber), SVNRevision.create(revNumber), false, os);
                    SVNFileUtil.closeFile(os);
                    os = null;
                    // translate
                    tmpFile2 = SVNFileUtil.createUniqueFile(new File(path
                            .getParentFile(), ".svn/tmp/text-base"), path
                            .getName(), ".tmp");
                    boolean special = wcAccess.getAnchor().getProperties(
                            path.getName(), false).getPropertyValue(
                            SVNProperty.SPECIAL) != null;
                    if (special) {
                        tmpFile2 = tmpFile;
                    } else {
                        SVNTranslator.translate(wcAccess.getAnchor(), path
                                .getName(), SVNFileUtil.getBasePath(tmpFile),
                                SVNFileUtil.getBasePath(tmpFile2), true, false);
                    }
                    // cat tmp file
                    is = SVNFileUtil.openFileForReading(tmpFile2);
                    int r;
                    while ((r = is.read()) >= 0) {
                        dst.write(r);
                    }
                } catch (IOException e) {
                    SVNErrorManager.error("svn: " + e.getMessage());
                } finally {
                    SVNFileUtil.closeFile(os);
                    SVNFileUtil.closeFile(is);
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                    if (tmpFile2 != null) {
                        tmpFile2.delete();
                    }
                }
            }
        }
    }

    public void doGetFileContents(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean expandKeywords, OutputStream dst) throws SVNException {
        revision = revision == null || !revision.isValid() ? SVNRevision.HEAD : revision;
        // now get contents from URL.
        Map properties = new HashMap();
        SVNRepository repos = createRepository(url, null, pegRevision, revision);

        SVNNodeKind nodeKind = repos.checkPath("", repos.getPegRevision());
        if (nodeKind == SVNNodeKind.DIR) {
            SVNErrorManager.error("svn: URL '" + url + " refers to a directory");
        }
        if (nodeKind != SVNNodeKind.FILE) {
            return;
        }
        OutputStream os = null;
        InputStream is = null;
        File file;
        File file2;
        try {
            file = File.createTempFile("svn-contents", ".tmp");
            file2 = File.createTempFile("svn-contents", ".tmp");
        } catch (IOException e) {
            SVNErrorManager.error("svn: Cannot create temporary files: " + e.getMessage());
            return;
        }
        try {
            os = new FileOutputStream(file);
            repos.getFile("", repos.getPegRevision(), properties, os);
            os.close();
            os = null;
            if (expandKeywords) {
                // use props at committed (peg) revision, not those.
                String keywords = (String) properties.get(SVNProperty.KEYWORDS);
                String eol = (String) properties.get(SVNProperty.EOL_STYLE);
                byte[] eolBytes = SVNTranslator.getWorkingEOL(eol);
                Map keywordsMap = SVNTranslator.computeKeywords(keywords, url.toString(),
                        (String) properties.get(SVNProperty.LAST_AUTHOR),
                        (String) properties.get(SVNProperty.COMMITTED_DATE),
                        (String) properties.get(SVNProperty.COMMITTED_REVISION));
                SVNTranslator.translate(file, file2, eolBytes, keywordsMap,
                        false, true);
            } else {
                file2 = file;
            }

            is = SVNFileUtil.openFileForReading(file2);
            int r;
            while ((r = is.read()) >= 0) {
                dst.write(r);
            }
        } catch (IOException e) {
            //
            e.printStackTrace();
        } finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
            if (file != null) {
                file.delete();
            }
            if (file2 != null) {
                file2.delete();
            }
        }
    }

    public void doCleanup(File path) throws SVNException {
        SVNFileType fType = SVNFileType.getType(path);
        if (fType == SVNFileType.NONE) {
            SVNErrorManager.error("svn: '" + path + "' does not exist");
        } else if (fType == SVNFileType.FILE || fType == SVNFileType.SYMLINK) {
            path = path.getParentFile();
        }
        if (!SVNWCAccess.isVersionedDirectory(path)) {
            SVNErrorManager.error("svn: '" + path
                    + "' is not under version control");
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        wcAccess.open(true, true, true);
        wcAccess.getAnchor().cleanup();
        wcAccess.close(true);
    }

    public void doSetProperty(File path, String propName, String propValue, boolean force, boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        propName = validatePropertyName(propName);
        if (SVNRevisionProperty.isRevisionProperty(propName)) {
            SVNErrorManager.error("svn: Revision property '" + propName + "' not allowed in this context");
        } else if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorManager.error("svn: '" + propName + "' is a wcprop , thus not accessible to clients");
        }
        propValue = validatePropertyValue(propName, propValue, force);
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            doSetLocalProperty(wcAccess.getAnchor(), wcAccess.getTargetName(), propName, propValue, force, recursive, handler);
        } finally {
            wcAccess.close(true);
        }
    }

    public void doSetRevisionProperty(File path, SVNRevision revision, String propName, String propValue, boolean force, ISVNPropertyHandler handler) throws SVNException {
        propName = validatePropertyName(propName);
        SVNURL url = getURL(path);
        doSetRevisionProperty(url, revision, propName, propValue, force, handler);
    }

    public void doSetRevisionProperty(SVNURL url, SVNRevision revision, String propName, String propValue, boolean force, ISVNPropertyHandler handler) throws SVNException {
        propName = validatePropertyName(propName);
        if (!force && SVNRevisionProperty.AUTHOR.equals(propName) && propValue != null && propValue.indexOf('\n') >= 0) {
            SVNErrorManager.error("svn: Value will not be set unless forced");
        }
        if (propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorManager.error("svn: '" + propName + "' is a wcprop , thus not accessible to clients");
        }
        SVNRepository repos = createRepository(url, null, SVNRevision.UNDEFINED, revision);
        long revNumber = getRevisionNumber(revision, repos, null);
        repos.setRevisionPropertyValue(revNumber, propName, propValue);
        if (handler != null) {
            handler.handleProperty(revNumber, new SVNPropertyData(propName, propValue));
        }
    }

    public SVNPropertyData doGetProperty(final File path, String propName,
            SVNRevision pegRevision, SVNRevision revision, boolean recursive)
            throws SVNException {
        final SVNPropertyData[] data = new SVNPropertyData[1];
        doGetProperty(path, propName, pegRevision, revision, recursive, new ISVNPropertyHandler() {
            public void handleProperty(File file, SVNPropertyData property) {
                if (data[0] == null && path.equals(file)) {
                    data[0] = property;
                }
            }
            public void handleProperty(SVNURL url, SVNPropertyData property) {
            }
            public void handleProperty(long revision, SVNPropertyData property) {
            }
        });
        return data[0];
    }

    public SVNPropertyData doGetProperty(final SVNURL url, String propName,
            SVNRevision pegRevision, SVNRevision revision, boolean recursive)
            throws SVNException {
        final SVNPropertyData[] data = new SVNPropertyData[1];
        doGetProperty(url, propName, pegRevision, revision, recursive, new ISVNPropertyHandler() {
            public void handleProperty(File file, SVNPropertyData property) {
            }
            public void handleProperty(long revision, SVNPropertyData property) {
            }
            public void handleProperty(SVNURL location, SVNPropertyData property) throws SVNException {
                if (data[0] == null && url.toString().equals(location.toString())) {
                    data[0] = property;
                }
            }
        });
        return data[0];
    }

    public void doGetProperty(File path, String propName, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorManager.error("svn: '" + propName + "' is a wcprop , thus not accessible to clients");
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.WORKING;
        }
        SVNWCAccess wcAccess = createWCAccess(path);

        wcAccess.open(false, recursive);
        SVNEntry entry = wcAccess.getTargetEntry();
        if (entry == null) {
            SVNErrorManager.error("svn: '" + path + "' is not under version control");
        }
        if (revision != SVNRevision.WORKING && revision != SVNRevision.BASE && revision != SVNRevision.COMMITTED) {
            SVNURL url = entry.getSVNURL();
            SVNRepository repository = createRepository(url, path, pegRevision, revision);
            long revisionNumber = getRevisionNumber(revision, repository, path);
            revision = SVNRevision.create(revisionNumber);
            doGetRemoteProperty(url, "", repository, propName, revision, recursive, handler);
        } else {
            doGetLocalProperty(wcAccess.getAnchor(), wcAccess.getTargetName(), propName, revision, recursive, handler);
        }
        wcAccess.close(false);
    }

    public void doGetProperty(SVNURL url, String propName, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorManager.error("svn: '" + propName + "' is a wcprop , thus not accessible to clients");
        }
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        doGetRemoteProperty(url, "", repos, propName, revision, recursive, handler);
    }

    public void doGetRevisionProperty(File path, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorManager.error("svn: '" + propName + "' is a wcprop , thus not accessible to clients");
        }
        if (!revision.isValid()) {
            SVNErrorManager.error("svn: Valid revision have to be specified to fetch revision property");
        }
        SVNURL url = getURL(path);
        SVNRepository repository = createRepository(url, path, SVNRevision.UNDEFINED, revision);
        long revisionNumber = getRevisionNumber(revision, repository, path);
        doGetRevisionProperty(repository, propName, revisionNumber, handler);
    }

    public void doGetRevisionProperty(SVNURL url, String propName, SVNRevision revision, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
            SVNErrorManager.error("svn: '" + propName + "' is a wcprop , thus not accessible to clients");
        }
        if (!revision.isValid()) {
            SVNErrorManager.error("svn: Valid revision have to be specified to fetch revision property");
        }
        SVNRepository repos = createRepository(url);
        long revNumber = getRevisionNumber(revision, repos, null);
        doGetRevisionProperty(repos, propName, revNumber, handler);
    }

    private void doGetRevisionProperty(SVNRepository repos, String propName, long revNumber, ISVNPropertyHandler handler) throws SVNException {
        if (propName != null) {
            String value = repos.getRevisionPropertyValue(revNumber, propName);
            if (value != null) {
                handler.handleProperty(revNumber, new SVNPropertyData(propName, value));
            }
        } else {
            Map props = new HashMap();
            repos.getRevisionProperties(revNumber, props);
            for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                String value = (String) props.get(name);
                handler.handleProperty(revNumber, new SVNPropertyData(name, value));
            }
        }
    }

    public void doDelete(File path, boolean force, boolean dryRun)
            throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, true, true);
            if (!force) {
                wcAccess.getAnchor().canScheduleForDeletion(
                        wcAccess.getTargetName());
            }
            if (!dryRun) {
                wcAccess.getAnchor().scheduleForDeletion(
                        wcAccess.getTargetName());
            }
        } finally {
            wcAccess.close(true);
        }
    }

    public void doAdd(File path, boolean force, boolean mkdir,
            boolean climbUnversionedParents, boolean recursive)
            throws SVNException {
        if (!path.exists() && !mkdir) {
            SVNErrorManager.error("svn: '" + path + "' doesn't exist");
        }
        if (climbUnversionedParents) {
            File parent = path.getParentFile();
            if (parent != null
                    && SVNWCUtil.getWorkingCopyRoot(path, true) == null) {
                // path is in unversioned dir, try to add this parent before
                // path.
                try {
                    doAdd(parent, false, mkdir, climbUnversionedParents, false);
                } catch (SVNException e) {
                    SVNErrorManager.error("svn: '" + path
                            + "' doesn't belong to svn working copy");
                }
            }
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            String name = wcAccess.getTargetName();

            if ("".equals(name) && !force) {
                SVNErrorManager
                        .error("svn: '"
                                + path
                                + "' is the root of the working copy, it couldn't be scheduled for addition");
            }
            SVNDirectory dir = wcAccess.getAnchor();
            SVNFileType ftype = SVNFileType.getType(path);
            if (ftype == SVNFileType.FILE || ftype == SVNFileType.SYMLINK) {
                addSingleFile(dir, name);
            } else if (ftype == SVNFileType.DIRECTORY && recursive) {
                // add dir and recurse.
                addDirectory(wcAccess, wcAccess.getAnchor(), name, force);
            } else {
                // add single dir, no force - report error anyway.
                dir.add(wcAccess.getTargetName(), mkdir, false);
            }
        } finally {
            wcAccess.close(true);
        }
    }

    public void doRevert(File path, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);

        // force recursive lock.
        boolean reverted = false;
        boolean replaced = false;
        SVNNodeKind kind = null;
        Collection recursiveFiles = new ArrayList();
        try {
            wcAccess.open(true, false);
            SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                    wcAccess.getTargetName(), true);
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path
                        + "' is not under version control");
            }
            kind = entry.getKind();
            File file = wcAccess.getAnchor().getFile(wcAccess.getTargetName());
            if (entry.isDirectory()) {
                if (!entry.isScheduledForAddition() && !file.isDirectory()) {
                    handleEvent(SVNEventFactory.createNotRevertedEvent(
                            wcAccess, wcAccess.getAnchor(), entry),
                            ISVNEventHandler.UNKNOWN);
                    return;
                }
            }

            SVNEvent event = SVNEventFactory.createRevertedEvent(wcAccess,
                    wcAccess.getAnchor(), entry);
            if (entry.isScheduledForAddition()) {
                boolean deleted = entry.isDeleted();
                if (entry.isFile()) {
                    wcAccess.getAnchor().destroy(entry.getName(), false);
                } else if (entry.isDirectory()) {
                    if ("".equals(wcAccess.getTargetName())) {
                        SVNErrorManager
                                .error("svn: Cannot revert addition of the root directory; please try again from the parent directory");
                    }
                    if (!file.exists()) {
                        wcAccess.getAnchor().getEntries().deleteEntry(
                                entry.getName());
                    } else {
                        wcAccess.open(true, true, true);
                        wcAccess.getAnchor().destroy(entry.getName(), false);
                    }
                }
                reverted = true;
                if (deleted && !"".equals(wcAccess.getTargetName())) {
                    // we are not in the root.
                    SVNEntry replacement = wcAccess.getAnchor().getEntries()
                            .addEntry(entry.getName());
                    replacement.setDeleted(true);
                    replacement.setKind(kind);
                }
            } else if (entry.isScheduledForReplacement()
                    || entry.isScheduledForDeletion()) {
                replaced = entry.isScheduledForReplacement();
                if (entry.isDirectory()) {
                    reverted |= wcAccess.getTarget().revert("");
                } else {
                    reverted |= wcAccess.getAnchor().revert(entry.getName());
                }
                reverted = true;
            } else {
                if (entry.isDirectory()) {
                    reverted |= wcAccess.getTarget().revert("");
                } else {
                    reverted |= wcAccess.getAnchor().revert(entry.getName());
                }
            }
            if (reverted) {
                if (kind == SVNNodeKind.DIR && replaced) {
                    recursive = true;
                }
                if (!"".equals(wcAccess.getTargetName())) {
                    entry.unschedule();
                    entry.setConflictNew(null);
                    entry.setConflictOld(null);
                    entry.setConflictWorking(null);
                    entry.setPropRejectFile(null);
                }
                wcAccess.getAnchor().getEntries().save(false);
                if (kind == SVNNodeKind.DIR) {
                    SVNEntry inner = wcAccess.getTarget().getEntries()
                            .getEntry("", true);
                    if (inner != null) {
                        // may be null if it was removed from wc.
                        inner.unschedule();
                        inner.setConflictNew(null);
                        inner.setConflictOld(null);
                        inner.setConflictWorking(null);
                        inner.setPropRejectFile(null);
                    }
                }
                wcAccess.getTarget().getEntries().save(false);
            }
            if (kind == SVNNodeKind.DIR && recursive) {
                // iterate over targets and revert
                for (Iterator ents = wcAccess.getTarget().getEntries().entries(
                        true); ents.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) ents.next();
                    if ("".equals(childEntry.getName())) {
                        continue;
                    }
                    recursiveFiles.add(wcAccess.getTarget().getFile(childEntry.getName()));
                }
            }
            if (reverted) {
                // fire reverted event.
                handleEvent(event, ISVNEventHandler.UNKNOWN);
            }
        } finally {
            wcAccess.close(true);
        }
        // recurse
        if (kind == SVNNodeKind.DIR && recursive) {
            // iterate over targets and revert
            for (Iterator files = recursiveFiles.iterator(); files.hasNext();) {
                File file = (File) files.next();
                doRevert(file, recursive);
            }
        }
    }

    public void doResolve(File path, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(true, recursive);
            String target = wcAccess.getTargetName();
            SVNDirectory dir = wcAccess.getAnchor();

            if (wcAccess.getTarget() != wcAccess.getAnchor()) {
                target = "";
                dir = wcAccess.getTarget();
            }
            SVNEntry entry = dir.getEntries().getEntry(target, false);
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path
                        + "' is not under version control");
                return;
            }

            if (!recursive || entry.getKind() != SVNNodeKind.DIR) {
                if (dir.markResolved(target, true, true)) {
                    SVNEvent event = SVNEventFactory.createResolvedEvent(
                            wcAccess, dir, entry);
                    handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } else {
                doResolveAll(wcAccess, dir);
            }
        } finally {
            wcAccess.close(true);
        }
    }

    private void doResolveAll(SVNWCAccess access, SVNDirectory dir)
            throws SVNException {
        SVNEntries entries = dir.getEntries();
        Collection childDirs = new ArrayList();
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName()) || entry.isFile()) {
                if (dir.markResolved(entry.getName(), true, true)) {
                    SVNEvent event = SVNEventFactory.createResolvedEvent(
                            access, dir, entry);
                    handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
            } else if (entry.isDirectory()) {
                SVNDirectory childDir = dir.getChildDirectory(entry.getName());
                if (childDir != null) {
                    childDirs.add(childDir);
                }
            }
        }
        entries.save(true);
        for (Iterator dirs = childDirs.iterator(); dirs.hasNext();) {
            SVNDirectory child = (SVNDirectory) dirs.next();
            doResolveAll(access, child);
        }
    }

    public void doLock(File[] paths, boolean stealLock, String lockMessage)
            throws SVNException {
        Map entriesMap = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            SVNWCAccess wcAccess = createWCAccess(paths[i]);
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                        wcAccess.getTargetName(), true);
                if (entry == null || entry.isHidden()) {
                    SVNErrorManager.error("svn: '" + entry.getName()
                            + "' is not under version control");
                }
                if (entry.getURL() == null) {
                    SVNErrorManager.error("svn: '" + entry.getName()
                            + "' has no URL");
                }
                SVNRevision revision = stealLock ? SVNRevision.UNDEFINED
                        : SVNRevision.create(entry.getRevision());
                entriesMap
                        .put(entry.getURL(), new LockInfo(paths[i], revision));
                wcAccess.getAnchor().getEntries().close();
            } finally {
                wcAccess.close(true);
            }
        }
        for (Iterator urls = entriesMap.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            LockInfo info = (LockInfo) entriesMap.get(url);
            SVNWCAccess wcAccess = createWCAccess(info.myFile);

            SVNRepository repos = createRepository(url);
            SVNLock lock;
            try {
                lock = repos.setLock("", lockMessage, stealLock,
                        info.myRevision.getNumber());
            } catch (SVNException error) {
                handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess
                        .getTargetName(), SVNEventAction.LOCK_FAILED, null,
                        error.getMessage()), ISVNEventHandler.UNKNOWN);
                continue;
            }
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                        wcAccess.getTargetName(), true);
                entry.setLockToken(lock.getID());
                entry.setLockComment(lock.getComment());
                entry.setLockOwner(lock.getOwner());
                entry.setLockCreationDate(SVNTimeUtil.formatDate(lock.getCreationDate()));
                if (wcAccess.getAnchor().getProperties(entry.getName(), false).getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                    SVNFileUtil.setReadonly(wcAccess.getAnchor().getFile(entry.getName()), false);
                }
                wcAccess.getAnchor().getEntries().save(true);
                wcAccess.getAnchor().getEntries().close();
                handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess
                        .getTargetName(), SVNEventAction.LOCKED, lock, null),
                        ISVNEventHandler.UNKNOWN);
            } catch (SVNException e) {
                handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess
                        .getTargetName(), SVNEventAction.LOCK_FAILED, lock, e
                        .getMessage()), ISVNEventHandler.UNKNOWN);
            } finally {
                wcAccess.close(true);
            }
        }
    }

    public void doLock(SVNURL[] urls, boolean stealLock, String lockMessage) throws SVNException {
        for (int i = 0; i < urls.length; i++) {
            SVNURL url = urls[i];
            SVNRepository repos = createRepository(url);
            SVNLock lock = null;
            try {
                lock = repos.setLock("", lockMessage, stealLock, -1);
            } catch (SVNException error) {
                handleEvent(SVNEventFactory.createLockEvent(url.toString(), SVNEventAction.LOCK_FAILED, lock, null), ISVNEventHandler.UNKNOWN);
                continue;
            }
            handleEvent(SVNEventFactory.createLockEvent(url.toString(), SVNEventAction.LOCKED, lock, null), ISVNEventHandler.UNKNOWN);
        }
    }

    public void doUnlock(File[] paths, boolean breakLock) throws SVNException {
        Map entriesMap = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            SVNWCAccess wcAccess = createWCAccess(paths[i]);
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                        wcAccess.getTargetName(), false);
                if (entry == null) {
                    SVNErrorManager.error("svn: '" + paths[i]
                            + "' is not under version control");
                }
                if (entry.getURL() == null) {
                    SVNErrorManager.error("svn: '" + entry.getName()
                            + "' has no URL");
                }
                String lockToken = entry.getLockToken();
                if (!breakLock && lockToken == null) {
                    SVNErrorManager.error("svn: '" + entry.getName()
                            + "' is not locked in this working copy");
                }
                entriesMap.put(entry.getURL(),
                        new LockInfo(paths[i], lockToken));
                wcAccess.getAnchor().getEntries().close();
            } finally {
                wcAccess.close(true);
            }
        }
        for (Iterator urls = entriesMap.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            LockInfo info = (LockInfo) entriesMap.get(url);
            SVNWCAccess wcAccess = createWCAccess(info.myFile);

            SVNRepository repos = createRepository(url);
            SVNLock lock = null;
            boolean removeLock;
            try {
                repos.removeLock("", info.myToken, breakLock);
                removeLock = true;
            } catch (SVNException error) {
                // remove lock if error is owner_mismatch.
                removeLock = true;
            }
            if (!removeLock) {
                handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess
                        .getTargetName(), SVNEventAction.UNLOCK_FAILED, null,
                        "unlock failed"), ISVNEventHandler.UNKNOWN);
                continue;
            }
            try {
                wcAccess.open(true, false);
                SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(
                        wcAccess.getTargetName(), true);
                entry.setLockToken(null);
                entry.setLockComment(null);
                entry.setLockOwner(null);
                entry.setLockCreationDate(null);
                wcAccess.getAnchor().getEntries().save(true);
                wcAccess.getAnchor().getEntries().close();
                if (wcAccess.getAnchor().getProperties(entry.getName(), false).getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
                    SVNFileUtil.setReadonly(wcAccess.getAnchor().getFile(entry.getName()), true);
                }
                handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess
                        .getTargetName(), SVNEventAction.UNLOCKED, lock, null),
                        ISVNEventHandler.UNKNOWN);
            } catch (SVNException e) {
                handleEvent(SVNEventFactory.createLockEvent(wcAccess, wcAccess
                        .getTargetName(), SVNEventAction.UNLOCK_FAILED, lock, e
                        .getMessage()), ISVNEventHandler.UNKNOWN);
            } finally {
                wcAccess.close(true);
            }
        }
    }

    public void doUnlock(SVNURL[] urls, boolean breakLock) throws SVNException {
        Map lockTokens = new HashMap();
        if (!breakLock) {
            for (int i = 0; i < urls.length; i++) {
                SVNURL url = urls[i];
                // get lock token for url
                SVNRepository repos = createRepository(url);
                SVNLock lock = repos.getLock("");
                if (lock == null) {
                    SVNErrorManager.error("svn: '" + url + "' is not locked");
                    return;
                }
                lockTokens.put(url, lock.getID());
            }
        }
        for (int i = 0; i < urls.length; i++) {
            SVNURL url = urls[i];
            // get lock token for url
            SVNRepository repos = createRepository(url);
            String id = (String) lockTokens.get(url);
            try {
                repos.removeLock("", id, breakLock);
            } catch (SVNException e) {
                handleEvent(SVNEventFactory.createLockEvent(url.toString(),
                        SVNEventAction.UNLOCK_FAILED, null, null),
                        ISVNEventHandler.UNKNOWN);
                continue;
            }
            handleEvent(SVNEventFactory.createLockEvent(url.toString(),
                    SVNEventAction.UNLOCKED, null, null),
                    ISVNEventHandler.UNKNOWN);
        }
    }

    public void doInfo(File path, SVNRevision revision, boolean recursive,
            ISVNInfoHandler handler) throws SVNException {
        if (handler == null) {
            return;
        }
        if (!(revision == null || !revision.isValid() || revision == SVNRevision.WORKING)) {
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNRevision wcRevision = null;
            SVNURL url = null;
            try {
                wcAccess.open(false, false);
                SVNEntry entry = wcAccess.getTargetEntry();
                if (entry == null) {
                    SVNErrorManager.error("svn: '" + path + "' is not under version control");
                }
                url = entry.getSVNURL();
                if (url == null) {
                    SVNErrorManager.error("svn: '" + path.getAbsolutePath() + "' has no URL");
                }
                wcRevision = SVNRevision.create(entry.getRevision());
            } finally {
                wcAccess.close(false);
            }
            doInfo(url, wcRevision, revision, recursive, handler);
            return;
        }
        SVNWCAccess wcAccess = createWCAccess(path);
        try {
            wcAccess.open(false, recursive);
            collectInfo(wcAccess.getAnchor(), wcAccess.getTargetName(),
                    recursive, handler);
        } finally {
            wcAccess.close(false);
        }
    }

    public void doInfo(SVNURL url, SVNRevision pegRevision,
            SVNRevision revision, boolean recursive, ISVNInfoHandler handler)
            throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }

        SVNRepository repos = createRepository(url, null, pegRevision, revision);;
        long revNum = getRevisionNumber(revision, repos, null);
        SVNDirEntry rootEntry = repos.info("", revNum);
        if (rootEntry == null || rootEntry.getKind() == SVNNodeKind.NONE) {
            SVNErrorManager.error("'" + url + "' non-existent in revision "
                    + revNum);
        }
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        String reposUUID = repos.getRepositoryUUID();
        // 1. get locks for this dir and below.
        SVNLock[] locks;
        try {
            locks = repos.getLocks("");
        } catch (SVNException e) {
            // may be not supported.
            locks = new SVNLock[0];
        }
        locks = locks == null ? new SVNLock[0] : locks;
        Map locksMap = new HashMap();
        for (int i = 0; i < locks.length; i++) {
            SVNLock lock = locks[i];
            locksMap.put(lock.getPath(), lock);
        }
        String fullPath = url.getPath();
        String rootPath = fullPath.substring(reposRoot.getPath().length());
        if (!rootPath.startsWith("/")) {
            rootPath = "/" + rootPath;
        }
        collectInfo(repos, rootEntry, SVNRevision.create(revNum), rootPath,
                reposRoot, reposUUID, url, locksMap, recursive, handler);
    }

    
    public String doGetWorkingCopyID(final File path, String trailURL) throws SVNException {
        try {
            createWCAccess(path);
        } catch (SVNException e) {
            SVNFileType pathType = SVNFileType.getType(path);
            if (pathType == SVNFileType.DIRECTORY) {
                return "exported";
            } 
            SVNErrorManager.error("svn: '" + path + "' is not versioned and not exported");
        }
        SVNStatusClient statusClient = new SVNStatusClient((ISVNAuthenticationManager) null, getOptions());
        statusClient.setIgnoreExternals(true);
        final long[] maxRevision = new long[1];
        final long[] minRevision = new long[1];
        final boolean[] switched = new boolean[2];
        final String[] wcURL = new String[1];
        statusClient.doStatus(path, true, false, true, false, false, new ISVNStatusHandler() {
            public void handleStatus(SVNStatus status) {
                if (status.getEntryProperties() == null || status.getEntryProperties().isEmpty()) {
                    return;
                }
                if (status.getContentsStatus() != SVNStatusType.STATUS_ADDED) {
                    SVNRevision revision = status.getRevision();
                    if (revision != null) {
                        if (minRevision[0] < 0 || minRevision[0] > revision.getNumber()) {
                            minRevision[0] = revision.getNumber();
                        }
                        maxRevision[0] = Math.max(maxRevision[0], revision.getNumber());
                    }
                }
                switched[0] |= status.isSwitched();
                switched[1] |= status.getContentsStatus() != SVNStatusType.STATUS_NORMAL;
                switched[1] |= status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL &&
                    status.getPropertiesStatus() != SVNStatusType.STATUS_NONE;
                if (wcURL[0] == null && status.getFile() != null && status.getFile().equals(path) && status.getURL() != null) {
                    wcURL[0] = status.getURL().toString();
                }
            }
        });
        if (!switched[0] && trailURL != null) {
            if (wcURL[0] == null) {
                switched[0] = true;
            } else {
                switched[0] = !wcURL[0].endsWith(trailURL);
            }
        }
        StringBuffer id = new StringBuffer();
        id.append(minRevision[0]);
        if (minRevision[0] != maxRevision[0]) {
            id.append(":").append(maxRevision[0]);
        }
        if (switched[1]) {
            id.append("M");
        }
        if (switched[0]) {
            id.append("S");
        }
        return id.toString();
    } 

    public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(path, revision, false, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }

    public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision,
            SVNRevision revision) throws SVNException {
        final SVNInfo[] result = new SVNInfo[1];
        doInfo(url, pegRevision, revision, false, new ISVNInfoHandler() {
            public void handleInfo(SVNInfo info) {
                if (result[0] == null) {
                    result[0] = info;
                }
            }
        });
        return result[0];
    }

    private static void collectInfo(SVNDirectory dir, String name,
            boolean recursive, ISVNInfoHandler handler) throws SVNException {
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry(name, false);
        try {
            if (entry != null) {
                if (entry.isFile()) {
                    // child file
                    File file = dir.getFile(name);
                    handler.handleInfo(SVNInfo.createInfo(file, entry));
                    return;
                } else if (entry.isDirectory() && !"".equals(name)) {
                    // child dir
                    dir = dir.getChildDirectory(name);
                    if (dir != null) {
                        collectInfo(dir, "", recursive, handler);
                    }
                    return;
                } else if ("".equals(name)) {
                    // report root.
                    handler
                            .handleInfo(SVNInfo
                                    .createInfo(dir.getRoot(), entry));
                }

                if (recursive) {
                    for (Iterator ents = entries.entries(true); ents.hasNext();) {
                        SVNEntry childEntry = (SVNEntry) ents.next();
                        if ("".equals(childEntry.getName())) {
                            continue;
                        }
                        if (childEntry.isDirectory()) {
                            SVNDirectory childDir = dir
                                    .getChildDirectory(childEntry.getName());
                            if (childDir != null) {
                                collectInfo(childDir, "", recursive, handler);
                            }
                        } else if (childEntry.isFile()) {
                            handler.handleInfo(SVNInfo.createInfo(dir.getFile(childEntry.getName()), childEntry));
                        }
                    }
                }
            }
        } finally {
            entries.close();
        }

    }

    private static void collectInfo(SVNRepository repos, SVNDirEntry entry,
            SVNRevision rev, String path, SVNURL root, String uuid, SVNURL url,
            Map locks, boolean recursive, ISVNInfoHandler handler)
            throws SVNException {
        String displayPath = repos.getFullPath(path);
        displayPath = displayPath.substring(repos.getLocation().getPath().length());
        if ("".equals(displayPath) || "/".equals(displayPath)) {
            displayPath = path;
        }
        handler.handleInfo(SVNInfo.createInfo(displayPath, root, uuid, url, rev, entry, (SVNLock) locks.get(path)));
        if (entry.getKind() == SVNNodeKind.DIR && recursive) {
            Collection children = repos.getDir(path, rev.getNumber(), null,
                    new ArrayList());
            for (Iterator ents = children.iterator(); ents.hasNext();) {
                SVNDirEntry child = (SVNDirEntry) ents.next();
                SVNURL childURL = url.appendPath(child.getName(), false);
                collectInfo(repos, child, rev, SVNPathUtil.append(path, child
                        .getName()), root, uuid, childURL, locks, recursive,
                        handler);
            }
        }
    }

    private void addDirectory(SVNWCAccess wcAccess, SVNDirectory dir,
            String name, boolean force) throws SVNException {

        if (dir.add(name, false, force) == null) {
            return;
        }

        File file = dir.getFile(name);
        SVNDirectory childDir = dir.getChildDirectory(name);
        if (childDir == null) {
            return;
        }
        File[] children = file.listFiles();
        for (int i = 0; children != null && i < children.length; i++) {
            File childFile = children[i];
            if (getOptions().isIgnored(childFile.getName())) {
                continue;
            }
            if (".svn".equals(childFile.getName())) {
                continue;
            }
            SVNFileType fileType = SVNFileType.getType(childFile);
            if (fileType == SVNFileType.FILE || fileType == SVNFileType.SYMLINK) {
                SVNEntry entry = childDir.getEntries().getEntry(
                        childFile.getName(), true);
                if (force && entry != null && !entry.isScheduledForDeletion()
                        && !entry.isDeleted()) {
                    continue;
                }
                addSingleFile(childDir, childFile.getName());
            } else if (SVNFileType.DIRECTORY == fileType) {
                addDirectory(wcAccess, childDir, childFile.getName(), force);
            }
        }
    }

    private void addSingleFile(SVNDirectory dir, String name) throws SVNException {
        File file = dir.getFile(name);
        dir.add(name, false, false);

        String mimeType;
        SVNProperties properties = dir.getProperties(name, false);
        if (SVNFileUtil.isSymlink(file)) {
            properties.setPropertyValue(SVNProperty.SPECIAL, "*");
        } else {
            Map props = new HashMap();
            boolean executable;
            props = getOptions().applyAutoProperties(name, props);
            mimeType = (String) props.get(SVNProperty.MIME_TYPE);
            if (mimeType == null) {
                mimeType = SVNFileUtil.detectMimeType(file);
                if (mimeType != null) {
                    props.put(SVNProperty.MIME_TYPE, mimeType);
                    props.remove(SVNProperty.EOL_STYLE);
                }
            }
            if (!props.containsKey(SVNProperty.EXECUTABLE)) {
                executable = SVNFileUtil.isExecutable(file);
                if (executable) {
                    props.put(SVNProperty.EXECUTABLE, "*");
                }
            }
            for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) props.get(propName);
                properties.setPropertyValue(propName, propValue);
            }
        }
    }

    private void doGetRemoteProperty(SVNURL url, String path,
            SVNRepository repos, String propName, SVNRevision rev,
            boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        long revNumber = getRevisionNumber(rev, repos, null);
        SVNNodeKind kind = repos.checkPath(path, revNumber);
        Map props = new HashMap();
        if (kind == SVNNodeKind.DIR) {
            Collection children = repos.getDir(path, revNumber, props,
                    recursive ? new ArrayList() : null);
            if (propName != null) {
                String value = (String) props.get(propName);
                if (value != null) {
                    handler.handleProperty(url, new SVNPropertyData(propName, value));
                }
            } else {
                for (Iterator names = props.keySet().iterator(); names.hasNext();) {
                    String name = (String) names.next();
                    if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                            || name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                        continue;
                    }
                    String value = (String) props.get(name);
                    handler.handleProperty(url, new SVNPropertyData(name, value));
                }
            }
            if (recursive) {
                for (Iterator entries = children.iterator(); entries.hasNext();) {
                    SVNDirEntry child = (SVNDirEntry) entries.next();
                    SVNURL childURL = url.appendPath(child.getName(), false);
                    String childPath = "".equals(path) ? child.getName() : SVNPathUtil.append(path, child.getName());
                    doGetRemoteProperty(childURL, childPath, repos, propName, rev, recursive, handler);
                }
            }
        } else if (kind == SVNNodeKind.FILE) {
            repos.getFile(path, revNumber, props, null);
            if (propName != null) {
                String value = (String) props.get(propName);
                if (value != null) {
                    handler.handleProperty(url, new SVNPropertyData(propName, value));
                }
            } else {
                for (Iterator names = props.keySet().iterator(); names
                        .hasNext();) {
                    String name = (String) names.next();
                    if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                            || name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                        continue;
                    }
                    String value = (String) props.get(name);
                    handler.handleProperty(url, new SVNPropertyData(name, value));
                }
            }
        }
    }

    private void doGetLocalProperty(SVNDirectory anchor, String name,
            String propName, SVNRevision rev, boolean recursive,
            ISVNPropertyHandler handler) throws SVNException {
        SVNEntries entries = anchor.getEntries();
        SVNEntry entry = entries.getEntry(name, true);
        if (entry == null
                || (rev == SVNRevision.WORKING && entry
                        .isScheduledForDeletion())) {
            return;
        }
        if (!"".equals(name)) {
            if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory dir = anchor.getChildDirectory(name);
                if (dir != null) {
                    doGetLocalProperty(dir, "", propName, rev, recursive,
                            handler);
                }
            } else if (entry.getKind() == SVNNodeKind.FILE) {
                SVNProperties props = rev == SVNRevision.WORKING ? anchor
                        .getProperties(name, false) : anchor.getBaseProperties(
                        name, false);
                if (propName != null) {
                    String value = props.getPropertyValue(propName);
                    if (value != null) {
                        handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, value));
                    }
                } else {
                    Map propsMap = props.asMap();
                    for (Iterator names = propsMap.keySet().iterator(); names
                            .hasNext();) {
                        String pName = (String) names.next();
                        String value = (String) propsMap.get(pName);
                        handler.handleProperty(anchor.getFile(name), new SVNPropertyData(pName, value));
                    }
                }
            }
            entries.close();
            return;
        }
        SVNProperties props = rev == SVNRevision.WORKING ? anchor
                .getProperties(name, false) : anchor.getBaseProperties(name,
                false);
        if (propName != null) {
            String value = props.getPropertyValue(propName);
            if (value != null) {
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, value));
            }
        } else {
            Map propsMap = props.asMap();
            for (Iterator names = propsMap.keySet().iterator(); names.hasNext();) {
                String pName = (String) names.next();
                String value = (String) propsMap.get(pName);
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(pName, value));
            }
        }
        if (!recursive) {
            return;
        }
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry childEntry = (SVNEntry) ents.next();
            if ("".equals(childEntry.getName())) {
                continue;
            }
            doGetLocalProperty(anchor, childEntry.getName(), propName, rev,
                    recursive, handler);
        }
    }

    private void doSetLocalProperty(SVNDirectory anchor, String name,
            String propName, String propValue, boolean force,
            boolean recursive, ISVNPropertyHandler handler) throws SVNException {
        SVNEntries entries = anchor.getEntries();
        if (!"".equals(name)) {
            SVNEntry entry = entries.getEntry(name, true);
            if (entry == null || (recursive && entry.isDeleted())) {
                return;
            }
            if (entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory dir = anchor.getChildDirectory(name);
                if (dir != null) {
                    doSetLocalProperty(dir, "", propName, propValue, force,
                            recursive, handler);
                }
            } else if (entry.getKind() == SVNNodeKind.FILE) {
                if (SVNProperty.IGNORE.equals(propName)
                        || SVNProperty.EXTERNALS.equals(propName)) {
                    if (!recursive) {
                        SVNErrorManager.error("svn: setting '" + propName
                                + "' property is not supported for files");
                    }
                    return;
                }
                SVNProperties props = anchor.getProperties(name, false);
                File wcFile = anchor.getFile(name);
                if (SVNProperty.EXECUTABLE.equals(propName)) {
                    SVNFileUtil.setExecutable(wcFile, propValue != null);
                }
                if (!force && SVNProperty.EOL_STYLE.equals(propName) && propValue != null) {
                    if (SVNProperty.isBinaryMimeType(props.getPropertyValue(SVNProperty.MIME_TYPE))) {
                        if (!recursive) {
                            SVNErrorManager.error("svn: File '" + wcFile + "' has binary mime type property");
                        }
                        return;
                    }
                    if (!SVNTranslator.checkNewLines(wcFile)) {
                        SVNErrorManager.error("svn: File '" + wcFile + "' has inconsistent newlines");
                    } 
                }
                props.setPropertyValue(propName, propValue);

                if (SVNProperty.EOL_STYLE.equals(propName)
                        || SVNProperty.KEYWORDS.equals(propName)) {
                    entry.setTextTime(null);
                    entries.save(false);
                } else if (SVNProperty.NEEDS_LOCK.equals(propName)
                        && propValue == null) {
                    SVNFileUtil.setReadonly(wcFile, false);
                }
                if (handler != null) {
                    handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, propValue));
                }
            }
            entries.close();
            return;
        }
        SVNProperties props = anchor.getProperties(name, false);
        if (SVNProperty.KEYWORDS.equals(propName)
                || SVNProperty.EOL_STYLE.equals(propName)
                || SVNProperty.MIME_TYPE.equals(propName)
                || SVNProperty.EXECUTABLE.equals(propName)) {
            if (!recursive) {
                SVNErrorManager.error("svn: setting '" + propName
                        + "' property is not supported for directories");
            }
        } else {
            props.setPropertyValue(propName, propValue);
            if (handler != null) {
                handler.handleProperty(anchor.getFile(name), new SVNPropertyData(propName, propValue));
            }
        }
        if (!recursive) {
            return;
        }
        for (Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            doSetLocalProperty(anchor, entry.getName(), propName, propValue,
                    force, recursive, handler);
        }
    }

    private static String validatePropertyName(String name) throws SVNException {
        if (name == null || name.trim().length() == 0) {
            SVNErrorManager.error("svn: Bad property name: '" + name + "'");
            return name;
        }
        name = name.trim();
        if (!(Character.isLetter(name.charAt(0)) || name.charAt(0) == ':' || name
                .charAt(0) == '_')) {
            SVNErrorManager.error("svn: Bad property name: '" + name + "'");
        }
        for (int i = 1; i < name.length(); i++) {
            if (!(Character.isLetterOrDigit(name.charAt(i))
                    || name.charAt(i) == '-' || name.charAt(i) == '.'
                    || name.charAt(i) == ':' || name.charAt(i) == '_')) {
                SVNErrorManager.error("svn: Bad property name: '" + name + "'");
            }
        }
        return name;
    }

    private static String validatePropertyValue(String name, String value, boolean force) throws SVNException {
        if (value == null) {
            return value;
        }
        if (!force && SVNProperty.EOL_STYLE.equals(name)) {
            value = value.trim();
        } else if (!force && SVNProperty.MIME_TYPE.equals(name)) {
            value = value.trim();
        } else if (SVNProperty.IGNORE.equals(name)
                || SVNProperty.EXTERNALS.equals(name)) {
            if (!value.endsWith("\n")) {
                value += "\n";
            }
            if (SVNProperty.EXTERNALS.equals(name)) {
                SVNExternalInfo[] externalInfos = SVNWCAccess.parseExternals(
                        "", value);
                for (int i = 0; externalInfos != null
                        && i < externalInfos.length; i++) {
                    String path = externalInfos[i].getPath();
                    if (path.indexOf(".") >= 0 || path.indexOf("..") >= 0 || path.startsWith("/")) {
                        SVNErrorManager.error("svn: Invalid external definition: " + value);
                    }

                }
            }
        } else if (SVNProperty.KEYWORDS.equals(name)) {
            value = value.trim();
        } else if (SVNProperty.EXECUTABLE.equals(name)
                || SVNProperty.SPECIAL.equals(name)
                || SVNProperty.NEEDS_LOCK.equals(name)) {
            value = "*";
        }
        return value;
    }

    private static class LockInfo {

        public LockInfo(File file, SVNRevision rev) {
            myFile = file;
            myRevision = rev;
        }

        public LockInfo(File file, String token) {
            myFile = file;
            myToken = token;
        }

        private File myFile;
        private SVNRevision myRevision;
        private String myToken;
    }
}
