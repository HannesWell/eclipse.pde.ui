/*******************************************************************************
 * Copyright (c) 2016, 2018 Red Hat Inc. and others
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sopot Cela (Red Hat Inc.)
 *     Lucas Bullen (Red Hat Inc.) - [Bug 522317] Support environment arguments tags in Generic TP editor
 *                                 - [Bug 528706] autocomplete does not respect multiline tags
 *                                 - [Bug 531918] filter completions
 *******************************************************************************/
package org.eclipse.pde.internal.genericeditor.target.extension.autocomplete;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.viewers.BoldStylerProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.StyledString.Styler;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.processors.AttributeNameCompletionProcessor;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.processors.AttributeValueCompletionProcessor;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.processors.DelegateProcessor;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.processors.TagCompletionProcessor;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.processors.TagValueCompletionProcessor;
import org.eclipse.pde.internal.genericeditor.target.extension.model.Node;
import org.eclipse.pde.internal.genericeditor.target.extension.model.xml.Parser;
import org.eclipse.pde.internal.genericeditor.target.extension.model.xml.XMLElement.Attribute;

/**
 *
 * Main content assist class that is used to dispatch the specific content
 * assist types (see COMPLETION_TYPE_* fields). Uses regex to match each type.
 */
public class TargetDefinitionContentAssist implements IContentAssistProcessor {

