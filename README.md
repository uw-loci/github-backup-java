[![](https://travis-ci.org/uw-loci/github-backup-java.svg?branch=master)](https://travis-ci.org/uw-loci/github-backup-java)

GitHub Backup Tool
==================

This tool was inspired by joeyh's [github-backup](github.com/joeyh/github-backup). When attempting to use joeyh's tool, it became apparent that unauthenticated GitHub API access was being used, which is [rate limited](http://developer.github.com/v3/#rate-limiting) to 60 accesses per hour, which is insufficient for backing up reasonably sized repositories. This was purely a limitation of the Haskell-based GitHub API being used. Thus this project originated as a Java-based equivalent using a [github-api](https://github.com/kohsuke/github-api) that easily allows authenticatation (which provides 5000 API accesses per hour).

You can use this tool to backup GitHub-specific information that wouldn't
normally be part of a git repository. For example, issues, milestones, commit
comments, and user/team data. This information is written to a special branch in a
local git repository, in a structured, human-readable format.

Having your information on disk effectively backs it up in case of catastrophic
GitHub failure. Also, because it's on a git branch with one commit per run, you
can easily visualize the history of your GitHub data by comparing commits.


Getting Started
===============

There are a number of ways to run GitHub backup, but the general workflow is always:
* Select a local git directory to store your backup branch
* Determine which parameters you want to use
* Run the backup

GitHub Backup will do its best to preserve the state of whatever repository you choose as the backup destination - changes will be stashed/popped and the current branch will be restored - but you should close anything that may automatically be querying information about your backup destination.

For example, if your destination repository is also an Eclipse project you're currently working on, make sure to turn off

```
Project -> Build Automatically
```

before running GitHub Backup.

Backup Behavior
================

Regardless of how you run GitHub Backup, you should understand the options available to you during its execution.

* Local directory - GitHub Backup needs an existing local git repository to put the backup branch it creates (or modifies).

* Branch names - each backup operation creates a new orphan (no commits) branch or checks out a previously created backup branch. Branch names are specific to the user or repository being backed up, so you can easily keep back ups of multiple remote repos on a single local repository.

* Specifying a user or repository - if a ```user``` or ```repo``` parameter is given to GitHub Backup, it will attempt to back up their remote info (if they are indeed a valid GitHub user or repository). If neither is specified, whichever remote repository is tied to the local git repository will be backed up automatically.

* GitHub credentials - there are four ways to authenticate with GitHub to query the information that will be backed up:
  * Username/password
  * Username/token
  * Token
  * Anonymously

  Based on what combination of ```login``` name, ```password``` and OAuth ```token``` you provide, GitHub Backup will try to connect with authentication. If authenticated, you have a default of 5,000 API accesses per hour. This is more than sufficient to back up a typical repository ([SCIFIO](https://github.com/scifio/scifio) takes ~100 accesses, for example).

  If authentication fails or credentials are not provided, anonymous authentication will be used. This provides a rate limit of 60 accesses per hour, which can be very restricting.

  However, GitHub Backup is designed with a resume feature that is guaranteed to complete eventually (barring random communication errors during the backup process!). If it runs out of API accesses, it will write a resume file and the commit message will indicate an incremental backup took place. On future runs, minimal API accesses (1-2 at most) will be used until the resume point is reached, at which point backup will continue as normal until completion, or API accesses run out again. There is no limit to the number of times the backup process can be resumed.

  NB: ```login``` is used for authentication, but ```user``` is the target to back up! Each must be specified, even if they are for the same user.

* Clean (complete) backup -  Note that the nature of the resume process means that any changes in incrementally backed up data will NOT be reflected in the GitHub Backup branch until a complete backup is performed. This behavior can be forced using the ```clean``` flag.


Running from an Executable
==========================

For your convenience, there are executables available for each platform. This is the simplest and fastest way to start using GitHub Backup.

In all cases, once the actual application is running the behavior of the backup is the same:

1. A file chooser will pop up. Use this to select your local git repository where the backup branch will be stored.
2. After choosing a directory, a sequence of dialogs will pop up to populate each GitHub backup parameter. These parameters are optional (see the [Backup Behavior](#backup-behavior) section) and you can specify as many or as few as you prefer.
3. When the last parameter dialog is confirmed, the backup process will begin. When it completes, you will get a notification of how the backup went. If it was incomplete, you should check your local git repository - you may need to clean up files, depending on what went wrong. If it was just a matter of running out of GitHub API accesses, simply run the backup again in an hour or so and it will resume where it left off.

OSX
---

Users:

1. Download the .app from [jenkins](http://jenkins.imagej.net/job/github-backup-osx/lastSuccessfulBuild/artifact/target/GitHubBackup.tar.gz)
2. Extract GitHubBackup.app from the downloaded .tar.gz
3. (optional) Drag the downloaded .app to your Applications folder
4. Run GitHubBackup.app

Developers:

1. Check out the source with ```git clone git@github.com:uw-loci/github-backup-java```
2. Build the jar with ```mvn package``` from the github-backup-java directory
3. Run ```ant bundle-github-backup``` from the github-backup-java directory to create a target/GitHubBackup.app

Windows
-------

See the [Maven](#running-from-maven) and [executable jar](#as-an-executable-jar) sections for now.

Linux
-----

1. Download the .jar from [jenkins](http://jenkins.imagej.net/job/github-backup-java/lastSuccessfulBuild/artifact/target/github-backup-jar-with-dependencies.jar) to the directory of your choice
2. Download the github-backup.sh script from [jenkins](http://jenkins.imagej.net/job/github-backup-java/lastSuccessfulBuild/artifact/github-backup.sh).
3. Run ```github-backup.sh /full/path/to/github-backup.jar```
4. (Optional) Add the ```github-backup.sh``` script to a script location on your PATH (e.g. /usr/bin).

The ```github-backup.sh``` script overwrites itself the first time it's run with a script that will execute the given path as a jar. All arguments are automatically piped to the jar, so you use the script just like you would the [jar itself](#as-an-executable-jar).

If you use a fully qualified path the first time you run ```github-backup.sh``` then the script can be anywhere on your computer and it will always find the github-backup.jar.

If you add ```github-backup.sh``` to your path, you can run the backup script from anywhere on your computer.


Running from Maven
==================

A [Maven](http://maven.apache.org/) mojo plugin is provided. This can be convenient if your local git repository is also a Maven project (a pom.xml is required to run the GitHubBackupMojo).

From the command line
---------------------

The simplest way to use the GitHubBackupMojo is from the command line. To install the mojo:

1. Run ```git clone git@github.com:uw-loci/github-backup-java```
2. Run ```mvn install``` from the github-backup-java directory.

That's it! In any directory with a pom.xml you can now run either of these commands:

* ```mvn loci:github-backup:help``` - prints usage information, including a list of available parameters.
* ```mvn loci:github-backup:backup``` - performs a backup operation.
  Parameters can be passed using standard Maven syntax:
  ```-Dparam=value```

From a pom
----------

You can bind mojos to specific maven lifecycles, causing them to be executed each time that lifecycle runs.

For example, to run GitHubBackup each time you run ```mvn install```, add the following to your project's pom.xml:

  ```
  <build>
    <plugins>
      <plugin>
        <groupId>com.piercedveil</groupId>
        <artifactId>github-backup</artifactId>
        <version>1.0.0</version>
        <executions>
          <execution>
            <id>backup-install</id>
            <phase>install</phase>
            <goals>
              <goal>backup</goal>
            </goals>
            <configuration>
              <localDir></localDir>
              <login></login>
              <pass></pass>
              <token></token>
              <user></user>
              <repo></repo>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
  ```

Note that if you want to use authentication in this case, and will be committing this to a public repository, you should authenticate with an [OAuth token](https://help.github.com/articles/creating-an-access-token-for-command-line-use). Tokens can be used for API access like this, but not standard github.com logins. Also, you can have any number of OAuth tokens, and they can be deleted at any time.

As an Executable Jar
====================

The GitHubBackup project also creates an executable jar. To get the executable jar you can download the latest stable version from [jenkins](http://jenkins.imagej.net/job/github-backup-java/lastSuccessfulBuild/artifact/target/github-backup-jar-with-dependencies.jar).

If you want to build from source instead:

1. Run ```git clone git@github.com:uw-loci/github-backup-java```
2. Run ```mvn package``` from the github-backup-java directory.

The executable jar will be built to "/target/github-backup-jar-with-dependencies.jar". Executable jars can be double-clicked to open. To pass arguments you'll have to run it from the command line:

```
java -jar path/to/github-backup-jar-with-dependencies.jar [args]
```

Run with the ```-h``` flag to see the list of arguments.

Bugs, Requests, Comments
========================

Please send any bugs or feedback to the [LOCI Software mailing list](http://loci.wisc.edu/mailman/listinfo/loci-software). Thank you for using GitHub Backup!

Future plans
============

* Windows and linux executables

* Support for more repository hosts

* Recursive repository/user backup

* Initialize a local git repo if none found
