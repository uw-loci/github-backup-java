/*
 * #%L
 * Maven plugin for backing up a GitHub repository.
 * %%
 * Copyright (C) 2013 - 2014 Board of Regents of the University of
 * Wisconsin-Madison
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.backup.github;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven mojo for running {@link GitHubBackup} on git projects that are also
 * <a href="https://maven.apache.org/">maven-ized</a>.
 * <p>
 * Parameters:
 * </p>
 * <ul>
 * <li>localDir - path to local git repository</li>
 * <li>login - GitHub user name for authentication</li>
 * <li>pass - GitHub password for authentication</li>
 * <li>token - GitHub OAuth token for authentication</li>
 * <li>user - GitHub user to back up</li>
 * <li>repo - GitHub repository to back up</li>
 * <li>clean - Boolean flag. Set "true" to force a complete backup (if last
 * backup was incremental)</li>
 * </ul>
 */
@Mojo(name = "backup", aggregator = true)
public class GitHubBackupMojo extends AbstractMojo {

	// -- Parameters --

	/**
	 * Path to the local git repository to store the backup information.
	 */
	@Parameter(property = "localDir")
	private String localDir;

	/**
	 * GitHub user name (or id) to use for authentication.
	 */
	@Parameter(property = "login")
	private String login;

	/**
	 * GitHub password to use for authentication.
	 */
	@Parameter(property = "pass")
	private String pass;

	/**
	 * GitHub OAuth token to use for authentication.
	 */
	@Parameter(property = "token")
	private String token;

	/**
	 * GitHub user to backup. If absent, a repository will be backed up.
	 */
	@Parameter(property = "user")
	private String user;

	/**
	 * GitHub repository to backup. If not specified, the repository of the
	 * current working directory will be used.
	 */
	@Parameter(property = "repo")
	private String repo;

	/**
	 * Whether or not to perform a complete backup.
	 */
	@Parameter(property = "clean", defaultValue = "false")
	private boolean clean;

	// -- Mojo API Methods --

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		final String[] args =
			BackupTools.makeArgs(localDir, login, pass, token, user, repo, clean);
		GitHubBackup.main(args);
	}

	// -- Helper Methods --

}
