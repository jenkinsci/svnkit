/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;


public class SVNUpdateClient extends SVNBasicClient {
    
    private boolean myIsDoNotSleepForTimeStamp;

    public SVNUpdateClient(final ISVNCredentialsProvider credentialsProvider, ISVNEventListener eventDispatcher) {
        super(new ISVNRepositoryFactory() {
            public SVNRepository createRepository(String url) throws SVNException {
                SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
                repos.setCredentialsProvider(credentialsProvider);
                return repos;
            }
        }, null, eventDispatcher);
    }

    public SVNUpdateClient(ISVNRepositoryFactory repositoryFactory, ISVNEventListener eventDispatcher) {
        super(repositoryFactory, null, eventDispatcher);
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
            SVNRepository repos = createRepository(wcAccess.getAnchor().getEntries().getEntry("").getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            repos.update(revNumber, target, recursive, reporter, editor);

            if (editor.getTargetRevision() >= 0) {
                handleExternals(wcAccess);
                dispatchEvent(SVNEvent.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {            
            wcAccess.close(true, recursive);
            if (!myIsDoNotSleepForTimeStamp) {
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
            SVNRepository repos = createRepository(wcAccess.getTargetEntryProperty(SVNProperty.URL));
            DebugLog.log("wc: " + wcAccess);
            DebugLog.log("url: " + wcAccess.getAnchor().getEntries().getEntry("").getURL());
            repos = createRepository(wcAccess.getAnchor().getEntries().getEntry("").getURL());
            String target = "".equals(wcAccess.getTargetName()) ? null : wcAccess.getTargetName();
            repos.update(url, revNumber, target, recursive, reporter, editor);
            
            if (editor.getTargetRevision() >= 0) {
                handleExternals(wcAccess);
                dispatchEvent(SVNEvent.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
            }
            return editor.getTargetRevision();
        } finally {
            wcAccess.close(true, recursive);
            if (!myIsDoNotSleepForTimeStamp) {
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
        myIsDoNotSleepForTimeStamp = true;
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
                    SVNErrorManager.error(0, null);
                }
            } else {
                SVNErrorManager.error(0, null);
            }
        } finally {
            SVNFileUtil.sleepForTimestamp();
            myIsDoNotSleepForTimeStamp = false;
        }
        return result;
    }
    
    public void doCopy(String srcURL, File dstPath, SVNRevision revision) throws SVNException {
        srcURL = validateURL(srcURL);
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
        
        boolean sameRepositories = false;
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
                myIsDoNotSleepForTimeStamp = true;
                try {
                    SVNReporter reporter = new SVNReporter(wcAccess2, true);
                    SVNUpdateEditor editor = new SVNUpdateEditor(wcAccess2, null, true);
                    
                    repos.update(revNumber, null, true, reporter, editor);
                    dispatchEvent(SVNEvent.createUpdateCompletedEvent(wcAccess, editor.getTargetRevision()));
                    if (sameRepositories) {
                        addDir(wcAccess.getAnchor(), dstPath.getName(), srcURL, editor.getTargetRevision());
                        addDir(wcAccess2.getAnchor(), "", srcURL, editor.getTargetRevision());
                        // fire added event.
                        dispatchEvent(SVNEvent.createAddedEvent(wcAccess, wcAccess.getAnchor(), 
                                wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
                    } else {
                        SVNErrorManager.error(0, null);
                    }
                } finally {
                    myIsDoNotSleepForTimeStamp = false;
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
                    long fileRevision = repos.getFile("", revNumber, properties, os);
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
                        }
                    }
                    if (tmpFile != null) {
                        tmpFile.delete();
                    }
                }
                addFile(wcAccess.getAnchor(), dstPath.getName(), properties, sameRepositories ? srcURL : null, revNumber);
                wcAccess.getAnchor().runLogs();
                // fire added event.
                dispatchEvent(SVNEvent.createAddedEvent(wcAccess, wcAccess.getAnchor(), 
                        wcAccess.getAnchor().getEntries().getEntry(dstPath.getName())));
            }
        } finally {
            if (wcAccess != null) {
                wcAccess.close(true, false);
            }
            SVNFileUtil.sleepForTimestamp();
        }
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
        long revNumber = getRevisionNumber(url, revision);
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
                // 
                Map keywords = SVNTranslator.computeKeywords((String) properties.get(SVNProperty.KEYWORDS),
                        url, 
                        (String) properties.get(SVNProperty.LAST_AUTHOR),
                        (String) properties.get(SVNProperty.COMMITTED_DATE),
                        Long.parseLong((String) properties.get(SVNProperty.COMMITTED_REVISION)));
                if (eolStyle == null) {
                    eolStyle = (String) properties.get(SVNProperty.EOL_STYLE);
                }
                byte[] eols = eolStyle == null ? null : SVNTranslator.getEOL(eolStyle);
                SVNTranslator.translate(tmpFile, dstPath, eols, keywords, properties.get(SVNProperty.SPECIAL) != null, true);
                if (properties.get(SVNProperty.EXECUTABLE) != null) {
                    SVNFileUtil.setExecutable(dstPath, true);
                }
//                dispatchEvent(SVNEvent.createAddedEvent(dstPath));
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException e) {
                    }
                }
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        } else if (targetNodeKind == SVNNodeKind.DIR) {
            
        }
        
