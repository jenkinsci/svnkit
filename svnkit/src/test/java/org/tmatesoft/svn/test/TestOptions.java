package org.tmatesoft.svn.test;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TestOptions {

    public static final String TEST_PROPERTIES_RESOURCE = "/org/tmatesoft/svn/test/test.properties";
    public static final String TEST_PROPERTIES_TEMPLATE_RESOURCE = "/org/tmatesoft/svn/test/test.properties.template";

    public static TestOptions instance;

    public static TestOptions loadFrom(Properties properties) {
        final SVNURL repositoryUrl = getRepositoryUrl(properties);
        final File tempDirectory = getTempDirectory(properties);
        final String sqlite3Command = getSqlite3Command(properties);
        final long largeUpdateStep = getLargeUpdateStep(properties);
        final String svnCommand = getSvnCommand(properties);
        final List<SVNRevisionRange> updateSchedule = getUpdateSchedule(properties);
        final String apacheCtlCommand = getApacheCtlCommand(properties);
        final File apacheRoot = getApacheRoot(properties);
        final String htpasswdCommand = getHtpasswdCommand(properties);
        final String svnserveCommand = getSvnserveCommand(properties);
        return new TestOptions(repositoryUrl, tempDirectory, sqlite3Command, largeUpdateStep, svnCommand, updateSchedule, apacheCtlCommand, apacheRoot, htpasswdCommand, svnserveCommand);
    }

    private final SVNURL repositoryUrl;

    private final File tempDirectory;

    private final String sqlite3Command;

    private final long largeUpdateStep;

    private final String svnCommand;

    private final List<SVNRevisionRange> updateSchedule;

    private final String apacheCtlCommand;
    private final File apacheRoot;
    private final String htpasswdCommand;
    private final String svnserveCommand;

    public TestOptions(SVNURL repositoryUrl, File tempDirectory, String sqlite3Command, long largeUpdateStep, String svnCommand, List<SVNRevisionRange> updateSchedule, String apacheCtlCommand, File apacheRoot, String htpasswdCommand, String svnserveCommand) {
        this.repositoryUrl = repositoryUrl;
        this.tempDirectory = tempDirectory;
        this.sqlite3Command = sqlite3Command;
        this.largeUpdateStep = largeUpdateStep;
        this.svnCommand = svnCommand;
        this.updateSchedule = updateSchedule;
        this.apacheCtlCommand = apacheCtlCommand;
        this.apacheRoot = apacheRoot;
        this.htpasswdCommand = htpasswdCommand;
        this.svnserveCommand = svnserveCommand;
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

    public long getLargeUpdateStep() {
        return largeUpdateStep;
    }

    public String getSvnCommand() {
        return svnCommand;
    }

    public List<SVNRevisionRange> getUpdateSchedule() {
        return updateSchedule;
    }

    public String getApacheCtlCommand() {
        return apacheCtlCommand;
    }

    public File getApacheRoot() {
        return apacheRoot;
    }

    public String getHtpasswdCommand() {
        return htpasswdCommand;
    }

    public String getSvnserveCommand() {
        return svnserveCommand;
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

    private static long getLargeUpdateStep(Properties properties) {
        return getLongProperty(properties, "large.update.step", 10);
    }

    private static String getSvnCommand(Properties properties) {
        return properties.getProperty("svn.command", "svn");
    }

    private static long getLongProperty(Properties properties, String propertyName, long defaultValue) {
        final String valueString = properties.getProperty(propertyName);
        if (valueString == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(valueString);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static List<SVNRevisionRange> getUpdateSchedule(Properties properties) {
        final String updateScheduleString = properties.getProperty("update.schedule");
        if (updateScheduleString == null) {
            return null;
        }

        final String[] rangeStrings = updateScheduleString.split(",");

        final List<SVNRevisionRange> ranges = new ArrayList<SVNRevisionRange>();
        for (String rangeString : rangeStrings) {
            final int dashIndex = rangeString.indexOf('-');
            if (dashIndex >= 0) {
                final String startRevisionString = rangeString.substring(0, dashIndex);
                final String endRevisionString = rangeString.substring(dashIndex+1);
                ranges.add(new SVNRevisionRange(SVNRevision.parse(startRevisionString), SVNRevision.parse(endRevisionString)));
            } else {
                final SVNRevision revision = SVNRevision.parse(rangeString);
                ranges.add(new SVNRevisionRange(revision, revision));
            }
        }
        return ranges;
    }

    private static String getApacheCtlCommand(Properties properties) {
        return properties.getProperty("apachectl.command");
    }

    private static File getApacheRoot(Properties properties) {
        final String apacheRootPath = properties.getProperty("apache.root");
        if (apacheRootPath == null) {
            return null;
        }
        return new File(apacheRootPath);
    }

    private static String getHtpasswdCommand(Properties properties) {
        return properties.getProperty("htpasswd.command");
    }

    private static String getSvnserveCommand(Properties properties) {
        return properties.getProperty("svnserve.command");
    }
}