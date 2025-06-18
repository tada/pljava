package org.postgresql.pljava.pgxs;

import org.apache.maven.plugin.AbstractMojoExecutionException;

/**
 * Enables obtaining an interface from the script using
 * {@link javax.script.Invocable} in order to correctly handle errors.
 */
public interface GoalScript {

	/**
	 * Executes the driver code for running the script.
	 * @return MojoExecutionException or MojoFailureException in case of error,
	 * null in case of successful execution
	 */
	AbstractMojoExecutionException execute();

}
