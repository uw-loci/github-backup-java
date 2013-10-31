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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.github.GHCommitComment;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPerson;
import org.kohsuke.github.GHPersonSet;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;

/**
 * Backs up GitHub information to a branch in a local git repository. This is an
 * easy way to back up information about a repository without touching a main
 * branch of the repository. For example - GitHub issues, comments, pull
 * requests, etc... all live exclusively on the GitHub cloud. Using this tool
 * allows you to preserve that information locally, and view a history of what
 * has changed in your repository.
 * <p>
 * NB: authentication via specifying a {@code id} and {@code pass} parameter is
 * highly recommended. Unauthenticated API access is rate limited to 60 per
 * hour, while authenticated is rate limited to 5,000.
 * </p>
 * <p>
 * If the API access limit is reached at any point, the backup process will
 * stop. A resume file will be written which will be automatically consumed
 * during subsequent runs to begin backing up at the GitHub API limits typically
 * refresh every hour. Note that the backup execution is linear to allow this
 * resume functionality, so on a resumed execution, modifications to previously
 * backed up entities will NOT be detected.
 * </p>
 * <p>
 * To start a new backup from scratch, run with the "-c" flag.
 * </p>
 */
public class GitHubBackup {

	// -- Constants --

	/**
	 * File name of the temporary resume file.
	 */
	public static final String RESUME_FILE = "RESUME_FILE";

	// -- Parameters --

	/**
	 * Path to the local git repository to store the backup information.
	 */
	@Option(name = "-d", aliases = "--git-dir", usage = "a local git repository")
	private String localDir;

	/**
	 * GitHub user name (or id) to use for authentication.
	 */
	@Option(name = "-l", aliases = "--login",
		usage = "user name for authentication")
	private String login;

	/**
	 * GitHub password to use for authentication.
	 */
	@Option(name = "-p", aliases = "--pass",
		usage = "password to use for authentication")
	private String pass;

	/**
	 * GitHub OAuth token to use for authentication.
	 */
	@Option(name = "-t", aliases = "--token",
		usage = "token to use for authentication.")
	private String token;

	/**
	 * GitHub user to backup. If absent, a repository will be backed up.
	 */
	@Option(name = "-u", aliases = "--user",
		usage = "github user name to back up")
	private String user;

	/**
	 * GitHub repository to backup. If absent, the repository of the current
	 * working directory will be used.
	 */
	@Option(name = "-r", aliases = "--repo",
		usage = "github repository to back up")
	private String repo;

	/**
	 * Flag indicating we should remove the incremental build metadata.
	 */
	@Option(name = "-c", aliases = "--clean",
		usage = "clean any metadata after an incremental build instead of running"
			+ " the backup program. Forces a full backup.")
	private Boolean clean = false;

	/**
	 * Flag for printing help message.
	 */
	@Option(name = "-h", aliases = "--help", usage = "show help information")
	private Boolean help = false;

	// -- Fields --

	/**
	 * Count of GitHub API accesses remaining
	 */
	private int accessRemaining;

	/**
	 * Object representation of the GitHub remote being backed up.
	 */
	private GitHub remote;

	/**
	 * Indicates the last unsuccessful backup. Nothing will be backed up until
	 * this point is reached, allowing incremental backup.
	 */
	private String resumeString;

	// -- Public API --

	/**
	 * Run the backup. Connects to GitHub and harvests information about the
	 * specified user or repository (or the working directory repository if no
	 * target specified).
	 * 
	 * @return true iff there were no problems during backup
	 */
	public boolean run() {
		// Finds the local Git repo where backup information will be saved
		Git localGit = findLocalRepo();

		// Get a GitHub connection
		remote = findConnection();

		// Check how many remaining API accesses we have
		updateRemaining();
		log("remaining accesses, pre-backup: " + accessRemaining);

		boolean success = true;
		// If a user was specified, we'll try to back up all info for that user
		if (user != null) {
			GHUser ghUser = getUser(remote);
			if (ghUser != null) {
				success = performBackup(localGit, ghUser);
			}
			// If a user was specified and no repo, we can finish now.
			if (repo == null) return success;
		}
		// If no user was found, attempt to back up all information relevant to the
		// specified repository.
		GHRepository ghRepo = null;
		if (repo != null) {
			ghRepo = getRepo(remote);
		}
		// If no user or path was specified, use local repository information.
		if (ghRepo == null) {
			repo = validateRemote(localGit);
			ghRepo = getRepo(remote);
		}
		if (ghRepo != null) {
			// Found a local (working directory) git repository - back it up
			success = performBackup(localGit, ghRepo) && success;
		}

		// Check and report the actual remaining API accesses
		updateRemaining();
		log("remaining post-backup: " + accessRemaining);
		return success;
	}

	// -- Helper methods --

	/**
	 * Helper method to ensure state is preserved in the local git repo - e.g.
	 * stash/pop changes, checkout the original branch, perform the final commit,
	 * and maintain resume status.
	 * 
	 * @param git - local git repository
	 * @param backup - A GitHub user or repository to backup
	 * @return true iff there were no problems during backup
	 */
	private boolean performBackup(Git git, Object backup) {
		boolean success = true;
		// cache local branch ref for checkout after the backup
		Ref currentBranch = null;
		currentBranch = getFullBranch(git.getRepository());

		// No need to pop unless we stash something
		boolean doPop = false;
		Set<String> modified = getModified(git);
		// Stash any local changes
		if (modified != null && modified.size() > 0) {
			stash(git);
			doPop = true;
		}

		// Clean the resume file if a full backup is requested, otherwise read the
		// existing resume state (if any)
		if (clean) {
			cleanResumeFile(git);
		}
		else {
			readResumeString();
		}

		// Perform the actual backup, delegating appropriately based on what the
		// base target was.
		try {
			if (backup instanceof GHUser) {
				backupUser(git, (GHUser) backup);
			}
			else if (backup instanceof GHRepository) {
				backupRepo(git, (GHRepository) backup);
			}
		}
		catch (Error e) {
			// The GitHub occasionally wraps the IOExceptions it declares in Errors
			// instead. Typically this is in response to a HTTP 502 error, so the
			// user should be able to try again shortly.
			err("Error during backup. If this is a server error, try again in"
				+ " a few minutes.");
			success = false;
		}
		finally {
			if (resumeString == null) {
				// make sure the resume file isn't part of this commit if unnecessary
				cleanResumeFile(git);
			}
		}

		// Perform the commit
		commit(git);
		// Clean up any leftover files (e.g. the resume file)
		cleanRepo(git);
		// check out the original branch
		doPop = checkout(git, currentBranch) && doPop;

		// Pop the top stash if necessary
		if (doPop) {
			pop(git);
		}
		return success;
	}

