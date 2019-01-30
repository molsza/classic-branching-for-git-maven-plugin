package com.github.molsza.maven.plugin.git.branching;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

public abstract class ModelMojo extends AbstractMojo {


  final public static String MAVEN_HELPER_PLUGIN = "org.codehaus.mojo:build-helper-maven-plugin:%s";
  final public static String MAVEN_VERSION_PLUGIN = "org.codehaus.mojo:versions-maven-plugin:%s";

  @Parameter(defaultValue = "false", property = "auto-push")
  protected Boolean push;

  @Parameter(defaultValue = "false", property = "auto-pull")
  protected Boolean pull;

  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession mavenSession;
  @Parameter(defaultValue = "${project.version}", required = true)
  private String projectVersion;

  @Parameter(defaultValue = "master", required = true)
  protected String masterBranch;

  @Parameter(defaultValue = "false", property = "remove-snapshots-from-release-pom")
  protected Boolean removeSnapshots;

  @Parameter(defaultValue = "${settings}", readonly = true)
  protected Settings settings;

  protected void pomRevert(Commandline mavenCmd) throws MojoExecutionException {
    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_VERSION_PLUGIN, "revert")});
    executeCommand(mavenCmd, true);
  }

  protected int executeCommand(Commandline commandline, boolean showOutput) throws MojoExecutionException {
    return executeCommand(commandline, showOutput, null);
  }

  protected int executeCommand(Commandline commandline, boolean showOutput, BufferedWriter writer) throws MojoExecutionException {
    Process process;
    int exitCode = -1;
    getLog().debug(commandline.toString());
    try {
      process = commandline.execute();
      BufferedReader bufferedInputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = bufferedInputStream.readLine()) != null) {
        if (writer != null) {
          writer.append(line);
          writer.newLine();
        }
        if (showOutput) {
          getLog().info(line);
        } else {
          getLog().debug(line);
        }
      }
      exitCode = process.waitFor();
      if (writer != null) writer.flush();
    } catch (IOException e) {//nop}
    } catch (InterruptedException e) {
      throw new MojoExecutionException(e.getMessage());
    } catch (CommandLineException e) {
      getLog().error("Cannot execute " + commandline.toString());
      throw new MojoExecutionException(e.getMessage());
    }

    return exitCode;
  }

  protected String getCurrentProjectVersion() throws MojoFailureException {
    MavenXpp3Reader mavenReader = new MavenXpp3Reader();
    try (FileReader fileReader = new FileReader(mavenSession.getCurrentProject().getFile().getAbsoluteFile())) {
      final Model model = mavenReader.read(fileReader);
      if (model.getVersion() == null) {
        throw new MojoFailureException("Maven version undetermined");
      }
      return model.getVersion();
    } catch (Exception e) {
      throw new MojoFailureException(e.getMessage());
    }
  }

  protected void checkUncommitted(Commandline gitCmd) throws MojoFailureException, MojoExecutionException {
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"diff", "--exit-code"});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("You have uncommitted changes. Commit, remove or stash them first.");
    }
    gitCmd.addArguments(new String[]{"--cached"});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("You have uncommitted changes. Commit, remove or stash them first.");
    }
  }

  protected void checkout(Commandline gitCmd, String branch) throws MojoExecutionException, MojoFailureException {
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"checkout", branch});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("Cannot checkout " + branch + " branch");
    }
  }

  protected void pull(Commandline gitCmd) throws MojoExecutionException, MojoFailureException {
    if (pull) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"pull"});
      if (executeCommand(gitCmd, true) != 0) {
        throw new MojoFailureException("Cannot pull changes");
      }
    }
  }

  protected void push(Commandline gitCmd) throws MojoExecutionException, MojoFailureException {
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"push"});
    if (push) {
      if (executeCommand(gitCmd, true) != 0) {
        throw new MojoFailureException("Cannot pull changes");
      }
    } else {
      getLog().info("Automatic push is disabled. If you want to push your local changes then run:");
      getLog().info(gitCmd.toString());
    }
    gitCmd.clearArgs();
  }


  protected String makeRelease(Commandline mavenCmd, Commandline gitCmd) throws MojoFailureException, MojoExecutionException {
    if (removeSnapshots) {
      getLog().info("Replacing snapshots dependencies with its release versions and setting the release version");
      mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_VERSION_PLUGIN, "use-releases"), "-DfailIfNotReplaced=true", "-DgenerateBackupPoms=true", String.format(MAVEN_VERSION_PLUGIN, "set"), "-DremoveSnapshot=true", "verify"});
      if (executeCommand(mavenCmd, true) != 0) {
        pomRevert(mavenCmd);
        throw new MojoFailureException("Cannot set new version, check logs for details");
      }
    } else {
      getLog().info("Setting the release version");
      mavenCmd.addArguments(new String[]{"-B", "-DgenerateBackupPoms=true", String.format(MAVEN_VERSION_PLUGIN, "set"), "-DremoveSnapshot=true", "verify"});
      if (executeCommand(mavenCmd, false) != 0) {
        pomRevert(mavenCmd);
        throw new MojoFailureException("Cannot set new version, check logs for details");
      }
    }

    String currentVersion = getCurrentProjectVersion();
    getLog().info("Current released version is " + currentVersion);

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"commit", "-am", String.format("Release %s", currentVersion)});

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

  protected void fetch(Commandline gitCmd) throws MojoExecutionException {
    if (pull) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"fetch"});
      executeCommand(gitCmd, true);
    }
  }

  protected String currentBranch(Commandline gitCmd) throws MojoExecutionException, MojoFailureException {
    StringWriter writer = new StringWriter();
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"rev-parse", "--abbrev-ref", "HEAD"});
    if (executeCommand(gitCmd, false, new BufferedWriter(writer)) != 0) {
      throw new MojoFailureException("Cannot determine the branch name, are you in a git repository?");
    } else {
      return writer.getBuffer().toString().trim();
    }
  }
}
