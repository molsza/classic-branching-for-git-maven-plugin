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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.Map;

public abstract class ModelMojo extends AbstractMojo {


  final public static String MAVEN_HELPER_PLUGIN = "org.codehaus.mojo:build-helper-maven-plugin:%s";
  final public static String MAVEN_VERSION_PLUGIN = "org.codehaus.mojo:versions-maven-plugin:%s";

  @Parameter(defaultValue = "false",  property = "autoPush")
  protected Boolean autoPush;

  @Parameter(defaultValue = "false", property = "autoPull")
  protected Boolean autoPull;

  @Parameter(defaultValue = "${session}", readonly = true)
  protected MavenSession mavenSession;

//  @Parameter(defaultValue = "${project.version}", required = true)
//  private String projectVersion;

  @Parameter(defaultValue = "master", required = true, property = "masterBranchName")
  protected String masterBranchName;

  @Parameter(defaultValue = "false", property = "removeSnapshotsFromReleasePom")
  protected Boolean removeSnapshotsFromReleasePom;

  @Parameter(defaultValue = "release", property = "releaseBranchPrefix")
  protected String releaseBranchPrefix;

  @Parameter(defaultValue = "feature", property = "featureBranchPrefix")
  protected String featureBranchPrefix;

  @Parameter(defaultValue = "v", property = "releaseTagPrefix")
  protected String releaseTagPrefix;

  @Parameter(defaultValue = "false", property = "separateFixBranch")
  protected Boolean separateFixBranch;

  @Parameter(defaultValue = "${settings}", readonly = true)
  protected Settings settings;

  protected void pomRevert(Commandline mavenCmd) throws MojoExecutionException {
    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_VERSION_PLUGIN, "revert")});
    executeCommand(mavenCmd, true);
  }

  protected void pomCommit(Commandline mavenCmd) throws MojoExecutionException {
    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_VERSION_PLUGIN, "commit")});
    executeCommand(mavenCmd, true);
  }

  protected int executeCommand(Commandline commandline, boolean showOutput) throws MojoExecutionException {
    return executeCommand(commandline, showOutput, null);
  }

  protected int executeCommand(Commandline commandline, boolean showOutput, BufferedWriter writer) throws MojoExecutionException {
    Process process;
    int exitCode = -1;
    if(showOutput) {
      getLog().info("Executing: " + commandline.toString());
    } else {
      getLog().debug("Executing: " + commandline.toString());
    }
    try {
      process = commandline.execute();
      StringWriter internalWriter = new StringWriter();
      BufferedReader bufferedInputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = bufferedInputStream.readLine()) != null) {
        if (writer != null) {
          writer.append(line);
          writer.newLine();
        }
        internalWriter.append(line);
        internalWriter.append("\n");
        if (showOutput) {
          getLog().info(line);
        } else {
          getLog().debug(line);
        }
      }
      exitCode = process.waitFor();
      if(exitCode != 0) {
        getLog().debug("Exit code is "+exitCode);
      }
      if(exitCode == 0 && (commandline.toString().contains("push") || commandline.toString().contains("pull"))) {
        String output = internalWriter.toString();
        if(output.contains("There is no tracking information for the current branch") || output.contains("fatal")) {
          exitCode = 1;
          getLog().info(output);
        }
      }
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
    if (autoPull) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"pull"});
      if (executeCommand(gitCmd, true) != 0) {
        throw new MojoFailureException("Cannot pull changes from remote repository");
      }
    }
  }

  protected void push(Commandline gitCmd) throws MojoExecutionException, MojoFailureException {
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"push"});
    if (autoPush) {
      if (executeCommand(gitCmd, true) != 0) {
        throw new MojoFailureException("Cannot pull changes from remote repository");
      }
    } else {
      getLog().info("Automatic push is disabled (enable them using autoPush). If you want to push your local changes then run:");
      getLog().info(gitCmd.toString());
    }
    gitCmd.clearArgs();
  }


  protected String makeRelease(Commandline mavenCmd, Commandline gitCmd) throws MojoFailureException, MojoExecutionException {
    if (removeSnapshotsFromReleasePom) {
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

    getLog().info("Create tag " + releaseTagName(currentVersion));
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"tag", "-a", releaseTagName(currentVersion), "-m", String.format("Version %s", currentVersion)});

    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("Cannot create a tag");
    }
    return currentVersion;
  }

  protected void fetch(Commandline gitCmd) throws MojoExecutionException {
    if (autoPull) {
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

  protected String releaseName(String name) throws MojoFailureException {
    return addPrefixToName(name, releaseBranchPrefix);
  }

  protected String featureName(String name) throws MojoFailureException {
    return addPrefixToName(name, featureBranchPrefix);
  }

  protected String releaseTagName(String name) {
    return releaseTagPrefix+name;
  }

  protected String addPrefixToName(String name, String prefix) throws MojoFailureException {
    if(prefix == null || prefix.isEmpty()) {
      return name;
    } else {
      if(name.contains("/")){
        if(name.startsWith(prefix)) {
          return name;
        } else {
          throw new MojoFailureException("Branch "+name+" should be prefixed by: "+ prefix);
        }
      } else {
        return prefix + "/" + name;
      }
    }
  }

  protected void executeOrPrintPushCommand(Commandline gitCmd) throws MojoExecutionException {
    if (autoPush) {
      executeCommand(gitCmd, true);
    } else {
      getLog().info(gitCmd.toString());
    }
  }

  protected Commandline getMavenExecutable() {
    Commandline cmd = new Commandline();
    String home = System.getProperties().getProperty("maven.home");
    if(home == null || home.isEmpty()) {
      cmd.setExecutable("mvn");
    } else {
      if(!home.endsWith(File.separator)) {
        home += File.separator;
      }
      cmd.setExecutable(home+"bin"+File.separator+"mvn");
    }
    return cmd;
  }

}