        return -1;
    }

    public long doExport(File srcPath, File dstPath, SVNRevision pegRevision, SVNRevision revision, String eolStyle, boolean force, boolean recursive) throws SVNException {
        // get url from wc, use passed revision to make a checkout or 
        // just make a copy of wc working files if revision is not valid. 
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
            wcAccess.close(true, recursive);
            
        }
    }
    
    private void doRelocate(SVNDirectory dir, String targetName, String oldURL, String newURL, boolean recursive) throws SVNException {
        SVNEntries entries = dir.getEntries();
        for(Iterator ents = entries.entries(); ents.hasNext();) {
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
    

    private SVNDirectory createVersionedDirectory(File dstPath, String url, String uuid, long revNumber) throws SVNException {
        SVNDirectory.createVersionedDirectory(dstPath);
        // add entry first.
        SVNDirectory dir = new SVNDirectory(null, "", dstPath);
        SVNEntries entries = dir.getEntries();
        SVNEntry entry = entries.getEntry("");
        if (entry == null) {
            entry = entries.addEntry("");
        }
        entry.setURL(url);
        entry.setUUID(uuid);
        entry.setKind(SVNNodeKind.DIR);
        entry.setRevision(revNumber);
        entry.setIncomplete(true);;
        entries.save(true);
        return dir;
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
        dir.mergeProperties(fileName, regularProps, null, log);

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
    
    private void handleExternals(SVNWCAccess wcAccess) throws SVNException {
        myIsDoNotSleepForTimeStamp = true;
    
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
                        dispatchEvent(SVNEvent.createUpdateExternalEvent(wcAccess, ""));
                        doCheckout(external.getNewURL(), external.getFile(), revision,
                                revision, true);
                    } else if (external.getNewURL() == null) {
                        if (SVNWCAccess.isVersionedDirectory(external.getFile())) {
                            SVNWCAccess externalAccess = createWCAccess(external.getFile());
                            externalAccess.open(true, true);
                            externalAccess.getAnchor().destroy("", true);
                            externalAccess.close(true, true);
                        }                    
                    } else if (external.isModified()) {
                        deleteExternal(external);
                        external.getFile().mkdirs();
                        dispatchEvent(SVNEvent.createUpdateExternalEvent(wcAccess, ""));
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
                            dispatchEvent(SVNEvent.createUpdateExternalEvent(wcAccess, ""));
                            doCheckout(external.getNewURL(), external.getFile(), revision,
                                    revision, true);
                        }
                    }
                } finally {
                    setEventPathPrefix(null);
                }
            }
        } finally {
            setEventPathPrefix(null);
            myIsDoNotSleepForTimeStamp = false;
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
                externalAccess.close(true, true);
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
}
 