/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.correction;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.PDEManager;
import org.eclipse.pde.internal.core.ibundle.IBundle;
import org.eclipse.pde.internal.core.ibundle.IBundlePluginModelBase;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.nls.ExternalizeStringsOperation;
import org.eclipse.pde.internal.ui.nls.ModelChange;
import org.eclipse.pde.internal.ui.nls.ModelChangeElement;
import org.eclipse.pde.internal.ui.util.ModelModification;
import org.eclipse.pde.internal.ui.util.PDEModelUtility;
import org.eclipse.text.edits.MalformedTreeException;
import org.osgi.framework.Constants;

public class ExternalizeResolution extends AbstractXMLMarkerResolution {

	public ExternalizeResolution(int resolutionType, IMarker marker) {
		super(resolutionType, marker);
	}

	protected void createChange(IPluginModelBase model) {
		Object node = findNode(model);
		ModelChange change = new ModelChange(model, true);
		ModelChangeElement element = new ModelChangeElement(change, node);
		if (element.updateValue()) {
			String localization = PDEManager.getBundleLocalization(model);
			if (localization == null) 
				addLocalization(model, localization = "plugin"); //$NON-NLS-1$
			localization += ModelChange.LOCALIZATION_FILE_SUFFIX;
			IProject project = model.getUnderlyingResource().getProject();
			IFile file = project.getFile(localization);
			ExternalizeStringsOperation.checkPropertiesFile(file);
			try {
				ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
				manager.connect(file.getFullPath(), null);
				ITextFileBuffer buffer = manager.getTextFileBuffer(file.getFullPath());
				if (buffer.isDirty())
					buffer.commit(null, true);
				
				IDocument document = buffer.getDocument();
				ExternalizeStringsOperation.getPropertiesInsertEdit(document, element).apply(document);
				buffer.commit(null, true);
			} catch (CoreException e) {
				PDEPlugin.log(e);
			} catch (MalformedTreeException e) {
				PDEPlugin.log(e);
			} catch (BadLocationException e) {
				PDEPlugin.log(e);
			} finally {
				try {
					FileBuffers.getTextFileBufferManager().disconnect(file.getFullPath(), null);
				} catch (CoreException e) {
					PDEPlugin.log(e);
				}
			}
		}
	}

	public String getLabel() {
		if (isAttrNode())
			return NLS.bind(PDEUIMessages.ExternalizeResolution_attrib, getNameOfNode());
		if (fLocationPath.charAt(0) == '(')
			return NLS.bind(PDEUIMessages.ExternalizeResolution_text, getNameOfNode());
		return NLS.bind(PDEUIMessages.ExternalizeResolution_header, fLocationPath);
	}
	
		private void addLocalization(IPluginModelBase model, String localizationValue) {
		// should always be IBundlePluginModelBase.  Only time wasn't was when we only passed in plugin.xml to ModelModification contructor.
		// Now that we pass in both the Manifest and plugin.xml if we are externalizing the a plugin.xml string (see run(IMarker)), 
		// model should always be IBundlePluginModelBase
		if (model instanceof IBundlePluginModelBase) {
			IBundle bundle = ((IBundlePluginModelBase)model).getBundleModel().getBundle();
			bundle.setHeader(Constants.BUNDLE_LOCALIZATION, localizationValue);
		}
	}
	
	public void run(IMarker marker) {
		fResource = marker.getResource();
		IFile file = ((IFile)marker.getResource());
		ModelModification modification = null;
		// if file we are externalizing is not manifest, try to pass manifest in if it exists
		if (!file.getName().equals(PDEModelUtility.F_MANIFEST)) {
			IFile manifest = file.getProject().getFile(PDEModelUtility.F_MANIFEST_FP);
			if (manifest.exists()) {
				modification = new ModelModification(manifest, file) {
					protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
						createChange(model);
					}
				};
			} 
		}
		if (modification == null) {
			modification = new ModelModification(file) {
				protected void modifyModel(IBaseModel model, IProgressMonitor monitor) throws CoreException {
					createChange(model);
				}
			};
		}
		PDEModelUtility.modifyModel(modification, null);
	}

}
