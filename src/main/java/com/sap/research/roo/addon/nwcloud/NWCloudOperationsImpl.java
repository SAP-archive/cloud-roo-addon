/*
 * Copyright 2012 SAP AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sap.research.roo.addon.nwcloud;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;

import org.osgi.service.component.ComponentContext;

import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.project.maven.Pom;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.Plugin;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.util.FileUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.springframework.roo.support.util.DomUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Roo Addon for SAP NetWeaver Cloud - Operations Implementation class
 * -------------------------------------------------------------------
 * 
 * This class implements the interface of the addon operations as defined in NWCloudOperations.
 * The interface in NWCloudOperations has been used to bind Roo commands defined in NWCloudCommands
 * to actual methods, which are now implemented here.
 *
 * @see NWCloudOperations
 * @see NWCloudCommands
 */
@Component
@Service
public class NWCloudOperationsImpl implements NWCloudOperations {

	// --------------------------------------------------------------------------------
	// Initial stuff
	// --------------------------------------------------------------------------------

	/**
	 * Suffix that will be appended to filename when making backups before modifications. 
	 */
	private static final String BAK_SUFFIX = ".pre.nwcloud";

	/**
	 * Get hold of a JDK Logger
	 *  - Output with log level severe is red, warning is purple, and info is green
	 *  - Output with log levels fine, finer, and finest are not shown on console in standard mode
	 */
	private Logger log = Logger.getLogger(getClass().getName());

	/**
	 *  When this OSGi bundle is activated (see function "activate"), we store the
	 *  OSGi context passed from the surrounding OSGi environment we "live in" here.
	 */
	private ComponentContext context = null;

	/**
	 *  Get some references from the OSGi environment in order to access functionality
	 *  from other parts of Roo (e.g. using ProjectOperations, FileManager, ...).
	 */
	@Reference private ProjectOperations projectOperations;
	@Reference private FileManager fileManager;
	@Reference private PathResolver pathResolver;

	/**
	 * This is called when our OSGi bundle is activated. We use this opportunity to store
	 * the OSGi context passed from the surrounding OSGi environment we "live in".
	 * 
	 * @param context OSGi context passed from the surrounding OSGi environment we "live in"
	 */
	protected void activate(ComponentContext context) {
		// Store OSGi context
		this.context = context;
	}

	// --------------------------------------------------------------------------------
	// Command: nwcloud enable-deploy
	// --------------------------------------------------------------------------------

	/**
	 * This returns true if the command "nwcloud enable-deploy" of our addon should be available to
	 * the user. We make the command available if "pom.xml" exists and if its reverse command
	 * "nwcloud disable-deploy" is unavailable (see nwcloudDisableDeployIsAvailable() for the checks there).
	 * 
	 * @return True if command can be used (POM exists), false otherwise 
	 */
	public boolean nwcloudEnableDeployIsAvailable() {
		
		boolean result = false;
		
		if (this.getPOM()!=null) {
			result = !nwcloudDisableDeployIsAvailable();
		}
		
		return result;

	}

	/**
	 * This is our command "nwcloud enable-deploy". It will do two things:
	 * 1.) It will change the build plugins defined in the "pom.xml" to match the ones
	 *     defined in "src/main/resources/[...]/configuration.xml". One aspect of this is
	 *     the integration of our maven-nwcloud-plugin in the package phase of the Maven build,
	 *     so that the goal "hint" of it is called that is providing further information
	 *     for the user on what can be done now (e.g. deploying WAR to NW Cloud).
	 * 2.) Copy the "src/main/resources/[...]/nwcloud.properties" to the root of the project.
	 *     The file "nwcloud.properties" stores the configuration for the actions that
	 *     can be performed using the maven-nwcloud-plugin.
	 */
	public void nwcloudEnableDeploy() {

		// 1. Change build plugins in "pom.xml" according to "src/main/resources/[...]/configuration.xml"
		//    This will
		//      - Add maven-bundle-plugin to create a more OSGi compatible MANIFEST.MF
		//      - Reconfigure maven-war-plugin to use the MANIFEST.MF created by maven-bundle-plugin
		//      - Add maven-nwcloud-plugin to print out hints on how to deploy to NW Cloud after packaging
		this.backup(this.getPOM().getPath(), null);
		Element configurationXml = XmlUtils.getConfiguration(getClass());
		if (configurationXml!=null) {
			this.updateBuildPlugins(configurationXml);
		} else {
			this.log.warning("NWCloud-AddOn: Getting 'src/main/resources/[...]/configuration.xml' from addon returned null.");
		}

		// 2. Copy "src/main/resources/[...]/nwcloud.properties" to root of project.
		//    The file "nwcloud.properties" stores the configuration for the actions that
		//    can be performed with the maven-nwcloud-plugin.
		this.copyFileFromAddonToProject(this.getPOM().getRoot(), "nwcloud.properties", "Config file for maven-nwcloud-plugin");	

	}

