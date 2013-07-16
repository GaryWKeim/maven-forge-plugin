/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package org.terracotta.forge.plugin;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.aether.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * print out dependencies of a given artifact
 * 
 * @author hhuynh
 * @goal copy-dependencies
 * @requiresDependencyResolution compile
 */
public class CopyArtifactDependenciesMojo extends AbstractResolveDependenciesMojo {
  /**
   * output dir
   * 
   * @required
   * @parameter property="outputDir"
   */
  private File                    outputDir;

  /**
   * remove version from artifact filename
   * 
   * @parameter property="removeVersion" default-value="false"
   */
  private boolean removeVersion;

  /**
   * copy dependencies
   */
  public void execute() throws MojoExecutionException {
    try {
      Collection<Artifact> deps = resolve();
      copyDeps(deps);
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void copyDeps(Collection<Artifact> deps) throws IOException {
    for (Artifact a : deps) {
      String filename = a.getFile().getName();
      if (a.isSnapshot()) {
        filename = a.getArtifactId() + "-" + a.getBaseVersion() + "." + a.getExtension();
      }
      if (removeVersion) {
        filename = a.getArtifactId() + "." + a.getExtension();
      }
      File destFile = new File(outputDir, filename);
      if (destFile.exists()) {
        FileUtils.deleteQuietly(destFile);
      }
      FileUtils.copyFile(a.getFile(), destFile);
    }
  }
}