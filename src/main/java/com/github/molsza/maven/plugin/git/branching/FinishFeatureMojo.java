package com.github.molsza.maven.plugin.git.branching;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Run this goal when you want to finish your current feature.
 * Goal merges the current branch into main branch and removes the current branch.
 */
@Mojo(name = "finish-feature", aggregator = true)
public class FinishFeatureMojo
    extends ModelMojo {

  @Component
  protected Prompter prompter;

  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline gitCmd = new Commandline();
    gitCmd.setExecutable("git");

    String featureBranch = currentBranch(gitCmd);
    if (!featureBranch.startsWith(featureBranchPrefix)) {
      throw new MojoFailureException("This command can be run only in feature branch, current branch is " + featureBranch);
    }

    checkUncommitted(gitCmd);

    String commitMessage = "";
    if (settings.isInteractiveMode()) {
      try {
        commitMessage = prompter.prompt("What should be merge message (empty for default)?");
      } catch (PrompterException e) {
        throw new MojoFailureException(e.getMessage());
      }
    }

    boolean notrack = false;
    try {
      pull(gitCmd);
    } catch (MojoFailureException e) {
      notrack = true;
    }
    checkout(gitCmd, masterBranchName);
    pull(gitCmd);

    gitCmd.clearArgs();
    if(commitMessage == null || commitMessage.trim().isEmpty()) {
      gitCmd.addArguments(new String[]{"merge", "--no-ff", featureBranch});
    } else {
      gitCmd.addArguments(new String[]{"merge", "--no-ff","-m",commitMessage, featureBranch});
    }
    if (executeCommand(gitCmd, true) != 0) {
      throw new MojoFailureException("There are issues with your merge. Check the logs above.");
    }

    if (deleteLocalBranch(gitCmd, featureBranch) == 0) {
      getLog().info("Local branch deleted: " + featureBranch);
      if (!notrack) {
        gitCmd.clearArgs();
        gitCmd.addArguments(new String[]{"push", "origin", "--delete", featureBranch});
        if (autoPush) {
          if (executeCommand(gitCmd, false) == 0) {
            getLog().info("Remote branch deleted: " + featureBranch);
          }
        } else {
          getLog().info("Your branch still exists in remote. To remove it run:");
          getLog().info(gitCmd.toString());
        }
      }
    }

    getLog().info("Feature branch " + featureBranch + " has been merged to " + masterBranchName);
    push(gitCmd);

  }

}
