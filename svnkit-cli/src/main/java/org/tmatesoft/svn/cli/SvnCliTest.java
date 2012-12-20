package org.tmatesoft.svn.cli;

import java.io.IOException;
import java.security.Permission;

import org.tmatesoft.svn.cli.svn.SVN;
import org.tmatesoft.svn.core.SVNException;

public class SvnCliTest {
    public static void main(String[] args) throws SVNException, IOException {
        SVN.main(new String[] {
                "log",
                "--limit",
                "10",
                "-g",
                "C:/Users/alex/workspace/sqljet/org.tmatesoft.sqljet.trunk",
                });
        System.exit(0);
        
        System.exit(0);
    }

    protected static void disableSystemExitCall() {
        final SecurityManager securityManager = new SecurityManager() {

            @Override
            public void checkExit(int status) {
                super.checkExit(status);
                throw new SecurityException("System.exit calls not allowed!");
            }

            @Override   
            public void checkPermission(Permission perm) {
            }
        };
        System.setSecurityManager(securityManager);
    }

    /**
     * Enables System.exit calls
     */
    protected static void enableSystemExitCall() {
        System.setSecurityManager(null);
    }

}
