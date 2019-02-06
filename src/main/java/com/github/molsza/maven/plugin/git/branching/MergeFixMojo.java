package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Use this goal if you need to merge the selected fix to master
 * Goal will create the branch from current commit and will equalize the version with main branch
 */
@Mojo(name = "merge-fix", aggregator = true)
public class MergeFixMojo
    extends ModelMojo {


  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = getMavenExecutable();
    Commandline gitCmd = new Commandline();
    gitCmd.setExecutable("git");

    checkUncommitted(gitCmd);

    String currentBranch = currentBranch(gitCmd);
    if (!currentBranch.startsWith(releaseBranchPrefix)) {
      throw new MojoFailureException("This command can be run only in release branch, current branch is " + currentBranch);
    }

    checkout(gitCmd, masterBranchName);
    pull(gitCmd);
    String masterVersion = getCurrentProjectVersion();
    checkout(gitCmd, currentBranch);

    getLog().info("Merging " + currentBranch + " into " + masterBranchName);

    String mergebranchName = currentBranch + "-merge";

    getLog().info("Creating merging branch " + mergebranchName);
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"checkout", "-b", mergebranchName});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("Temporary merge branch has not been created");
    }

    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_VERSION_PLUGIN, "set"), "-DnewVersion=" + masterVersion, String.format(MAVEN_VERSION_PLUGIN, "commit")});
    if (executeCommand(mavenCmd, false) != 0) {
      throw new MojoFailureException("Cannot change version to" + masterVersion);
    }

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"commit", "-am", String.format("Setting version " + masterVersion)});
    executeCommand(gitCmd, false);

    checkout(gitCmd, masterBranchName);
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"merge", "--no-ff", "-m", "Merge " + currentBranch, mergebranchName});
    if (executeCommand(gitCmd, true) != 0) {
      throw new MojoFailureException("There are issues with your merge. Check the logs above. Resolve it and commit manually.");
    }

    getLog().info("Merged without conflicts");
    getLog().info("Removing local branch " + mergebranchName);
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"branch", "-d", mergebranchName});
    executeCommand(gitCmd, true);

    if (!autoPush) {
      getLog().info("Changes are in your local repository.");
      getLog().info("If you are happy with the results then run:");
    }
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"push"});
    executeOrPrintPushCommand(gitCmd);
  }

}
