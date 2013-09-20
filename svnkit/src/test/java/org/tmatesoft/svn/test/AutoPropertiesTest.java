package org.tmatesoft.svn.test;

import junit.framework.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnTarget;

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

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