	/**
	 * This function will loop all build plugins defined by us in "src/main/resources/[...]/configuration.xml",
	 * create a org.springframework.roo.project.Plugin object out of each, remove these from the "pom.xml",
	 * and re-add them in the (raw) way defined by us. After this, all build plugins defined by us in
	 * "configuration.xml" should be in "pom.xml" exactly in the way we defined them.
	 * 
	 * @param configuration org.w3c.dom.Element storing the content of "src/main/resources/[...]/configuration.xml"
	 */
	private void updateBuildPlugins(Element configuration) {

		// Loop all "/configuration/nwcloud/build/plugins/plugin" elements in passed DOM element,
		// create org.springframework.roo.project.Plugin objects out of them and remove these
		// from the "pom.xml".
		List<Element> xmlPlugins = XmlUtils.findElements("/configuration/nwcloud/build/plugins/plugin", configuration);
		if (xmlPlugins!=null) {
			if (!xmlPlugins.isEmpty()) {

				for (Element xmlPlugin : xmlPlugins) {
					Plugin buildPlugin = new Plugin(xmlPlugin);
					this.removeBuildPlugin(buildPlugin);
				}
				
				// Re-add the build plugins to the "pom.xml" exactly in the way defined in the passed DOM element.	
				for (Element xmlPlugin : xmlPlugins) {
					this.addRawBuildPlugin(xmlPlugin);
				}

			} else {
				this.log.warning("NWCloud-AddOn: Getting elements '/configuration/nwcloud/build/plugins/plugin' from 'configuration.xml' returned 0 matching elements.");
			}
		} else {
			this.log.warning("NWCloud-AddOn: Getting elements '/configuration/nwcloud/build/plugins/plugin' from 'configuration.xml' returned null.");
		}
	}
	
	/**
	 * Remove the passed plugin from build plugins in "pom.xml".
	 * This function is a convenience function to call the function
	 * removeBuildPlugin(Plugin plugin, String containingPath, String path)
	 * for this use case.
	 * 
	 * @param plugin org.springframework.roo.project.Plugin to remove from build plugins in "pom.xml" 
	 */
	public void removeBuildPlugin(Plugin plugin) {
		this.removeBuildPlugin(plugin, "/project/build/plugins", "/project/build/plugins/plugin");
	}

	/**
	 * Remove the passed "plugin" from the containing element defined by "containingPath"
	 * in the path of "path". This function is called by removeBuildPlugin(Plugin plugin),
	 * which is a convenience function to call this function for the use case of removing
	 * build plugins from the "pom.xml" file.
	 * 
	 * @param plugin org.springframework.roo.project.Plugin to remove from "pom.xml"
	 * @param containingPath String of path to containing element to remove plugin from
	 * @param path String of path to remove the plugin from
	 */
	private void removeBuildPlugin(Plugin plugin, String containingPath, String path) {

		if (plugin!=null) {

			// Read "pom.xml" and store reference to root Element
			Document document = XmlUtils.readXml(fileManager.getInputStream(this.getPOM().getPath()));
			Element root = document.getDocumentElement();
	
			// Loop through all elements in the path of the containing element that match the
			// desired path of removal candidates. If the candidate matches the plugin that
			// should be removed, it will be removed from the "pom.xml".
			String descriptionOfChange = "";
			Element pluginsElement = XmlUtils.findFirstElement(containingPath, root);
			String pluginID = plugin.getGroupId() + plugin.getArtifactId();
			for (Element candidate : XmlUtils.findElements(path, root)) {
				try {
					Plugin candidatePlugin = new Plugin(candidate);
					String candidatePluginID =	candidatePlugin.getGroupId() + candidatePlugin.getArtifactId();
					//log.log(Level.INFO, " - Comparing '"+pluginID+"' with '"+candidatePluginID+"'");
					if (pluginID.equals(candidatePluginID)) {
						pluginsElement.removeChild(candidate);
						descriptionOfChange = "Removal of build plugin: " + plugin.getArtifactId();
						// We will not break the loop (even though we could theoretically), just in case it was declared in the POM more than once
					}
				} catch (Exception e) {
					// Ignore
				}
			}
	
			// Clean up the element containing the build plugins in "pom.xml"
			DomUtils.removeTextNodes(pluginsElement);

			// Update "pom.xml" file
			fileManager.createOrUpdateTextFileIfRequired(this.getPOM().getPath(), XmlUtils.nodeToString(document), descriptionOfChange, true);

		} else {
			this.log.warning("NWCloud-AddOn: The given plugin object that should be removed from POM build plugins was null.");
		}

	}

