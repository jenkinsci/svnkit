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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.regex.Pattern;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNWorkspaceManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.ws.fs.FSEntryFactory;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;

/**
 * @author TMate Software Ltd.
 */
public abstract class AbstractRepositoryTest extends TestCase {

    private String myURL;
    private SVNRepository myRepository;
    private String myRoot;
    private File myFixtureRoot;

    protected AbstractRepositoryTest(String url, String methodName) {
        super(methodName);
        myURL = url;
    }

    protected void setRoot(String root) {
        myRoot = root;
    }

    protected String getRealRepositoryURL() {
        return myURL;
    }
    protected String getRepositoryURL() {
        if (myRoot != null) {
            return myURL + "/" + myRoot;
        }
        return myURL;
    }
    
    protected File getRealFixtureRoot() {
        return myFixtureRoot;
    }

    protected File getFixtureRoot() {
        if (myRoot != null) {
            return new File(myFixtureRoot, myRoot);
        }
        return myFixtureRoot;
    }

    protected void visitFixture(IFileVisitor visitor) throws Throwable {
        if (visitor == null) {
            return;
        }
        doVisitFixture("", visitor, getFixtureRoot());
    }

    protected void accept(File root, IFileVisitor visitor) throws Throwable {
        if (visitor == null) {
            return;
        }
        doVisitFixture("", visitor, root);
    }

    private void doVisitFixture(String path, IFileVisitor visitor, File root) throws Throwable {
        if (root.isDirectory()) {
            visitor.visitDirectory(root, path);
            File[] children = root.listFiles();
            for (int i = 0; i < children.length; i++) {
                File child = children[i];
                String subPath = path;
                if (subPath.length() > 0 && !subPath.endsWith("/")) {
                    subPath += "/";
                }
                subPath += child.getName();
                doVisitFixture(subPath, visitor, child);
            }
        } else if (root.isFile()) {
            visitor.visitFile(root, path);
        }
    }

    protected SVNRepository createRepository(SVNRepositoryLocation location) throws Throwable {
        SVNRepository repos = SVNRepositoryFactory.create(location);
        if (repos != null) {
            repos.setCredentialsProvider(new SVNSimpleCredentialsProvider("user", "test"));
        }
        return repos;
    }

    protected SVNRepository getRepository() {
        if (myRepository == null) {
            SVNRepositoryLocation location = null;
            try {
                location = SVNRepositoryLocation.parseURL(getRepositoryURL());
            } catch (SVNException e) {
                fail("can't create repository location: " + e.getMessage());
            }
            try {
                myRepository = SVNRepositoryFactory.create(location);
                myRepository.setCredentialsProvider(new SVNSimpleCredentialsProvider("user", "test"));
            } catch (SVNException e1) {
                fail(e1.getMessage());
            }
            assertNotNull("can't fetch SVNRepository object for " + location.toString(), myRepository);
        }
        return myRepository;
    }

