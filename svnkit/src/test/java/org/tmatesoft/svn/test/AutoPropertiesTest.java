package org.tmatesoft.svn.test;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;

public class AutoPropertiesTest {

    @Test
    public void testAutoPropertiesProperty() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testAutoPropertiesProperty", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setFileProperty("", SVNProperty.INHERITABLE_AUTO_PROPS, SVNPropertyValue.create("*.txt=svn:eol-style=native"));
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file.txt");

            SVNFileUtil.createNewFile(file);

            final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
            scheduleForAddition.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForAddition.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(file));
            SVNProperties properties = getProperties.run();

            Assert.assertNotNull(properties);
            Assert.assertEquals(1, properties.size());
            Assert.assertEquals(SVNProperty.NATIVE, properties.getStringValue(SVNProperty.EOL_STYLE));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testUseAutoPropertiesConfigOption() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testUseAutoPropertiesConfigOption", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final File configDirectory = sandbox.createDirectory("configDirectory");
            final DefaultSVNOptions svnOptions = SVNWCUtil.createDefaultOptions(configDirectory, false);
            svnOptions.setUseAutoProperties(true);
            svnOperationFactory.setOptions(svnOptions);

            final File workingCopyDirectory = sandbox.createDirectory("wc");
            final File file = new File(workingCopyDirectory, "file.txt");

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(url));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            TestUtil.writeFileContentsString(file, "");

            SVNConfigFile configFile = new SVNConfigFile(new File(configDirectory, "config"));
            configFile.setPropertyValue("auto-props", "*.txt", "svn:eol-style=native", true);
            configFile.save();

            final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
            scheduleForAddition.setSingleTarget(SvnTarget.fromFile(file));
            scheduleForAddition.setApplyAutoProperties(true);
            scheduleForAddition.run();

            final SvnGetProperties getProps = svnOperationFactory.createGetProperties();
            getProps.setDepth(SVNDepth.EMPTY);
            getProps.setSingleTarget(SvnTarget.fromFile(file));
            final SVNProperties properties = getProps.run();

            Assert.assertEquals(1, properties.size());
            Assert.assertEquals(SVNProperty.NATIVE, SVNPropertyValue.getPropertyAsString(properties.getSVNPropertyValue(SVNProperty.EOL_STYLE)));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
