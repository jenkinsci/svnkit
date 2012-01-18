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
    public static final String TEST_PROPERTIES_TEMPLATE_RESOURCE = "/org/tmatesoft/svn/test/test.properties.template";

    public static TestOptions instance;

    public static TestOptions loadFrom(Properties properties) {
        final SVNURL repositoryUrl = getRepositoryUrl(properties);
        final File tempDirectory = getTempDirectory(properties);
        final String sqlite3Command = getSqlite3Command(properties);
        return new TestOptions(repositoryUrl, tempDirectory, sqlite3Command);
    }

    private final SVNURL repositoryUrl;
    private final File tempDirectory;
    private final String sqlite3Command;

    public TestOptions(SVNURL repositoryUrl, File tempDirectory, String sqlite3Command) {
        this.repositoryUrl = repositoryUrl;
        this.tempDirectory = tempDirectory;
        this.sqlite3Command = sqlite3Command;
    }

    public SVNURL getRepositoryUrl() {
        return repositoryUrl;
    }

    public File getTempDirectory() {
        return tempDirectory;
    }

    public String getSqlite3Command() {
        return sqlite3Command;
    }

    public static TestOptions getInstance() {
        if (instance == null) {
            Properties properties;

            properties = loadPropertiesFromResource(TEST_PROPERTIES_RESOURCE);
            if (properties != null) {
                instance = TestOptions.loadFrom(properties);
                return instance;
            }
            properties = loadPropertiesFromResource(TEST_PROPERTIES_TEMPLATE_RESOURCE);
            if (properties != null) {
                instance = TestOptions.loadFrom(properties);
                return instance;
            }

            throw new RuntimeException("Unable to load properties resources: " + TEST_PROPERTIES_RESOURCE + " and " + TEST_PROPERTIES_TEMPLATE_RESOURCE);
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

    private static String getSqlite3Command(Properties properties) {
        final String sqlite3Command = properties.getProperty("sqlite3.command");
        return sqlite3Command == null ? "sqlite3" : sqlite3Command;
    }
}