	/**
	 * This function adds a given XML node to the build plugins section of "pom.xml".
	 * 
	 * @param pluginXML org.w3c.dom.Node storing the XML of the build plugin definition to add to "pom.xml"
	 */
	public void addRawBuildPlugin(Node pluginXML) {
		
		if (pluginXML!=null) {

			// Read "pom.xml" and store reference to root Element
			Document document = XmlUtils.readXml(fileManager.getInputStream(this.getPOM().getPath()));
			Element root = document.getDocumentElement();
	
			// Get build plugins Element in POM
			Element pluginsElement = XmlUtils.findFirstElement("/project/build/plugins", root);
			if (pluginsElement!=null) {

				// Append the build plugin passed via parameter pluginXML to the build plugins element of POM
				Node plugin  = document.importNode(pluginXML, true);
				pluginsElement.appendChild(plugin);
		
				// Fetch artifactID of added build plugin, create description of change, and update "pom.xml"
				NodeList childNodes = plugin.getChildNodes();
				String addedArtifactID = null;
				for (int i=0;i<childNodes.getLength();i++) {
					Node child = childNodes.item(i);
					if (child!=null) {
						if (child.getNodeName()!=null) {
							if (child.getNodeName().equals("artifactId")) {
								if (child.getTextContent()!=null) {
									addedArtifactID = child.getTextContent(); 
								}
							}
						}
					}
				}
				String descriptionOfChange = null;
				if (addedArtifactID!=null) {
					descriptionOfChange = "Added raw build plugin: " + addedArtifactID;
				} else {
					descriptionOfChange = "Added a raw build plugin";
				}
				fileManager.createOrUpdateTextFileIfRequired(this.getPOM().getPath(), XmlUtils.nodeToString(document), descriptionOfChange, true);

			} else {
				this.log.warning("NWCloud-AddOn: The build plugins element could not be found in the POM and thus, no new plugin element can be added to it.");
			}

		} else {
			this.log.warning("NWCloud-AddOn: The given XML element that should be added to the build plugins section of the POM was null.");
		}

	}

	// --------------------------------------------------------------------------------
	// Command: nwcloud disable-deploy
	// --------------------------------------------------------------------------------

	/**
	 * This returns true if the command "nwcloud disable-deploy" of our addon should be available to
	 * the user. We check if a backup of the "pom.xml" exists and assume that this is the backup
	 * of the POM that has been modified by "nwcloud enable-deploy". Additionally we check that the
	 * "nwcloud.properties" file exists in the root of the project.
	 * 
	 * @return True if command can be used (backup of POM and nwcloud.properties exists), false otherwise 
	 */
	public boolean nwcloudDisableDeployIsAvailable() {

		boolean result = false;

		if (this.getPOM()!=null) {
			result = fileManager.exists(this.getPOM().getPath() + BAK_SUFFIX);
			if (result) {
				result = fileManager.exists(this.getPOM().getRoot() + File.separatorChar + "nwcloud.properties");
			}
		}

		return result;
		
	}

	/**
	 * This command will revert the command "nwcloud enable-deploy".
	 */
	public void nwcloudDisableDeploy() {

		// We know that backup of "pom.xml" as well as "nwcloud.properties" exist in root of project.
		// Otherwise this command would not be available on the Roo shell (see nwcloudDisableDeployIsAvailable).

		this.backupRevert(this.getPOM().getPath(), "Restoring old build plugin configuration in pom.xml");
		fileManager.delete(this.getPOM().getRoot() + File.separatorChar + "nwcloud.properties", "Delete config file for maven-nwcloud-plugin");

	}

