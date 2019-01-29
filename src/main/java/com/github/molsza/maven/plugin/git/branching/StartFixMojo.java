package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.IOException;


@Mojo(name = "start-fix", aggregator = true)
public class StartFixMojo
        extends ModelMojo {


  public void execute()
          throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    try {


      mavenCmd.clearArgs();
      mavenCmd.addArguments(new String[]{"-B", "build-helper:parse-version", "versions:set", "-DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}-SNAPSHOT", "versions:commit"});
      if (executeCommand(mavenCmd, false) != 0) {
        throw new MojoFailureException("Cannot set new snapshot version");
      }

      String currentVersion = getCurrentProjectVersion();
      getLog().info("Fix version is " + currentVersion);


      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"commit", "-am", String.format("Start version %s", currentVersion)});
      executeCommand(gitCmd, false);

      if (push) {
        gitCmd.clearArgs();
        gitCmd.addArguments(new String[]{"push"});
        executeCommand(gitCmd, true);
        getLog().info("All changes pushed");
      } else {
        getLog().info("Changes are in your local repository.");
        getLog().info("If you are happy with the results then run:");
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