	/**
	 * Performs the backup for all information on the given user.
	 * 
	 * @param git - local git repository
	 * @param user - GitHub user to back up
	 * @return true if the backup operation completes
	 */
	private boolean backupUser(Git git, GHUser user) {
		makeBranch(git, user.toString());
		backupUser(git, user, localDir);
		return true;
	}

	/**
	 * Writes information about the specified user to the given file path.
	 * 
	 * @param git - local git repository
	 * @param user - GitHub user to back up
	 * @param baseDir - base directory to write the user file
	 */
	private void backupUser(Git git, GHUser user, String baseDir) {
		String filePath = getFilePath(baseDir, "user_" + user.getId());
		BufferedWriter writer = null;

		// If we're continuing an incremental backup that terminated while backing
		// up user information, we want to append to the user file instead of
		// overwriting any partially backed up information.
		boolean overwrite = true;
		if (canResume(ResumePoint.userFollowers(), ResumePoint.userFollows(),
			ResumePoint.userOrgs()))
		{
			overwrite = false;
		}

		// Back up any information that doesn't require GitHub accesses, as well as
		// the followers for this user.
		if (checkPoint(git, ResumePoint.userFollowers())) {
			writer = getWriter(filePath, overwrite);
			try {
				writeLine(writer, "name: " + user.getName());
			}
			catch (IOException e) {
				err("Failed to get user name.");
				e.printStackTrace();
			}

			try {
				writeLine(writer, "email: " + user.getEmail());
			}
			catch (IOException e) {
				err("Failed to get user e-mail.");
				e.printStackTrace();
			}

			try {
				writeLine(writer, "location: " + user.getLocation());
			}
			catch (IOException e) {
				err("Failed to get user location.");
				e.printStackTrace();
			}

			writeLine(writer, "avatar: " + user.getAvatarUrl());

			try {
				writeLine(writer, "blog: " + user.getBlog());
			}
			catch (IOException e) {
				err("Failed to get user blog.");
				e.printStackTrace();
			}

			try {
				writeLine(writer, "company: " + user.getCompany());
			}
			catch (IOException e) {
				err("Failed to get user company.");
				e.printStackTrace();
			}

			try {
				writeLine(writer, "created on: " + user.getCreatedAt());
			}
			catch (IOException e) {
				err("Failed to get user created date.");
				e.printStackTrace();
			}

			try {
				GHPersonSet<GHUser> followers = user.getFollowers();
				writeLine(writer, "followed by:");
				for (GHUser follower : followers) {
					try {
						writeLine(writer, "\t" + follower.getName() + ", " +
							follower.getId());
					}
					catch (IOException e) {
						err("Failed to get follower name.");
						e.printStackTrace();
					}
				}
			}
			catch (IOException e) {
				err("Failed to get followers for user.");
				e.printStackTrace();
			}
		}

		// Writes the users this user is following
		if (checkPoint(git, ResumePoint.userFollows())) {
			if (writer == null) writer = getWriter(filePath, overwrite);
			try {
				GHPersonSet<GHUser> follows = user.getFollows();
				writeLine(writer, "follows:");
				for (GHUser follow : follows) {
					try {
						writeLine(writer, "\t" + follow.getName() + ", " + follow.getId());
					}
					catch (IOException e) {
						err("Failed to get followed name.");
						e.printStackTrace();
					}
				}
			}
			catch (IOException e) {
				err("Failed to get followed users.");
				e.printStackTrace();
			}
		}

		// Writes the organizations this user belongs to
		if (checkPoint(git, ResumePoint.userOrgs())) {
			if (writer == null) writer = getWriter(filePath, overwrite);
			try {
				GHPersonSet<GHOrganization> orgs = user.getOrganizations();
				writeLine(writer, "organizations:");
				for (GHOrganization org : orgs) {
					try {
						writeLine(writer, "\t" + org.getName());
					}
					catch (IOException e) {
						err("Failed to get organization name.");
						e.printStackTrace();
					}
				}
			}
			catch (IOException e) {
				err("Failed to get organizations for user.");
				e.printStackTrace();
			}
		}

		cleanUp(filePath, git, writer);
		cleanEmptyDir(baseDir);
	}

	/**
	 * Performs the backup for all information on the given repository.
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository to back up
	 * @return true if the backup completes
	 */
	private boolean backupRepo(Git git, GHRepository repo) {
		makeBranch(git, repo.toString());
		backupRepo(git, repo, localDir);
		// Back up all GitHub-centric information about the repository
		backupIssues(git, repo, getDir(localDir, "issues"));
		backupPullRequests(git, repo, getDir(localDir, "pull_requests"));
		backupHooks(git, repo, getDir(localDir, "hooks"));
		backupTeams(git, repo, getDir(localDir, "teams"));
		backupCommitComments(git, repo.listCommitComments(), getDir(localDir,
			"commit_comments"));
		backupMilestones(git, repo, getDir(localDir, "milestones"));
		cleanEmptyDir(localDir);
		return true;
	}