	// --------------------------------------------------------------------------------
	// Command: nwcloud enable-jpa
	// --------------------------------------------------------------------------------
	
	/**
	 * This returns true if the command "nwcloud enable-jpa" of our addon should be available to the user.
	 * We just check if a "pom.xml" exists and if the reverse command "nwcloud disable-jpa" is unavailable.
	 * See nwcloudDisableJPAIsAvailable() for the checks performed there.
	 * 
	 * @return True if commands can be used (persistency.xml exists), false otherwise 
	 */
	public boolean nwcloudEnableJPAIsAvailable() {

		boolean result = false;
		
		if (this.getPOM()!=null) {
			result = !nwcloudDisableJPAIsAvailable();
		}
		
		return result;

	}

	/**
	 * This is the command "nwcloud enable-jpa". It will configure the JPA persistence layer in a
	 * way that will use the NW Cloud persistence service.
	 */
	public void nwcloudEnableJPA() {

		// TODO
		// One could check here if ECLIPSELINK is used as JPA provider in persistence.xml
		// and abort, if something else is used.

		// 1. Copy the "persistence.xml" from the addon resources to the directory
		//    "src\main\resources\META-INF\persistence.xml" of the project.
		//    -> the existing "persistence.xml" will be overwritten, so we will do
		//       a backup before to be able to revert this operation.
		
		// Get the META-INF dir of the current project ("src\main\resources\META-INF")
		// (Later in the packaged WAR this directory will reside in "WEB-INF\classes\META-INF".) 
		String dirWebMetaInf = this.getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF");
		// Backup "persistence.xml" which is located in this folder
		this.backup(dirWebMetaInf + File.separatorChar + "persistence.xml", null);
		// Overwrite the existing "persistence.xml" with the one included in the resources of our addon
		copyFileFromAddonToProject(dirWebMetaInf, "persistence.xml", "NW Cloud JPA persistency config (needs EclipseLink)");
		
		// --------------------------------------------------------------------------------
		
		// 2. Backup "src\main\webapp\WEB-INF\web.xml" first, and then modify it by inserting
		//    the following to declare the DataSource which the application server should
		//    fetch from the environment and provide to the web app through JNDI.
		//		<resource-ref>
		//			<res-ref-name>jdbc/DefaultDB</res-ref-name>
		//			<res-type>javax.sql.DataSource</res-type>
		//		</resource-ref>

		// Get the WEB-INF dir ("src\main\webapp\WEB-INF"), where the "web.xml" is located.
		String dirWebInf = this.getPathResolved(Path.SRC_MAIN_WEBAPP, "WEB-INF");
		String fileWebXml = dirWebInf + File.separatorChar + "web.xml";
		
		// Backup "src\main\webapp\META-INF\web.xml"
		this.backup(fileWebXml, null);

		// Insert declaration for app server in "web.xml" to import DataSource from environment to JNDI

		// Read "web.xml" and store reference to root Element
		Document document = XmlUtils.readXml(fileManager.getInputStream(fileWebXml));
		Element root = document.getDocumentElement();

		// Add JNDI ressource definition for JPA data source to use (if it does not yet exist)
		if (XmlUtils.findFirstElement("/web-app/resource-ref/resource-ref-name[text()='jdbc/DefaultDB']", root)==null) {
			
			// Create needed DOM elements
			String elemNamespace="http://java.sun.com/xml/ns/javaee";
			Element resRefElement = document.createElementNS(elemNamespace, "resource-ref");
			Element resRefNameElement = document.createElementNS(elemNamespace, "res-ref-name");
			Node resRefNameElementText = document.createTextNode("jdbc/DefaultDB");
			Element resRefTypeElement = document.createElementNS(elemNamespace, "res-type");
			Node resRefTypeElementText = document.createTextNode("javax.sql.DataSource");
			
			// Connect and insert elements in XML document
			resRefNameElement.appendChild(resRefNameElementText);
			resRefTypeElement.appendChild(resRefTypeElementText);
			resRefElement.appendChild(resRefNameElement);
			resRefElement.appendChild(resRefTypeElement);
			root.appendChild(resRefElement);

			// Update "web.xml"
			String descriptionOfChange="Added JNDI ressource for JPA datasource";
			fileManager.createOrUpdateTextFileIfRequired(fileWebXml, XmlUtils.nodeToString(document), descriptionOfChange, true);

		}

		// --------------------------------------------------------------------------------
		
		// 3. Modify "src\main\resources\META-INF\spring\applicationContext.xml"
		//    - Remove the existing declaration of the "dataSource" bean
		//			<bean class="org.apache.commons.dbcp.BasicDataSource" destroy-method="close" id="dataSource">
		//				[...]
		//			</bean>
		//    - Insert statement to fetch dataSource from JNDI (as created by application server)
		//      	<jee:jndi-lookup id="dataSource" jndi-name="jdbc/DefaultDB" />

		// Get the Spring config file of the current project ("src\main\resources\META-INF\spring\applicationContext.xml")
		// and create a backup of it, so we're able to revert the changes.
		String fileSpringConf = this.getPathResolved(Path.SPRING_CONFIG_ROOT, "applicationContext.xml");
		this.backup(fileSpringConf, null);

		// Read "applicationContext.xml" and store reference to root Element
		document = XmlUtils.readXml(fileManager.getInputStream(fileSpringConf));
		root = document.getDocumentElement();

		// Loop through all bean elements and remove all beans having id "dataSource"
		String descriptionOfChange = "";
		List<Element> beanElements = XmlUtils.findElements("/beans/bean", root);
		for (Element beanElement : beanElements) {
			// Did we find the bean with the id "dataSource"?
			if (beanElement.getAttribute("id").equalsIgnoreCase("dataSource")) {
				Node parent =  beanElement.getParentNode();
				parent.removeChild(beanElement);
				DomUtils.removeTextNodes(parent);
				descriptionOfChange="Removed bean storing static datasource";
				// We will not break the loop (even though we could theoretically), just in case there is more than one such bean declared				
			}
		}

		// Update "applicationContext.xml" file if something has changed
		fileManager.createOrUpdateTextFileIfRequired(fileSpringConf, XmlUtils.nodeToString(document), descriptionOfChange, true);

		// Add bean for dynamic JNDI lookup of datasource (if it does not yet exist)
		if (XmlUtils.findFirstElement("/beans/jndi-lookup[@id='dataSource']", root)==null) {
			
			Element newJndiElement = document.createElementNS("http://www.springframework.org/schema/jee", "jee:jndi-lookup");
			newJndiElement.setAttribute("id", "dataSource");
			newJndiElement.setAttribute("jndi-name", "jdbc/DefaultDB");
			root.appendChild(newJndiElement);
			descriptionOfChange="Added bean for dynamic JNDI lookup of datasource";
	
			// Update "applicationContext.xml"
			fileManager.createOrUpdateTextFileIfRequired(fileSpringConf, XmlUtils.nodeToString(document), descriptionOfChange, true);

		}

	}

