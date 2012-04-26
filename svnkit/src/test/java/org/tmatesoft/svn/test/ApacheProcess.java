package org.tmatesoft.svn.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;

public class ApacheProcess {

    private static final String APACHE_CONFIG_TEMPLATE_RESOURCE = "/org/tmatesoft/svn/test/apache.config.template";

    public static ApacheProcess run(TestOptions testOptions, File repositoryRoot) throws SVNException {
        return run(testOptions, repositoryRoot, null);
    }

    public static ApacheProcess run(TestOptions testOptions, File repositoryRoot, Map<String, String> loginToPassword) throws SVNException {
        final int port = TestUtil.findFreePort();
        final File tempDirectory = SVNFileUtil.createTempDirectory("svnkit.test.apache.temp.dir");
        final String apachectlCommand = testOptions.getApacheCtlCommand();
        final File apacheRoot = testOptions.getApacheRoot();
        final String htpasswdCommand = testOptions.getHtpasswdCommand();

        assert apacheRoot != null && apachectlCommand != null;

        final ApacheProcess apacheProcess = new ApacheProcess(apachectlCommand, port, apacheRoot, repositoryRoot, tempDirectory, loginToPassword, htpasswdCommand);
        apacheProcess.start();
        return apacheProcess;
    }

    public static void shutdown(ApacheProcess apacheProcess) {
        if (apacheProcess != null) {
            apacheProcess.shutdown();
        }
    }

    private final String apachectlCommand;
    private final int port;
    private final File apacheRoot;
    private final File repositoryRoot;
    private final File tempDirectory;
    private final Map<String, String> loginToPassword;
    private final String htpasswdCommand;

    private File configFile;
    private File authzFile;

    private ApacheProcess(String apachectlCommand, int port, File apacheRoot, File repositoryRoot, File tempDirectory, Map<String, String> loginToPassword, String htpasswdCommand) {
        this.apachectlCommand = apachectlCommand;
        this.port = port;
        this.apacheRoot = apacheRoot;
        this.repositoryRoot = repositoryRoot;
        this.tempDirectory = tempDirectory;
        this.loginToPassword = loginToPassword;
        this.htpasswdCommand = htpasswdCommand;
    }

    public SVNURL getUrl() {
        try {
            return SVNURL.parseURIEncoded("http://localhost:" + port + "/repos");
        } catch (SVNException e) {
            throw wrapException(e);
        }
    }

    public File getAuthzFile() {
        return authzFile;
    }

    public void reload() {
        apachectl("graceful");
    }

    public void shutdown() {
        stop();

        SVNFileUtil.deleteAll(tempDirectory, true);
    }

