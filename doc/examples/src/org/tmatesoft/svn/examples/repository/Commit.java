/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.examples.repository;

import java.io.ByteArrayInputStream;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/*
 * This is an example of how to commit several types of changes to a repository: 
 *  - a new directory with a file, 
 *  - modification to anexisting file, 
 *  - copying a directory into a branch, 
 *  - deletion of the directory and its entries.
 * 
 * Main aspects of performing a commit with the help of ISVNEditor:
 * 0)initialize the library (this is done in setupLibrary() method);
 * 
 * 1)create an SVNRepository driver for a particular  repository  location, that 
 * will be the root directory for committing - that is all paths that are  being  
 * committed will be below that root;
 * 
 * 2)provide user's authentication information - name/password, since committing 
 * generally requires authentication;
 * 
 * 3)"ask" your SVNRepository for a commit editor:
 *     
 *     ISVNCommitEditor editor = SVNRepository.getCommitEditor();
 * 
 * 4)"edit"  a repository - perform a sequence of edit calls (for
 * example, here you "say" to the server that you have added such-and-such a new
 * directory at such-and-such a path as well as a new file). First of all,  
 * ISVNEditor.openRoot()  is called to 'open' the root directory;
 * 
 * 5)at last you close the editor with the  ISVNEditor.closeEdit()  method  that
 * fixes your modificaions in the repository finalizing the commit.
 * 
 * For  each  commit  a  new  ISVNEditor is required - that is after having been 
 * closed the editor can no longer be used!
 * 
 * This example can be run for a locally installed Subversion repository via the
 * svn:// protocol. This is how you can do it:
 * 
 * 1)after   you   install  the   Subversion  pack (available  for  download  at 
 * http://subversion.tigris.org)  you  should  create  a  new  repository in  a 
 * directory, like this (in a command line under a Windows OS):
 * 
 * >svnadmin create X:\path\to\rep
 * 
 * 2)after the repository is created you can add a new account: navigate to your
 * repository root (X:\path\to\rep\),  then  move  to  \conf  and  open the file 
 * 'passwd'. In the file you'll see the section [users]. Uncomment it and add  a 
 * new account below the section name, like:
 * 
 * [users] 
 * userName = userPassword.
 * 
 * In the program you may further use this account as user's credentials.
 * 
 * 3)the next step is to launch the custom Subversion  server  (svnserve)  in  a
 * background mode for the just created repository:
 * 
 * >svnserve -d -r X:\path\to
 * 
 * That's all. The repository is now available via svn://localhost/rep.
 * 
 */
public class Commit {

    public static void main(String[] args) {
        /*
         * Initialize the library. It must be done before calling any 
         * method of the library.
         */
        setupLibrary();
        
        /*
         * Run commit example and process error if any.
         */
        try {
            commitExample();
        } catch (SVNException e) {
            SVNErrorMessage err = e.getErrorMessage();
            /*
             * Display all tree of error messages. 
             * Utility method SVNErrorMessage.getFullMessage() may be used instead of the loop.
             */
            while(err != null) {
                System.err.println(err.getErrorCode().getCode() + " : " + err.getMessage());
                err = err.getChildErrorMessage();
            }
            System.exit(1);
        }
        System.exit(0);
    }

    private static void commitExample() throws SVNException {
        /*
         * URL that points to repository. 
         */
        SVNURL url = SVNURL.parseURIEncoded("svn://localhost/rep");
        /*
         * Credentials to use for authentication.
         */
        String userName = "foo";
        String userPassword = "bar";
        
        /*
         * Sample file contents.
         */
        byte[] contents = "This is a new file".getBytes();
        byte[] modifiedContents = "This is the same file but modified a little.".getBytes();

        /*
         * Create an instance of SVNRepository class. This class is the main entry point 
         * for all "low-level" Subversion operations supported by Subversion protocol. 
         * 
         * These operations includes browsing, update and commit operations. See 
         * SVNRepository methods javadoc for more details.
         */
        SVNRepository repository = SVNRepositoryFactory.create(url);

        /*
         * User's authentication information (name/password) is provided via  an 
         * ISVNAuthenticationManager  instance.  SVNWCUtil  creates  a   default 
         * authentication manager given user's name and password.
         * 
         * Default authentication manager first attempts to use provided user name 
         * and password and then falls back to the credentials stored in the 
         * default Subversion credentials storage that is located in Subversion 
         * configuration area. If you'd like to use provided user name and password 
         * only you may use BasicAuthenticationManager class instead of default 
         * authentication manager:
         * 
         *  authManager = new BasicAuthenticationsManager(userName, userPassword);
         *  
         * You may also skip this point - anonymous access will be used. 
         */
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(userName, userPassword);
        repository.setAuthenticationManager(authManager);

        /*
         * Get type of the node located at URL we used to create SVNRepository.
         * 
         * "" (empty string) is path relative to that URL, 
         * -1 is value that may be used to specify HEAD (latest) revision.
         */
        SVNNodeKind nodeKind = repository.checkPath("", -1);

        /*
         * Checks  up  if the current path really corresponds to a directory. If 
         * it doesn't, the program exits. SVNNodeKind is that one who says  what
         * is located at a path in a revision. 
         */
        if (nodeKind == SVNNodeKind.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "No entry at URL ''{0}''", url);
            throw new SVNException(err);
        } else if (nodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry at URL ''{0}'' is a file while directory was expected", url);
            throw new SVNException(err);
        }
        
