package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public class SetPropertyTest {

    @Test
    public void testSetEolStyleAndMimeType() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetEolStyleAndMimeType", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setEolStyle = svnOperationFactory.createSetProperty();
            setEolStyle.setSingleTarget(SvnTarget.fromFile(file));
            setEolStyle.setPropertyName(SVNProperty.EOL_STYLE);
            setEolStyle.setPropertyValue(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF));
            setEolStyle.run();

            final SvnSetProperty setMimeType = svnOperationFactory.createSetProperty();
            setMimeType.setSingleTarget(SvnTarget.fromFile(file));
            setMimeType.setPropertyName(SVNProperty.MIME_TYPE);
            setMimeType.setPropertyValue(SVNPropertyValue.create("application/xml"));
            try {
                setMimeType.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.ILLEGAL_TARGET, e.getErrorMessage().getErrorCode());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testSetMimeTypeAndEolStyle() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetEolStyleAndMimeType", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setMimeType = svnOperationFactory.createSetProperty();
            setMimeType.setSingleTarget(SvnTarget.fromFile(file));
            setMimeType.setPropertyName(SVNProperty.MIME_TYPE);
            setMimeType.setPropertyValue(SVNPropertyValue.create("application/xml"));
            setMimeType.run();

            final SvnSetProperty setEolStyle = svnOperationFactory.createSetProperty();
            setEolStyle.setSingleTarget(SvnTarget.fromFile(file));
            setEolStyle.setPropertyName(SVNProperty.EOL_STYLE);
            setEolStyle.setPropertyValue(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF));
            try {
                setEolStyle.run();
                Assert.fail("An exception should be thrown");
            }catch (SVNException e) {
                Assert.assertEquals(SVNErrorCode.ILLEGAL_TARGET, e.getErrorMessage().getErrorCode());
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testSetEolStyleAndMimeTypeForce() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetEolStyleAndMimeTypeForce", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setEolStyle = svnOperationFactory.createSetProperty();
            setEolStyle.setSingleTarget(SvnTarget.fromFile(file));
            setEolStyle.setPropertyName(SVNProperty.EOL_STYLE);
            setEolStyle.setPropertyValue(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF));
            setEolStyle.run();

            final SvnSetProperty setMimeType = svnOperationFactory.createSetProperty();
            setMimeType.setForce(true);
            setMimeType.setSingleTarget(SvnTarget.fromFile(file));
            setMimeType.setPropertyName(SVNProperty.MIME_TYPE);
            setMimeType.setPropertyValue(SVNPropertyValue.create("application/xml"));
            setMimeType.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(file));
            final SVNProperties properties = getProperties.run();

            Assert.assertEquals(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF), properties.getSVNPropertyValue(SVNProperty.EOL_STYLE));
            Assert.assertEquals(SVNPropertyValue.create("application/xml"), properties.getSVNPropertyValue(SVNProperty.MIME_TYPE));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testSetMimeTypeAndEolStyleForce() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testSetMimeTypeAndEolStyleForce", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");

            final SvnSetProperty setMimeType = svnOperationFactory.createSetProperty();
            setMimeType.setSingleTarget(SvnTarget.fromFile(file));
            setMimeType.setPropertyName(SVNProperty.MIME_TYPE);
            setMimeType.setPropertyValue(SVNPropertyValue.create("application/xml"));
            setMimeType.run();

            final SvnSetProperty setEolStyle = svnOperationFactory.createSetProperty();
            setEolStyle.setForce(true);
            setEolStyle.setSingleTarget(SvnTarget.fromFile(file));
            setEolStyle.setPropertyName(SVNProperty.EOL_STYLE);
            setEolStyle.setPropertyValue(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF));
            setEolStyle.run();

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(file));
            final SVNProperties properties = getProperties.run();

            Assert.assertEquals(SVNPropertyValue.create(SVNProperty.EOL_STYLE_LF), properties.getSVNPropertyValue(SVNProperty.EOL_STYLE));
            Assert.assertEquals(SVNPropertyValue.create("application/xml"), properties.getSVNPropertyValue(SVNProperty.MIME_TYPE));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "SetPropertyTest";
    }
}