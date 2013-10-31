/*
 * #%L
 * Maven plugin for backing up github state.
 * %%
 * Copyright (C) 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
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
 * A static utility class providing consistent name patterns that can be used to
 * mark resume points in {@link GitHubBackup} when the API access limit is
 * reached.
 * 
 * @author Mark Hiner
 */
public final class ResumePoint {

	/**
	 * @return Backup string for collaborator names
	 */
	public static String collabName() {
		return "COLLAB_NAME";
	}

	/**
	 * @return Backup string for commit comment
	 */
	public static String commitComments() {
		return "COMMIT_COMMENTS";
	}

	/**
	 * @param closed - whether the string for closed or open issue comments should
	 *          be returned
	 * @return Base backup string for issue comments
	 */
	public static String issueComments(boolean closed) {
		return repoIssues(closed) + "_COMMENTS";
	}

	/**
	 * @param closed - whether the string for closed or open issue comments should
	 *          be returned
	 * @param id - id of the associated issue
	 * @return Issue-id specific backup string for issue comments
	 */
	public static String issueComments(boolean closed, int id) {
		return issueComments(closed) + "_" + id;
	}

	/**
	 * @return Backup string for milestones
	 */
	public static String milestones() {
		return "MILESTONES";
	}

	/**
	 * @param closed - whether the string for closed or open pull request comments
	 *          should be returned
	 * @return Base backup string for pull request comments
	 */
	public static String prComments(boolean closed) {
		return repoPR(closed) + "_COMMENTS";
	}

	/**
	 * @param closed - whether the string for closed or open pull request comments
	 *          should be returned
	 * @param id - id of the associated pull request
	 * @return PR-specific backup string for pull request comment access
	 */
	public static String prComments(boolean closed, int id) {
		return prComments(closed) + "_" + id;
	}

	/**
	 * @param closed - whether the string for closed or open pull request merge
	 *          status should be returned
	 * @return Base backup string for pull request merge status
	 */
	public static String prIsMerged(boolean closed) {
		return repoPR(closed);
	}

	/**
	 * @param closed - whether the string for closed or open pull request merge
	 *          status should be returned
	 * @param id - id of the associated pull request
	 * @return PR-specific backup string for pull request merge status
	 */
	public static String prIsMerged(boolean closed, int id) {
		return prIsMerged(closed) + "_" + id;
	}

	/**
	 * @return Backup string for repository collaborators
	 */
	public static String repoCollab() {
		return "REPO_COLLAB";
	}

	/**
	 * @return Backup string for repository hooks
	 */
	public static String repoHooks() {
		return "REPO_HOOKS";
	}

	/**
	 * @return Backup string for repository issues
	 */
	public static String repoIssues() {
		return "REPO_ISSUES";
	}

	/**
	 * @param closed - whether the string for open or closed issues should be
	 *          returned
	 * @return Open/closed state-specific backup string for issues
	 */
	public static String repoIssues(boolean closed) {
		return repoIssues() + (closed ? "_CLOSED" : "_OPEN");
	}

	/**
	 * @return Base backup string for user information
	 */
	public static String repoUser() {
		return "REPO_USER";
	}

	/**
	 * @return Base backup string for pull requests
	 */
	public static String repoPR() {
		return "REPO_PRS";
	}

	/**
	 * @param closed - whether the string for open or closed pull requests should
	 *          be returned
	 * @return Open/closed state-specific backup string for pull requests
	 */
	public static String repoPR(boolean closed) {
		return repoPR() + (closed ? "_CLOSED" : "_OPEN");
	}

	/**
	 * @return Base backup string for teams
	 */
	public static String repoTeam() {
		return "REPO_TEAM";
	}

	/**
	 * @return Backup string for accessing all team members
	 */
	public static String teamMembers() {
		return repoTeam() + "_MEMBERS";
	}

	/**
	 * @param id - Desired team id
	 * @return Backup string for members of a specified team
	 */
	public static String teamMembers(int id) {
		return teamMembers() + "_" + id;
	}

	/**
	 * @return Backup string for those followed by a user
	 */
	public static String userFollows() {
		return repoUser() + "_FOLLOWS";
	}

	/**
	 * @return Backup string for those following a user
	 */
	public static String userFollowers() {
		return repoUser() + "_FOLLOWERS";
	}

	/**
	 * @return Backup string for a user's organizations
	 */
	public static String userOrgs() {
		return repoUser() + "_ORGS";
	}

}
