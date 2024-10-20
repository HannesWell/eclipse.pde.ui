/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Lucas Bullen (Red Hat Inc.)
 *******************************************************************************/
package org.eclipse.pde.internal.genericeditor.target.extension.model.xml;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMLElement {

	public record Attribute(String name, String value, int startOffset, int endOffset) {
	}

	private final String element;
	private final int offset;
	private final String name;
	private final Map<String, Attribute> attributes = new LinkedHashMap<>();
	private final boolean isEndElement;
	private final boolean isStartElement;

	private static final Pattern startElementNamePattern = Pattern.compile("<\\s*(?<name>\\w*).*", Pattern.DOTALL); //$NON-NLS-1$
	private static final Pattern endElementNamePattern = Pattern.compile("</\\s*(?<name>\\w*).*", Pattern.DOTALL); //$NON-NLS-1$
	private static final Pattern attributePattern = Pattern.compile("((?<key>\\w*)\\s*=\\s*\"(?<value>.*?)\")", //$NON-NLS-1$
			Pattern.DOTALL);
	private static final Pattern IS_END_ELEMENT_PATTERN = Pattern.compile("</(.|\n)*|(.|\n)*/>(.|\n)*");
	private static final Pattern IS_START_ELEMENT_PATTERN = Pattern.compile("<[^/](.|\n)*");

	public XMLElement(String element, int offset) {
		this.element = element;
		this.offset = offset;
		this.isEndElement = IS_END_ELEMENT_PATTERN.matcher(element).matches();
		this.isStartElement = IS_START_ELEMENT_PATTERN.matcher(element).matches();

		Pattern namePattern = isStartElement() ? startElementNamePattern : endElementNamePattern;
		Matcher nameMatcher = namePattern.matcher(element);
		nameMatcher.matches();
		name = nameMatcher.group("name"); //$NON-NLS-1$

		Matcher attrMatcher = attributePattern.matcher(element);
		while (attrMatcher.find()) {
			String key = attrMatcher.group("key"); //$NON-NLS-1$
			String value = attrMatcher.group("value"); //$NON-NLS-1$
			int start = offset + attrMatcher.start();
			int end = offset + attrMatcher.end();
			attributes.put(key, new Attribute(key, value, start, end));
		}
	}
	public boolean isEndElement() {
		return isEndElement;
	}

	public boolean isStartElement() {
		return isStartElement;
	}

	public int getStartOffset() {
		return offset;
	}

	public int getEndOffset() {
		return offset + element.length();
	}

	public Collection<Attribute> getAttributes() {
		return Collections.unmodifiableCollection(attributes.values());
	}

	public String getAttributeValueByKey(String key) {
		Attribute attribute = attributes.get(key);
		return attribute != null ? attribute.value() : null;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return element;
	}
}
