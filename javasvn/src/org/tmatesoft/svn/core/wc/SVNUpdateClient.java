/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntries;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExportEditor;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class SVNUpdateClient extends SVNBasicClient {

    public SVNUpdateClient() {
    }

    public SVNUpdateClient(ISVNEventListener eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNUpdateClient(ISVNCredentialsProvider credentialsProvider) {
        super(credentialsProvider);
    }

    public SVNUpdateClient(ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, eventDispatcher);
    }

    public SVNUpdateClient(final ISVNCredentialsProvider credentialsProvider, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(credentialsProvider, options, eventDispatcher);
    }

    public SVNUpdateClient(ISVNRepositoryFactory repositoryFactory, SVNOptions options, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }


    public long doUpdate(File file, SVNRevision revision, boolean recursive) throws SVNException {        
        long revNumber = getRevisionNumber(file, revision);
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, null, recursive);
            SVNRepository repos = createRepository(wcAccess.getAnchor().getEntries().getEntry("", true).getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            DebugLog.log("calling repos update");
            repos.update(revNumber, target, recursive, reporter, editor);
            DebugLog.log("completed");

            if (editor.getTargetRevision() >= 0) {
            	if (recursive && !isIgnoreExternals()) {
            		handleExternals(wcAccess);
            	}
                DebugLog.log("dispatching completed event");
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

    public long doSwitch(File file, String url, SVNRevision revision, boolean recursive) throws SVNException {
        url = validateURL(url);
        long revNumber = getRevisionNumber(file, revision);
        SVNWCAccess wcAccess = createWCAccess(file);
        final SVNReporter reporter = new SVNReporter(wcAccess, recursive);
        try {
            wcAccess.open(true, recursive);
            SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess, url, recursive);
            SVNRepository repos = createRepository(wcAccess.getAnchor().getEntries().getEntry("", true).getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            repos.update(url, revNumber, target, recursive, reporter, editor);
            
            if (editor.getTargetRevision() >= 0 && recursive && !isIgnoreExternals()) {
                handleExternals(wcAccess);
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
    
    public long doCheckout(String url, File dstPath, SVNRevision pegRevision, SVNRevision revision, boolean recursive) throws SVNException {
        url = validateURL(url);
        if (dstPath == null) {
            dstPath = new File(".", PathUtil.tail(url));
        }
        if (!revision.isValid() && !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
            revision = SVNRevision.HEAD;
        } else if (!revision.isValid()) {
            revision = pegRevision;
        } else if (!pegRevision.isValid()) {
            pegRevision = revision;
        }
        url = getURL(url, pegRevision, revision);
        SVNRepository repos = createRepository(url);
        long revNumber = getRevisionNumber(url, revision);
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
            if (!dstPath.exists() || (dstPath.isDirectory() && !SVNWCAccess.isVersionedDirectory(dstPath))) {
                createVersionedDirectory(dstPath, url, uuid, revNumber);
                result = doUpdate(dstPath, revision, recursive);
            } else if (dstPath.isDirectory() && SVNWCAccess.isVersionedDirectory(dstPath)) {
                SVNWCAccess wcAccess = SVNWCAccess.create(dstPath);
                if (url.equals(wcAccess.getTargetEntryProperty(SVNProperty.URL))) {
                    result = doUpdate(dstPath, revision, recursive);
                } else {
                    SVNErrorManager.error("svn: working copy with different URL '" + wcAccess.getTargetEntryProperty(SVNProperty.URL) + "' already exists at checkout destination");
                }
            } else {
                SVNErrorManager.error(0, null);
            }
        } finally {
            if (!isCommandRunning()) {
                SVNFileUtil.sleepForTimestamp();
            }
            setDoNotSleepForTimeStamp(false);
        }
        return result;
    }
    
    public long doExport(String url, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        url = validateURL(url);
        if (dstPath == null) {
            dstPath = new File(".", PathUtil.tail(url));
        }
        if (!revision.isValid() && !pegRevision.isValid()) {
            pegRevision = SVNRevision.HEAD;
            revision = SVNRevision.HEAD;
        } else if (!revision.isValid()) {
            revision = pegRevision;
        } else if (!pegRevision.isValid()) {
            pegRevision = revision;
        }
        url = getURL(url, pegRevision, revision);
        SVNRepository repos = createRepository(url);
        final long revNumber = getRevisionNumber(url, revision);
        SVNNodeKind targetNodeKind = repos.checkPath("", revNumber);
        
        if (targetNodeKind == SVNNodeKind.FILE) {
            if (dstPath.isDirectory()) {
                dstPath = new File(dstPath, PathUtil.decode(PathUtil.tail(url)));                
            }
            if (dstPath.exists()) {
                if (!force) {
                    SVNErrorManager.error(0, null);
                }
            } else {
                dstPath.getParentFile().mkdirs();
            }
            Map properties = new HashMap();
            OutputStream os = null;
            File tmpFile = SVNFileUtil.createUniqueFile(dstPath.getParentFile(), dstPath.getName(), ".tmp");
            try {
                os = new FileOutputStream(tmpFile);
                repos.getFile("", revNumber, properties, os);
                os.close();
                os = null;
                if (force && dstPath.exists()) { 
                    SVNFileUtil.deleteAll(dstPath);
                }
                Map keywords = SVNTranslator.computeKeywords((String) properties.get(SVNProperty.KEYWORDS),
                        url, 
                        (String) properties.get(SVNProperty.LAST_AUTHOR),
                        (String) properties.get(SVNProperty.COMMITTED_DATE),
                        (String) properties.get(SVNProperty.COMMITTED_REVISION));
                if (eolStyle == null) {
                    eolStyle = (String) properties.get(SVNProperty.EOL_STYLE);
                }
                byte[] eols = SVNTranslator.getWorkingEOL(eolStyle);
                SVNTranslator.translate(tmpFile, dstPath, eols, keywords, properties.get(SVNProperty.SPECIAL) != null, true);
                if (properties.get(SVNProperty.EXECUTABLE) != null) {
                    SVNFileUtil.setExecutable(dstPath, true);
                }
                dispatchEvent(SVNEventFactory.createExportAddedEvent(dstPath.getParentFile(), dstPath));
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
        } else if (targetNodeKind == SVNNodeKind.DIR) {
            SVNExportEditor editor = new SVNExportEditor(this, url, dstPath, force, eolStyle);
            repos.update(revNumber, null, recursive, new ISVNReporterBaton() {
                public void report(ISVNReporter reporter) throws SVNException {
                    reporter.setPath("", null, revNumber, true);
                    reporter.finishReport();
                }
            }, editor);
            if (!isIgnoreExternals() && recursive) {
                Map externals = editor.getCollectedExternals();
                for(Iterator files = externals.keySet().iterator(); files.hasNext();) {
                    File rootFile = (File) files.next();
                    String propValue = (String) externals.get(rootFile);
                    if (propValue == null) {
                        continue;
                    }
                    SVNExternalInfo[] infos = SVNWCAccess.parseExternals("", propValue);
                    for (int i = 0; i < infos.length; i++) {
                        File targetDir = new File(rootFile, infos[i].getPath());
                        String srcURL = infos[i].getOldURL();
                        SVNRevision srcRevision = SVNRevision.create(infos[i].getOldRevision());
                        String relativePath = targetDir.getAbsolutePath().substring(dstPath.getAbsolutePath().length());
                        relativePath = relativePath.replace(File.separatorChar, '/');
                        relativePath = PathUtil.removeLeadingSlash(relativePath);
                        relativePath = PathUtil.removeTrailingSlash(relativePath);
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
        }
        dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, revNumber));
        
        return revNumber;
    }

    public long doExport(File srcPath, final File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, final boolean force, boolean recursive) throws SVNException {
        if (!SVNWCAccess.isVersionedDirectory(srcPath)) {
            SVNErrorManager.error(0, null);
        }
        DebugLog.log("exporting at revision: " + revision);
        SVNWCAccess wcAccess = createWCAccess(srcPath);
        String url = wcAccess.getTargetEntryProperty(SVNProperty.URL);
        if (revision == null || revision == SVNRevision.UNDEFINED) {
            revision = SVNRevision.WORKING;
        }
        if (revision != SVNRevision.BASE && revision != SVNRevision.WORKING) {
            // get rev number from wc.
            long revNumber = getRevisionNumber(srcPath, revision);
            revision = SVNRevision.create(revNumber);
            return doExport(url, dstPath, pegRevision, revision, eolStyle, force, recursive);
        } else {
            if (!force && dstPath.exists()) {
                SVNErrorManager.error(0, null);
            } 
            if (dstPath.exists()) {
                SVNFileUtil.deleteAll(dstPath);
            }
            try {
                wcAccess.open(true, recursive);
                if (srcPath.isFile()) {
                    SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry(dstPath.getName(), true);
                    if (entry == null || (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) || 
                            (entry.isScheduledForAddition() && revision == SVNRevision.BASE)) {
                        return -1;
                    }
                    copyVersionedFile(dstPath, wcAccess.getAnchor(), dstPath.getName(), revision, force, eolStyle);
                } else {
                    SVNEntry entry = wcAccess.getAnchor().getEntries().getEntry("", true);
                    if (entry == null || (revision == SVNRevision.WORKING && entry.isScheduledForDeletion()) || (entry.isScheduledForAddition() && revision == SVNRevision.BASE)) {
                        return -1;
                    }
                    copyVersionedDir(dstPath, wcAccess.getAnchor(), recursive, revision, force, eolStyle);
                }
                dispatchEvent(SVNEventFactory.createUpdateCompletedEvent(null, -1));
            } finally {
                wcAccess.close(true);
            }
        }
        return -1;
    }
    
    public void doRelocate(File dst, String oldURL, String newURL, boolean recursive) throws SVNException {
        oldURL = validateURL(oldURL);
        newURL = validateURL(newURL);
        SVNRepository repos = createRepository(newURL);
        repos.testConnection();
        String uuid = repos.getRepositoryUUID();
        SVNWCAccess wcAccess = createWCAccess(dst);
        try {
            wcAccess.open(true, recursive);
            String oldUUID = wcAccess.getTargetEntryProperty(SVNProperty.UUID);
            if (!oldUUID.equals(uuid)) {
                SVNErrorManager.error("The repository at '" + newURL + "' has uuid '" + uuid + "', but the WC has '" + oldUUID + "'");
            }
            doRelocate(wcAccess.getAnchor(), wcAccess.getTargetName(), oldURL, newURL, recursive);
        } finally {
            wcAccess.close(true);
            
        }
    }
    
    private void doRelocate(SVNDirectory dir, String targetName, String oldURL, String newURL, boolean recursive) throws SVNException {
        SVNEntries entries = dir.getEntries();
        for(Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (targetName != null && !"".equals(targetName)) {
                if (!targetName.equals(entry.getName())) {
                    continue;
                }
            }
            String copyFromURL = entry.getCopyFromURL();
            if (copyFromURL != null && copyFromURL.startsWith(oldURL)) {
                copyFromURL = copyFromURL.substring(oldURL.length());
                copyFromURL = PathUtil.append(newURL, copyFromURL);
                copyFromURL = validateURL(copyFromURL);
                entry.setCopyFromURL(copyFromURL);
            }
            if (recursive && entry.isDirectory() && !"".equals(entry.getName())) {
                if (dir.getChildDirectory(entry.getName()) != null) {
                    doRelocate(dir.getChildDirectory(entry.getName()), null, oldURL, newURL, recursive);
                }
            } else if (entry.isFile() || "".equals(entry.getName())) {                
                String url = entry.getURL();
                if (url.startsWith(oldURL)) {
                    url = url.substring(oldURL.length());
                    url = PathUtil.append(newURL, url);
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
            for(Iterator externals = wcAccess.externals(); externals.hasNext();) {
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
                        doCheckout(external.getNewURL(), external.getFile(), revision,
                                revision, true);
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
                        doCheckout(external.getNewURL(), external.getFile(), revision,
                                revision, true);
                    } else {
                        if (!external.getFile().isDirectory()) {
                            external.getFile().mkdirs();
                            doCheckout(external.getNewURL(), external.getFile(), revision,
                                    revision, true);
                        } else {
                            String url = null;
                            if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                                SVNWCAccess externalAccess = createWCAccess(external.getFile());
                                url = externalAccess.getTargetEntryProperty(SVNProperty.URL);
                            }
                            if (!external.getNewURL().equals(url)) {
                                deleteExternal(external);
                            }
                            // update or checkout.
                            external.getFile().mkdirs();
                            dispatchEvent(SVNEventFactory.createUpdateExternalEvent(wcAccess, ""));
                            doCheckout(external.getNewURL(), external.getFile(), revision,
                                    revision, true);
                        }
                    }
                } catch (Throwable th) {
                    dispatchEvent(new SVNEvent(th.getMessage()));
                    DebugLog.error(th);
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
                DebugLog.error(th);
            } finally {
                externalAccess.close(true);
            }
        }
        if (external.getFile().exists()) {
            external.getFile().getParentFile().mkdirs();
            File newLocation = SVNFileUtil.createUniqueFile(external.getFile().getParentFile(), external.getFile().getName(), ".OLD");
            try {
                SVNFileUtil.rename(external.getFile(), newLocation);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
    }

    private void copyVersionedDir(File dstPath, SVNDirectory dir, boolean recursive, SVNRevision revision, boolean force, String eol) throws SVNException {
        if (!force && dstPath.exists()) {
            SVNErrorManager.error(0, null);
        }
        SVNFileUtil.deleteAll(dstPath);
        dstPath.mkdirs();
        SVNEntries entries = dir.getEntries();
        for(Iterator ents = entries.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            if (revision != SVNRevision.WORKING && entry.isScheduledForAddition()) {
                continue;
            }
            if (revision != SVNRevision.BASE && entry.isScheduledForDeletion()) {
                continue;
            }
            if (entry.isFile()) {
                copyVersionedFile(new File(dstPath, entry.getName()), dir, entry.getName(), revision, force, eol);                
            } else if (recursive && entry.isDirectory() && dir.getFile(entry.getName(), false).isDirectory()) {
                SVNDirectory childDir = dir.getChildDirectory(entry.getName());
                if (childDir != null) {
                    copyVersionedDir(new File(dstPath, entry.getName()), childDir, recursive, revision, force, eol);
                }
            }
        }
    }
    
    private void copyVersionedFile(File dstPath, SVNDirectory dir, String fileName, SVNRevision revision, boolean force, String eol) throws SVNException {
        
        if (!force && dstPath.exists()) {
            SVNErrorManager.error(0, null);
        }
        SVNFileUtil.deleteAll(dstPath);
        Map keywordsMap = null;
        SVNProperties props = revision == SVNRevision.BASE ? dir.getBaseProperties(fileName, false) : dir.getProperties(fileName, false);
        SVNEntry entry = dir.getEntries().getEntry(fileName, true);
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        String date = entry.getCommittedDate();
        byte[] eols = eol != null ? SVNTranslator.getEOL(eol) : null;
        if (keywords != null) {
            String author = entry.getAuthor();
            String rev = Long.toString(entry.getCommittedRevision());
            if (revision != SVNRevision.BASE) {
                boolean modified = dir.hasTextModifications(fileName, false);
                if (modified) {
                    author = "(local)";
                    rev += "M";
                }
            }
            keywordsMap = SVNTranslator.computeKeywords(keywords, entry.getURL(), author, entry.getCommittedDate(), rev);
        }
        if (eols == null) {
            eol = props.getPropertyValue(SVNProperty.EOL_STYLE); 
            eols =  SVNTranslator.getWorkingEOL(eol);
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
            
        File srcFile = revision == SVNRevision.BASE ? 
                dir.getBaseFile(fileName, false) : dir.getFile(fileName, false);
        if (!srcFile.isFile()) {
            SVNErrorManager.error(0, null);
        }
        SVNTranslator.translate(srcFile, dstPath, eols, keywordsMap, special, true);
        if (executable) {
            SVNFileUtil.setExecutable(dstPath, true);
        }
        if (!special && date != null) {
            dstPath.setLastModified(TimeUtil.parseDate(date).getTime());
        }
    }
}
 