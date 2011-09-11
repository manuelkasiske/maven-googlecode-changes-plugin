/**
 * Copyright 2010.
 *
 * This file is part of googlecode-changes-maven-plugin.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.changes.model.Action;
import org.apache.maven.plugins.changes.model.Release;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

/**
 * 
 * Helper class for changes.xml files.
 * 
 */
public final class Changes
{
	private static Log log;

	private Changes()
	{
	}

	private static Log getLog()
	{
		return log;
	}

	private static void setLog(Log log)
	{
		Changes.log = log;
	}

	private static Element addReleaseSection(final Element element, final Release release)
	{
		getLog().debug("addReleaseSection(" + element + ", " + release + ")");
		if (element != null)
		{
			if (release != null)
			{
				Element temp = element.addElement("release");
				temp.addAttribute("date", release.getDateRelease());
				temp.addAttribute("version", release.getVersion());
				temp.addAttribute("description", release.getDescription());
			}
		}

		getLog().debug("addReleaseSection: returning " + element);
		return element;
	}

	/**
	 * 
	 * Generates a changes.xml file based on a release. <br />
	 * Output file will be created if needed otherwise existing one will be
	 * reused.
	 * 
	 * @param release
	 * @param xmlPath
	 * @param log
	 * @throws MojoFailureException
	 */
	public static void generate(final Release release, final File xmlPath, final Log log) throws MojoFailureException
	{
		final Document document;
		final Element releaseElement;

		setLog(log);

		if (!xmlPath.exists())
		{
			getLog().info("Creating file <" + xmlPath + ">");
			try
			{
				xmlPath.getParentFile().mkdirs();
				if (!xmlPath.createNewFile())
				{
					throw new MojoFailureException("<" + xmlPath + "> already exists");
				}
			}
			catch (IOException e)
			{
				throw new MojoFailureException(e.getMessage());
			}

			final DocumentFactory documentFactory = new DocumentFactory();
			Element el = documentFactory.createElement(QName.get("document", "", "http://maven.apache.org/changes/1.0.0"));
			el.addAttribute(QName.get("schemaLocation", "xsi", "http://www.w3.org/2001/XMLSchema-instance"),
					"http://maven.apache.org/changes/1.0.0 http://maven.apache.org/xsd/changes-1.0.0.xsd");
			document = documentFactory.createDocument(el);

			final Element documentElement = document.getRootElement();
			documentElement.addElement("properties");
			final Element bodyElement = documentElement.addElement("body");

			releaseElement = addReleaseSection(bodyElement, release);
		}
		else
		{
			getLog().info("Reuse existing file <" + xmlPath + ">");

			try
			{
				document = new SAXReader().read(xmlPath);
			}
			catch (DocumentException e)
			{
				throw new MojoFailureException(e.getMessage());
			}

			Node releaseNode = document.selectSingleNode("/document/body/release[@version='" + release.getVersion() + "']");
			if (releaseNode != null)
			{
				getLog().debug("Using existing release node for version <" + release.getVersion() + ">");
				releaseElement = Element.class.cast(releaseNode);
			}
			else
			{
				// TODO: Make creation of new body nodes more stable
				getLog().debug("Creating new release node for version <" + release.getVersion() + ">");
				if (document.selectSingleNode("/document/body") == null)
				{
					final Element documentElement;
					if (document.selectSingleNode("/document") == null)
					{
						documentElement = document.addElement("document");
					}
					else
					{
						documentElement = Element.class.cast(document.selectSingleNode("/document"));
					}
					documentElement.addElement("body");
				}

				Element bodyElement = Element.class.cast(document.selectSingleNode("/document/body"));
				getLog().debug("new file, calling addReleaseSection(" + bodyElement + ", " + release + ")");

				releaseElement = addReleaseSection(bodyElement, release);
			}
		}

		getLog().debug("releaseElement: " + releaseElement);

		if (release.getActions() != null)
		{
			for (final Object object : release.getActions())
			{
				final Action action = Action.class.cast(object);
				getLog().debug("Action " + action.getIssue() + " : " + action.getAction());

				Node actionNode = document.selectSingleNode("/document/body/release[@version='" + release.getVersion() + "']/action[@issue='"
						+ action.getIssue() + "']");
				if (action.getIssue() == null || actionNode == null)
				{
					if (releaseElement != null)
					{
						Element actionElement = releaseElement.addElement("action");
						actionElement.addAttribute("dev", action.getDev());
						actionElement.addAttribute("type", action.getType());
						actionElement.addAttribute("due-to", action.getDueTo());
						actionElement.addAttribute("due-to-email", action.getDueToEmail());
						actionElement.addAttribute("issue", action.getIssue());
						actionElement.addText(action.getAction());
					}
				}
				else
				{
					getLog().warn("Action <" + action.getIssue() + "> already exists; skipping");
				}
			}
		}

		try
		{
			final XMLWriter writer = new XMLWriter(new FileWriter(xmlPath), OutputFormat.createPrettyPrint());
			writer.write(document);
			writer.close();
		}
		catch (IOException e)
		{
			throw new MojoFailureException(e.getMessage());
		}
	}

}
