package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.IOException;


@Mojo(name = "release", aggregator = true)
public class ReleaseMojo
        extends ModelMojo {

  public void execute()
          throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    try {
      checkUncommitted(gitCmd);

      String currentVersion = makeRelease(mavenCmd, gitCmd);


      getLog().info("Creating release branch " + String.format("release/%s", currentVersion));
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"branch", String.format("release/%s", currentVersion), String.format("v%s", currentVersion)});
      if (executeCommand(gitCmd, false) != 0) {
        getLog().error("Release branch has not been created");
      }

      mavenCmd.clearArgs();
      mavenCmd.addArguments(new String[]{"-B", "build-helper:parse-version", "versions:set", "-DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.nextMinorVersion}.0-SNAPSHOT", "versions:commit"});
      if (executeCommand(mavenCmd, false) != 0) {
        throw new MojoFailureException("Cannot set new snapshot version");
      }

      String newSnapshotVersion = getCurrentProjectVersion();

      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"commit", "-am", String.format("Start version %s", newSnapshotVersion)});
      executeCommand(gitCmd, false);

      if (push) {
        gitCmd.clearArgs();
        gitCmd.addArguments(new String[]{"push", "-u", "origin", String.format("release/%s:release/%s", currentVersion, currentVersion)});
        executeCommand(gitCmd, true);
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
        getLog().info(String.format(" git push -u origin release/%s:release/%s", currentVersion, currentVersion));
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