	private static final char ATTRIBUTE_NAME_VALUE_SEPARATOR = '=';

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument document = viewer.getDocument();
		String text = document.get();
		Parser parser = Parser.getDefault();
		try {
			parser.parse(document);
		} catch (XMLStreamException e) {
			// TODO handle parsing errors
		}
		return detectCompletionType(document, text, offset, parser);
	}

	private ICompletionProposal[] detectCompletionType(IDocument doc, String text, int offset, Parser parser) {
		if (offset == 0) {
			return new ICompletionProposal[0];
		}
		try {
			doc.getLineInformationOfOffset(offset);
		} catch (BadLocationException e) {
			ILog.get().error("Invalid offset: " + offset, e);
			return new ICompletionProposal[0];
		}
		Node rootNode = parser.getRootNode();
		Node activeNode = findNodeAt(rootNode, offset);

		if (activeNode != null) {
			if (activeNode.getOffsetStartTagEnd() <= offset && offset <= activeNode.getOffsetEndTagStart()) {
				// Cursor is within the active node
				// -> either a new child or a text-value is modified/added

				int searchTextStart;
				List<Node> children = activeNode.getChildNodes();
				if (!children.isEmpty()) {
					OptionalInt nextSibling = IntStream.range(0, children.size())
							.filter(i -> offset < children.get(i).getOffsetStart()).findFirst();
					searchTextStart = children.get(nextSibling.orElse(children.size()) - 1).getOffsetEnd();
				} else {
					searchTextStart = activeNode.getOffsetStartTagEnd();
				}
				String searchTerm = text.substring(searchTextStart, offset).strip();

				if (searchTerm.startsWith("<")) {
					return new TagCompletionProcessor(searchTerm.substring(1), activeNode, offset, null)
							.getCompletionProposals();
				} else {
					return Stream.of( //
							new TagCompletionProcessor(searchTerm, activeNode, offset, "<"),
							new TagValueCompletionProcessor(searchTerm, activeNode.getNodeTag(), offset))
							.map(DelegateProcessor::getCompletionProposals).flatMap(Arrays::stream)
							.toArray(ICompletionProposal[]::new);
				}
			}
			if (offset < activeNode.getOffsetStartTagEnd()) {
				// Cursor is within the starting tag of the active node
				// -> attributes are modified/added
				Optional<Attribute> activeAttribute = activeNode.getAttributes().stream()
						.filter(a -> a.startOffset() < offset && offset < a.endOffset()).findFirst();
				// TODO: handle case where only parts of the attribute name are
				// typed in. Then the AST is incomplete
				// TODO: check what's the first 'token' before the offset that's
				// not a word
				if (activeAttribute.isPresent()) {
					// cursor is within an attribute

					int attributeStart = activeAttribute.get().startOffset();
					int separator = text.indexOf(ATTRIBUTE_NAME_VALUE_SEPARATOR, attributeStart);
					// TODO: check handling within whitespace?! Respectively
					// ensure
					// that the searchTerm drops all whitespace
					if (offset < separator) {
						String acKey = activeNode.getNodeTag();
						String searchTerm = text.substring(attributeStart, offset);
						return new AttributeNameCompletionProcessor(searchTerm, acKey, offset, text)
								.getCompletionProposals();
					} else if (separator < offset) {
						String acKey = activeAttribute.get().name();
						// remove leading and trailing whitespace and quote
						String searchTerm = text.substring(separator + 1, offset).strip().substring(1);
						return new AttributeValueCompletionProcessor(searchTerm, acKey, offset)
								.getCompletionProposals();
					}
				}
			}
		}
		return new ICompletionProposal[0];
	}

	private Node findNodeAt(Node node, int offset) {
		if (node.getOffsetStart() < offset && offset < node.getOffsetEnd()) {
			for (Node child : node.getChildNodes()) {
				Node activeNode = findNodeAt(child, offset);
				if (activeNode != null) {
					return activeNode;
				}
			}
			return node;
		}
		return null;
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return null;
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '<' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	private static Styler bold = new BoldStylerProvider(
			JFaceResources.getFontRegistry().get(JFaceResources.DEFAULT_FONT)).getBoldStyler();

	/**
	 * Uses a search term to determine if a string is a match. If it is a match,
	 * then a StyledString is generated showing how it is matched.
	 *
	 * Matches if searchTerm is empty, string contains searchTerm, or if searchTerm
	 * matches string using the camelCase technique where digits and symbols are
	 * considered as upper case letters
	 *
	 * @param string
	 *            The string in question
	 * @param searchTerm
	 *            The query string
	 * @return string styled showing how searchTerm is matched, or null if not
	 *         matched
	 */
	public static StyledString getFilteredStyledString(String string, String searchTerm) {
		if (string == null) {
			return null;
		}
		if (searchTerm.isEmpty()) {
			return new StyledString(string);
		} else if (string.toLowerCase().contains(searchTerm.toLowerCase())) {
			int index = string.toLowerCase().indexOf(searchTerm.toLowerCase());
			int len = searchTerm.length();
			StyledString styledString = new StyledString(string.substring(0, index));
			styledString.append(string.substring(index, index + len), bold);
			return styledString.append(string.substring(index + len, string.length()));
		}
		int searchCharIndex = 0;
		int subStringCharIndex = 0;
		String[] stringParts = string.split("((?=[A-Z])|(?<=[._])|(?=[0-9])(?<![0-9]))");
		StyledString styledString = new StyledString();
		while (searchCharIndex < searchTerm.length()) {
			for (String subString : stringParts) {
				if (searchCharIndex == searchTerm.length()) {
					styledString.append(subString);
					continue;
				}
				while (searchCharIndex < searchTerm.length() && subStringCharIndex < subString.length()) {
					if (subString.charAt(subStringCharIndex) == searchTerm.charAt(searchCharIndex)) {
						searchCharIndex++;
						subStringCharIndex++;
					} else {
						break;
					}
				}
				if (subStringCharIndex > 0) {
					styledString.append(subString.substring(0, subStringCharIndex), bold);
					styledString.append(subString.substring(subStringCharIndex));
					subStringCharIndex = 0;
				} else {
					styledString.append(subString);
				}
			}
			if (searchCharIndex == searchTerm.length()) {
				// All of searchTerm has matched in the string
				return styledString;
			} else if (stringParts.length == 0) {
				// Have gone through all substrings without match
				return null;
			} else {
				// Try again looking beyond first substring
				searchCharIndex = 0;
				subStringCharIndex = 0;
				stringParts = Arrays.copyOfRange(stringParts, 1, stringParts.length);
				styledString = new StyledString();
			}
		}
		return null;
	}
}
