/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin.util;

import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.surefire.AbstractSurefireMojo;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author hhuynh
 */
public class Util {

  public static final String LAST_CHANGED_REV = "Last Changed Rev";
  public static final String URL              = "URL";
  public static final String         UNKNOWN          = "unknown";


  /**
   * Run a shell command and return the output as String
   */
  public static String exec(String command, List<String> params, File workDir, Log log) {
    File outputFile;
    try {
      outputFile = File.createTempFile("exec", ".out");
      outputFile.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Project dummyProject = new Project();
    dummyProject.init();

    ExecTask execTask = new ExecTask();
    execTask.setProject(dummyProject);
    execTask.setOutput(outputFile);
    execTask.setDir(workDir != null ? workDir : new File(System.getProperty("user.dir")));
    execTask.setExecutable(command);
    execTask.setResultProperty("svninfoexitcode");
    if (params != null) {
      for (String param : params) {
        execTask.createArg().setValue(param);
      }
    }

    FileReader reader = null;
    try {
      execTask.execute();
      reader = new FileReader(outputFile);
      return IOUtils.toString(reader);
    } catch (Exception e) {
      // This should not be terminal, for example if svn is not installed or this is not an svn project
      log.warn("Unable to use svn info : " + e);
    } finally {
      IOUtils.closeQuietly(reader);
      outputFile.delete();
    }
    return "";
  }

  public static SCMInfo getScmInfo(String svnRepo, Log log) throws IOException {

    SCMInfo scmInfo = getSvnInfo(new File(svnRepo).getCanonicalPath(), log);
    if (scmInfo != null && scmInfo.url != null) {
      return scmInfo;
    }

    scmInfo = getGitInfo(svnRepo, log);
    if (scmInfo == null) {
      scmInfo = new SCMInfo(); //not null, for convenine
    }
    return scmInfo;
  }

  public static SCMInfo getSvnInfo(String svnRepo, Log log) throws IOException {
    String svnCommand = "svn";
    String svnHome = System.getenv("SVN_HOME");
    if (svnHome != null) {
      svnCommand = svnHome + "/bin/svn";
    }
    //This is for ease of testing
    svnHome = System.getProperty("SVN_HOME");
    if (svnHome != null) {
      svnCommand = svnHome + "/bin/svn";
    }

    if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
      if (new File(svnCommand + ".bat").exists()) {
        svnCommand += ".bat";
      }
    }

    String result = exec(svnCommand, Arrays.asList("info", svnRepo), null, log);
    log.debug("svn info " + svnRepo + ": " + result);
    return parseSvnInfo(result);

  }


  public static SCMInfo getGitInfo(String gitRepo, Log log) {
    SCMInfo result = new SCMInfo();


    try (Repository repository = new FileRepositoryBuilder().readEnvironment()
            .findGitDir(new File(gitRepo)).setMustExist(true).build()) {

      String remoteName = repository.getRemoteNames().stream().filter(name ->
              Stream.of("upstream", "origin").anyMatch(it -> it.equals(name))
      ).findFirst().orElse(null);
      if (remoteName == null) {
        log.info("Failed to find a standard remote name at " + gitRepo);
        return null;
      }
      result.url = repository.getConfig().getString("remote", remoteName, "url");
      result.branch = repository.getBranch();
      result.revision = repository.resolve(Constants.HEAD).getName();

    } catch (IllegalArgumentException ix) {
      // means there is no git repo
      return null;
    } catch (Exception e) {
      log.info("Failed to read git info from " + gitRepo, e);
      // partial read?  Let's not return partial data
      return null;
    }

    return result;
  }


  public static String getZipEntries(File file) throws IOException {
    StringBuilder buff = new StringBuilder();
    ZipFile zipFile = new ZipFile(file);
    try {
      Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
      while (zipEntries.hasMoreElements()) {
        buff.append((zipEntries.nextElement().getName())).append("\n");
      }
      return buff.toString();
    } finally {
      zipFile.close();
    }
  }

