package com.github.molsza.maven.plugin.git.branching;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Commandline;

@Mojo(name = "finish-feature", aggregator = true)
public class FinishFeatureMojo
    extends ModelMojo {

  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    String featureBranch = currentBranch(gitCmd);
    if(!featureBranch.startsWith("feature/")) {
      throw new MojoFailureException("This command can be run only in feature branch, current branch is " + featureBranch);
    }

    checkUncommitted(gitCmd);

    pull(gitCmd);
    checkout(gitCmd, masterBranch);
    pull(gitCmd);

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"merge", "--no-ff", featureBranch});
    if(executeCommand(gitCmd, true) != 0) {
      throw new MojoFailureException("There are issues with your merge. Check the logs above.");
    }

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"branch","-d", featureBranch});
    if(executeCommand(gitCmd, false) == 0) {
      getLog().info("Local branch deleted: "+featureBranch);
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"push", "origin", "--delete", featureBranch});
      if(push) {
        if(executeCommand(gitCmd,false) == 0) {
          getLog().info("Remote branch deleted: "+featureBranch);
        }
      } else {
        getLog().info("Your branch still exists in remote. To remove it run:");
        getLog().info(gitCmd.toString());
      }
    }

    getLog().info("Feature branch "+featureBranch + " has been merged to "+masterBranch);
    push(gitCmd);

  }

}
