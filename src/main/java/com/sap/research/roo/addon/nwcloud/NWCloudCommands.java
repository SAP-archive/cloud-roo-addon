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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.shell.CliAvailabilityIndicator;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CommandMarker;

/**
 * Roo Addon for SAP HANA Cloud - Command Class
 * --------------------------------------------
 * 
 * The command class is registered by the Roo shell following an automatic classpath scan,
 * and enables the desired commands in Roo by implementing the CommandMarker interface.
 * 
 * The commands will be bound to methods defined in interface NWCloudOperations.
 * The actual implementation of the interface is done by class NWCloudOperationsImpl.
 * 
 * @see NWCloudOperations
 * @see NWCloudOperationsImpl
 */
@Component
@Service
public class NWCloudCommands implements CommandMarker {
	
	/**
	 * Get a reference to the NWCloudOperations from the underlying OSGi container
	 */
	@Reference private NWCloudOperations operations; 
	
	/**
	 * The activate method for this OSGi component, which will be called by the OSGi container upon bundle activation.
	 *  
	 * @param context The component context can be used to get access to the OSGi container (i.e. find out if certain bundles are active)
	 */
	protected void activate(ComponentContext context) {
		// Nothing to do here
    }

	/**
	 * The deactivate method for this OSGi component, which will be called by the OSGi container upon bundle deactivation.
	 * 
	 * @param context The component context can be used to get access to the OSGi container (i.e. find out if certain bundles are active)
	 */
	protected void deactivate(ComponentContext context) {
		// Nothing to do here
	}
	
	// *************************************************************************
	// SAP HANA Cloud Addon Commands
	// *************************************************************************
	
	// --------------------------------------------------------------------------------
	// nwcloud enable-deploy
	// --------------------------------------------------------------------------------

	@CliAvailabilityIndicator("nwcloud enable-deploy")
	public boolean nwcloudEnableDeployIsAvailable() {
		return operations.nwcloudEnableDeployIsAvailable();
	}
	
	@CliCommand(value = "nwcloud enable-deploy", help="Prepare application for deployment on SAP HANA Cloud platform")
	public void nwcloudEnableDeploy() {
		operations.nwcloudEnableDeploy();
	}

	// --------------------------------------------------------------------------------
	// nwcloud disable-deploy
	// --------------------------------------------------------------------------------

	@CliAvailabilityIndicator("nwcloud disable-deploy")
	public boolean nwcloudDisableDeployIsAvailable() {
		return operations.nwcloudDisableDeployIsAvailable();
	}
	
	@CliCommand(value = "nwcloud disable-deploy", help="Revert command nwcloud enable-deploy")
	public void nwcloudDisableDeploy() {
		operations.nwcloudDisableDeploy();
	}

	// --------------------------------------------------------------------------------
	// nwcloud enable-jpa
	// --------------------------------------------------------------------------------

	@CliAvailabilityIndicator("nwcloud enable-jpa")
	public boolean nwcloudEnableJPAIsAvailable() {
		return operations.nwcloudEnableJPAIsAvailable();
	}

	@CliCommand(value = "nwcloud enable-jpa", help="Configure JPA persistency to use SAP HANA Cloud persistency service")
	public void nwcloudEnableJPA() {
		operations.nwcloudEnableJPA();
	}

	// --------------------------------------------------------------------------------
	// nwcloud disable-jpa
	// --------------------------------------------------------------------------------

	@CliAvailabilityIndicator("nwcloud disable-jpa")
	public boolean nwcloudDisableJPAIsAvailable() {
		return operations.nwcloudDisableJPAIsAvailable();
	}

	@CliCommand(value = "nwcloud disable-jpa", help="Revert command nwcloud enable-jpa")
	public void nwcloudDisableJPA() {
		operations.nwcloudDisableJPA();
	}

	// --------------------------------------------------------------------------------
	// nwcloud addon-debug
	// --------------------------------------------------------------------------------

/*
	@CliCommand(value = "nwcloud addon-debug", help="Show some output of the SAP HANA Cloud addon (for debugging only)")
	public void nwcloudAddonDebug() {
		operations.nwcloudAddonDebug();
	}
*/
	
}