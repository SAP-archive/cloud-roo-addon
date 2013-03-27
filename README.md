SAP HANA Cloud Addon for Spring Roo
========================================

Introduction
------------

### What is it about? ###

[Spring Roo](http://www.springsource.org/spring-roo) is a console-based Rapid Application Development (RAD) tool maintained by [SpringSource](http://www.springsource.org/) and made available to the public under the Apache License 2.0. Roo can be extended by addons, that provide additional functionality to the tool.

This project, the "SAP NetWeaver Cloud Addon for Spring Roo" is an addon for Roo which adds commands to Roo that make it easy and comfortable to configure your Roo-generated projects for use on SAP NetWeaver Cloud and to do the actual deployment. This addon is also provided under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

[SAP Hana Cloud](http://scn.sap.com/community/developer-center/cloud-platform) is a Java-based Platform-as-a-Service (PaaS) provided by [SAP AG](http://www.sap.com/). Currently SAP AG provides a [free developer account](http://scn.sap.com/docs/DOC-28197) for SAP NetWeaver Cloud, so it's easy to give it a try.

You can use Roo and the provided addon here to create a basic web application, deploy and run it on SAP NetWeaver Cloud in minutes. Roo generates 100% pure Java code, packaged as a standard Maven project, so it is easy to handle and extend. Import it in your favorite IDE like Eclipse and start extending it, or write a mobile app connecting to the REST interfaces to your data, that Roo can automatically create for you. Try it out, and learn how Roo and this addon can help you kickstarting your ideas on SAP NetWeaver Cloud.


Tutorial
--------

### How to get started? ###

We have prepared a tutorial that shows you how to install the addon, create a web application and deploy and run it on SAP NetWeaver Cloud. The tutorial is available [here](http://sap.github.com/cloud-roo-addon/tutorial.html).


Building, installing and using the addon
----------------------------------------

In the following this document explains how to

1. build
2. install
3. use

the addon.

If you don't want to build the addon by yourself, but just use it, then please refer to the tutorial (see above), where we also included a link to a download package with pre-compiled binaries and automated installer.


### Prerequisites ###

1. Building

	You need to have a recent version of [Apache Maven](http://maven.apache.org/) installed and available on the path, so that you can issue the command `mvn` on the commandline regardless of the directory you're currently in.

2. Installing

	For installing the addon to Roo, you need to have [Spring Roo](http://www.springsource.org/spring-roo) installed and available on the path, so that you can issue the command `roo` (or `roo.sh` if you are using Linux or Mac OS) on the commandline regardless of the directory you're currently in. The addon is known to be compatible with Roo in version 1.2.1 and 1.2.2.

3. Using

	The addintional commands provided by the addon are available after installing the addon. To build and deploy Roo projects after having executed the command `nwcloud enable-deploy`, you will need to have the [NWCloud-Maven-Plugin](https://github.com/sap/cloud-maven-plugin) installed.


### Building the addon ###

Clone the addon project from the git repository to a directory of your choice. To do a fresh build of the addon, issue the following command on the commandline in the root of the project dir:

	mvn clean package


### Installing the addon ###

After having built the Roo addon in the "target" subfolder of this project, you are able to install it from there to Roo.

Simply start a Roo shell and issue a command following this schema:

	roo> osgi start --url file:///<path-to-project-dir>/target/com.sap.research.roo.addon.nwcloud-1.0.0.RELEASE.jar

After this, check the installed addon bundles using:

	roo> osgi ps

You should see a line like the following appearing in the shown list:

	[  74] [Active     ] [    1] roo-nwcloud-addon (1.0.0.RELEASE)

If you want to uninstall the addon, then you could do this using the following command:

	roo> osgi uninstall --bundleSymbolicName com.sap.research.roo.addon.nwcloud


### Using the addon ###

Please refer to the tutorial we provide (see above for link) to learn on how to use the addon.


Provided Roo commands
---------------------

### Which commands will the addon add to Roo? ###

After installing the addon, you will have the following additional commands available in the Roo shell:

	nwcloud enable-deploy

Modifies the Roo project to embed the NWCloud-Maven-Plugin in the build process, which supports the user to easily deploy the created web application to SAP NetWeaver Cloud.

 	nwcloud disable-deploy

Reverts the command "nwcloud enable-deploy"

	nwcloud enable-jpa

Configures the JPA persistency of the Roo project to use the SAP NetWeaver Cloud persistency service.

	nwcloud disable-jpa

Reverts the command "nwcloud enable-jpa"


### What does the Roo command "nwcloud enable-deploy" do? ###

This command modifies the maven project file ("pom.xml") of the Roo project in a way that the [Cloud-Maven-Plugin](https://github.com/sap/cloud-maven-plugin) (groupId "com.sap.research", artifactId "nwcloud-maven-plugin") is called during the build process. Each time `mvn package` is called (i.e. your Roo web project is packaged to a WAR file), the NWCloud-Maven-Plugin will show a help with all available commands for deployment (e.g. `mvn nwcloud:deploy` for deploying to NW Cloud). For this to work, the user once needs to set some parameters (e.g. NW Cloud host and account to deploy to, the user to use for login, location of [SAP NetWeaver Cloud SDK](https://tools.netweaver.ondemand.com/) on local machine, ...), which are stored in the file "nwcloud.properties" in the root of the Roo project. The file is created when the `nwcloud enable-deploy` command is issued in Roo shell and needs to be modified afterwards. After this initial setup, the NWCloud-Maven-Plugin reads the parameters from there and is able to deploy/undeploy, start/stop the created WAR file to NW Cloud. The NWCloud-Maven-Plugin itself is an encapsulated Ant build script and can also be used to deploy other WAR files (not only such created by Roo) to NW Cloud.


### What does the Roo command "nwcloud enable-jpa" do? ###

This command modifies the JPA persistency setup of the project to use the NW Cloud persistency service. In a first step the JPA persitency configuration "persistence.xml" (in foler "src\main\resources\META-INF" of the project) will be replaced (basic JPA setup based on EclipseLink). In the next step "applicationContext.xml" (in folder "src\main\resources\META-INF\spring") will be modified. The static data source definition bean will be replaced by a bean to lookup the data source dynamically via JNDI. In a last step the file "web.xml" (in folder "src\main\webapp\WEB-INF") will be modified in a way, that the NW Cloud application server component will provide the data source via JNDI when starting up the web application, so it can be found and used by the previously configured bean.


Additional information
----------------------

### License ###

This project is copyrighted by [SAP AG](http://www.sap.com/) and made available under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html). Please also confer to the text files "LICENSE" and "NOTICE" included with the project sources.


### Contributions ###

Contributions to this project are very welcome, but can only be accepted if the contributions themselves are given to the project under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.html). Contributions other than those given under Apache License 2.0 will be rejected.
