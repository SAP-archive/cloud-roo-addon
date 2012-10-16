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

/**
 * Roo Addon for SAP NetWeaver Cloud - Operations Interface
 * --------------------------------------------------------
 * 
 * This class defines the interface of the commands our addon should provide.
 * 
 * The class NWCloudCommands uses the methods defined in this interface to define Roo
 * commands and bind the actions of the commands to methods defined in this interface.
 * 
 * The actual implementation of this interface is provided by the class NWCloudOperationsImpl.
 * 
 * The methods executing the actual commands are methods. The boolean functions are
 * functions that define if a command should be available to the user on the commandline
 * (true) or not (false). As example it would not make any sense to offer commands to the
 * user that manipulate the pom.xml of the project if it doesn't exist yet. Or to offer a
 * command to manipulate the "persistence.xml" of JPA persistency, if the project does
 * not use JPA persistency.
 * 
 * @see NWCloudOperationsImpl
 * @see NWCloudCommands
 */
public interface NWCloudOperations {

	// --------------------------------------------------------------------------------
	// nwcloud enable-deploy
	// --------------------------------------------------------------------------------
	
	boolean nwcloudEnableDeployIsAvailable();

	void nwcloudEnableDeploy();
	
	// --------------------------------------------------------------------------------
	// nwcloud unenable-deploy
	// --------------------------------------------------------------------------------
	
	boolean nwcloudDisableDeployIsAvailable();

	void nwcloudDisableDeploy();

	// --------------------------------------------------------------------------------
	// nwcloud enable-jpa
	// --------------------------------------------------------------------------------
	
	boolean nwcloudEnableJPAIsAvailable();
	
	void nwcloudEnableJPA();

	// --------------------------------------------------------------------------------
	// nwcloud unenable-jpa
	// --------------------------------------------------------------------------------
	
	boolean nwcloudDisableJPAIsAvailable();
	
	void nwcloudDisableJPA();

	// --------------------------------------------------------------------------------
	// nwcloud addon-debug
	// --------------------------------------------------------------------------------

/*	
	void nwcloudAddonDebug();
*/

}
