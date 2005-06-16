/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNFileEntry;
import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.TimeUtil;

/**
 * @author TMate Software Ltd.
 */
public class TestSVNFSEntries extends AbstractRepositoryTest {

    public TestSVNFSEntries(String url, String methodName) {
        super(url, methodName);
    }
    
    public void testCheckout() throws Throwable {
        doCheckout(getRepository());
    }

    public void testSubCheckout() throws Throwable {
        SVNRepository repository = createRepository(SVNRepositoryLocation.parseURL(getRepositoryURL() + "/directory"));
        doCheckout(repository);
    }
    
    public void doCheckout(SVNRepository repository) throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        
        AllTests.runSVNCommand("co", new String[] {repository.getLocation().toString(), dst2.getAbsolutePath()});
        ISVNDirectoryEntry root = createDirEntry(dst.getAbsolutePath(), repository.getLocation().toString());
        repository.checkout(-1, null, true, new SVNCheckoutEditor2(new SVNCommitMediator(root), root));
        assertEquals(dst, dst2);
    }
    
    public void testUpdate() throws Throwable {
        doUpdate(getRepository(), true, "testFile.txt");
    }

    public void testSubUpdate() throws Throwable {
        SVNRepository repository = createRepository(SVNRepositoryLocation.parseURL(getRepositoryURL() + "/directory"));
        doUpdate(repository, false, "testFile2.txt");
    }
    
    public void testImport() throws Throwable {
        // import into "directory"
        SVNRepository repository = createRepository(SVNRepositoryLocation.parseURL(getRepositoryURL() + "/directory"));
        ISVNDirectoryEntry root = createDirEntry(getFixtureRoot().getAbsolutePath(), repository.getLocation().toString());
        // import fixture into "directory".
        doImport(root, repository);
        // checkout to compare import result.
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        // checkout from root repos.
        AllTests.runSVNCommand("co", new String[] {getRepository().getLocation().toString(), dst2.getAbsolutePath()});
        
        root = createDirEntry(dst.getAbsolutePath(), getRepository().getLocation().toString());
        ISVNWorkspaceMediator mediator = new SVNCommitMediator(root);
        ISVNEditor editor = new SVNCheckoutEditor2(mediator, root);
        getRepository().checkout(-2, null, true, editor);

        assertEquals(dst2, dst);
        
    }
    
    public void testAddDeleteCommit() throws Throwable {
        // add (dir, file), delete (dir, file), change (dir props, file)
        // do update.
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        
        AllTests.runSVNCommand("co", new String[] {getRepository().getLocation().toString(), dst2.getAbsolutePath()});
        // same to dst with the help of ISVNEntry
        ISVNDirectoryEntry root = createDirEntry(dst.getAbsolutePath(), getRepository().getLocation().toString()); 
        ISVNWorkspaceMediator mediator = new SVNCommitMediator(root);
        ISVNEditor editor = new SVNCheckoutEditor2(mediator, root);
        getRepository().checkout(-2, null, true, editor);
        // test add.
        File dir = new File(dst, "addedDir");
        dir.mkdirs();
        File dir2 = new File(dir, "subFolder");
        dir2.mkdirs();
        File file = new File(dir, "addedFile.txt");
        OutputStream os = new FileOutputStream(file);
        os.write("test".getBytes());
        os.close();
        root.scheduleForAddition("addedDir", false, true);
        root.save();
        root.dispose();
        dir = new File(dst2, "addedDir");
        dir.mkdirs();
        dir2 = new File(dir, "subFolder");
        dir2.mkdirs();
        file = new File(dir, "addedFile.txt");
        os = new FileOutputStream(file);
        os.write("test".getBytes());
        os.close();

        AllTests.runSVNAnonCommand("add", new String[] {dir.getAbsolutePath()});

        assertEquals(dst, dst2);

        // add one more file to root.
        file = new File(dst, "addedFileToRoot.txt");
        os = new FileOutputStream(file);
        os.write("test".getBytes());
        os.close();
        file = new File(dst2, "addedFileToRoot.txt");
        os = new FileOutputStream(file);
        os.write("test".getBytes());
        os.close();
        
        root.scheduleForAddition("addedFileToRoot.txt", false, true);
        root.save();
        root.dispose();
        AllTests.runSVNAnonCommand("add", new String[] {file.getAbsolutePath()});
        assertEquals(dst, dst2);
 
        // add more to added...
        file = new File(new File(dst, "addedDir"), "addedFileToSubRoot.txt");
        os = new FileOutputStream(file);
        os.write("test".getBytes());
        os.close();
        file = new File(new File(dst2, "addedDir"), "addedFileToSubRoot.txt");
        os = new FileOutputStream(file);
        os.write("test".getBytes());
        os.close();

        ((ISVNDirectoryEntry) root.getChild("addedDir")).scheduleForAddition("addedFileToSubRoot.txt", false, true);
        root.save();
        root.dispose();
        AllTests.runSVNAnonCommand("add", new String[] {file.getAbsolutePath()});
        assertEquals(dst, dst2);
        
        // do delete single file!
        root.scheduleForDeletion("testFile.txt");
        root.save();
        root.dispose();
        file = new File(dst2, "testFile.txt");
        AllTests.runSVNAnonCommand("delete", new String[] {file.getAbsolutePath()});
        assertEquals(dst, dst2);
        
        // do delete dir with a file (int two steps)
        ((ISVNDirectoryEntry) root.getChild("directory")).scheduleForDeletion("testFile2.txt");
        root.scheduleForDeletion("directory");
        root.save();
        root.dispose();
        file = new File(dst2, "directory");
        // in single step
        AllTests.runSVNAnonCommand("delete", new String[] {file.getAbsolutePath()});
        
        assertEquals(dst, dst2);
        
        editor = getRepository().getCommitEditor("commit", mediator);
        doCommit(root, editor);

        dst2 = AllTests.createPlayground();
        dst2.mkdirs();
        AllTests.runSVNCommand("co", new String[] {getRepository().getLocation().toString(), dst2.getAbsolutePath()});
        
        assertEquals(dst, dst2);
    }
    
    private void doCommit(ISVNEntry root, ISVNEditor editor) throws SVNException {
        if (!isModified(root)) {
            return;
        }
        long revision = -1;
        String revStr = root.getPropertyValue("svn:entry:committed-rev");
        // previous revision!
        if (revStr != null) {
            revision = Long.parseLong(revStr);
        }
        root.setPropertyValue("svn:entry:committed-rev", null);
        if (root.isScheduledForDeletion()) {
            editor.deleteEntry(root.getPath(), revision);
        } else if (root.isDirectory()) {
            if (root.isScheduledForAddition()) {
                editor.addDir(root.getPath(), null, -1);
            } else {
                if ("".equals(root.getPath())) {
                    editor.openRoot(revision);
                } else {
                    editor.openDir(root.getPath(), revision);
                }
            }
            // update modified props.
            // recurse
            // drop "schedule" property from entry
            List toDelete = new LinkedList();
            ISVNDirectoryEntry dir = (ISVNDirectoryEntry) root;
            for(Iterator children = dir.childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                doCommit(child, editor);
                // if was scheduled for deletion => simply delete
                if (child.isScheduledForDeletion()) {
                    toDelete.add(child.getName());
                } else {
                    // remove schedule=add
                    dir.unschedule(child.getName());
                }
            }
            for(Iterator deleted = toDelete.iterator(); deleted.hasNext();) {
                dir.deleteChild((String) deleted.next(), false);
            }
            editor.closeDir();
            if ("".equals(root.getPath())) {
                SVNCommitInfo info = editor.closeEdit();
                updateWorkingCopy(root, info, root.getPropertyValue("svn:entry:uuid"));
                root.dispose();
            }
        } else {
            if (root.isScheduledForAddition()) {
                editor.addFile(root.getPath(), null, -1);
            } else {
                editor.openFile(root.getPath(), revision);
            }
            // update modified props.
            // drop "commited-rev" -> will be reset.
            ISVNFileEntry file = (ISVNFileEntry) root;
            file.generateDelta(root.getPath(), editor);
            editor.closeFile(root.getPath(), null);
        }
    }
    
    
    private void updateWorkingCopy(ISVNEntry root, SVNCommitInfo info, String uuid) throws SVNException {
        // update "revision" for all entries.
        String revStr = Long.toString(info.getNewRevision());
        root.setPropertyValue("svn:entry:revision", revStr);
        // update "lastAuthor" along with commited-rev        
        if (root.getPropertyValue("svn:entry:committed-rev") == null) {
            root.setPropertyValue("svn:entry:committed-rev", revStr);
            root.setPropertyValue("svn:entry:last-author", info.getAuthor());
            root.setPropertyValue("svn:entry:committed-date", TimeUtil.formatDate(info.getDate()));
            root.setPropertyValue("svn:entry:uuid", uuid);
        }
        if (root.isDirectory()) {
            ISVNDirectoryEntry dir = (ISVNDirectoryEntry) root;
            for(Iterator children = dir.childEntries(); children.hasNext();) {
                updateWorkingCopy((ISVNEntry) children.next(), info, uuid);
            }
        }
        root.commit();
    }
    
    private void doUpdate(SVNRepository repository, boolean delete, String fileName) throws Throwable {
        File dst = AllTests.createPlayground();
        File dst2 = AllTests.createPlayground();
        
        AllTests.runSVNCommand("co", new String[] {repository.getLocation().toString(), dst2.getAbsolutePath()});
        final ISVNDirectoryEntry root = createDirEntry(dst.getAbsolutePath(), repository.getLocation().toString());
        ISVNWorkspaceMediator mediator = new SVNCommitMediator(root);
        ISVNEditor editor = new SVNCheckoutEditor2(mediator, root);
        // do checkout.
        repository.checkout(-2, null, true, editor);            
        assertEquals(dst2, dst);
        
        ISVNEditor commit = repository.getCommitEditor("test commit", mediator);
        commit.openRoot(1);

        // add file            
        commit.addDir("newDirectory", null, -1);
        commit.addFile("newDirectory/newFile.txt", null, -1);
        commit.applyTextDelta("newDirectory/newFile.txt", null);
        SVNDiffWindow window = SVNDiffWindowBuilder.createReplacementDiffWindow(4);
        OutputStream os = commit.textDeltaChunk("newDirectory/newFile.txt", window);
        os.write("file".getBytes());
        os.close();
        commit.textDeltaEnd("newDirectory/newFile.txt");
        commit.closeFile("newDirectory/newFile.txt", null);
        commit.closeDir();
        // change file
        commit.openFile(fileName, 1);
        commit.applyTextDelta(fileName, null);
        window = SVNDiffWindowBuilder.createReplacementDiffWindow(8);
        os = commit.textDeltaChunk(fileName, window);
        os.write("modified".getBytes());
        os.close();
        commit.textDeltaEnd(fileName);
        commit.closeFile(fileName, null);
        // delete only for root repository!
        if (delete) {
            commit.deleteEntry("directory", 1);
        }             
        
        commit.closeDir(); // ROOT
        commit.closeEdit();
        // do update
        ISVNEntry root2 = createDirEntry(dst.getAbsolutePath(), repository.getLocation().toString());
        editor = new SVNCheckoutEditor2(mediator, root2);
        ISVNReporterBaton reportBaton = new SVNFSReporterBaton(root2);
        AllTests.runSVNCommand("up", new String[] {dst2.getAbsolutePath()});
        repository.update(-2, null, true, reportBaton, editor);            
        assertEquals(dst2, dst);
    }
    
    private void doImport(ISVNDirectoryEntry root, SVNRepository repository) throws Throwable {
        // import fixture into existing repos but under different root?
        ISVNWorkspaceMediator mediator = new SVNCommitMediator(root);
        ISVNEditor editor = repository.getCommitEditor("import", mediator);
        try {
            doImport(editor, root);
            SVNCommitInfo info = editor.closeEdit();
            assertNotNull(info);
            assertEquals(2, info.getNewRevision());
        } catch (Throwable th) {
            th.printStackTrace();
            try {
                editor.abortEdit();
            } catch (Throwable e) {}
            throw th;
        }
    }
    
    private void doImport(ISVNEditor editor, ISVNEntry root) throws SVNException {
        if (root.isDirectory()) {
            if (!"".equals(root.getPath())) {
                editor.addDir(root.getPath(), null, -1);
            } else {
                editor.openRoot(-1);
            }
            ISVNDirectoryEntry dir = (ISVNDirectoryEntry) root;
            for(Iterator children = dir.unmanagedChildEntries(false); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                doImport(editor, child);
            }
            editor.closeDir();
        } else {
            ISVNFileEntry file = (ISVNFileEntry) root;
            editor.addFile(root.getPath(), null, -1);
            file.setPropertyValue("svn:entry:schedule", "add");
            file.generateDelta(root.getPath(), editor);
            editor.closeFile(root.getPath(), null);
                        
        }
    }
    
    private ISVNDirectoryEntry createDirEntry(String path, String url) throws SVNException {
        ISVNDirectoryEntry result = new FSEntryFactory().createEntry(path);
        result.setPropertyValue("svn:entry:url", url);
        return result;
    }
    
    private static boolean isModified(ISVNEntry root) throws SVNException {
        if (root.isScheduledForAddition() || root.isScheduledForDeletion() || 
                root.isPropertiesModified()) {
            return true;
        }
        if (!root.isDirectory() && root.asFile().isContentsModified()) {
            return true;
        } else if (root.isDirectory()) {
            for(Iterator children = root.asDirectory().childEntries(); children.hasNext();) {
                ISVNEntry child = (ISVNEntry) children.next();
                if (isModified(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class SVNFSReporterBaton implements ISVNReporterBaton {
        private final ISVNEntry myRoot;

        private SVNFSReporterBaton(ISVNEntry root) {
            myRoot = root;
        }

        public void report(ISVNReporter reporter) throws SVNException {
            doReport(myRoot, reporter);
            reporter.finishReport();
        }

        private void doReport(ISVNEntry r, ISVNReporter reporter) throws SVNException {
            String revStr = r.getPropertyValue("svn:entry:committed-rev");
            if (revStr != null) {
                long rev = Long.parseLong(revStr);
                reporter.setPath(r.getPath(), null, rev, false);
            }
            if (r.isDirectory()) {
                ISVNDirectoryEntry dir = (ISVNDirectoryEntry) r;
                for(Iterator entries = dir.childEntries(); entries.hasNext();) {
                    doReport((ISVNEntry) entries.next(), reporter);
                }
            }                    
        }
    }
}
 