  public static SCMInfo parseSvnInfo(String svnInfo) throws IOException {
    Properties props = new Properties();
    BufferedReader br = new BufferedReader(new StringReader(svnInfo));
    String line = null;
    while ((line = br.readLine()) != null) {
      String[] tokens = line.split(":", 2);
      if (tokens.length == 2) {
        props.put(tokens[0].trim(), tokens[1].trim());
      }
    }

    if (props.size() < 3) {
      //definitely failed
      return null;
    }

    SCMInfo result = new SCMInfo();
    result.url = props.getProperty(URL, UNKNOWN);
    result.revision = props.getProperty(LAST_CHANGED_REV, UNKNOWN);
    result.branch = guessBranchOrTagFromUrl(result.url);
    return result;
  }


  public static String guessBranchOrTagFromUrl(String url) {
    if (url.contains("trunk")) return "trunk";
    int startIndex = url.indexOf("branches/");
    if (startIndex > 0) {
      int endIndex = url.indexOf("/", startIndex + 9);
      if (endIndex < 0) {
        endIndex = url.length();
      }
      return url.substring(startIndex + 9, endIndex);
    }
    startIndex = url.indexOf("tags/");
    if (startIndex > 0) {
      int endIndex = url.indexOf("/", startIndex + 5);
      if (endIndex < 0) {
        endIndex = url.length();
      }
      return url.substring(startIndex + 5, endIndex);
    }
    return UNKNOWN;
  }

  public static boolean isEmpty(String s) {
    return s == null || s.length() == 0;
  }



  /**
   * Adds configurable toolchains support, allowing to specify toolchains to
   * use for just this plugin execution.
   *
   * if a <toolchains></toolchains> block is provided in configuration,
   * find the first matching toolchain and set AbstractSurefireMojo.jvm (private) so that
   * surefire/failsafe use it during execution.
   * This also sets JAVA_HOME in test environment to the same JVM
   *
   * @throws MojoExecutionException
   */
  public static void overrideToolchainConfiguration(Map<String, String> toolchainSpec, ToolchainManager manager, MavenSession session, Log logger, AbstractSurefireMojo pluginInstance) throws MojoExecutionException {

    if ( toolchainSpec != null && toolchainSpec.size() > 0 && manager != null ) {
      String javaExecutableFromToolchain;
      String javaHomeFromToolchain;
      List<Toolchain> toolchains = manager.getToolchains(session, "jdk", toolchainSpec);
      if (toolchains.size() > 0) {
        Toolchain selectedToolchain = toolchains.get(0);
        javaExecutableFromToolchain = selectedToolchain.findTool("java");
        javaHomeFromToolchain = ((DefaultJavaToolChain)selectedToolchain).getJavaHome();
        if (!new File(javaExecutableFromToolchain).canExecute()) {
          throw new MojoExecutionException("Identified matching toolchain " + javaExecutableFromToolchain
                  + " but it is not an executable file");
        }
        logger.info("Setting surefire's jvm to " + javaExecutableFromToolchain
                + " from toolchain " + selectedToolchain + ", requirements: " + toolchainSpec);
      } else {
        throw new MojoExecutionException("Unable to find a matching toolchain for configuration " + toolchainSpec);
      }

      //unfortunately, current AbstractSurefireMojo.getEffectiveJvm()
      // accesses the jvm field directly, not through getJvm(),
      // so we have to hack this:
      try {
        Field jvmField = AbstractSurefireMojo.class.getDeclaredField("jvm");
        jvmField.setAccessible(true);
        jvmField.set(pluginInstance, javaExecutableFromToolchain);

        //we also want to set JAVA_HOME for the test jvm so subprocesses that do odd things
        //like spawn more jvms will do it right
        Map<String, String> environmentVariables = pluginInstance.getEnvironmentVariables();
        environmentVariables.put("JAVA_HOME", javaHomeFromToolchain);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new MojoExecutionException("Unable to set jvm field in superclass to " + javaExecutableFromToolchain, e);
      }
    }
  }

}
