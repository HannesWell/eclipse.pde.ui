/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.schema;

import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.FormLayoutFactory;
import org.eclipse.pde.internal.ui.editor.PDEFormEditor;
import org.eclipse.pde.internal.ui.editor.PDEFormPage;
import org.eclipse.pde.internal.ui.editor.text.ColorManager;
import org.eclipse.pde.internal.ui.editor.text.IColorManager;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.ScrolledForm;

public class SchemaOverviewPage extends PDEFormPage {
		 
	 public static final String PAGE_ID = "overview"; //$NON-NLS-1$
	 
	 private IColorManager fColorManager = ColorManager.getDefault();
	 
	 private DocSection fDocSection;
	 
	 private SchemaSpecSection fGeneralInfoSection;
	 private SchemaIncludesSection fInclusionSection;
	 
	 public SchemaOverviewPage(PDEFormEditor editor) {
 		 super(editor, PAGE_ID, PDEUIMessages.SchemaEditor_DocPage_title);
	 }
	 
	 public void setActive(boolean active) {
 		 if (!active)
	 		 getManagedForm().commit(false);
 		 super.setActive(active);
	 }
	 
	 protected void createFormContent(IManagedForm managedForm) {
 		 super.createFormContent(managedForm);
 		 ScrolledForm form = managedForm.getForm();
 		 form.getBody().setLayout(FormLayoutFactory.createFormGridLayout(false, 1));

 		 Composite top = managedForm.getToolkit().createComposite(form.getBody());
 		 top.setLayout(FormLayoutFactory.createFormPaneGridLayout(true, 2));
 		 top.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
 		 
 		 fGeneralInfoSection = new SchemaSpecSection(this, top);		 
 		 fInclusionSection = new SchemaIncludesSection(this, top);
 		 fDocSection = new DocSection(this, form.getBody(), fColorManager);

 		 managedForm.addPart(fGeneralInfoSection);
 		 managedForm.addPart(fInclusionSection);
 		 managedForm.addPart(fDocSection);
 		 
 		 PlatformUI.getWorkbench().getHelpSystem().setHelp(form.getBody(), IHelpContextIds.SCHEMA_EDITOR_DOC);
	 }
	 
	 public void dispose() {
 		 fColorManager.dispose();
 		 super.dispose();
	 }

}