	/**
	 * Writes information about the specified repository to the local directory
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository to back up
	 * @param baseDir - base directory to write the repository file
	 */
	private void backupRepo(Git git, GHRepository repo, String baseDir) {
		// Backup the owner info
		if (checkPoint(git, ResumePoint.repoUser())) {
			try {
				GHUser owner = repo.getOwner();
				backupUser(git, owner, baseDir);
			}
			catch (IOException e) {
				err("Failed to get repository owner.");
				e.printStackTrace();
			}
		}
		// Create a basic info file for information about the repo itself
		if (checkPoint(git, ResumePoint.repoCollab())) {
			String repoPath = getFilePath(baseDir, "repo");
			BufferedWriter writer = getWriter(repoPath);
			writeLine(writer, "name: " + repo.getName());
			writeLine(writer, "language: " + repo.getLanguage());
			writeLine(writer, "description: " + repo.getDescription());
			writeLine(writer, "homepage: " + repo.getHomepage());
			writeLine(writer, "master: " + repo.getMasterBranch());
			writeLine(writer, "created on: " + repo.getCreatedAt());
			try {
				writeLine(writer, "collaborators:");
				for (GHPerson collaborator : repo.getCollaborators()) {
					writeLine(writer, "\tid: " + collaborator.getId());
				}
			}
			catch (IOException e) {
				err("No collaborator information for this repository.");
				e.printStackTrace();
			}
			cleanUp(repoPath, git, writer);
		}
	}

	/**
	 * Backs up all issues to the given base directory.
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository
	 * @param baseDir - base directory to write issues
	 */
	private void backupIssues(Git git, GHRepository repo, String baseDir) {
		// Write open issues to a subdirectory
		if (checkPoint(git, ResumePoint.repoIssues(false))) {
			try {
				backupIssues(git, repo.getIssues(GHIssueState.OPEN), getDir(baseDir,
					"open"), false);
			}
			catch (IOException e) {
				err("Failed to get open issues.");
			}
		}

		// Write closed issues to a subdirectory
		if (checkPoint(git, ResumePoint.repoIssues(true))) {
			try {
				backupIssues(git, repo.getIssues(GHIssueState.CLOSED), getDir(baseDir,
					"closed"), true);
			}
			catch (IOException e) {
				err("Failed to get closed issues.");
			}
		}

		cleanEmptyDir(baseDir);
	}

	/**
	 * Write backup information for all issues of a given state. Issues are backed
	 * up one per file in the given base directory.
	 * 
	 * @param git - local git repository
	 * @param issues - list of issues to back up
	 * @param baseDir - base directory to write issues
	 * @param closed - issue open/closed state
	 */
	private void backupIssues(Git git, List<GHIssue> issues, String baseDir,
		boolean closed)
	{
		for (GHIssue issue : issues) {
			String issuePath = getFilePath(baseDir, issue.getNumber());
			if (checkPoint(git, ResumePoint.issueComments(closed, issue.getNumber())))
			{
				// Write the information for each issue
				BufferedWriter writer = getWriter(issuePath);
				writeLine(writer, "title: " + issue.getTitle());
				writeLine(writer, "body:");
				writeLine(writer, issue.getBody());
				writeLine(writer, "assignee: " + issue.getAssignee());
				writeLine(writer, "milestone: " + getTitle(issue.getMilestone()));
				writeLine(writer, "comments:");
				try {
					for (GHIssueComment comment : issue.getComments()) {
						writeLine(writer, comment.getBody());
					}
				}
				catch (IOException e) {
					err("No comments for issue: " + issue.getTitle());
				}
				cleanUp(issuePath, git, writer);
			}
		}
		cleanEmptyDir(baseDir);
	}

	/**
	 * Write backup information for all pull requests of a given state. PRs are
	 * backed up one per file in the given base directory.
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository
	 * @param baseDir - base directory to write pull requests
	 */
	private void backupPullRequests(Git git, GHRepository repo, String baseDir) {
		// Write open pull requests to a subdirectory
		if (checkPoint(git, ResumePoint.repoPR(false))) {
			try {
				backupPullRequests(git, repo.getPullRequests(GHIssueState.OPEN),
					getDir(baseDir, "open"), false);
			}
			catch (IOException e) {
				err("No open pull requests found.");
			}
		}

		// Write closed pull requests to a subdirectory
		if (checkPoint(git, ResumePoint.repoPR(true))) {
			try {
				backupPullRequests(git, repo.getPullRequests(GHIssueState.CLOSED),
					getDir(baseDir, "closed"), true);
			}
			catch (IOException e) {
				err("No closed pull requests found.");
			}
		}

		cleanEmptyDir(baseDir);
	}

	/**
	 * Backs up all pull requests to the given base directory.
	 * 
	 * @param git - local git repository
	 * @param listPullRequests - list of pull requests to back up
	 * @param baseDir - base directory to write pull requests
	 * @param closed - pull request open/closed state
	 */
	private void backupPullRequests(Git git,
		List<GHPullRequest> listPullRequests, String baseDir, boolean closed)
	{
		for (GHPullRequest pr : listPullRequests) {
			String prPath = getFilePath(baseDir, pr.getNumber());
			BufferedWriter writer = null;
			boolean overwrite = true;
			if (canResume(ResumePoint.prComments(closed))) {
				overwrite = false;
			}
			// Write the base PR information + its merge status
			if (checkPoint(git, ResumePoint.prIsMerged(closed, pr.getNumber()))) {
				writer = getWriter(prPath, overwrite);
				writeLine(writer, "title: " + pr.getTitle());
				writeLine(writer, "body:");
				writeLine(writer, pr.getBody());
				writeLine(writer, "number: " + pr.getNumber());
				writeLine(writer, "milestone: " + getTitle(pr.getMilestone()));
				writeLine(writer, "labels:");
				for (String label : pr.getLabels()) {
					writeLine(writer, "\t" + label);
				}
				try {
					writeLine(writer, "merged: " + pr.isMerged());
				}
				catch (IOException e) {
					err("Failed to get PR merge status for: " + pr.getTitle());
				}
			}

			// Write any comment information for this PR
			if (checkPoint(git, ResumePoint.prComments(closed, pr.getNumber()))) {
				if (writer == null) writer = getWriter(prPath, overwrite);

				writeLine(writer, "comments:");
				try {
					for (GHIssueComment comment : pr.getComments()) {
						writeLine(writer, "\t" + comment.getBody());
					}
				}
				catch (IOException e) {
					err("Failed to get PR comments for: " + pr.getTitle());
				}
			}
			cleanUp(prPath, git, writer);
		}
		cleanEmptyDir(baseDir);
	}

