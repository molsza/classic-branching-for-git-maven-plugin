package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.IOException;


@Mojo(name = "finish-fix", aggregator = true)
public class FinishFixMojo extends ModelMojo {


  public void execute()
          throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    try {


      String currentVersion = makeRelease(mavenCmd, gitCmd);

      if (push) {
        gitCmd.clearArgs();
        gitCmd.addArguments(new String[]{"push"});
        executeCommand(gitCmd, true);
        gitCmd.clearArgs();
        gitCmd.addArguments(new String[]{"push", "origin", String.format("v%s", currentVersion)});
        executeCommand(gitCmd, true);
        getLog().info(String.format("v%s", currentVersion) + " has been released");
      } else {
        getLog().info("Changes are in your local repository.");
        getLog().info("If you are happy with the results then run:");
        getLog().info(String.format(" git push origin v%s", currentVersion));
        getLog().info(" git push");
      }

    } catch (CommandLineException e) {
      throw new MojoFailureException(e.getMessage());
    } catch (InterruptedException e) {
      throw new MojoExecutionException("plugin has been interrupted");
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }


}
