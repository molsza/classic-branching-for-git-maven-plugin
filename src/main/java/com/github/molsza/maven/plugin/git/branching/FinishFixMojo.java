package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Use this goal when you want to conclude your work on a current hotfix.
 * You should be in release branch you want to complete.
 * Goal will set the new versions. Verify the build. And create a tag.
 */
@Mojo(name = "finish-fix", aggregator = true)
public class FinishFixMojo extends ModelMojo {


  public void execute()
          throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    checkUncommitted(gitCmd);

    String currentBranch = currentBranch(gitCmd);
    if(!currentBranch.startsWith(releaseBranchPrefix)) {
      throw new MojoFailureException("This command can be run only in release branch, current branch is " + currentBranch);
    }

    String currentVersion = getCurrentProjectVersion();
    if(!currentVersion.endsWith("SNAPSHOT")) {
      throw new MojoFailureException("You should first run start-fix command. Your current version is not a snapshot");
    }

    currentVersion = makeRelease(mavenCmd, gitCmd);
    if (autoPush) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"autoPush"});
      executeCommand(gitCmd, true);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"autoPush", "origin", releaseTagName(currentVersion)});
      executeCommand(gitCmd, true);
    } else {
      getLog().info("Changes are in your local repository.");
      getLog().info("If you are happy with the results then run:");
      getLog().info(String.format(" git autoPush origin %s", releaseTagName(currentVersion)));
      getLog().info(" git autoPush");
    }
    getLog().info(releaseTagName(currentVersion) + " has been released");

  }


}