    protected void setUp() throws Exception {
        super.setUp();
        // repositories
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        // credentials
        boolean isAnon = myURL.endsWith("/svn/");
        // fs wc
        FSEntryFactory.setup();
        AllTests.setAnonymousAccess(isAnon);
        try {
            // do create repository and set myRepositoryURL
            myURL = AllTests.createRepository(myURL, "fixture");
            myFixtureRoot = AllTests.getFixtureRoot();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public static void addToSuite(TestSuite suite, String url, Class clazz) {
        addToSuite(suite, url, clazz, null);

    }

    public static void addToSuite(TestSuite suite, String url, Class clazz, String selectedMethod) {
        if ((clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
            return;
        }
        Constructor constructor = null;
        try {
            constructor = clazz.getConstructor(new Class[] { String.class, String.class });
        } catch (Throwable th) {
            constructor = null;
        }
        if (constructor == null) {
            return;
        }
        Method[] methods = clazz.getMethods();
        Pattern pattern = selectedMethod != null ? Pattern.compile(selectedMethod) : null;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!method.getName().startsWith("test") || "test".equals(method.getName()) || method.getParameterTypes().length > 0
                    || (method.getModifiers() & Modifier.STATIC) != 0) {
                continue;
            }
            if (pattern != null && !pattern.matcher(method.getName()).matches()) {
                continue;

            }
            try {
                Object instance = constructor.newInstance(new Object[] { url, method.getName() });
                suite.addTest((TestCase) instance);
            } catch (Throwable e) {}
        }
    }

    protected int getFixtureFilesCount() throws Throwable {
        final int[] count = { 0 };
        visitFixture(new IFileVisitor() {
            public void visitDirectory(File dir, String relativePath) throws Throwable {
                if (!relativePath.equals("")) {
                    visitFile(dir, relativePath);
                }
            }

            public void visitFile(File file, String relativePath) throws Throwable {
                count[0]++;
            }
        });
        return count[0];
    }

    protected void assertEquals(File f1, File f2) {
        assertEquals(f1, f2, false);
    }

    protected void assertEquals(File f1, File f2, boolean names) {
        assertEquals(f1, f2, null, names);
    }

    protected void assertEquals(File f1, File f2, FileFilter filter, boolean names) {
        assertTrue(f1.exists());
        assertEquals(f1.isDirectory(), f2.isDirectory());
        assertEquals(f1.isFile(), f2.isFile());
        assertEquals(f1.exists(), f2.exists());
        if (names) {
            assertEquals(f1.getName(), f2.getName());
        }
        if (f1.isDirectory()) {
            File[] fl1 = f1.listFiles();
            File[] fl2 = f2.listFiles();
            assertNotNull(fl1);
            assertNotNull(fl2);
            Arrays.sort(fl1);
            Arrays.sort(fl2);
            assertEquals(f1.getAbsolutePath() + " : " + f2.getAbsolutePath(), fl1.length, fl2.length);
            for (int i = 0; i < fl1.length; i++) {
                assertEquals(fl1[i], fl2[i], filter, true);
            }
        } else if (f1.isFile()) {
            if (filter != null && !filter.accept(f1)) {
                return;
            }
            assertEquals(f1.getAbsolutePath() + " : " + f2.getAbsolutePath(), f1.length(), f2.length());
            if (f1.getAbsolutePath().replace(File.separatorChar, '/').indexOf("/.svn/") >= 0) {
                // do not compare contents for admin files, just length
                return;
            }
            FileInputStream fi1 = null;
            FileInputStream fi2 = null;
            try {
                fi1 = new FileInputStream(f1);
                fi2 = new FileInputStream(f2);
                long length = f1.length();
                while (length >= 0) {
                    int b1 = fi1.read();
                    int b2 = fi2.read();
                    if (b1 != b2) {
                        fail(f1.getAbsolutePath() + " : " + f2.getAbsolutePath() + " files are not equal");
                    }
                    length--;
                }
            } catch (IOException e) {
                fail(e.getMessage());
            } finally {
                if (fi1 != null)
                    try {
                        fi1.close();
                    } catch (IOException e1) {}
            }
            if (fi2 != null) {
                try {
                    fi2.close();
                } catch (IOException e1) {}
            }
        } else {
            fail("file types are different");
        }
    }

    protected File createDirectory(File parent, String name) {
        File dir = new File(parent, name);
        dir.mkdirs();
        return dir;
    }

    protected void modifyFile(File file, String contents) throws IOException {
        OutputStream os = new FileOutputStream(file);
        os.write(contents.getBytes());
        os.close();
    }

    protected File createFile(File file, String contents) throws IOException {
        file.createNewFile();
        modifyFile(file, contents);
        return file;
    }

    protected ISVNWorkspace createWorkspace(File folder) throws SVNException {
        ISVNWorkspace ws = SVNWorkspaceManager.createWorkspace("file", folder.getAbsolutePath());
        if (ws != null) {
            ws.setCredentials("user", "test");
        }
        return ws;
    }
    
    protected String getFileContent(File file) throws IOException {
        StringBuffer sb = new StringBuffer();
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            while(true) {
                int ch = reader.read();
                if (ch < 0) {
                    break;
                }
                sb.append((char) ch);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return sb.toString();
    }
}
