package org.tmatesoft.svn.test;

import java.io.File;

import junit.framework.Assert;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnScheduleForRemoval;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

public class SvnWcUtilTest {

    private static final String FILE2_PATH = "A/B/beta";
    private static final String DIR_PATH = "C";
    private static final String DIRB_PATH = "A/B";
    private static final String DIRG_PATH = "A/G";
    private static final String MISSING_DIR_PATH = "C/missing";
    private static final String FILE1_PATH = "A/alpha";

    private static final String EXT_PATH = "ext";
    private static final String EXT_DEEP_PATH = "ext-deep/ext";
    private static final String FILE_PATH_IN_EXT = "ext/alpha";
    private static final String DIR_PATH_IN_EXT = "ext/B";
    private static final String FILE_PATH_IN_DEEP_EXT = "ext-deep/ext/alpha";
    private static final String DIR_PATH_IN_DEEP_EXT = "ext-deep/ext/B";

    private static final String UNVERSIONED_DIR1_PATH = "unversioned";
    private static final String UNVERSIONED_DIR2_PATH = "C/unversioned";
    private static final String UNVERSIONED_FILE_PATH = "unversioned.txt";

    @Before
    public void setup() {
        Assume.assumeTrue(!TestUtil.isNewWorkingCopyOnly());
    }

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
    
    @Test
    public void testIsWcRoot() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName(), TestOptions.getInstance());
        final SVNURL url = prepareRepository(sandbox);
        final File wc17 = prepareWc(sandbox, url, SvnWcGeneration.V17);
        final File wc16 = prepareWc(sandbox, url, SvnWcGeneration.V16);

        final File unversioned = sandbox.createDirectory("unversioned");
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(unversioned));
        
        testIsWcRoot(wc16);
        testIsWcRoot(wc17);

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

        testIsWcRoot(innerWc16In16);
        testIsWcRoot(innerWc16In17);
        testIsWcRoot(innerWc17In16);
        testIsWcRoot(innerWc17In17);
        
        testIsWcRoot(innerWc16InUnversionedIn16);
        testIsWcRoot(innerWc16InUnversionedIn17);
        testIsWcRoot(innerWc17InUnversionedIn16);
        testIsWcRoot(innerWc17InUnversionedIn17);
        
    }

    @Test
    public void testGetWcRoot() throws SVNException {
        final Sandbox sandbox = Sandbox.createWithCleanup(getClass().getSimpleName(), TestOptions.getInstance());
        final SVNURL url = prepareRepository(sandbox);
        final File wc17 = prepareWc(sandbox, url, SvnWcGeneration.V17);
        final File wc16 = prepareWc(sandbox, url, SvnWcGeneration.V16);

        final File unversioned = sandbox.createDirectory("unversioned");
        Assert.assertEquals(null, SVNWCUtil.getWorkingCopyRoot(unversioned, false));
        
        testGetWcRoot(wc17);
        testGetWcRoot(wc16);

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

        testGetWcRoot(innerWc16In16);
        testGetWcRoot(innerWc16In17);
        testGetWcRoot(innerWc17In16);
        testGetWcRoot(innerWc17In17);
        
        testGetWcRoot(innerWc16InUnversionedIn16);
        testGetWcRoot(innerWc16InUnversionedIn17);
        testGetWcRoot(innerWc17InUnversionedIn16);
        testGetWcRoot(innerWc17InUnversionedIn17);
        
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

        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, EXT_PATH)));
        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, EXT_DEEP_PATH)));
        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, DIR_PATH_IN_EXT)));
        Assert.assertTrue(SVNWCUtil.isVersionedDirectory(new File(wcRoot, DIR_PATH_IN_DEEP_EXT)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, FILE_PATH_IN_EXT)));
        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, FILE_PATH_IN_DEEP_EXT)));

        Assert.assertFalse(SVNWCUtil.isVersionedDirectory(new File(wcRoot, EXT_DEEP_PATH).getParentFile()));
    }

    private void testIsWcRoot(final File wcRoot) throws SVNException {
        Assert.assertTrue(SVNWCUtil.isWorkingCopyRoot(wcRoot));

        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, DIR_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, MISSING_DIR_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, FILE1_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, FILE2_PATH)));

        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, UNVERSIONED_FILE_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, UNVERSIONED_DIR1_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, UNVERSIONED_DIR2_PATH)));

        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, DIRB_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, DIRG_PATH)));

        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, DIR_PATH_IN_DEEP_EXT)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, DIR_PATH_IN_EXT)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, FILE_PATH_IN_DEEP_EXT)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, FILE_PATH_IN_EXT)));
        
        Assert.assertTrue(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, EXT_PATH)));
        Assert.assertTrue(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, EXT_DEEP_PATH)));
        Assert.assertFalse(SVNWCUtil.isWorkingCopyRoot(new File(wcRoot, EXT_DEEP_PATH).getParentFile()));

    }

    private void testGetWcRoot(final File wcRoot) throws SVNException {
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(wcRoot, false));

        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIR_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, MISSING_DIR_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, FILE1_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, FILE2_PATH), false));

        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, UNVERSIONED_FILE_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, UNVERSIONED_DIR1_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, UNVERSIONED_DIR2_PATH), false));

        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIRB_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIRG_PATH), false));

        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIR_PATH_IN_DEEP_EXT), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIR_PATH_IN_EXT), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, FILE_PATH_IN_DEEP_EXT), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, FILE_PATH_IN_EXT), false));
        
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, EXT_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, EXT_DEEP_PATH), false));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, EXT_DEEP_PATH).getParentFile(), false));
        
        File extRoot = new File(wcRoot, EXT_PATH);
        File deepExtRoot = new File(wcRoot, EXT_DEEP_PATH);

        Assert.assertEquals(deepExtRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIR_PATH_IN_DEEP_EXT), true));
        Assert.assertEquals(extRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, DIR_PATH_IN_EXT), true));
        Assert.assertEquals(deepExtRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, FILE_PATH_IN_DEEP_EXT), true));
        Assert.assertEquals(extRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, FILE_PATH_IN_EXT), true));
        
        Assert.assertEquals(extRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, EXT_PATH), true));
        Assert.assertEquals(deepExtRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, EXT_DEEP_PATH), true));
        Assert.assertEquals(wcRoot, SVNWCUtil.getWorkingCopyRoot(new File(wcRoot, EXT_DEEP_PATH).getParentFile(), true));
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
        
        SvnSetProperty ps = of.createSetProperty();
        ps.setSingleTarget(SvnTarget.fromFile(directory));
        ps.setPropertyName(SVNProperty.EXTERNALS);
        ps.setPropertyValue(SVNPropertyValue.create("^/A " + EXT_PATH + "\n^/A " + EXT_DEEP_PATH));
        ps.run();
        
        SvnUpdate up = of.createUpdate();
        up.setSingleTarget(SvnTarget.fromFile(directory));
        up.setIgnoreExternals(false);
        up.setDepth(SVNDepth.INFINITY);
        up.run();
        
        Assert.assertTrue(new File(directory, DIR_PATH_IN_DEEP_EXT).isDirectory());
        Assert.assertTrue(new File(directory, FILE_PATH_IN_DEEP_EXT).isFile());
        Assert.assertTrue(new File(directory, DIR_PATH_IN_EXT).isDirectory());
        Assert.assertTrue(new File(directory, FILE_PATH_IN_EXT).isFile());
        
        return directory;
    }

}
