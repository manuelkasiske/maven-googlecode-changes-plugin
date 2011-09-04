/**
 * Copyright 2010.
 *
 * This file is part of maven-googlecode-changes-plugin.
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.googlecode.maven;

import com.google.gdata.client.projecthosting.IssuesQuery;
import com.google.gdata.client.projecthosting.ProjectHostingService;
import com.google.gdata.data.projecthosting.IssuesEntry;
import com.google.gdata.data.projecthosting.IssuesFeed;
import com.google.gdata.data.projecthosting.Label;
import com.google.gdata.data.projecthosting.State;

import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.changes.model.Action;
import org.apache.maven.plugins.changes.model.Release;

/**
 * Create a report based on a googlecode issues.
 * 
 * @goal create-report
 */
public class CreateReportMojo extends AbstractMojo
{
	private static final String TYPE_LABEL = "Type-";
	private static final String MILESTONE_LABEL = "Milestone-";
	private static final String ALL_MILESTONES = "all";

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	/**
	 * Username used to log in googlecode.
	 * 
	 * @parameter expression="${googlecodeUsername}"
	 */
	private String username;

	/**
	 * Password used to log in googlecode.
	 * 
	 * @parameter expression="${googlecodePassword}"
	 */
	private String password;

	/**
	 * Project identifier.
	 * 
	 * @parameter default-value="${project.artifactId}"
	 *            expression="${projectIdentifier}"
	 * @required
	 */
	private String projectIdentifier;

	/**
	 * Project version.
	 * 
	 * @parameter default-value="${project.version}" expression="${milestone}"
	 * @required
	 */
	private String milestone;

	/**
	 * Path of the changes.xml that will be generated.
	 * 
	 * @parameter 
	 *            default-value="${basedir}/target/generated-changes/changes.xml"
	 *            expression="${xmlPath}"
	 * @required
	 */
	private File xmlPath;

	/**
	 * Mapping between changes.xml action types and googlecode issue types. All
	 * your own Trackers fields should be mapped to one of: add, fix, remove,
	 * update.
	 * 
	 * @parameter
	 * @required
	 */
	private Map<String, String> issueTypes;

	/**
	 * {@inheritDoc}
	 */
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			final ProjectHostingService service = new ProjectHostingService("maven-changes-1");
			if (this.username != null)
			{
				service.setUserCredentials(this.username, this.password);
			}

			final URL feedUrl = new URL("http://code.google.com/feeds/issues/p/" + this.projectIdentifier + "/issues/full");
			getLog().debug(feedUrl.toString());

			final IssuesQuery query = new IssuesQuery(feedUrl);
			// TODO: Here is the place for issue 2
			query.setLabel(CreateReportMojo.MILESTONE_LABEL + "1_0_RC");
			query.setMaxResults(Integer.MAX_VALUE);

			final IssuesFeed issues = service.getFeed(query, IssuesFeed.class);

			final Release release = new Release();
			if (!this.milestone.equals(CreateReportMojo.ALL_MILESTONES))
			{
				release.setVersion(this.milestone);
			}

			// Not available
			release.setDescription("");
			release.setDateRelease(CreateReportMojo.DATE_FORMAT.format(new Date()));

			final List<Action> actions = new LinkedList<Action>();
			for (final IssuesEntry issue : issues.getEntries())
			{
				getLog().debug(issue.toString());
				getLog().debug("  Issue " + issue.getId() + " : " + issue.getTitle().getPlainText());

				// TODO: config option whether we want only closed issues...
				if (!State.Value.CLOSED.equals(issue.getState().getValue()))
				{
					continue;
				}

				final Action action = new Action();
				if (issue.getOwner() != null)
				{
					action.setDev(issue.getOwner().getUsername().getValue());
				}

				// There are four valid values: add, fix, remove, update.
				final String issueType = extractNamedLabel(issue.getLabels(), CreateReportMojo.TYPE_LABEL);
				if (this.issueTypes.containsKey(issueType))
				{
					action.setType(this.issueTypes.get(issueType));
				}
				else
				{
					getLog().warn("Type <" + issueType + "> cannot be translated for issue <" + issue.getIssueId() + ">; skipping");
					continue;
				}

				if (issue.getVersionId() == null)
				{
					action.setIssue("unknown");
				}
				else
				{
					action.setIssue(issue.getVersionId());
				}

				action.setAction(issue.getIssueId().getValue() + ": " + issue.getTitle().getPlainText());

				getLog().debug("Action " + action.getIssue() + " : " + action.getAction());
				actions.add(action);
			}

			release.setActions(actions);
			getLog().debug("Release action length: " + release.getActions().size());

			Changes.generate(release, this.xmlPath, getLog());
		}
		catch (Exception e)
		{
			getLog().error(e);
			throw new MojoExecutionException(e.getMessage());
		}
	}

	/**
	 * @param labels
	 * @param name
	 * @return value of label with specified name if exists null otherwise
	 */
	private String extractNamedLabel(final List<Label> labels, final String name)
	{
		for (final Label label : labels)
		{
			final String labelName = label.getValue();
			if (labelName.startsWith(name))
			{
				return labelName.substring(name.length());
			}
		}

		return null;
	}
}
