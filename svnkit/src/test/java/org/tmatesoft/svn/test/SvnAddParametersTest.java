package org.tmatesoft.svn.test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnAddParameters;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnScheduleForAddition;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnAddParametersTest {

    @Test
    public void testInconsistentEolsReportError() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testInconsistentEolsReportError", options);
        try {
            final File someFile = prepareMixedEolsFileInWorkingCopy(svnOperationFactory, sandbox, SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_CR);

            try {
                add(svnOperationFactory, someFile, ISvnAddParameters.Action.REPORT_ERROR);
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(e.getErrorMessage().getErrorCode(), SVNErrorCode.ILLEGAL_TARGET);
                //expected
            }
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testInconsistentEolsAddAsIs() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testInconsistentEolsAddAsIs", options);
        try {
            final File someFile = prepareMixedEolsFileInWorkingCopy(svnOperationFactory, sandbox, SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_CR);

            add(svnOperationFactory, someFile, ISvnAddParameters.Action.ADD_AS_IS);

            final SVNProperties properties = getProperties(svnOperationFactory, someFile);
            Assert.assertEquals(null, properties.getSVNPropertyValue(SVNProperty.EOL_STYLE));
            Assert.assertEquals(null, properties.getSVNPropertyValue(SVNProperty.MIME_TYPE));
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    @Test
    public void testInconsistentEolsAddAsBinary() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testInconsistentEolsAddAsBinary", options);
        try {
            final File someFile = prepareMixedEolsFileInWorkingCopy(svnOperationFactory, sandbox, SVNProperty.EOL_STYLE, SVNProperty.EOL_STYLE_CR);

            add(svnOperationFactory, someFile, ISvnAddParameters.Action.ADD_AS_BINARY);

            final SVNProperties properties = getProperties(svnOperationFactory, someFile);
            Assert.assertEquals(null, properties.getSVNPropertyValue(SVNProperty.EOL_STYLE));
            Assert.assertEquals(SVNFileUtil.BINARY_MIME_TYPE, properties.getSVNPropertyValue(SVNProperty.MIME_TYPE).getString());
        } finally {
            sandbox.dispose();
            svnOperationFactory.dispose();
        }
    }

    private SVNProperties getProperties(SvnOperationFactory svnOperationFactory, File someFile) throws SVNException {
        final SVNProperties[] properties = new SVNProperties[1];

        final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
        getProperties.setSingleTarget(SvnTarget.fromFile(someFile));
        getProperties.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties svnProperties) throws SVNException {
                properties[0] = svnProperties;
            }
        });
        getProperties.run();

        return properties[0] == null ? new SVNProperties() : properties[0];
    }

    private File prepareMixedEolsFileInWorkingCopy(SvnOperationFactory svnOperationFactory, Sandbox sandbox, String propertyName, String propertyValueString) throws SVNException, IOException {
        final SVNURL url = sandbox.createSvnRepository();

        final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
        final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

        final File someFile = new File(workingCopyDirectory, "someFile");
        final String mixedEolsContentsString = "line1" + "\n" + "line2" + "\r\n";

        TestUtil.writeFileContentsString(someFile, mixedEolsContentsString);

        final ISVNOptions svnOptions = createOptionsForAutoProperties(sandbox.createDirectory("options"),
                someFile.getName(), propertyName, propertyValueString);
        svnOperationFactory.setOptions(svnOptions);
        return someFile;
    }

    private void add(SvnOperationFactory svnOperationFactory, File someFile, ISvnAddParameters.Action action) throws SVNException {
        final SvnScheduleForAddition scheduleForAddition = svnOperationFactory.createScheduleForAddition();
        scheduleForAddition.setSingleTarget(SvnTarget.fromFile(someFile));
        scheduleForAddition.setAddParents(false);
        scheduleForAddition.setApplyAutoProperties(true);
        scheduleForAddition.setIncludeIgnored(true);
        scheduleForAddition.setMkDir(false);
        scheduleForAddition.setAddParameters(createSvnAddParameters(action));
        scheduleForAddition.run();
    }

    private ISVNOptions createOptionsForAutoProperties(File optionsDirectory, String filePattern, String propertyName, String propertyValueString) {
        final Map<String, String> autoProperties = new HashMap<String, String>();
        autoProperties.put(filePattern, propertyName + "=" + propertyValueString);
        return createOptionsForAutoProperties(optionsDirectory, autoProperties);
    }

    private ISvnAddParameters createSvnAddParameters(final ISvnAddParameters.Action action) {
        return new ISvnAddParameters() {
            public ISvnAddParameters.Action onInconsistentEOLs(File file) {
                return action;
            }
        };
    }

    private ISVNOptions createOptionsForAutoProperties(File optionsDirectory, Map<String, String> autoProperties) {
        final File configFile = new File(optionsDirectory, "config");
        final SVNConfigFile svnConfigFile = new SVNConfigFile(configFile);
        svnConfigFile.setPropertyValue("miscellany", "enable-auto-props", "true", true);

        final DefaultSVNOptions svnOptions = new DefaultSVNOptions(optionsDirectory, false);
        svnOptions.setAutoProperties(autoProperties);
        return svnOptions;
    }

    private String getTestName() {
        return "SvnAddParametersTest";
    }
}