        /*
         * Get exact value of the latest (HEAD) revision.
         */
        long latestRevision = repository.getLatestRevision();
        System.out.println("Repository latest revision (before committing): " + latestRevision);
        
        /*
         * Gets an editor for committing the changes to  the  repository.  NOTE:
         * you  must not invoke methods of the SVNRepository until you close the
         * editor with the ISVNEditor.closeEdit() method.
         * 
         * commitMessage will be applied as a log message of the commit.
         * 
         * ISVNWorkspaceMediator instance will be used to store temporary files, 
         * when 'null' is passed, then default system temporary directory will be used to
         * create temporary files.  
         */
        ISVNEditor editor = repository.getCommitEditor("directory and file added", null);

        /*
         * Add a directory and a file within that directory.
         * 
         * SVNCommitInfo object contains basic information on the committed revision, i.e.
         * revision number, author name, commit date and commit message. 
         */
        SVNCommitInfo commitInfo = addDir(editor, "test", "test/file.txt", contents);
        System.out.println("The directory was added: " + commitInfo);

        /*
         * Modify file added at the previous revision.
         */
        editor = repository.getCommitEditor("file contents changed", null);
        commitInfo = modifyFile(editor, "test", "test/file.txt", contents, modifiedContents);
        System.out.println("The file was changed: " + commitInfo);

        /*
         * Copy recently added directory to another location. This operation
         * will create new directory "test2" taking its properties and contents 
         * from the directory "test". Revisions history will be preserved.
         * 
         * Copy is usually used to create tags or branches in repository or to 
         * rename files or directories.
         */
        
        /*
         * To make a copy of a repository entry, absolute path of that entry
         * and revision from which to make a copy are needed. 
         * 
         * Absolute path is the path relative to repository root URL and 
         * such paths always start with '/'. Relative paths are relative 
         * to SVNRepository instance location.
         * 
         * For more information see SVNRepository.getRepositoryRoot(...) and
         * SVNRepository.getLocation() methods. Utility method getRepositoryPath(...)
         * converts relative path to the absolute one.
         */
        String absoluteSrcPath = repository.getRepositoryPath("test");
        long srcRevision = repository.getLatestRevision();

        editor = repository.getCommitEditor("directory copied", null);        
        
        commitInfo = copyDir(editor, absoluteSrcPath, "test2", srcRevision);
        System.out.println("The directory was copied: " + commitInfo);

        /*
         * Delete directory "test".
         */
        editor = repository.getCommitEditor("directory deleted", null);
        commitInfo = deleteDir(editor, "test");
        System.out.println("The directory was deleted: " + commitInfo);

        /*
         * And directory "test2".
         */
        editor = repository.getCommitEditor("copied directory deleted", null);
        commitInfo = deleteDir(editor, "test2");
        System.out.println("The copied directory was deleted: " + commitInfo);
        