	/**
	 * Writes all hook information to the given base directory.
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository
	 * @param baseDir - base directory to write hook information
	 */
	private void backupHooks(Git git, GHRepository repo, String baseDir) {
		if (checkPoint(git, ResumePoint.repoHooks())) {
			try {
				List<GHHook> hooks = repo.getHooks();
				// Write to a hook subdirectory
				backupHooks(git, hooks, baseDir);
			}
			catch (IOException e) {
				err("No hook information for repo.");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Writes hook information, one hook per file, to the given directory.
	 * 
	 * @param git - local git repository
	 * @param hooks - list of GitHub hooks
	 * @param baseDir - base directory to write hooks
	 */
	private void backupHooks(Git git, List<GHHook> hooks, String baseDir) {
		for (GHHook hook : hooks) {
			String hookPath = getFilePath(baseDir, hook.getId());
			BufferedWriter writer = getWriter(hookPath);
			// Write out the hook info
			writeLine(writer, "name: " + hook.getName());
			writeLine(writer, "is active: " + hook.isActive());
			writeLine(writer, "configuration:");
			for (String key : hook.getConfig().keySet()) {
				writeLine(writer, key + " : " + hook.getConfig().get(key));
			}
			cleanUp(hookPath, git, writer);
		}
		cleanEmptyDir(baseDir);
	}

	/**
	 * Backs up any team information in this repository to the given base
	 * directory.
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository
	 * @param baseDir - base directory to write team info
	 */
	private void backupTeams(Git git, GHRepository repo, String baseDir) {
		if (checkPoint(git, ResumePoint.repoTeam(), 2)) {
			try {
				Set<GHTeam> teams = repo.getTeams();
				// Write the team information to a subdirectory
				backupTeams(git, teams, baseDir);
			}
			catch (IOException e) {
				err("No team information for repo.");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Writes out individual team information, one team per file, to the given
	 * directory.
	 * 
	 * @param git - local git repository
	 * @param teams - list of teams to back up
	 * @param baseDir - base directory to write teams
	 */
	private void backupTeams(Git git, Set<GHTeam> teams, String baseDir) {
		for (GHTeam team : teams) {
			if (checkPoint(git, ResumePoint.teamMembers(team.getId()))) {
				String teamPath = getFilePath(baseDir, team.getId());
				BufferedWriter writer = getWriter(teamPath);
				// Write all info for the current team
				writeLine(writer, "name: " + team.getName());
				writeLine(writer, "permission: " + team.getPermission());
				writeLine(writer, "members:");
				try {
					for (GHUser member : team.getMembers()) {
						writeLine(writer, "\t" + member.getLogin() + ", " + member.getId());
					}
				}
				catch (IOException e) {
					err("No team member information for team: " + team.getName());
				}
				cleanUp(teamPath, git, writer);
			}
		}
		cleanEmptyDir(baseDir);
	}

	/**
	 * Backs up all GitHub comments on any commits for the current repository, one
	 * file per commit.
	 * 
	 * @param git - local git repository
	 * @param commitComments - a list of all commit comments for the current repo
	 * @param baseDir - base directory to write commit comments
	 */
	private void backupCommitComments(Git git,
		PagedIterable<GHCommitComment> commitComments, String baseDir)
	{
		// Map the sha1 of each commit to their comments
		Map<String, List<GHCommitComment>> commentMap =
			new HashMap<String, List<GHCommitComment>>();

		if (checkPoint(git, ResumePoint.commitComments(), 2)) {
			for (GHCommitComment comment : commitComments) {
				// Build the map of commit hash : comment
				List<GHCommitComment> commentList = commentMap.get(comment.getSHA1());
				if (commentList == null) {
					commentList = new ArrayList<GHCommitComment>();
					commentMap.put(comment.getSHA1(), commentList);
				}
				commentList.add(comment);
			}

			// Write the comments out in a file matching the sha1 of their commit
			for (String sha1 : commentMap.keySet()) {
				String commitPath = getFilePath(baseDir, sha1);
				BufferedWriter writer = getWriter(commitPath);
				// Write each comment out for the current commit
				for (GHCommitComment comment : commentMap.get(sha1)) {
					writeLine(writer, "comment: " + comment.getId());
					writeLine(writer, "\tpath: " + comment.getPath());
					writeLine(writer, "\tline: " + comment.getLine());
					writeLine(writer, "\tbody: " + comment.getBody());
				}
				cleanUp(commitPath, git, writer);
			}
		}
		cleanEmptyDir(baseDir);
	}

	/**
	 * Backs up all milestone information from the provided GitHub repository to
	 * the given base directory.
	 * 
	 * @param git - local git repository
	 * @param repo - remote GitHub repository
	 * @param baseDir - base directory to write milestone information
	 */
	private void backupMilestones(Git git, GHRepository repo, String baseDir) {
		// back up open milestones to a subdirectory
		backupMilestones(git, repo.listMilestones(GHIssueState.OPEN), getDir(
			baseDir, "open"));

		// back up closed milestones to a subdirectory
		backupMilestones(git, repo.listMilestones(GHIssueState.CLOSED), getDir(
			baseDir, "closed"));

		cleanEmptyDir(baseDir);
	}

	/**
	 * Backs up all milestone information to the given directory, one file per
	 * milestone.
	 * 
	 * @param git - local git repository
	 * @param milestones - a list of GitHub milestones
	 * @param baseDir - base directory to write milestones
	 */
	private void backupMilestones(Git git, PagedIterable<GHMilestone> milestones,
		String baseDir)
	{
		// Milestones don't have their own ids so we just number them in the order
		// they come back, to avoid potential clashes with odd title characters
		int i = 0;
		if (checkPoint(git, ResumePoint.milestones(), 2)) {
			for (GHMilestone milestone : milestones) {
				String milestonePath = getFilePath(baseDir, i);
				BufferedWriter writer = getWriter(milestonePath);
				// Write milestone info out
				writeLine(writer, "title: " + milestone.getTitle());
				writeLine(writer, "description: " + milestone.getDescription());
				writeLine(writer, "state: " + milestone.getState().toString());
				cleanUp(milestonePath, git, writer);
				i++;
			}
		}
	}

	/**
	 * Helper method to get the title of a milestone. Safe to use on null
	 * milestones.
	 * 
	 * @param milestone - milestone of interest
	 * @return The milestone's title, or null if the milestone is null
	 */
	private String getTitle(GHMilestone milestone) {
		return milestone == null ? null : milestone.getTitle();
	}

	/**
	 * @param path - file name to write to
	 * @return A writer, in replace mode, configured for the given path.
	 */
	private BufferedWriter getWriter(String path) {
		return getWriter(path, true);
	}

	/**
	 * Helper method to create a BufferedWriter. Consumes any exceptions.
	 * 
	 * @param path - file name to write to
	 * @param overWrite - If false, the writer will be created in append mode.
	 * @return A configured writer for the given path, or null if there was a
	 *         problem.
	 */
	private BufferedWriter getWriter(String path, boolean overWrite) {
		try {
			return new BufferedWriter(new FileWriter(path, !overWrite));
		}
		catch (IOException e) {
			err("Failed to create writer for path: " + path);
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Convenience method to write a line of output plus a newline character.
	 * Handles any exceptions.
	 * 
	 * @param writer - The configured writer to use for writing.
	 * @param output - String to write to a new line.
	 */
	private void writeLine(BufferedWriter writer, String output) {
		try {
			writer.write(output);
			writer.newLine();
		}
		catch (IOException e) {
			err("Failed to write line: " + output);
		}
	}

	/**
	 * Closes the given writer and stages the given file path. Consumes any
	 * exceptions.
	 * 
	 * @param path - path to add (typically the same path the writer was writing
	 *          to)
	 * @param git - local git repository
	 * @param writer - writer to close
	 */
	private void cleanUp(String path, Git git, BufferedWriter writer) {
		// Close the writer
		if (writer != null) {
			try {
				writer.close();
			}
			catch (IOException e) {
				err("Failed to close writer for path: " + path);
				e.printStackTrace();
			}
		}
		// Stage the target path
		if (git != null) {
			try {
				git.add().addFilepattern(getRelPath(path)).call();
			}
			catch (Exception e) {
				err("Failed to add file: " + path +
					". Working directory may be incorrect, and backup data may be lost");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Convenience method to clean a directory, if it's empty.
	 * 
	 * @param dir - dir to remove
	 */
	private void cleanEmptyDir(String dir) {
		File emptyDir = new File(dir);
		if (emptyDir.exists() && emptyDir.isDirectory() &&
			emptyDir.list().length == 0)
		{
			emptyDir.deleteOnExit();
		}
	}

	/**
	 * Reads the {@link #RESUME_FILE} if it's present, storing its value for
	 * {@link #checkPoint} use later to facilitate incremental backup.
	 */
	private void readResumeString() {
		File resumePath = new File(getFilePath(localDir, RESUME_FILE));
		if (resumePath.exists()) {
			try {
				BufferedReader r = new BufferedReader(new FileReader(resumePath));
				if (r.ready()) {
					resumeString = r.readLine();
				}
				r.close();
			}
			catch (FileNotFoundException e) {}
			catch (IOException e) {
				err("Error reading resume file.");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Creates a resume file for future runs to consume, marking the first
	 * incomplete backup point.
	 * 
	 * @param git - local git repository
	 * @param resumePoint - A {@link ResumePoint} indicating, for an incremental
	 *          backup, where work should continue on the next run.
	 */
	private void writeResumeString(Git git, String resumePoint) {
		String resumePath = getFilePath(localDir, RESUME_FILE);
		BufferedWriter w = getWriter(resumePath);
		writeLine(w, resumePoint);
		cleanUp(resumePath, git, w);
	}

	/**
	 * Removes the resume file, if it exists, and "git rm"s it to ensure it's not
	 * part of the current commit. Any backup run after this method is called will
	 * be a complete backup.
	 * 
	 * @param git - local git repository
	 */
	private void cleanResumeFile(Git git) {
		File resumePath = new File(getFilePath(localDir, RESUME_FILE));
		if (resumePath.exists()) {
			try {
				// Ensure the resume file is removed in this commit
				git.rm().addFilepattern(getRelPath(resumePath.getAbsolutePath()))
					.call();
			}
			catch (NoFilepatternException e) {
				err("Error removing unnecessary resume file. Working directory may be"
					+ " incorrect.");
				e.printStackTrace();
			}
			catch (GitAPIException e) {
				err("Error removing unnecessary resume file. Working directory may be"
					+ " incorrect.");
				e.printStackTrace();
			}
			// delete the file
			resumePath.delete();
		}
	}

	/**
	 * As {@link #getFilePath(String, String)} but auto-converts primitive ints to
	 * Strings.
	 * 
	 * @param baseDir - Directory for the file path
	 * @param fileName - Name of the file
	 * @return As {@link #getFilePath(String, String)}.
	 */
	private String getFilePath(String baseDir, Integer fileName) {
		return getFilePath(baseDir, fileName.toString());
	}

	/**
	 * Helper method to make a file path for a particular root and file name. Adds
	 * a ".txt" extension.
	 * 
	 * @param baseDir - Directory for the file path
	 * @param fileName - Name of the file
	 * @return The absolute path to the combined baseDir + fileName, as
	 *         {@link File#File(String, String)};
	 */
	private String getFilePath(String baseDir, String fileName) {
		return new File(baseDir, fileName + ".txt").getAbsolutePath();
	}

	/**
	 * Create, per {@link File#mkdir()}, the specified new directory as a child of
	 * the base directory. Returns the absolute path to the new directory.
	 * 
	 * @param baseDir - Root of the subdirectory
	 * @param subDir - Relative path to a new subdirectory
	 * @return Absolute path of the new directory
	 */
	private String getDir(String baseDir, String subDir) {
		File directory = new File(baseDir, subDir);
		directory.mkdir();
		return directory.getAbsolutePath();
	}

	/**
	 * Convenience method for finding relative paths from the local git repository
	 * to a target file. Useful for "git add" and "git rm" commands.
	 * 
	 * @param file - Target file
	 * @return The relative path from {@link #localDir}, the local git repository,
	 *         to the target file.
	 */
	private String getRelPath(String file) {
		return new File(localDir).toURI().relativize(new File(file).toURI())
			.getPath();
	}

	/**
	 * Determine whether there are enough accesses remaining to perform an action.
	 * <p>
	 * NB: The {@link GitHub#getRateLimit()} method reports this information.
	 * Although it doesn't actually consume a github API access to do so, it does
	 * send a HTTP request which is prone to timing out. Thus, the remaining
	 * accesses are counted manually during backup execution. Thus, it is very
	 * important for the accessNeeded paramter to be accurate.
	 * </p>
	 * <p>
	 * Erroneous accessNeeded values (or failing to call this method) can result
	 * in stalling or premature backup termination.
	 * </p>
	 * 
	 * @param accessNeeded - Minimum number of GitHub accesses the next operation
	 *          will require
	 * @param resumePoint - Point to restore to if we don't have enough accesses
	 *          remaining
	 * @return true iff there are at least enough accesses remaining to cover the
	 *         accesses needed.
	 */
	private boolean canAccess(int accessNeeded, String resumePoint) {
		accessRemaining -= accessNeeded;
		boolean haveLimit = accessRemaining >= 0;
		if (!haveLimit) {
			resumeString = resumePoint;
		}
		return haveLimit;
	}

	/**
	 * Resume point strings are constructed so when nested GitHub API accesses are
	 * performed, the nested strings contain the parent strings. This is critical
	 * to allow execution of nested code - which in turn is required to clean the
	 * resume string and restore normal backup execution.
	 * <p>
	 * Returns early, so resume points should be provided in the order they will
	 * be encountered to avoid premature resume behavior.
	 * </p>
	 * 
	 * @param resumePoints A list of possible resume points
	 * @return true iff the current {@link #resumeString} contains at least one of
	 *         the given resumePoints, implying that normal functionality will
	 *         resume in the currently executing block.
	 */
	private boolean canResume(String... resumePoints) {
		if (resumeString == null) return false;

		for (String resumePoint : resumePoints) {
			if (resumeString.contains(resumePoint)) {
				// Allow this code to execute, as this or a child method is the actual
				// resume point
				if (resumeString.equals(resumePoint)) {
					// This is the resume point, so we can clear the resume string and
					// restore standard execution
					resumeString = null;
				}
				return true;
			}
		}
		// Resume point not encountered yet.. disallow execution
		return false;
	}

	/**
	 * As {@link #checkPoint(Git, String, int)} for a single GitHub API access.
	 * 
	 * @param git - local git repository
	 * @param resumePoint - Current {@link ResumePoint} being checked
	 * @return true iff it's OK (enough GitHub API accesses, and we haven't
	 *         already backed this section up in a previous incremental run) to
	 *         run code that requires one (1) GitHub API access.
	 */
	private boolean checkPoint(Git git, String resumePoint) {
		return checkPoint(git, resumePoint, 1);
	}

	/**
	 * Verifies the following conditions:
	 * <ul>
	 * <li>Not recovering from an incremental build OR this is the recover point
	 * OR this is a parent of the recover point</li>
	 * <li>There are at least accessNeeded # of GitHub API accesses available for
	 * the current remote repository</li>
	 * </ul>
	 * If these conditions are satisfied, it is safe and necessary to access the
	 * GitHub API and backup the current information.
	 * 
	 * @param git - local git repository
	 * @param resumePoint - Current {@link ResumePoint} being checked
	 * @return true iff it's OK (enough GitHub API accesses, and we haven't
	 *         already backed this section up in a previous incremental run) to
	 *         run code that requires "accessNeeded" GitHub API accesses.
	 */
	private boolean checkPoint(Git git, String resumePoint, int accessNeeded) {
		// Verify we're not interfering with incremental build recovery
		boolean check = resumeString == null || canResume(resumePoint);

		// Make sure we don't overwrite a resume point
		if (resumeString == null) {
			// During normal execution (e.g. not recovering from an incremental build)
			// we write ahead resume strings before executing those sections. Then, if
			// writing those sections fails, we will resume to that point next
			// execution.
			writeResumeString(git, resumePoint);
		}

		// Verify we have the GitHub API accesses needed
		check = check && canAccess(accessNeeded, resumePoint);
		return check;
	}

	/**
	 * Updates the {@link #accessRemaining} field based on the current rate limit
	 * from the GitHub remote.
	 */
	private void updateRemaining() {
		try {
			accessRemaining = remote.getRateLimit().remaining;
		}
		catch (IOException e) {
			err("Failed to get GitHub rate limit.");
			e.printStackTrace();
			accessRemaining = 0;
		}
	}

	/**
	 * Helper method to get a GitHub user. Consumes any exceptions. The user is
	 * determined by the {@link #user} parameter. This operation requires one
	 * GitHub API access.
	 * 
	 * @param remote - Remote to check for the current user.
	 * @return A GHUser if the specified user if found, or null
	 */
	private GHUser getUser(GitHub remote) {
		try {
			// Make sure we have the GitHub access available
			if (canGetBase()) {
				return remote.getUser(user);
			}
		}
		catch (IOException e) {
			err("Failed to find user: " + user);
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Helper method to get a GitHub repository. Consumes any exceptions. The repo
	 * is determined by the {@link #repo} parameter. This operation requires one
	 * GitHub API access.
	 * 
	 * @param remote - Remote to check for the current repo.
	 * @return A GHRepository if the specified repo if found, or null
	 */
	private GHRepository getRepo(GitHub remote) {
		try {
			// Make sure we have the GitHub access available
			if (canGetBase()) {
				return remote.getRepository(repo);
			}
		}
		catch (IOException e) {
			err("Failed to find repository: " + repo);
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * @return true iff we have at least one GitHub API access remaining
	 */
	private boolean canGetBase() {
		if (accessRemaining > 0) {
			accessRemaining--;
			return true;
		}
		log("Couldn't get backup target from GitHub. No API accesses remaining. Try"
			+ " again in an hour or so after your rate limit has refreshed, or use"
			+ " authentication to increase your rate limit.");
		return false;
	}

	/**
	 * Helper method to check on the modified objects in the given git repository.
	 * Consumes any exceptions. Equivalent to "git status".
	 * 
	 * @param git - local git repository
	 * @return A list of all modified objects in the current repository, or null.
	 */
	private Set<String> getModified(Git git) {
		try {
			return git.status().call().getModified();
		}
		catch (NoWorkTreeException e) {
			e.printStackTrace();
		}
		catch (GitAPIException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Helper method to check out a branch in a git repository. Consumes any
	 * exceptions. Equivalent to "git checkout targetBranch".
	 * 
	 * @param git - local git repository
	 * @param targetBranch - Branch to check out
	 * @return true iff the checkout operation was successful
	 */
	private boolean checkout(Git git, Ref targetBranch) {
		try {
			if (targetBranch != null) {
				git.checkout().setName(targetBranch.getName()).call();
				return true;
			}
		}
		catch (Exception e) {
			err("Failed to check out original branch. Backed up working directory may have issues!");
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Helper method to clean a git repository. Consumes any exceptions.
	 * Equivalent to "git clean -fd".
	 * 
	 * @param git - local git repository
	 */
	private void cleanRepo(Git git) {
		try {
			git.clean().setCleanDirectories(true).setIgnore(true).call();
		}
		catch (NoWorkTreeException e) {
			err("Error cleaning the working directory.");
			e.printStackTrace();
		}
		catch (GitAPIException e) {
			err("Error cleaning the working directory.");
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to create a commit in a git repository. Consumes any
	 * exceptions. Equivalent to "git commit".
	 * <p>
	 * The commit message will be timestamped, with a message indicating whether
	 * this the backup was complete or incremental.
	 * </p>
	 * 
	 * @param git - local git repository
	 */
	private void commit(Git git) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		// Commit the work done by the backup method
		try {
			String message = dateFormat.format(date);
			if (resumeString == null) {
				message += " - complete";
			}
			else {
				message += " - incremental";
				log("Backup incomplete, hit rate limit. Re-run in an hour or so when "
					+ "API limit is refreshed, or ensure -l -p flags are set to increase"
					+ " rate limit.");
			}
			git.commit().setMessage(message).call();
		}
		catch (Exception e) {
			err("Failed to commit changes. Backed up working directory may have issues!");
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to stash any uncommitted changes in a repository. Consumes
	 * any exceptions. Equivalent to "git stash".
	 * 
	 * @param git - local git repository
	 */
	private void stash(Git git) {
		try {
			git.stashCreate().call();
		}
		catch (GitAPIException e) {
			err("Failed to stash current branch changes. Exiting...");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Helper method to pop the most recently stashed changes in a repository.
	 * Consumes any exceptions. Equivalent to "git stash pop".
	 * 
	 * @param git - local git repository
	 */
	private void pop(Git git) {
		try {
			if (git.stashList().call().isEmpty() == false) {
				git.stashApply().call();
				git.stashDrop().setAll(false).call();
			}
		}
		catch (Exception e) {
			err("Failed to pop stashed changes. Backed up working directory may have issues!");
			e.printStackTrace();
		}
	}

	/**
	 * Convenience method to get the current branch of a repository. Useful for
	 * restoring state after creating a new branch. Consumes exceptions.
	 * 
	 * @param repository - A local git repository
	 * @return A Ref for the current branch of this repository, or null
	 */
	private Ref getFullBranch(Repository repository) {
		String fullBranch = null;
		try {
			// Get a string representation of the current branch
			fullBranch = repository.getFullBranch();
		}
		catch (IOException e) {
			err("Failed to get current branch. Exiting...");
			e.printStackTrace();
			System.exit(1);
		}
		try {
			// get the Ref object for the current branch
			return repository.getRef(fullBranch);
		}
		catch (IOException e) {
			err("Failed to get hash for current branch. Exiting...");
			e.printStackTrace();
			System.exit(1);
		}
		return null;
	}

	/**
	 * Checks out the branch with the provided name in the specified repository.
	 * .*
	 * 
	 * @param git - local git repository
	 * @param branchName - Name of the branch to check out
	 * @return true iff the branch is successfully checked out
	 */
	private boolean makeBranch(Git git, String branchName) {
		return makeBranch(git, branchName, false);
	}

	/**
	 * Checks out the branch with the provided name in the specified repository.
	 * Attempts to create an orphan branch of the given name first if createBranch
	 * is true. If a branch is created, "backup-" is prepended to the given name.
	 * 
	 * @param git - local git repository
	 * @param branchName - Name of the branch to check out (and create)
	 * @param createBranch - If true, will attempt to create an orphan branch.
	 * @return true iff the branch is successfully checked out
	 */
	private boolean makeBranch(Git git, String branchName, boolean createBranch) {
		try {
			if (createBranch) {
				CreateOrphanBranchCommand cobc =
					new CreateOrphanBranchCommand(git.getRepository());
				// Create an orphan branch (no history)
				cobc.setName(branchName).call();
				// Delete the index for this branch so no files are tracked
				File index = git.getRepository().getIndexFile();
				if (index.exists()) index.delete();
				// Clean the directory
				cleanRepo(git);
				// At this point, we have no git history and no modified files. When
				// the backup method completes, its commit will be the first commit
				// on the branch.
			}
			else {
				branchName = "backup-" + cleanForGit(branchName).toLowerCase();
				// Attempt to check out the branch
				git.checkout().setName(branchName).call();
			}
		}
		catch (RefAlreadyExistsException e) {
			e.printStackTrace();
			return false;
		}
		catch (RefNotFoundException e) {
			if (!createBranch) {
				// Branch doesn't exist, so create it.
				return makeBranch(git, branchName, true);
			}
			e.printStackTrace();
			return false;
		}
		catch (InvalidRefNameException e) {
			e.printStackTrace();
			return false;
		}
		catch (CheckoutConflictException e) {
			e.printStackTrace();
			return false;
		}
		catch (GitAPIException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * @param branchName - the original branch name
	 * @return a santizied branch name, stripped of illegal characters/conventions
	 */
	private String cleanForGit(String branchName) {
		return branchName.replaceAll(":", "-");
	}

	/**
	 * Updates remote repository information using the local git repository.
	 * Verifies the remote pointed to is, indeed, a GitHub repository.
	 * <p>
	 * TODO - support additional hosts
	 * </p>
	 * 
	 * @param git - local git repository
	 * @return The path to the local repostiory wrapped by the provided Git, or
	 *         null.
	 */
	private String validateRemote(Git git) {
		try {
			// Get the remote configuration for "origin"
			// TODO may want to allow selection of different repositories, or iterate
			// over all repos?
			RemoteConfig remConfig =
				new RemoteConfig(git.getRepository().getConfig(), "origin");
			URIish origin = remConfig.getURIs().get(0);
			// Check for a github origin
			if (!origin.getHost().equals("github.com")) {
				throw new InvalidParameterException(
					"GitHubBackup script executed from non-GitHub local repository: " +
						origin.getHost());
			}
			return origin.getPath()
				.substring(0, origin.getPath().lastIndexOf(".git"));
		}
		catch (URISyntaxException e) {
			err("Failed to get a remote configuration.\n" + e.toString());
			System.exit(1);
		}
		return null;
	}

	/**
	 * Finds a local git repository using the provided {@link #localDir} path, or
	 * the current working directory if no path was provided (or the provided path
	 * doesn't contain a .git).
	 * 
	 * @return A Git object for interacting with the discovered repository, or
	 *         null.
	 */
	private Git findLocalRepo() {
		Git git = null;
		// Check the provided localDir parameter
		if (localDir != null) {
			try {
				git = Git.open(new File(localDir));
			}
			catch (IOException e) {
				err("Failed to find local Git repository: " + localDir);
			}
		}

		// Check the current working directory
		if (git == null) {
			localDir = System.getProperty("user.dir");
			final File dir = new File(localDir);
			try {
				git = Git.open(dir);
			}
			catch (IOException e) {
				err("Failed to find local Git repository in current working" +
					"directory: " + dir.getAbsolutePath() +
					"\nPlease run GitHubBackup from a directory containing a .git, or" +
					" provide a path to a local repository to use for backup.");
			}
		}

		return git;
	}

	/**
	 * Attempt to create a new GitHub connection using available authentication
	 * credentials if possible. Authentication requires that the following are
	 * set:
	 * <ul>
	 * <li>{@link #login}</li>
	 * <li>{@link #pass} OR {@link #token}</li>
	 * </ul>
	 * If we can't authenticate, anonymous connection will be attempted. If no
	 * connection can be established the application will terminate.
	 * 
	 * @return A GitHub object used for querying the GitHub API.
	 */
	private GitHub findConnection() {
		GitHub github = null;
		String errorMsg = "";
		// Connect using the OAuth token
		if (token != null) {
			try {
				github = GitHub.connectUsingOAuth(token);
			}
			catch (IOException e) {
				errorMsg += e.getMessage() + "\n";
			}
		}

		// Connect using the login/password combination
		if (login != null) {
			if (github == null && pass != null) {
				try {
					github = GitHub.connectUsingPassword(login, pass);
				}
				catch (IOException e) {
					errorMsg += e.getMessage() + "\n";
				}
			}

			// Connect using the login/token combination
			if (github == null && token != null) {
				try {
					github = GitHub.connect(login, token);
				}
				catch (IOException e) {
					errorMsg += e.getMessage() + "\n";
				}
			}
		}

		// If we couldn't authenticate, connect anonymously
		if (github == null) {
			try {
				github = GitHub.connectAnonymously();
			}
			catch (IOException e) {
				errorMsg += e.getMessage() + "\n";
			}
		}
		if (github == null) {
			err("Failed to connect to GitHub.\n" + errorMsg);
			System.exit(1);
		}
		return github;
	}

	/**
	 * Convenience method facilitating more robust error reporting in the future.
	 * 
	 * @param msg - Message to write to System.err
	 */
	private void err(String msg) {
		System.err.println(msg);
	}

	/**
	 * Convenience method facilitating more robust logging support in the future.
	 * 
	 * @param msg - Message to write to System.out
	 */
	private void log(String msg) {
		System.out.println(msg);
	}

	// -- Main Methods --

	/**
	 * Main method for running from the command line.
	 */
	public static void main(String... args) {
		new GitHubBackup().doMain(args);
	}

	/**
	 * Instanced main method entry point.
	 * 
	 * @return true iff there were no problems during backup
	 */
	public boolean doMain(String... args) {
		CmdLineParser parser = new CmdLineParser(this);
		try {
			// parse the arguments.
			parser.parseArgument(args);
		}
		catch (CmdLineException e) {
			// if there's a problem in the command line,
			// you'll get this exception. this will report
			// an error message.
			err(e.getMessage());
			err("java GitHubBackup [options...] arguments...");
			// print the list of available options
			parser.printUsage(System.err);
			err("");

			return false;
		}

		if (help) {
			System.out
				.println("Tool for backing up GitHub information to a local repository."
					+ "\nOpitons:");
			parser.printUsage(System.out);
			return true;
		}
		return run();
	}
}
