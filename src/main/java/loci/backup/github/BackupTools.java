/*
 * #%L
 * Maven plugin for backing up github state.
 * %%
 * Copyright (C) 2013 - 2014 Board of Regents of the University of
 *           Wisconsin-Madison
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
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.backup.github;

/**
 * Utility class for use with {@link GitHubBackup} wrappers, such as the
 * {@link GitHubBackupGUI} and {@link GitHubBackupMojo}.
 * 
 * @author Mark Hiner
 */
public final class BackupTools {

	// -- Static utility methods --

	/**
	 * @param localDir - path to local git repository
	 * @param login - User name for authentication
	 * @param pass - Password for authentication
	 * @param token - OAuth token for authentication
	 * @param user - GitHub user to backup
	 * @param repo - GitHub repository to backup
	 * @param clean - Whether or not to force a full backup
	 * @return A String[] formatted for the GitHubBackup class
	 */
	public static String[] makeArgs(String localDir, String login, String pass,
		String token, String user, String repo, boolean clean)
	{
		String args = "";

		if (localDir != null && localDir.length() > 0) args += "-d " + localDir;
		if (login != null && login.length() > 0) args =
			addSpace(args) + "-l " + login;
		if (pass != null && pass.length() > 0) args = addSpace(args) + "-p " + pass;
		if (token != null && token.length() > 0) args =
			addSpace(args) + "-t " + token;
		if (user != null && user.length() > 0) args = addSpace(args) + "-u " + user;
		if (repo != null && repo.length() > 0) args = addSpace(args) + "-r " + repo;
		if (clean) args = addSpace(args) + "-c";

		if (args.length() == 0) return new String[0];
		return args.split(" ");
	}

	// -- Helper Methods --

	/**
	 * Adds a space to a string, if the string has length > 0.
	 * 
	 * @param base - base string to modify
	 * @return the provided string with a space (' ') appended
	 */
	private static String addSpace(String base) {
		if (base.length() > 0) base += " ";
		return base;
	}

}