	// --------------------------------------------------------------------------------
	// Command: nwcloud disable-jpa
	// --------------------------------------------------------------------------------

	/**
	 * This returns true if the command "nwcloud disable-jpa" of our addon should be available to
	 * the user. We check if a backup of the "persistence.xml" and "applicationContext.xml" exists
	 * and assume that this is the backup of the file modified by "nwcloud enable-jpa". Additionally
	 * we check that the file "context.xml" exists.
	 * 
	 * @return True if command can be used (needed files to revert exist), false otherwise 
	 */
	public boolean nwcloudDisableJPAIsAvailable() {
		
		boolean result = false;

		if (this.getPOM()!=null) {
			result = fileManager.exists(this.getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF") + File.separatorChar + "persistence.xml" + BAK_SUFFIX);
			if (result) {
				result = fileManager.exists(this.getPathResolved(Path.SRC_MAIN_WEBAPP, "WEB-INF") + File.separatorChar + "web.xml" + BAK_SUFFIX);
				if (result) {
					fileManager.exists(this.getPathResolved(Path.SPRING_CONFIG_ROOT, "applicationContext.xml") + BAK_SUFFIX);
				}
			}
		}

		return result;
		
	}

	/**
	 * This command will revert the command "nwcloud enable-jpa".
	 */
	public void nwcloudDisableJPA() {

		// We know that the files we need exist, because otherwise this command would
		// not be available on the Roo shell (see nwcloudDisableJPAIsAvailable).

		this.backupRevert(this.getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF") + File.separatorChar + "persistence.xml", "Restoring former JPA persistency config");
		this.backupRevert(this.getPathResolved(Path.SRC_MAIN_WEBAPP, "WEB-INF") + File.separatorChar + "web.xml", "Restoring former web application config");
		this.backupRevert(this.getPathResolved(Path.SPRING_CONFIG_ROOT, "applicationContext.xml"), "Restoring former Spring application config");

	}

	// --------------------------------------------------------------------------------
	// Command: nwcloud addon-debug
	// --------------------------------------------------------------------------------

	/**
	 * This is the command "nwcloud addon-debug". It will just dump some debug output to the
	 * console, which could be useful during debugging of the addon. (Just for testing.)
	 */
/*
	public void nwcloudAddonDebug() {

		System.out.println("Dumping out found POMs ('pom.xml' files):");
		
		// Dump POMs
		System.out.println(" - projectOperations.getPoms()");
		java.util.Collection<org.springframework.roo.project.maven.Pom> modPoms = projectOperations.getPoms();
		for (org.springframework.roo.project.maven.Pom modPom : modPoms) {
			System.out.println("    - modPom.getModuleName():  '"+ modPom.getModuleName()  +"'");
			System.out.println("    - modPom.getGroupId():     '"+ modPom.getGroupId()     +"'");
			System.out.println("    - modPom.getArtifactId():  '"+ modPom.getArtifactId()  +"'");
			System.out.println("    - modPom.getDisplayName(): '"+ modPom.getDisplayName() +"'");
			System.out.println("    - modPom.getRoot():        '"+ modPom.getRoot()        +"'");
			System.out.println("    - modPom.getPath():        '"+ modPom.getPath()        +"'");
		}

		// Dump what we think is the actual pom.xml of the project
		System.out.println("We think current root POM is:");
		System.out.println(" - getPOM().getPath(): " + (this.getPOM()!=null?"'"+this.getPOM().getPath()+"'":"null") );

		// Dump where we assume "src/main/resources/META-INF/persistence.xml" to be
		System.out.println("The 'persistence.xml' file configuring JPA persistency should be here:");
		System.out.println(" - getPathResolved(Path.SRC_MAIN_RESOURCES, \"META-INF\"+File.separatorChar+\"persistence.xml\"): '" + this.getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF"+File.separatorChar+"persistence.xml") + "'");

		// Dump if "src/main/resources/META-INF/persistence.xml" exists
		System.out.println(" - Does '" + this.getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF"+File.separatorChar+"persistence.xml") + "' exist? " + fileManager.exists(this.getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF"+File.separatorChar+"persistence.xml")));

		// Test logger output
		System.out.println("Testing logger output with different levels:");
		log.severe (" - Testing logging with level severe.");  // Output on console is red
		log.warning(" - Testing logging with level warning."); // Output on console is purple
		log.info   (" - Testing logging with level info.");    // Output on console is green
		log.fine   (" - Testing logging with level fine.");    // Not shown in standard mode
		log.finer  (" - Testing logging with level finer.");   // Not shown in standard mode
		log.finest (" - Testing logging with level finest.");  // Not shown in standard mode

		// Example output:
		//	Dumping out found POMs ('pom.xml' files):
		//	 - projectOperations.getPoms()
		//	    - modPom.getModuleName():  ''
		//	    - modPom.getGroupId():     'com.sap.research'
		//	    - modPom.getArtifactId():  'voting'
		//	    - modPom.getDisplayName(): 'voting'
		//	    - modPom.getRoot():        'C:\Daten\Dev\Java\EclipseWorkspace-STS-29\tmp\'
		//	    - modPom.getPath():        'C:\Daten\Dev\Java\EclipseWorkspace-STS-29\tmp\pom.xml'
		//	We think current root POM is:
		//	 - getPOM().getPath(): 'C:\Daten\Dev\Java\EclipseWorkspace-STS-29\tmp\pom.xml'
		//	The 'persistence.xml' file configuring JPA persistency should be here:
		//	 - getPathResolved(Path.SRC_MAIN_RESOURCES, "META-INF"+File.separatorChar+"persistence.xml"): 'C:\Daten\Dev\Java\EclipseWorkspace-STS-29\tmp\src\main\resources\META-INF\persistence.xml'
		//	 - Does 'C:\Daten\Dev\Java\EclipseWorkspace-STS-29\tmp\src\main\resources\META-INF\persistence.xml' exist? true
		//	Testing logger output with different levels:
		//	 - Testing logging with level severe.
		//	 - Testing logging with level warning.
		//	 - Testing logging with level info.
		
	}
*/

	// --------------------------------------------------------------------------------
	// Utility functions
	// --------------------------------------------------------------------------------

	/**
	 * Get the Pom object of the "pom.xml" of the current project.
	 * Return null if there is none. If there is at least one POM, return the first.
	 * The full qualified path to the "pom.xml" is available by calling getPath() on Pom object.
	 */
	protected Pom getPOM() {
		
		Pom currentPOM = null;
		
		// Get all POMs of the current project 
		Collection<Pom> poms = projectOperations.getPoms();
		
		// If there is at least 1 POM in the collection, we return the first in there.
		if (poms!=null) {
			if (!poms.isEmpty()) {
				currentPOM = poms.iterator().next();
				if (poms.size()>1) {
					log.warning("NWCloud-AddOn: There is more than one pom.xml in the project. Just took the first.");
				}
			}
		}

		return currentPOM;
		
	}

	/**
	 * Get the full qualified name of a file or directory, which is placed in the given
	 * relative location under the project root dir. This is a convenience method that
	 * is equal to getPathResolved(Path.ROOT, String relativeLocation).
	 * 
	 * @param String relative location under project root (directory or file)
	 * @return String project root + relative location
	 */
	public String getPathResolved(String relativeLocation) {

		return getPathResolved(Path.ROOT, relativeLocation);

	}

	/**
	 * Get the full qualified name of a file or directory, which is placed in a special
	 * folder under the project root (e.g. in Path.SRC_MAIN_JAVA) and from there in
	 * the given relative location. So the overall filename will be assembled like:
	 * project root + special path + relative location
	 * 
	 * @param Path specialPath See constants defined in org.springframework.roo.project.Path
	 * @param String relative location under special path (directory or file)
	 * @return String project root + special path + relative location
	 */
	public String getPathResolved(Path specialPath, String relativeLocation) {
		
		Path sp = specialPath!=null ? specialPath : Path.ROOT;
		String rl = relativeLocation!=null ? relativeLocation : "";
		
		return pathResolver.getIdentifier(LogicalPath.getInstance(sp, ""), rl);

	}

	/**
	 * Copy the file with name "fileName" from the resources of this addon
	 * to the path "path" of the Roo project. The string "desc" will be
	 * shown as additional description of the file operation on Roo shell.
	 * If "desc" is null or an empty string, the description will be omitted.
	 *  
	 * @param path String of path where to copy the file in the Roo project
	 * @param fileName String of the name of the file from addon resources that should be copied 
	 * @param desc String of description of change (will be omitted if null or empty)
	 */
	private void copyFileFromAddonToProject(String path, String fileName, String desc) {
		
		if ((path!=null) && (fileName!=null)) {

			String targetFile = path + File.separatorChar + fileName;
			
			// Use MutableFile in combination with FileManager to take advantage of Roo's
			// transactional file handling which offers automatic rollback if an exception occurs
			MutableFile mutableFile = fileManager.exists(targetFile) ? fileManager.updateFile(targetFile) : fileManager.createFile(targetFile);
			if (desc!=null) {
				if (!desc.trim().isEmpty()) {
					mutableFile.setDescriptionOfChange(desc);
				}
			}
			
            java.io.InputStream inputStream = null;
            java.io.OutputStream outputStream = null;
            try {
                inputStream = FileUtils.getInputStream(this.getClass(), fileName);
                outputStream = mutableFile.getOutputStream();
                IOUtils.copy(inputStream, outputStream);
            } catch (Exception e) {
                throw new IllegalStateException("NWCloud-AddOn: Could not copy '"+fileName+"' from addon resources to '"+path+"'.",e);
            } finally {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(outputStream);
            }

		} else {
			this.log.warning("NWCloud-AddOn: A file should be copyied from the addon resources to a ceratain path. But passed filename and/or path were null.");
		}

	}

	/**
	 * Copy file "sourceFile" to file "targetFile" within the Roo project.
	 * The string "desc" will be shown as additional description of
	 * the file operation on Roo shell. If "desc" is null or an empty
	 * string, the description will be omitted.
	 * 
	 * @param sourceFile String of full qualified name of the file to copy
	 * @param targetFile String of full qualified filename that the "fromFile" should be copied to 
	 * @param desc String of description of change (will be omitted if null or empty)
	 */
	private void copyFileWithinProject(String sourceFile, String targetFile, String desc) {
		
		if ((sourceFile!=null) && (targetFile!=null)) {

			if(fileManager.exists(sourceFile)) {
				
				// Use MutableFile in combination with FileManager to take advantage of Roo's
				// transactional file handling which offers automatic rollback if an exception occurs
				MutableFile mutableFile = fileManager.exists(targetFile) ? fileManager.updateFile(targetFile) : fileManager.createFile(targetFile);
				if (desc!=null) {
					if (!desc.trim().isEmpty()) {
						mutableFile.setDescriptionOfChange(desc);
					}
				}
				
	            java.io.InputStream inputStream = null;
	            java.io.OutputStream outputStream = null;
	            try {
	                inputStream = fileManager.getInputStream(sourceFile);
	                outputStream = mutableFile.getOutputStream();
	                IOUtils.copy(inputStream, outputStream);
	            } catch (Exception e) {
	                throw new IllegalStateException("NWCloud-AddOn: Could not copy file '"+sourceFile+"' to '"+targetFile+"'.",e);
	            } finally {
	                IOUtils.closeQuietly(inputStream);
	                IOUtils.closeQuietly(outputStream);
	            }

			} else {
				this.log.warning("NWCloud-AddOn: A file should be copyied within the project, but the passed passed source file does not exist: '"+sourceFile+"'");
			}

		} else {
			this.log.warning("NWCloud-AddOn: A file should be copyied within the project, but passed source and/or target filename were null.");
		}

	}

	/**
	 * Make a backup of the file "sourceFile". The name of the backup file
	 * will be the name of the "sourceFile" with added suffix BAK_SUFFIX.
	 * The string "desc" will be shown as additional description of
	 * the file operation on Roo shell. If "desc" is null or an empty
	 * string, then "Backup" will be passed as description.
	 * 
	 * @param sourceFile String of full qualified name of the file to backup
	 * @param desc String of description of change. If null or empty "Backup" will be used.
	 */
	private void backup(String sourceFile, String desc) {
		
		if (sourceFile!=null) {

			if(fileManager.exists(sourceFile)) {
				
				String tmpDesc = desc;
				if (desc!=null) {
					if (desc.trim().isEmpty()) {
						tmpDesc="Backup";
					}
				} else {
					tmpDesc="Backup";
				}
				String targetFile = sourceFile + BAK_SUFFIX;
				this.copyFileWithinProject(sourceFile, targetFile, tmpDesc);

			} else {
				this.log.warning("NWCloud-AddOn: Should backup a file, but the passed file name does not exist: '"+sourceFile+"'");
			}

		} else {
			this.log.warning("NWCloud-AddOn: Should backup a file, but the passed file name was null.");
		}

	}

	/**
	 * Replace the file "sourceFile" with its backup file (see backup(...) function).
	 * If this operation was successfull, delete the backup file.
	 * The string "desc" will be shown as additional description of
	 * the file operations on Roo shell. If "desc" is null or an empty
	 * string, then "Revert backup" will be passed as description.
	 * 
	 * @param sourceFile String of full qualified name of the file having a backup file
	 * @param desc String of description of change. If null or empty "Revert backup" will be used.
	 */
	private void backupRevert(String sourceFile, String desc) {
		
		if (sourceFile!=null) {

			String backupFile = sourceFile + BAK_SUFFIX;
			
			if(fileManager.exists(backupFile)) {
				
				String tmpDesc = desc;
				if (desc!=null) {
					if (desc.trim().isEmpty()) {
						tmpDesc="Revert backup";
					}
				} else {
					tmpDesc="Revert backup";
				}
				
				this.copyFileWithinProject(backupFile, sourceFile, tmpDesc);
				fileManager.delete(backupFile, tmpDesc);
				
			} else {
				this.log.warning("NWCloud-AddOn: Should revert the backup of a file, but the backup file does not exist: '"+backupFile+"'");
			}

		} else {
			this.log.warning("NWCloud-AddOn: Should revert the backup of a file, but the passed file name was null.");
		}

	}
	
}
