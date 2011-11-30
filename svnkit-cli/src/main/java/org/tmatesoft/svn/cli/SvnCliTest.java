package org.tmatesoft.svn.cli;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

public class SvnCliTest {

    /**
     * @param args
     * @throws SVNException 
     */
    public static void main(String[] args) throws SVNException {
        File wc = new File("C:/users/alex/sandbox/sg/copy/working_copies/wc");
        File wcBackup = new File("C:/users/alex/sandbox/sg/copy/working_copies/wc.backup");
        
        SVNFileUtil.deleteAll(wc, true);
        SVNFileUtil.copy(wcBackup, wc, false, true);
        
//        SVNURL root = SVNURL.fromFile(new File("C:/users/alex/sandbox/sg/copy/repositories/copy_tests-58"));
        
        SVN.main(new String[] {"up", "--parents", wc.getPath().replace(File.separatorChar, '/') + "/A/B/E/alpha"});
    }

}
