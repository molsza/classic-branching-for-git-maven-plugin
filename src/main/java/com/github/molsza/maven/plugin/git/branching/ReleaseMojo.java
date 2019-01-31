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
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");


    checkUncommitted(gitCmd);
    checkout(gitCmd, masterBranchName);
    fetch(gitCmd);
    pull(gitCmd);

    String currentVersion = makeRelease(mavenCmd, gitCmd);

    getLog().info("Creating release branch " + releaseName(currentVersion));
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"branch", releaseName(currentVersion), releaseTagName(currentVersion)});
    if (executeCommand(gitCmd, false) != 0) {
      getLog().error("Release branch has not been created");
    }

    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_HELPER_PLUGIN,"parse-version"), String.format(MAVEN_VERSION_PLUGIN,"set"), "-DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.nextMinorVersion}.0-SNAPSHOT", String.format(MAVEN_VERSION_PLUGIN,"commit")});
    if (executeCommand(mavenCmd, false) != 0) {
      throw new MojoFailureException("Cannot set new snapshot version");
    }

    String newSnapshotVersion = getCurrentProjectVersion();

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"commit", "-am", String.format("Start version %s", newSnapshotVersion)});
    executeCommand(gitCmd, false);

    if (autoPush) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "-u", "origin", releaseName(currentVersion)+":"+releaseName(currentVersion)});
      executeCommand(gitCmd, true);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push"});
      executeCommand(gitCmd, true);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "origin", releaseTagName(currentVersion)});
      executeCommand(gitCmd, true);
    } else {
      getLog().info("Changes are in your local repository.");
      getLog().info("If you are happy with the results then run:");
      getLog().info(String.format(" git push -u origin %s:%s", releaseName(currentVersion),releaseName(currentVersion)));
      getLog().info(String.format(" git push origin %s", releaseTagName(currentVersion)));
      getLog().info(" git push");
    }
    getLog().info(releaseTagName(currentVersion) + " has been released");
  }

}
