package org.eclipse.pde.ui.templates;
/*
 * (c) Copyright IBM Corp. 2000, 2002.
 * All Rights Reserved.
 */
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

/**
 * The base class for all the template options.
 */

public abstract class TemplateField {
	private BaseOptionTemplateSection section;
	private String label;
	/**
	 * The constructor for the field.
	 * @param section the section that owns this field
	 * @param label the label of this field
	 */
	public TemplateField(BaseOptionTemplateSection section, String label) {
		this.section = section;
		this.label = label;
	}
	/**
	 * Returns the field label.
	 * @return field label
	 */
	public String getLabel() {
		return label;
	}
	/**
	 * Changes the label of this field.
	 * @param label the new label of this field.
	 */
	public void setLabel(String label) {
		this.label = label;
	}
	/**
	 * Returns the template section that owns this option field.
	 * @return parent template section
	 */
	public BaseOptionTemplateSection getSection() {
		return section;
	}
	/**
	 * Factory method that creates the label in the provided parent.
	 * @param parant the parent composite to create the label in
	 * @param span number of columns that the label should span
	 * @return the newly created Label widget.
	 */
	protected Label createLabel(Composite parent, int span) {
		Label label = new Label(parent, SWT.NULL);
		label.setText(getLabel());
		return label;
	}
	/**
	 * Subclasses must implement this method to create the
	 * control of the template field.
	 * @param parent the parent composite the control should be
	 * created in
	 * @param span number of columns that the control should span
	 */
	public abstract void createControl(Composite parent, int span);
}