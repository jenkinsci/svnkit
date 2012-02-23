package org.tmatesoft.svn.test;

import java.io.File;

import junit.framework.Assert;

import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnWcUtilTest {

    private static final String FILE2_PATH = "A/B/beta";
    private static final String DIR_PATH = "C";
    private static final String DIRB_PATH = "A/B";
    private static final String DIRG_PATH = "A/G";
    private static final String MISSING_DIR_PATH = "C/missing";
    private static final String FILE1_PATH = "A/alpha";

    private static final String UNVERSIONED_DIR1_PATH = "unversioned";
    private static final String UNVERSIONED_DIR2_PATH = "C/unversioned";
    private static final String UNVERSIONED_FILE_PATH = "unversioned.txt";

    @Test
    public void testIsVersionedDirectory() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName(), TestOptions.getInstance());
        final SVNURL url = prepareRepository(sandbox);
        final File wc17 = prepareWc(sandbox, url, SvnWcGeneration.V17);
        final File wc16 = prepareWc(sandbox, url, SvnWcGeneration.V16);

        final File unversioned = sandbox.createDirectory("unversioned");
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(unversioned));
        
        testIsVersioned(wc16);
        testIsVersioned(wc17);

        File wc16copy = sandbox.createDirectory("wc16-copy");
        wc16copy.delete();
        File wc17copy = sandbox.createDirectory("wc17-copy");
        wc16copy.delete();
        SVNFileUtil.copyDirectory(wc16, wc16copy, true, null);
        SVNFileUtil.copyDirectory(wc17, wc17copy, true, null);
        
        
        File innerWc16In17 = new File(wc17copy, "innerWc16");
        File innerWc17In17 = new File(wc17copy, "innerWc17");        
        File innerWc16In16 = new File(wc16copy, "innerWc16");
        File innerWc17In16 = new File(wc16copy, "innerWc17");
        File innerWc16InUnversionedIn16 = new File(new File(wc16copy, UNVERSIONED_DIR1_PATH), "innerWc16");
        File innerWc17InUnversionedIn16 = new File(new File(wc16copy, UNVERSIONED_DIR1_PATH), "innerWc17");
        File innerWc16InUnversionedIn17 = new File(new File(wc17copy, UNVERSIONED_DIR1_PATH), "innerWc16");
        File innerWc17InUnversionedIn17 = new File(new File(wc17copy, UNVERSIONED_DIR1_PATH), "innerWc17");
        
        SVNFileUtil.copyDirectory(wc16, innerWc16In16, true, null);
        SVNFileUtil.copyDirectory(wc16, innerWc16In17, true, null);
        SVNFileUtil.copyDirectory(wc17, innerWc17In16, true, null);
        SVNFileUtil.copyDirectory(wc17, innerWc17In17, true, null);
        SVNFileUtil.copyDirectory(wc16, innerWc16InUnversionedIn16, true, null);
        SVNFileUtil.copyDirectory(wc16, innerWc16InUnversionedIn17, true, null);
        SVNFileUtil.copyDirectory(wc17, innerWc17InUnversionedIn16, true, null);
        SVNFileUtil.copyDirectory(wc17, innerWc17InUnversionedIn17, true, null);

        testIsVersioned(innerWc16In16);
        testIsVersioned(innerWc16In17);
        testIsVersioned(innerWc17In16);
        testIsVersioned(innerWc17In17);
        
        testIsVersioned(innerWc16InUnversionedIn16);
        testIsVersioned(innerWc16InUnversionedIn17);
        testIsVersioned(innerWc17InUnversionedIn16);
        testIsVersioned(innerWc17InUnversionedIn17);
}

    private void testIsVersioned(final File wcRoot) {
        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(wcRoot));
        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, DIR_PATH)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, MISSING_DIR_PATH)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, FILE1_PATH)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, FILE2_PATH)));

        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, UNVERSIONED_FILE_PATH)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, UNVERSIONED_DIR1_PATH)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, UNVERSIONED_DIR2_PATH)));

        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, DIRB_PATH)));
        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, DIRG_PATH)));
}
    
    private SVNURL prepareRepository(Sandbox sandbox) throws SVNException {
        final SVNURL url = sandbox.createSvnRepository();
        
        final CommitBuilder commitBuilder = new CommitBuilder(url);
        commitBuilder.addFile(FILE1_PATH, "contents1".getBytes());
        commitBuilder.addFile(FILE2_PATH, "contents2".getBytes());
        commitBuilder.addDirectory(DIR_PATH);
        commitBuilder.commit();
        
        return url;
    }
    
    private File prepareWc(Sandbox sandbox, SVNURL url, SvnWcGeneration generation) throws SVNException {
        File directory = sandbox.createDirectory("wc");
        SvnOperationFactory of = new SvnOperationFactory();
        of.setPrimaryWcGeneration(generation);
        SvnCheckout co = of.createCheckout();
        co.setSingleTarget(SvnTarget.fromFile(directory));
        co.setSource(SvnTarget.fromURL(url));
        co.run();

        SVNFileUtil.ensureDirectoryExists(new File(directory, "unversioned"));
        SVNFileUtil.ensureDirectoryExists(new File(directory, "C/unversioned"));
        SVNFileUtil.writeToFile(new File(directory, "unversioned.txt"), "text", null);
        
        SvnScheduleForRemoval rm = of.createScheduleForRemoval();
        rm.addTarget(SvnTarget.fromFile(new File(directory, DIRB_PATH)));
        rm.setDepth(SVNDepth.INFINITY);
        rm.run();
        
        SvnScheduleForAddition add = of.createScheduleForAddition();
        add.addTarget(SvnTarget.fromFile(new File(directory, DIRG_PATH)));
        add.setMkDir(true);
        add.run();

        return directory;
    }

}
