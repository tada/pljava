PL/Java is an open source project and contributions are vital for its success. In fact, all development of the project is done using contributions. Here are a few guide lines that will help you submit a contribution.

## Getting started
* Make sure you have a [GitHub account](/signup/free).
* Create a fork of the [PL/Java repository](/tada/pljava).
* Take a look at the [Git Best Practices](http://sethrobertson.github.com/GitBestPractices/) document.

## Let people know what you're planning
You should let the community know what you're planning to do by discussing it on the [PL/Java Mailing List](http://lists.pgfoundry.org/mailman/listinfo/pljava-dev). In many cases it might also be a good idea to first [create an issue](/tada/pljava/issues) where the details of what needs to be done can be discussed (the actual pull-request is an issue in itself so in case you already have something, that issue is probably sufficient).

## Making Changes
* Create a local clone of your fork.
* Create a topic branch for your work. You should branch off the _master_ branch. Name your branch by the type of contribution, source branch, and nature of the contribution, e.g., _bug/master/my_contribution_.  
Generally, the type is bug, or feature, but you can use something else if they don't fit. To create a topic branch based on master:  
_git checkout master && git pull && git checkout -b bug/master/my_contribution_
* Don't work directly on the _master_ branch, or any other core branch. Your pull request will be rejected unless it is on a topic branch.
* Keep your commits distinct. A commit should do one thing, and only one thing.
* Make sure your commit messages are in [the proper format](#wiki-commit-message-format).
* If your commit fixes an issue, close it with your commit message (by appending, e.g., fixes #1234, to the summary).

## Submitting Changes
* Push your changes to a topic branch in your fork of the repository.
* Submit a pull request to the tada/pljava repository.

<a id="commit-message-format"></a>
## Commit Message Format
What should be included in a commit message?
The three basic things to include are:
* Summary or title.
* Detailed description
* Issue number (optional).

Here is a sample commit message with all that information:
<pre>
Adds UTF-8 encoding to POM properties

Some POM's did not have the source encoding specified. This
caused unnecessary warning printouts during build. This commit
ensures that all POM's includes the correct declaration for
UTF-8.

Closes #1234
</pre>
The summary should be kept short, 50 characters or less, and the lines in the detailed message should not exceed 72 characters. These limits are recommended to get the best output possible from the git log command and also to be able to view the commits in a terminal window with 80 character limit.

The issue number is optional and should only be included when the commit really closes an issue. The close will then occur when the pull request is merged.
