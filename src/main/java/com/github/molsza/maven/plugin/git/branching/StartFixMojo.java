package com.github.molsza.maven.plugin.git.branching;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.codehaus.plexus.util.cli.Commandline;


@Mojo(name = "start-fix", aggregator = true)
public class StartFixMojo
    extends ModelMojo {


  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    checkUncommitted(gitCmd);
    fetch(gitCmd);
    pull(gitCmd);

    String currentBranch = currentBranch(gitCmd);
    if(!currentBranch.startsWith("release/")) {
      throw new MojoFailureException("This command can be run only in release branch, current branch is " + currentBranch);
    }

    String currentVersion = getCurrentProjectVersion();
    if(currentVersion.endsWith("SNAPSHOT")) {
      throw new MojoFailureException("Seems that you already started the fix, your next version is " + currentVersion);
    }

    getLog().info("Starting the fix for version "+ currentVersion);

    mavenCmd.clearArgs();
    mavenCmd.addArguments(new String[]{"-B", String.format(MAVEN_HELPER_PLUGIN,"parse-version"), String.format(MAVEN_VERSION_PLUGIN,"set"), "-DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.nextIncrementalVersion}-SNAPSHOT", String.format(MAVEN_VERSION_PLUGIN,"commit")});
    if (executeCommand(mavenCmd, false) != 0) {
      throw new MojoFailureException("Cannot set new snapshot version");
    }

    currentVersion = getCurrentProjectVersion();
    getLog().info("Fixed version is " + currentVersion);

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

  }


}
