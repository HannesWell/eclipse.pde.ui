package org.eclipse.pde.internal.core.feature;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.PrintWriter;
import java.net.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.core.ifeature.IFeatureURLElement;
import org.w3c.dom.Node;

public class FeatureURLElement
	extends FeatureObject
	implements IFeatureURLElement {
	private int elementType;
	private int siteType = UPDATE_SITE;
	private URL url;

	public FeatureURLElement(int elementType) {
		this.elementType = elementType;
	}
	public FeatureURLElement(int elementType, URL url) {
		this.elementType = elementType;
		this.url = url;
	}
	public int getElementType() {
		return elementType;
	}
	public URL getURL() {
		return url;
	}
	public int getSiteType() {
		return siteType;
	}
	protected void parse(Node node) {
		super.parse(node);
		String urlName = getNodeAttribute(node, "url");
		try {
			url = new URL(urlName);
		} catch (MalformedURLException e) {
		}
		String typeName = getNodeAttribute(node, "type");
		if (typeName != null && typeName.equals("web"))
			siteType = WEB_SITE;
	}

	public void setURL(URL url) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.url;
		this.url = url;
		firePropertyChanged(this, P_URL, oldValue, url);
	}

	public void setSiteType(int type) throws CoreException {
		ensureModelEditable();
		Integer oldValue = new Integer(this.siteType);
		this.siteType = type;
		firePropertyChanged(this, P_URL, oldValue, new Integer(type));
	}

	public void restoreProperty(String name, Object oldValue, Object newValue)
		throws CoreException {
		if (name.equals(P_URL)) {
			setURL((URL) newValue);
		} else if (name.equals(P_SITE_TYPE)) {
			setSiteType(((Integer) newValue).intValue());
		} else
			super.restoreProperty(name, oldValue, newValue);
	}

	public String toString() {
		if (label != null)
			return label;
		if (url != null)
			return url.toString();
		return super.toString();
	}
	public void write(String indent, PrintWriter writer) {
		String tag = null;
		switch (elementType) {
			case UPDATE :
				tag = "update";
				break;
			case DISCOVERY :
				tag = "discovery";
				break;
		}
		if (tag == null)
			return;
		writer.print(indent + "<" + tag);
		if (label != null) {
			writer.print(" label=\"" + getWritableString(label) + "\"");
		}
		if (url != null) {
			writer.print(" url=\"" + getWritableString(url.toString()) + "\"");
		}
		if (siteType == WEB_SITE) {
			writer.print(" type=\"web\"");
		}
		writer.println("/>");
	}
}