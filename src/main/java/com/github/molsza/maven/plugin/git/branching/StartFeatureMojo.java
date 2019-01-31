package com.github.molsza.maven.plugin.git.branching;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Use this goal to start new feature.
 * The new feature will start form your current branch, but finish goal will always merged it to main branch.
 * This is the only goal which doesn't require you to have no uncommitted changes.
 */
@Mojo(name = "start-feature", aggregator = true)
public class StartFeatureMojo
    extends ModelMojo {

  @Parameter(property = "feature-name")
  private String featureName;

  @Component
  protected Prompter prompter;

  public void execute()
      throws MojoExecutionException, MojoFailureException {
    Commandline mavenCmd = new Commandline();
    Commandline gitCmd = new Commandline();
    mavenCmd.setExecutable("mvn");
    gitCmd.setExecutable("git");

    checkout(gitCmd, masterBranchName);

    while (settings.isInteractiveMode() && (featureName == null || featureName.trim().isEmpty())) {
      try {
        featureName = prompter.prompt("Name of new feature");
      } catch (PrompterException e) {
        throw new MojoFailureException(e.getMessage());
      }
    }

    featureName = featureName.replace(" ","_");
    featureName = featureName(featureName);

    String currentVersion = getCurrentProjectVersion();
    getLog().info(" " + currentVersion);

    gitCmd.clearArgs();
    gitCmd.addArguments(new String[]{"checkout", "-b", featureName});
    executeCommand(gitCmd, true);

    if (autoPush) {
      gitCmd.clearArgs();
      gitCmd.addArguments(new String[]{"autoPush", "origin", featureName});
      executeCommand(gitCmd, true);
    } else {
      getLog().info("Changes are in your local repository.");
      getLog().info("If you are happy with the results then run:");
      getLog().info(" git autoPush origin " + featureName);
    }

  }

}
