package com.github.molsza.maven.plugin.git.branching;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public abstract class ModelMojo extends AbstractMojo {
  @Parameter(defaultValue = "false", property = "autopush")
  protected Boolean push;
  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession mavenSession;
  @Parameter(defaultValue = "${project.version}", required = true)
  private String projectVersion;

  @Parameter(defaultValue = "false", property = "removeSnapshotsFromRelease")
  protected Boolean removeSnapshots;

  protected void pomRevert(Commandline mavenCmd) throws CommandLineException, InterruptedException, IOException {
    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", "versions:revert"});
    executeCommand(mavenCmd, true);
  }

  protected int executeCommand(Commandline mavenCmd, boolean showOutput) throws CommandLineException, InterruptedException, IOException {
    Process process;
    int exitCode;
    getLog().debug(mavenCmd.toString());
    process = mavenCmd.execute();
    BufferedReader bufferedInputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
    exitCode = process.waitFor();
    String line;
    while ((line = bufferedInputStream.readLine()) != null) {
      if (exitCode != 0) getLog().info(mavenCmd.toString());
      if (showOutput || exitCode != 0) {
        getLog().info(line);
      } else {
        getLog().debug(line);
      }
    }
    return exitCode;
  }

  protected String getCurrentProjectVersion() throws MojoFailureException {
    try {
      MavenXpp3Reader mavenReader = new MavenXpp3Reader();
      FileReader fileReader = new FileReader(mavenSession.getCurrentProject().getFile().getAbsoluteFile());
      try {
        final Model model = mavenReader.read(fileReader);
        if (model.getVersion() == null) {
          throw new MojoFailureException("Maven version undetermined");
        }

        return model.getVersion();
      } finally {
        if (fileReader != null) {
          fileReader.close();
        }
      }
    } catch (Exception e) {
      throw new MojoFailureException(e.getMessage());
    }
  }

  protected void checkUncommitted(Commandline gitCmd) throws CommandLineException, InterruptedException, IOException, MojoFailureException {
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"diff", "--exit-code"});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("You have upstaged changes. Commit, remove or stash them first.");
    }
    gitCmd.addArguments(new String[]{"--cached"});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("You have uncommitted changes. Commit, remove or stash them first.");
    }
  }


  protected String makeRelease(Commandline mavenCmd, Commandline gitCmd) throws CommandLineException, InterruptedException, IOException, MojoFailureException {
    if (removeSnapshots) {
      getLog().info("Replacing snapshots dependencies with its release versions and setting the release version");
      mavenCmd.addArguments(new String[]{"-B", "versions:use-releases", "-DfailIfNotReplaced=true", "-DgenerateBackupPoms=true", "versions:set", "-DremoveSnapshot=true", "verify"});
      if (executeCommand(mavenCmd, true) != 0) {
        pomRevert(mavenCmd);
        throw new MojoFailureException("Cannot set new version, check logs for details");
      }
    } else {
      getLog().info("Setting the release version");
      mavenCmd.addArguments(new String[]{"-B", "-DgenerateBackupPoms=true", "versions:set", "-DremoveSnapshot=true", "verify"});
      if (executeCommand(mavenCmd, false) != 0) {
        pomRevert(mavenCmd);
        throw new MojoFailureException("Cannot set new version, check logs for details");
      }
    }

    String currentVersion = getCurrentProjectVersion();
    getLog().info("Current released version is " + currentVersion);

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"commit", "-am", String.format("Release %s",currentVersion)});

    if (executeCommand(gitCmd, false) != 0) {
      pomRevert(mavenCmd);
      throw new MojoFailureException("Cannot commit version updates");
    }

    getLog().info("Create tag " + String.format("v%s", currentVersion));
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"tag", "-a", String.format("v%s", currentVersion), "-m", String.format("Version %s", currentVersion)});

    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("Cannot create a tag");
    }
    return currentVersion;
  }

}
