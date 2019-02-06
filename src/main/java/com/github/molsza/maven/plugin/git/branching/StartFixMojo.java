package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Use this goal if you need to make the fix in current release (branch should be checkout) or start working on maintenance version.
 * Goal will increment the maintenance version number.
 * Work will continue on the current release branch.
 */
@Mojo(name = "start-fix", aggregator = true)
public class StartFixMojo
    extends ModelMojo {


  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = getMavenExecutable();
    Commandline gitCmd = new Commandline();
    gitCmd.setExecutable("git");

    checkUncommitted(gitCmd);
    fetch(gitCmd);
    pull(gitCmd);

    String currentBranch = currentBranch(gitCmd);
    if(!currentBranch.startsWith(releaseBranchPrefix)) {
      throw new MojoFailureException("This command can be run only in release branch, current branch is " + currentBranch);
    }

    String currentVersion = getCurrentProjectVersion();
    if(currentVersion.endsWith("SNAPSHOT")) {
      throw new MojoFailureException("Seems that you already started the fix, your next version is " + currentVersion);
    }

    getLog().info("Starting the fix for version "+ currentVersion);

    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_HELPER_PLUGIN,"parse-version"), String.format(MAVEN_VERSION_PLUGIN,"set"), "-DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}-SNAPSHOT", String.format(MAVEN_VERSION_PLUGIN,"commit")});
    if (executeCommand(mavenCmd, false) != 0) {
      throw new MojoFailureException("Cannot set new snapshot version");
    }

    currentVersion = getCurrentProjectVersion();
    getLog().info("Fixed version is " + currentVersion);

    String newBranchName = releaseName(removeSnapshot(currentVersion));
    if(separateFixBranch) {
      getLog().info("Creating release branch " + newBranchName);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"checkout","-b", newBranchName});
      if (executeCommand(gitCmd, false) != 0) {
        getLog().error("Release branch has not been created");
      }
    }

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"commit", "-am", String.format("Start version %s", currentVersion)});
    executeCommand(gitCmd, false);

    if(separateFixBranch) {
      getLog().info("Removing local branch " + currentBranch);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"branch", "-d", currentBranch});
      executeCommand(gitCmd, true);
    }
    if(!autoPush) {
      getLog().info("Changes are in your local repository.");
      getLog().info("If you are happy with the results then run:");
    }
    if(separateFixBranch) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "-u", "origin", newBranchName + ":" + newBranchName});
      executeOrPrintPushCommand(gitCmd);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "origin", "--delete", currentBranch});
      executeOrPrintPushCommand(gitCmd);
      gitCmd.clearArgs();
    } else {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push"});
      executeOrPrintPushCommand(gitCmd);
    }
  }

  private String removeSnapshot(String version) {
    int indexOf = version.indexOf("-SNAPSHOT");
    if(indexOf > 1) {
      return version.substring(0, indexOf);
    }
    return version;
  }


}
