import java.io.BufferedReader;
import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tigris.subversion.javahl.ChangePath;
import org.tigris.subversion.javahl.CopySource;
import org.tigris.subversion.javahl.LogMessageCallback;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.io.svn.SVNWriter;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPasswordCipher;
import org.tmatesoft.svn.core.internal.wc.SVNWinCryptPasswordCipher;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNAdminAreaFactorySelector;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class Tests {

    public static void main(String[] args) {
        try {
            DAVRepositoryFactory.setup();
            SVNRepositoryFactoryImpl.setup();
            FSRepositoryFactory.setup();

            final Collection collection = SVNClientManager.newInstance().getDiffClient().suggestMergeSources(SVNURL.parseURIEncoded("file:///home/alex/workspace/tmp/r/testMergeinfo/branches/b1"), SVNRevision.HEAD);
            
            System.exit(0);
            
            ISVNAdminAreaFactorySelector selector = new ISVNAdminAreaFactorySelector() {
                
                public Collection getEnabledFactories(File path, Collection factories, boolean writeAccess) throws SVNException {
                    Collection enabledFactories = new LinkedList();
                    for (Iterator factoriesIter = factories.iterator(); factoriesIter.hasNext();) {
                        SVNAdminAreaFactory factory = (SVNAdminAreaFactory) factoriesIter.next();
                        if (factory.getSupportedVersion() == 4) {
                            enabledFactories.add(factory);
                        }
                    }
                    return enabledFactories;
                }    
            };
            
            SVNAdminAreaFactory.setSelector(selector);
            SVNClientManager.newInstance().getUpdateClient().doCheckout(SVNURL.parseURIEncoded("file:///home/alex/workspace/tmp/repos"), 
                    new File("/home/alex/workspace/tmp/wcSelector14"), SVNRevision.UNDEFINED, SVNRevision.HEAD, true);
            
            SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("http://svn.svnkit.com/repos/svnkit/trunk"));
//          source directory:
            SVNURL srcURL =
            SVNURL.parseURIEncoded("http://localhost/repositories/repos/trunk/A/D/G/rho");
            // new URL of the source directory:
            SVNURL dstURL =
            SVNURL.parseURIEncoded("http://localhost/repositories/repos/trunk/A/rho");
            SVNCopySource copySource = new SVNCopySource(SVNRevision.HEAD, SVNRevision.HEAD, srcURL);
            SVNClientManager.newInstance().getCopyClient().doCopy(new SVNCopySource[] { copySource }, 
                    dstURL, true, false, true, "move", null); 
            
/*            SVNClient javaHL = new SVNClient();
            
            javaHL.logMessages("http://svn.collab.net/repos/svn/branches/1.5.x", Revision.HEAD, 
                    Revision.getInstance(0), Revision.HEAD, true, true, true, null, 0, 
                    new LogMessageCallback() {
               public void singleMessage(ChangePath[] changedPaths, long revision, String author, 
                       long timeMicros, String message, boolean hasChildren) {
                   System.out.println(revision + ": " + message);
               } 
            });
*/            
            SVNLogClient logger =  SVNClientManager.newInstance(null).getLogClient();
            final List entries = new LinkedList();
            logger.doList(SVNURL.parseURIEncoded("http://svn.collab.net/repos/svn/branches/1.5.x"), 
                    SVNRevision.HEAD, SVNRevision.HEAD, false, false, new ISVNDirEntryHandler() {
                public void handleDirEntry(SVNDirEntry entry) throws SVNException {
                    if (entry.getKind() == SVNNodeKind.FILE) {
                        System.out.println("path: " + entry.getRelativePath());
                    }
                }
            });
            
            SVNUpdateClient client = SVNClientManager.newInstance(null).getUpdateClient();
            client.doUpdate(new File("/home/alex/workspace/tmp/merge-wc"), SVNRevision.create(7), 
                    SVNDepth.UNKNOWN, false, false);

            String p1 = "/a/b";
            char ch = p1.charAt(p1.length());
            SVNURL urlka = SVNURL.parseURIEncoded("file:///path/to/root");
            SVNURL childURLka = urlka.appendPath("/trunk/xaxa", false);
            
            String str = SVNPathUtil.getCommonPathAncestor("/a/b/c", "/trunk/a/b/c");
            
            SVNCommitClient committer = SVNClientManager.newInstance(null).getCommitClient(); 
            committer.setEventHandler(new ISVNEventHandler() {
            	public void handleEvent(SVNEvent event, double progress)
            			throws SVNException {
            		System.out.println(event.getFile());
            	}
            	
            	public void checkCancelled() throws SVNCancelException {
            	}
            });
            committer.doCommit(new File[] { 
                    new File("/home/alex/workspace/tmp/wc/ext"),
                    }, 
                    false, "test", false, true);
            
            System.out.println("commit passed.");
            
            SVNCopyClient copier = SVNClientManager.newInstance(null).getCopyClient();
            SVNCopySource source = new SVNCopySource(SVNRevision.UNDEFINED, 
            		SVNRevision.WORKING, new File("/home/alex/workspace/tmp/wc/ff4"));
            copier.doCopy(new SVNCopySource[] { source }, new File("/home/alex/workspace/tmp/wc/dir/ff5"), false, false, false);
            
            LinkedList lst = new LinkedList();
            lst.add(SVNStatusType.UNCHANGED);
            lst.add(SVNStatusType.MERGED);
            int index = lst.indexOf(SVNStatusType.MERGED);
            
            TreeSet set = new TreeSet();
            set.add(new FooClass(1));
            set.add(new FooClass(2));
            set.add(new FooClass(3));
            Object[] arr = set.toArray();
            
            for (int i = 0; i < arr.length; i++) {
				FooClass foo = (FooClass) arr[i];
				foo.a += 10;
			}

            for (Iterator iterator = set.iterator(); iterator.hasNext();) {
				FooClass foo = (FooClass) iterator.next();
				System.out.println(foo.a);
			}
            
            File ff = new File("../b/alpha");
            System.out.println(ff.getAbsolutePath());
            System.out.println(SVNPathUtil.validateFilePath(ff.getAbsolutePath()));
            
            LinkedList list = new LinkedList();
            list.add(new Integer(5)); 
            list.add(new Integer(1));
            list.add(new Integer(4));
            
            InputReader inputer = new InputReader(System.in);
            Thread inputerThread = new Thread(inputer);
//            inputerThread.setDaemon(true);
            inputerThread.start();
            int i = 5;
            while (inputer.getReadInput() == null) {
                if (i-- <= 0) {
                    inputerThread.interrupt();
                    break;
                }
                Thread.sleep(1);
            }
            
            Object[] buffer = new Object[] { "get-location-segments", "/pp", null, 
                    new Long(1), new Long(2) };
            SVNWriter.write(SVNFileUtil.DUMMY_OUT, "(w(s(n)(n)(n)))", buffer);
            SVNRepository repo = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("svn://localhost/"));
            repo.getLocationSegments("file", -1, 1, 0, null);
            
            Map locks = new HashMap();
            long latestRev = repo.getLatestRevision();
            locks.put("iota", new Long(latestRev));
            repo.lock(locks, "bubu", false, null);
            
            committer.doCommit(new File[] { 
                    new File("/home/alex/workspace/tmp/bug_testing/wc3/dir/svnkit"),
                    new File("/home/alex/workspace/tmp/bug_testing/wc3/dir/svnkit/main"),
                    new File("/home/alex/workspace/tmp/bug_testing/wc3/dir/svnkit/sequence")
                    }, 
                    false, "test", false, true);
            
            SVNWCClient wcClient = SVNClientManager.newInstance(null).getWCClient();
            //wcClient.doSetProperty(new File(""), "something", "smth", false, SVNDepth.INFINITY, null);
            
            String[] pathsArray = new String[] {"/dirA", "/dirB", "/dirA/fileA", "/file"};
            Arrays.sort(pathsArray);
            
            int c = getLinesCount("1\n\n\n");
            SVNAdminClient admin = SVNClientManager.newInstance(null).getAdminClient();
            InputStream is = SVNFileUtil.openFileForReading(new File("I:/Workspace/org.tmatesoft.svnkit/svnkit-test/python/cmdline/mergetracking_data/basic-merge.dump"));
            admin.doLoad(new File("I:/Workspace/tmp/tr"), is);
            
            Class.forName("org.sqlite.JDBC");
            String fileName = "I:/Workspace/tmp/mergeinfo.db"; 

            Connection conn = DriverManager.getConnection("jdbc:sqlite:"+fileName);
            
            Pattern pat = Pattern.compile("(\\d{2})(:(\\d{2}))?-((\\d{2}))"); 
            Matcher matcher = pat.matcher("23-22");
            if (matcher.matches()) {
                System.out.println(matcher.groupCount());
                for ( i = 0; i <= matcher.groupCount(); i++) {
                    System.out.println(matcher.group(i));
                }
            }
            
            SVNRevision.parse("{T05:02:07.832000 +0070}");
            
            repo = SVNRepositoryFactory.create(SVNURL.parseURIEncoded("svn://localhost/testRepos"));
            repo.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager("tamerlan", new String("������".getBytes(), "Windows-1251")));
            repo.info("", -1);
            
            SVNWinCryptPasswordCipher cipher = new SVNWinCryptPasswordCipher();
            String password = "a";
            String encrypted = cipher.encrypt(password);
            String decrypted = cipher.decrypt(encrypted);
            
            SVNAdminClient adminCli = SVNClientManager.newInstance(null).getAdminClient();
            InputStream dump = SVNFileUtil.openFileForReading(new File("I:/Workspace/tmp/testsvn.dump"));
            adminCli.doLoad(new File("I:/Workspace/tmp/testRepos"), dump);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    protected static int getLinesCount(String str) {
        if ("".equals(str)) {
            return 1;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\r') {
                count++;
                if (i < str.length() - 1 && str.charAt(i + 1) == '\n') {
                    i++;
                }
            } else if (str.charAt(i) == '\n') {
                count++;
            }
        }
        if (count == 0) {
            count++;
        }
        return count;
    }

    private static class InputReader implements Runnable {
        private BufferedReader myReader;
        private StringBuffer myReadInput;
        private SVNErrorMessage myError;
        volatile boolean myIsCancelled;
        
        public InputReader(InputStream is) {
            myReader = new BufferedReader(new InputStreamReader(is));
        }
        
        public void run() {
            try {
                System.out.print("print something: ");
                boolean sawFirstHalfOfEOL = false;
                while (true) {
                    if (myIsCancelled) {
                        break;
                    }
                    if (!myReader.ready()) {
                        try {
                            System.out.println("nothing to read, sleep..");
                            Thread.currentThread().sleep(1);
                        } catch (InterruptedException e) {
                            //
                        }
                        continue;
                    }
                    int r = myReader.read();
                    if (r == -1) {
                        myError = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
                        break;
                    }
                    char ch = (char) (r & 0xFF);
                    
                    if (sawFirstHalfOfEOL && ch != '\n') {
                        myReader.reset();
                        break;
                    } 
                    
                    if (ch == '\n') {
                        break;
                    } else if (ch == '\r') {
                        myReader.mark(1);
                        sawFirstHalfOfEOL = true;
                        continue;
                    }
                    appendToResult(ch);
                }
            } catch (ClosedByInterruptException ie) {
                System.out.println("input reading was interrupted:");
                ie.printStackTrace();
            } catch (InterruptedIOException ie) {
                System.out.println("input reading was interrupted:");
                ie.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            System.out.println("Your input was: " + myReadInput);
        }
        
        public String getReadInput() {
            return myReadInput != null ? myReadInput.toString() : null;
        }
        
        private void appendToResult(char ch) {
            if (myReadInput == null) {
                myReadInput = new StringBuffer();
            }
            myReadInput.append(ch);
        }
    }

    private static class FooClass implements Comparable {
    	private int a;
    	public FooClass(int a) {
    		this.a = a;
    	}
    	
    	public int compareTo(Object o) {
    		FooClass foo = (FooClass) o;
    		return this.a == foo.a ? 0 : this.a < foo.a ? -1 : 1;
    	}
    }
    
    private static class PromptEraserThread implements Runnable {
    	volatile boolean myIsErase;
    	
    	public PromptEraserThread() {
    		myIsErase = true;
    	}

		public void run() {
			while (myIsErase) {
				System.out.print('\b');
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
			}
		}
    }

}
