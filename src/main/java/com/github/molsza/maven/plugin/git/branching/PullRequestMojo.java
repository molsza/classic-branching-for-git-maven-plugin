package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Use this goal to create a pull request form feature or release branch
 * Goal will create the pr branch from main branch (master) and merged the current branch into it squashing the commits.
 * If merge conflicts appear then you may need to solve it manually.
 */
@Mojo(name = "create-pr", aggregator = true)
public class PullRequestMojo
    extends ModelMojo {

  @Component
  protected Prompter prompter;

  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = getMavenExecutable();
    Commandline gitCmd = new Commandline();
    gitCmd.setExecutable("git");

    String currentBranch = currentBranch(gitCmd);
    if (currentBranch.equals(masterBranchName)) {
      throw new MojoFailureException("Do you want to merge " + masterBranchName + " with itself?");
    }
    checkUncommitted(gitCmd);

    String commitMessage = "";
    if (settings.isInteractiveMode()) {
      try {
        commitMessage = prompter.prompt("What should be the commit massage of your work?");
      } catch (PrompterException e) {
        throw new MojoFailureException(e.getMessage());
      }
    }

    String featureVersion = getCurrentProjectVersion();

    checkout(gitCmd, masterBranchName);
    pull(gitCmd);
    String masterVersion = getCurrentProjectVersion();

    if (!masterVersion.equals(featureVersion)) {
      checkout(gitCmd, currentBranch);
      mavenCmd.clearArgs();
      mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_VERSION_PLUGIN, "set"), "-DnewVersion=" + masterVersion, String.format(MAVEN_VERSION_PLUGIN, "commit")});
      if (executeCommand(mavenCmd, false) != 0) {
        throw new MojoFailureException("Cannot set new snapshot version");
      }
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"commit", "-am", String.format("Set version %s", masterVersion)});
      executeCommand(gitCmd, false);
      checkout(gitCmd, masterBranchName);
    }

    String mergebranchName = currentBranch + "-pr";

    getLog().info("Creating branch " + mergebranchName);
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"checkout", "-b", mergebranchName});
    if (executeCommand(gitCmd, false) != 0) {
      throw new MojoFailureException("Temporary merge branch has not been created");
    }

    getLog().info("Merging " + currentBranch + " into " + mergebranchName);

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"merge", "--squash", currentBranch});
    if (executeCommand(gitCmd, true) != 0) {
      throw new MojoFailureException("There are issues with your merge. Check the logs above. Resolve it and commit manually.");
    }
    gitCmd.clearArgs();
    if (commitMessage == null || commitMessage.trim().isEmpty()) {
      gitCmd.addArguments(new String[]{"commit","-m", currentBranch});
    } else {
      gitCmd.addArguments(new String[]{"commit","-m", commitMessage});
    }

    getLog().info("Merged without conflicts");
//    getLog().info("Removing local branch " + mergebranchName);
//    gitCmd.clearArgs();
//    gitCmd.addArguments(new String[]{"branch", "-d", mergebranchName});
//    executeCommand(gitCmd, true);

    if (!autoPush) {
      getLog().info("Changes are in your local repository.");
      getLog().info("If you are happy with the results then run:");
    }
    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"push", "-u", "origin", mergebranchName + ":" + mergebranchName});
    executeOrPrintPushCommand(gitCmd);

    getLog().info("You can now create your PR " + mergebranchName + " -> " + masterBranchName);

  }

}