        latestRevision = repository.getLatestRevision();
        System.out.println("Repository latest revision (after committing): " + latestRevision);
    }

    /*
     * This method performs commiting an addition of a  directory  containing  a
     * file.
     */
    private static SVNCommitInfo addDir(ISVNEditor editor, String dirPath,
            String filePath, byte[] data) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         * 
         * -1 - revision is HEAD (actually, for a comit  editor  this number  is 
         * irrelevant)
         */
        editor.openRoot(-1);
        /*
         * Adds a new directory (in this  case - to the  root  directory  for 
         * which the SVNRepository was  created). 
         * Since this moment all changes will be applied to this new  directory.
         * 
         * dirPath is relative to the root directory.
         * 
         * copyFromPath (the 2nd parameter) is set to null and  copyFromRevision
         * (the 3rd) parameter is set to  -1  since  the  directory is not added 
         * with history (is not copied, in other words).
         */
        editor.addDir(dirPath, null, -1);
        /*
         * Adds a new file to the just added  directory. The  file  path is also 
         * defined as relative to the root directory.
         *
         * copyFromPath (the 2nd parameter) is set to null and  copyFromRevision
         * (the 3rd parameter) is set to -1 since  the file is  not  added  with 
         * history.
         */
        editor.addFile(filePath, null, -1);
        /*
         * The next steps are directed to applying delta to the  file  (that  is 
         * the full contents of the file in this case).
         */
        editor.applyTextDelta(filePath, null);
        /*
         * Use delta generator utility class to generate and send delta
         * 
         * Note that you may use only 'target' data to generate delta when there is no 
         * access to the 'base' (previous) version of the file. However, using 'base' 
         * data will result in smaller network overhead.
         * 
         * SVNDeltaGenerator will call editor.textDeltaChunk(...) method for each generated 
         * "diff window" and then editor.textDeltaEnd(...) in the end of delta transmission.  
         * Number of diff windows depends on the file size. 
         *  
         */
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(data), editor, true);

        /*
         * Closes the new added file.
         */
        editor.closeFile(filePath, checksum);
        /*
         * Closes the new added directory.
         */
        editor.closeDir();
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This method performs committing file modifications.
     */
    private static SVNCommitInfo modifyFile(ISVNEditor editor, String dirPath,
            String filePath, byte[] oldData, byte[] newData) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         * 
         * -1 - revision is HEAD
         */
        editor.openRoot(-1);
        /*
         * Opens a next subdirectory (in this example program it's the directory
         * added  in  the  last  commit).  Since this moment all changes will be
         * applied to this directory.
         * 
         * dirPath is relative to the root directory.
         * -1 - revision is HEAD
         */
        editor.openDir(dirPath, -1);
        /*
         * Opens the file added in the previous commit.
         * 
         * filePath is also defined as a relative path to the root directory.
         */
        editor.openFile(filePath, -1);
        
        /*
         * The next steps are directed to applying and writing the file delta.
         */
        editor.applyTextDelta(filePath, null);
        
        /*
         * Use delta generator utility class to generate and send delta
         * 
         * Note that you may use only 'target' data to generate delta when there is no 
         * access to the 'base' (previous) version of the file. However, here we've got 'base' 
         * data, what in case of larger files results in smaller network overhead.
         * 
         * SVNDeltaGenerator will call editor.textDeltaChunk(...) method for each generated 
         * "diff window" and then editor.textDeltaEnd(...) in the end of delta transmission.  
         * Number of diff windows depends on the file size. 
         *  
         */
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(oldData), 0, new ByteArrayInputStream(newData), editor, true);

        /*
         * Closes the file.
         */
        editor.closeFile(filePath, checksum);

        /*
         * Closes the directory.
         */
        editor.closeDir();

        /*
         * Closes the root directory.
         */
        editor.closeDir();

        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This method performs committing a deletion of a directory.
     */
    private static SVNCommitInfo deleteDir(ISVNEditor editor, String dirPath) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         * 
         * -1 - revision is HEAD
         */
        editor.openRoot(-1);
        /*
         * Deletes the subdirectory with all its contents.
         * 
         * dirPath is relative to the root directory.
         */
        editor.deleteEntry(dirPath, -1);
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * This  method  performs how a directory in the repository can be copied to
     * branch.
     */
    private static SVNCommitInfo copyDir(ISVNEditor editor, String srcDirPath,
            String dstDirPath, long revision) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         * 
         * -1 - revision is HEAD
         */
        editor.openRoot(-1);
        
        /*
         * Adds a new directory that is a copy of the existing one.
         * 
         * srcDirPath   -  the  source  directory  path (relative  to  the  root 
         * directory).
         * 
         * dstDirPath - the destination directory path where the source will be
         * copied to (relative to the root directory).
         * 
         * revision    - the number of the source directory revision. 
         */
        editor.addDir(dstDirPath, srcDirPath, revision);
        /*
         * Closes the just added copy of the directory.
         */
        editor.closeDir();
        /*
         * Closes the root directory.
         */
        editor.closeDir();
        /*
         * This is the final point in all editor handling. Only now all that new
         * information previously described with the editor's methods is sent to
         * the server for committing. As a result the server sends the new
         * commit information.
         */
        return editor.closeEdit();
    }

    /*
     * Initializes the library to work with a repository via 
     * different protocols.
     */
    private static void setupLibrary() {
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        /*
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();
        
        /*
         * For using over file:///
         */
        FSRepositoryFactory.setup();
    }
}