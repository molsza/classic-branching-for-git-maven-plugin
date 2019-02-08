package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Use this goal when you want to make a new release for main branch.
 * Goal will remove the snapshots versions, create a tag, create the support branch, and increment the snapshot version in main branch.
 */
@Mojo(name = "release", aggregator = true)
public class ReleaseMojo
    extends ModelMojo {

  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = getMavenExecutable();
    Commandline gitCmd = new Commandline();
    gitCmd.setExecutable("git");

    checkUncommitted(gitCmd);
    checkout(gitCmd, masterBranchName);
    fetch(gitCmd);
    pull(gitCmd);

    String currentVersion = makeRelease(mavenCmd, gitCmd);

    try {
      getLog().info("Creating release branch " + releaseName(currentVersion));
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"branch", releaseName(convertToReleaseBranch(currentVersion)), releaseTagName(currentVersion)});
      if (executeCommand(gitCmd, false) != 0) {
        throw new MojoFailureException("Release branch has not been created");
      }

      mavenCmd.clearArgs();
      mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_HELPER_PLUGIN, "parse-version"), String.format(MAVEN_VERSION_PLUGIN, "set"), "-DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.nextMinorVersion}.0-SNAPSHOT", String.format(MAVEN_VERSION_PLUGIN, "commit")});
      if (executeCommand(mavenCmd, false) != 0) {
        throw new MojoFailureException("Cannot set new snapshot version");
      }

      String newSnapshotVersion = getCurrentProjectVersion();

      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"commit", "-am", String.format("Start version %s", newSnapshotVersion)});
      executeCommand(gitCmd, false);

      if (!autoPush) {
        getLog().info("Changes are in your local repository.");
        getLog().info("If you are happy with the results then run:");
      }
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "-u", "origin", releaseName(convertToReleaseBranch(currentVersion)) + ":" + releaseName(convertToReleaseBranch(currentVersion))});
      executeOrPrintPushCommand(gitCmd);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push"});
      executeOrPrintPushCommand(gitCmd);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "origin", releaseTagName(currentVersion)});
      executeOrPrintPushCommand(gitCmd);
      getLog().info(releaseTagName(currentVersion) + " has been released");
    } catch (Exception e) {
      pomRevert(mavenCmd);
    }
  }

  private String convertToReleaseBranch(String version) {
    if (!separateFixBranch) {
      String[] verArray = version.split("\\.");
      version = "";
      if (verArray.length >= 1) {
        version += verArray[0];
      }
      if (verArray.length >= 2) {
        version += "." + verArray[1] + ".x";
      } else {
        version += ".0.x";
      }
    }
    return version;
  }

}
