package org.tmatesoft.svn.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

public class TestOptions {

    public static final String TEST_PROPERTIES_RESOURCE = "/org/tmatesoft/svn/test/test.properties";

    public static TestOptions instance;

    public static TestOptions loadFrom(Properties properties) {
        final SVNURL repositoryUrl = getRepositoryUrl(properties);
        final File tempDirectory = getTempDirectory(properties);
        return new TestOptions(repositoryUrl, tempDirectory);
    }

    private final SVNURL repositoryUrl;
    private final File tempDirectory;

    public TestOptions(SVNURL repositoryUrl, File tempDirectory) {
        this.repositoryUrl = repositoryUrl;
        this.tempDirectory = tempDirectory;
    }

    public SVNURL getRepositoryUrl() {
        return repositoryUrl;
    }

    public File getTempDirectory() {
        return tempDirectory;
    }

    public static TestOptions getInstance() {
        if (instance == null) {
            final Properties properties = loadPropertiesFromResource(TEST_PROPERTIES_RESOURCE);
            if (properties == null) {
                throw new RuntimeException("Unable to load properties resource " + TEST_PROPERTIES_RESOURCE);
            }
            instance = TestOptions.loadFrom(properties);
        }
        return instance;
    }

    private static Properties loadPropertiesFromResource(String resourceName) {
        final InputStream inputStream = TestOptions.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            return null;
        }
        final Properties properties = new Properties();
        try {
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            return null;
        } finally {
            SVNFileUtil.closeFile(inputStream);
        }
    }

    private static SVNURL getRepositoryUrl(Properties properties) {
        try {
            return SVNURL.parseURIEncoded(properties.getProperty("repository.url"));
        } catch (SVNException e) {
            return null;
        }
    }

    private static File getTempDirectory(Properties properties) {
        final String tempDirectoryPath = properties.getProperty("temp.dir");
        return tempDirectoryPath == null ? new File(".tests") : new File(tempDirectoryPath);
    }
}