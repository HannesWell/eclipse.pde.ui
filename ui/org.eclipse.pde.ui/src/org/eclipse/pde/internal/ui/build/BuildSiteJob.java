/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.build;

import java.io.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.pde.internal.core.ifeature.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.wizards.exports.*;

public class BuildSiteJob extends FeatureExportJob {
	
	private IContainer fSiteContainer;

	public BuildSiteJob(IFeatureModel[] models, IContainer folder) {
		super(EXPORT_AS_UPDATE_JARS, 
				false, 
				folder.getLocation().toOSString(),
				null,  
				models);
		fSiteContainer = folder;
		setRule(MultiRule.combine(fSiteContainer.getProject(), getRule()));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.wizards.exports.FeatureExportJob#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IStatus run(IProgressMonitor monitor) {
		touchSite(monitor);
		IStatus status = super.run(monitor);
		refresh(monitor);
		return status;
	}
	
	private void touchSite(IProgressMonitor monitor) {
		File file = new File(fSiteContainer.getLocation().toOSString(), "site.xml"); //$NON-NLS-1$
		file.setLastModified(System.currentTimeMillis());
	}
	
	private void refresh(IProgressMonitor monitor) {
		try {
			fSiteContainer.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		} catch (CoreException e) {
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.wizards.exports.FeatureExportJob#getLogFoundMessage()
	 */
	protected String getLogFoundMessage() {
		return PDEPlugin.getResourceString("BuildSiteJob.message"); //$NON-NLS-1$
	}
}