    private void start() {
        apachectl("start");
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            throw wrapException(e);
        }
    }

    private void stop() {
        apachectl("stop");
    }

    private void apachectl(String action) {
        List<String> command = new ArrayList<String>();
        String cfgPath = getOrCreateConfigFile().getAbsolutePath().replace(File.separatorChar, '/');

        command.add(apachectlCommand);
        command.add("-f");
        command.add(cfgPath);
        command.add("-k");
        command.add(action);

        final ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);

        try {
            final Process process = processBuilder.start();
            final int rc = process.waitFor();
            if (rc != 0) {
                throw new RuntimeException("Apache exit code: " + rc);
            }
        } catch (IOException e) {
            throw wrapException(e);
        } catch (InterruptedException e) {
            throw wrapException(e);
        }
    }

    private File getOrCreateConfigFile() {
        if (configFile == null) {
            configFile = generateConfigFile();
        }
        return configFile;
    }

    private File getOrCreateAuthzFile() {
        if (authzFile == null) {
            authzFile = generateAuthzFile();
        }
        return authzFile;
    }

    private File generateConfigFile() {
        final File configFile = new File(tempDirectory, "apache.config");
        final InputStream inputStream = ApacheProcess.class.getResourceAsStream(APACHE_CONFIG_TEMPLATE_RESOURCE);
        if (inputStream == null) {
            throw new RuntimeException("Unable to load resource: " + APACHE_CONFIG_TEMPLATE_RESOURCE +", check classpath");
        }

        String configContents;
        try {
            configContents = readFullyAndClose(inputStream);
        } catch (IOException e) {
            throw wrapException(e);
        }
        configContents = configContents.replaceAll("%root%", apacheRoot.getAbsolutePath());
        configContents = configContents.replaceAll("%port%", String.valueOf(port));
        configContents = configContents.replaceAll("%repository.root%", repositoryRoot.getAbsolutePath());
        configContents = configContents.replaceAll("%home%", TestUtil.convertSlashesToDirect(tempDirectory.getAbsolutePath()));
        configContents = configContents.replaceAll("%pidfile%", TestUtil.convertSlashesToDirect(new File(tempDirectory, "apache.pid").getAbsolutePath()));
        configContents = configContents.replaceAll("%apache.options%", getApacheOptions());
        configContents = configContents.replaceAll("%apache.modules%", new File(apacheRoot, "modules").getAbsolutePath());
        configContents = configContents.replaceAll("%apache.svn.modules%", new File(apacheRoot, "modules").getAbsolutePath());
        configContents = configContents.replaceAll("%authzfile%", TestUtil.convertSlashesToDirect(getOrCreateAuthzFile().getAbsolutePath()));

        try {
            TestUtil.writeFileContentsString(configFile, configContents);
        } catch (SVNException e) {
            throw wrapException(e);
        }

        return configFile;
    }

    private File generateAuthzFile() {
        final File authzFile = new File(tempDirectory, "authz");

        final String fullPermissions =
                "[/]" + "\n" +
                "*=rw" + "\n";
        try {
            TestUtil.writeFileContentsString(authzFile, fullPermissions);
        } catch (SVNException e) {
            throw wrapException(e);
        }
        return authzFile;
    }

    private String getApacheOptions() {
        return getUserAuthOptions();
    }

    private String getUserAuthOptions() {
        if (loginToPassword == null) {
            return "";
        }

        final File htpasswdFile = createHtpasswdFile(loginToPassword);

        return "AuthType Basic" + "\n" +
                "AuthName \"Subversion Repository\"" + "\n" +
                "AuthUserFile " + htpasswdFile.getAbsolutePath() + "\n" +
               "Require valid-user" + "\n";
    }

    private File createHtpasswdFile(Map<String, String> loginToPassword) {
        final File htpasswdFile = new File(tempDirectory, "htpasswdfile");

        if (htpasswdCommand != null) {
            createHtpasswdWithCommand(loginToPassword, htpasswdFile, htpasswdCommand);
        } else {
            createHtpasswdManually(loginToPassword, htpasswdFile);
        }
        return htpasswdFile;
    }

    private void createHtpasswdWithCommand(Map<String, String> loginToPassword, File htpasswdFile, String htpasswdCommand) {
        boolean createNewFile = true;

        for (Map.Entry<String, String> entry : loginToPassword.entrySet()) {
            final String login = entry.getKey();
            final String password = entry.getValue();

            runHtpasswd(htpasswdCommand, htpasswdFile, login, password, createNewFile);

            createNewFile = false;
        }
    }

    private void runHtpasswd(String htpasswdCommand, File htpasswdFile, String login, String password, boolean createNewFile) {
        final List<String> commandLine = new ArrayList<String>();
        commandLine.add(htpasswdCommand);
        commandLine.add("-b");
        if (createNewFile) {
            commandLine.add("-c");
        }
        commandLine.add(htpasswdFile.getAbsolutePath());
        commandLine.add(login);
        commandLine.add(password);

        final ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(commandLine);
        try {
            final Process process = processBuilder.start();
            final int rc = process.waitFor();
            if (rc != 0) {
                throw new RuntimeException(htpasswdCommand + " returned " + rc);
            }
        } catch (IOException e) {
            throw wrapException(e);
        } catch (InterruptedException e) {
            throw wrapException(e);
        }
    }

    private void createHtpasswdManually(Map<String, String> loginToPassword, File htpasswdFile) {
        try {
            TestUtil.writeFileContentsString(htpasswdFile, createHttpasswdFileContents(loginToPassword));
        } catch (SVNException e) {
            throw wrapException(e);
        }
    }

    private String createHttpasswdFileContents(Map<String, String> loginToPassword) {
        final StringBuilder stringBuilder = new StringBuilder();

        for (Map.Entry<String, String> entry : loginToPassword.entrySet()) {
            final String login = entry.getKey();
            final String password = entry.getValue();

            stringBuilder.append(login).append(':').append("{SHA1}").append(calculateBase64OfSha1(password)).append('\n');
        }

        return stringBuilder.toString();
    }

    private String calculateBase64OfSha1(String password) {
        return calculateBase64(calculateSha1(password));
    }

    private byte[] calculateSha1(String password) {
        final SVNChecksumInputStream inputStream = new SVNChecksumInputStream(new ByteArrayInputStream(password.getBytes()), "sha1");
        try {
            readFullyAndClose(inputStream);
        } catch (IOException e) {
            throw wrapException(e);
        }
        return inputStream.getMessageDigest().digest();
    }

    private String calculateBase64(byte[] digest) {
        return SVNBase64.byteArrayToBase64(digest);
    }

    private String readFullyAndClose(InputStream inputStream) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        try {
            while (true) {
                final int read = inputStream.read(buffer);
                if (read < 0) {
                    break;
                }
                byteArrayOutputStream.write(buffer, 0, read);
            }

            return byteArrayOutputStream.toString();
        } finally {
            SVNFileUtil.closeFile(inputStream);
        }
    }

    private static RuntimeException wrapException(Exception e) {
        return new RuntimeException(e);
    }
}
