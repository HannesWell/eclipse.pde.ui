package org.eclipse.pde.internal.editor.manifest;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.*;
import org.eclipse.jface.resource.*;
import org.eclipse.core.resources.*;
import org.eclipse.jface.wizard.*;
import org.eclipse.pde.internal.wizards.extension.*;
import org.eclipse.pde.model.*;
import org.eclipse.jface.action.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.update.ui.forms.internal.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.base.model.*;
import org.eclipse.pde.model.plugin.*;
import org.eclipse.swt.*;
import org.eclipse.pde.internal.elements.*;
import java.util.*;
import org.eclipse.pde.internal.editor.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.pde.internal.base.schema.*;
import org.eclipse.pde.internal.schema.*;
import org.eclipse.pde.internal.*;
import org.eclipse.swt.custom.*;
import java.net.URL;
import java.net.MalformedURLException;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.pde.internal.parts.TreePart;
import org.eclipse.pde.internal.preferences.MainPreferencePage;
import org.eclipse.pde.internal.model.*;

public class DetailExtensionSection
	extends TreeSection
	implements IModelChangedListener {
	//private TableTreeViewer extensionTree;
	private TreeViewer extensionTree;
	private FormWidgetFactory factory;
	private Image extensionImage;
	public static final String SECTION_TITLE =
		"ManifestEditor.DetailExtensionSection.title";
	public static final String SECTION_NEW =
		"ManifestEditor.DetailExtensionSection.new";
	public static final String SECTION_SHOW_CHILDREN =
		"ManifestEditor.DetailExtensionSection.showAllChildren";
	public static final String POPUP_NEW = "Menus.new.label";
	public static final String POPUP_DELETE = "Actions.delete.label";
	private Image genericElementImage;
	private Button showAllChildrenButton;
	private SchemaRegistry schemaRegistry;
	private ExternalModelManager pluginInfoRegistry;

	class ExtensionContentProvider
		extends DefaultContentProvider
		implements ITreeContentProvider {

		public Object[] getChildren(Object parent) {
			Object[] children = null;
			if (parent instanceof IPluginBase)
				children = ((IPluginBase) parent).getExtensions();
			else if (parent instanceof IPluginExtension) {
				children = ((IPluginExtension) parent).getChildren();
			} else if (
				showAllChildrenButton.getSelection() && parent instanceof IPluginElement) {
				children = ((IPluginElement) parent).getChildren();
			}
			if (children == null)
				children = new Object[0];
			return children;
		}
		public boolean hasChildren(Object parent) {
			return getChildren(parent).length > 0;
		}
		public Object getParent(Object child) {
			if (child instanceof IPluginObject)
				return ((IPluginObject) child).getParent();
			return null;
		}
		public Object[] getElements(Object parent) {
			return getChildren(parent);
		}
	}

	class ExtensionLabelProvider
		extends LabelProvider
		implements ITableLabelProvider {
		public String getColumnText(Viewer v, Object obj, int index) {
			return getColumnText(obj, index);
		}
		public String getText(Object obj) {
			return getColumnText(obj, 1);
		}
		public Image getImage(Object obj) {
			return getColumnImage(obj, 1);
		}
		public String getColumnText(Object obj, int index) {
			if (index == 1) {
				return resolveObjectName(obj);
			}
			return "";
		}
		public Image getColumnImage(Object obj, int index) {
			if (index == 1) {
				return resolveObjectImage(obj);
			}
			return null;
		}

		public Image getColumnImage(Viewer v, Object obj, int index) {
			return getColumnImage(obj, index);
		}
	}

	public DetailExtensionSection(ManifestExtensionsPage page) {
		super(page, new String[] { PDEPlugin.getResourceString(SECTION_NEW)});
		this.setHeaderText(PDEPlugin.getResourceString(SECTION_TITLE));
		schemaRegistry = PDEPlugin.getDefault().getSchemaRegistry();
		pluginInfoRegistry = PDEPlugin.getDefault().getExternalModelManager();
	}
	private static void addItemsForExtensionWithSchema(
		MenuManager menu,
		IPluginExtension extension,
		IPluginParent parent) {
		ISchema schema = ((PluginExtension)extension).getSchema();
		String tagName = (parent == extension ? "extension" : parent.getName());
		ISchemaElement elementInfo = schema.findElement(tagName);
		if (elementInfo == null)
			return;
		ISchemaElement[] candidates = schema.getCandidateChildren(elementInfo);
		for (int i = 0; i < candidates.length; i++) {
			ISchemaElement candidateInfo = candidates[i];
			Action action = new NewElementAction(candidateInfo, parent);
			menu.add(action);
		}

	}
	public Composite createClient(Composite parent, FormWidgetFactory factory) {
		this.factory = factory;
		initializeImages();
		Composite container = createClientContainer(parent, 2, factory);

		showAllChildrenButton =
			factory.createButton(
				container,
				PDEPlugin.getResourceString(SECTION_SHOW_CHILDREN),
				SWT.CHECK);
		GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.horizontalSpan = 2;
		showAllChildrenButton.setLayoutData(gd);
		showAllChildrenButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				BusyIndicator.showWhile(extensionTree.getTree().getDisplay(), new Runnable() {
					public void run() {
						extensionTree.refresh();
					}
				});
			}
		});

		TreePart treePart = getTreePart();

		createViewerPartControl(container, SWT.MULTI, 2, factory);
		extensionTree = treePart.getTreeViewer();
		extensionTree.setAutoExpandLevel(TreeViewer.ALL_LEVELS);
		extensionTree.setContentProvider(new ExtensionContentProvider());
		extensionTree.setLabelProvider(new ExtensionLabelProvider());
		factory.paintBordersFor(container);
		return container;
	}

	protected void selectionChanged(IStructuredSelection selection) {
		Object item = selection.getFirstElement();
		fireSelectionNotification(item);
		getFormPage().setSelection(selection);
	}
	
	protected void handleDoubleClick(IStructuredSelection selection) {
		PropertiesAction action = new PropertiesAction(getFormPage().getEditor());
		action.run();
	}
	
	protected void buttonSelected(int index) {
		if (index==0) handleNew();
	}
				
	public void dispose() {
		IPluginModelBase model = (IPluginModelBase) getFormPage().getModel();
		model.removeModelChangedListener(this);
		super.dispose();
	}
	public boolean doGlobalAction(String actionId) {
		if (actionId.equals(org.eclipse.ui.IWorkbenchActionConstants.DELETE)) {
			handleDelete();
			return true;
		}
		return false;
	}
	public void expandTo(Object object) {
		extensionTree.setSelection(new StructuredSelection(object), true);
	}
	protected void fillContextMenu(IMenuManager manager) {
		ISelection selection = extensionTree.getSelection();
		Object object = ((IStructuredSelection) selection).getFirstElement();
		if (object instanceof IPluginParent) {
			IPluginParent parent = (IPluginParent) object;
			if (parent.getModel().getUnderlyingResource() != null) {
				fillContextMenu(getFormPage(), parent, manager);
				manager.add(new Separator());
			}
		}
		getFormPage().getEditor().getContributor().contextMenuAboutToShow(manager);
	}
	static void fillContextMenu(
		PDEFormPage page,
		final IPluginParent parent,
		IMenuManager manager) {
		fillContextMenu(page, parent, manager, false);
	}
	static void fillContextMenu(
		PDEFormPage page,
		final IPluginParent parent,
		IMenuManager manager,
		boolean addSiblingItems) {
		fillContextMenu(page, parent, manager, addSiblingItems, true);
	}
	static void fillContextMenu(
		PDEFormPage page,
		final IPluginParent parent,
		IMenuManager manager,
		boolean addSiblingItems,
		boolean fullMenu) {
		MenuManager menu = new MenuManager(PDEPlugin.getResourceString(POPUP_NEW));

		IPluginExtension extension = getExtension(parent);

		ISchema schema = ((PluginExtension)extension).getSchema();
		if (schema == null) {
			menu.add(new NewElementAction(null, parent));
		} else {
			addItemsForExtensionWithSchema(menu, extension, parent);
			if (addSiblingItems) {
				IPluginObject parentsParent = parent.getParent();
				if (!(parentsParent instanceof IPluginExtension)) {
					IPluginParent pparent = (IPluginParent) parentsParent;
					menu.add(new Separator());
					addItemsForExtensionWithSchema(menu, extension, pparent);
				}
			}
		}
		if (menu.isEmpty() == false) {
			manager.add(menu);
			manager.add(new Separator());
		}
		if (fullMenu) {
			manager.add(new Action(PDEPlugin.getResourceString(POPUP_DELETE)) {
				public void run() {
					try {
						IPluginObject parentsParent = parent.getParent();
						if (parent instanceof IPluginExtension) {
							IPluginBase plugin = (IPluginBase) parentsParent;
							plugin.remove((IPluginExtension) parent);
						} else {
							IPluginParent parentElement = (IPluginParent) parent.getParent();
							parentElement.remove(parent);
						}
					} catch (CoreException e) {
					}
				}
			});
			manager.add(new Separator());
			manager.add(new PropertiesAction(page.getEditor()));
		}
	}
	static IPluginExtension getExtension(IPluginParent parent) {
		while (parent != null && !(parent instanceof IPluginExtension)) {
			parent = (IPluginParent) parent.getParent();
		}
		return (IPluginExtension) parent;
	}
	private void handleDelete() {
		IStructuredSelection sel = (IStructuredSelection)extensionTree.getSelection();
		if (sel.isEmpty()) return;
		for (Iterator iter = sel.iterator(); iter.hasNext();) {
			IPluginObject object = (IPluginObject)iter.next();
			try {
				if (object instanceof IPluginElement) {
					IPluginElement ee = (IPluginElement) object;
					IPluginParent parent = (IPluginParent) ee.getParent();
					parent.remove(ee);
				} else if (object instanceof IPluginExtension) {
					IPluginExtension extension = (IPluginExtension) object;
					IPluginBase plugin = extension.getPluginBase();
					plugin.remove(extension);
				}
			} catch (CoreException e) {
				PDEPlugin.logException(e);
			}
		}
	}
	private void handleNew() {
		IFile file =
			((IFileEditorInput) getFormPage().getEditor().getEditorInput()).getFile();
		final IProject project = file.getProject();
		BusyIndicator.showWhile(extensionTree.getTree().getDisplay(), new Runnable() {
			public void run() {
				NewExtensionWizard wizard =
					new NewExtensionWizard(project, (IPluginModelBase) getFormPage().getModel());
				WizardDialog dialog =
					new WizardDialog(PDEPlugin.getActiveWorkbenchShell(), wizard);
				dialog.create();
				dialog.getShell().setSize(500, 500);
				dialog.open();
			}
		});
	}
	public void initialize(Object input) {
		IPluginModelBase model = (IPluginModelBase) input;
		extensionTree.setInput(model.getPluginBase());
		setReadOnly(!model.isEditable());
		getTreePart().setButtonEnabled(0, model.isEditable());
		model.addModelChangedListener(this);
	}
	public void initializeImages() {
		PDELabelProvider provider = PDEPlugin.getDefault().getLabelProvider();
		extensionImage = provider.get(PDEPluginImages.DESC_EXTENSION_OBJ);
		genericElementImage = provider.get(PDEPluginImages.DESC_GENERIC_XML_OBJ);
	}
	public void modelChanged(IModelChangedEvent event) {
		if (event.getChangeType() == IModelChangedEvent.WORLD_CHANGED) {
			extensionTree.refresh();
			return;
		}
		Object changeObject = event.getChangedObjects()[0];
		if (changeObject instanceof IPluginExtension
			|| changeObject instanceof IPluginElement) {
			// We do not need to react to changes in element whose
			// parents are not extensions
			IPluginObject pobj = (IPluginObject) changeObject;
			IPluginObject parent = pobj.getParent();
			
			if (showAllChildrenButton.getSelection()==false) {
			if (!(pobj instanceof IPluginExtension)
				&& !(parent instanceof IPluginExtension))
				return;
			}
			if (event.getChangeType() == event.INSERT) {
				//extensionTree.refresh();
				extensionTree.add(parent, pobj);
				extensionTree.setSelection(new StructuredSelection(changeObject), true);
				extensionTree.getTree().setFocus();
			} else if (event.getChangeType() == event.REMOVE) {
				//extensionTree.refresh();
				extensionTree.remove(pobj);
			} else {
				extensionTree.update(changeObject, null);
				if (extensionTree.getTree().isFocusControl()) {
					ISelection sel = getFormPage().getSelection();
					if (sel != null && sel instanceof IStructuredSelection) {
						IStructuredSelection ssel = (IStructuredSelection) sel;
						if (!ssel.isEmpty() && ssel.getFirstElement().equals(changeObject)) {
							// update property sheet
							getFormPage().setSelection(sel);
						}
					}
				}
			}
		}
	}
	private Image resolveObjectImage(Object obj) {
		if (obj instanceof IPluginExtension) {
			return extensionImage;
		} else if (obj instanceof IPluginElement) {
			String name = obj.toString();
			IPluginElement element = (IPluginElement) obj;
			Image customImage = getCustomImage(element);
			if (customImage != null)
				return customImage;
		}
		return genericElementImage;
	}

	static Image getCustomImage(IPluginElement element) {
		ISchemaElement elementInfo = ((PluginElement)element).getElementInfo();
		if (elementInfo != null && elementInfo.getIconProperty() != null) {
			String iconProperty = elementInfo.getIconProperty();
			IPluginAttribute att = element.getAttribute(iconProperty);
			String iconPath = null;
			if (att != null && att.getValue() != null) {
				iconPath = att.getValue();
			}
			if (iconPath != null) {
				//OK, we have an icon path relative to the plug-in
				return getImageFromPlugin(element, iconPath);
			}
		}
		return null;
	}

	private static Image getImageFromPlugin(
		IPluginElement element,
		String iconPathName) {
		IPluginModelBase model = element.getModel();
		URL modelURL = null;
		String path = null;

		if (model.getUnderlyingResource() == null) {
			// External
			path = model.getInstallLocation();
		} else {
			// Workspace
			path = model.getUnderlyingResource().getLocation().toOSString();
		}
		try {
			modelURL = new URL("file:" + path);
			return PDEPlugin.getDefault().getLabelProvider().getImageFromURL(modelURL, iconPathName);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	private String resolveObjectName(Object obj) {
		return resolveObjectName(schemaRegistry, pluginInfoRegistry, obj);
	}
	public static String resolveObjectName(
		SchemaRegistry schemaRegistry,
		ExternalModelManager pluginInfoRegistry,
		Object obj) {
		boolean fullNames = MainPreferencePage.isFullNameModeEnabled();
		if (obj instanceof IPluginExtension) {
			IPluginExtension extension = (IPluginExtension) obj;
			if (!fullNames) return extension.getPoint();
			
			ISchema schema = schemaRegistry.getSchema(extension.getPoint());

			// try extension point schema definition
			if (schema != null) {
				// exists
				return schema.getName();
			}
			// try extension point declaration
			IPluginExtensionPoint pointInfo =
				pluginInfoRegistry.findExtensionPoint(extension.getPoint());
			if (pointInfo != null) {
				return pointInfo.getResourceString(pointInfo.getName());
			}
		} else if (obj instanceof IPluginElement) {
			String name = obj.toString();
			PluginElement element = (PluginElement) obj;
			if (!fullNames) return name;
			ISchemaElement elementInfo = element.getElementInfo();
			if (elementInfo != null && elementInfo.getLabelProperty() != null) {
				IPluginAttribute att = element.getAttribute(elementInfo.getLabelProperty());
				if (att != null && att.getValue() != null)
					name = stripShortcuts(att.getValue());
				name = element.getResourceString(name);
			}
			return name;
		}
		return obj.toString();
	}
	public void setFocus() {
		if (extensionTree != null)
			extensionTree.getTree().setFocus();
	}
	public static String stripShortcuts(String input) {
		StringBuffer output = new StringBuffer();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '&')
				continue;
			else if (c == '@')
				break;
			output.append(c);
		}
		return output.toString();
	}
}