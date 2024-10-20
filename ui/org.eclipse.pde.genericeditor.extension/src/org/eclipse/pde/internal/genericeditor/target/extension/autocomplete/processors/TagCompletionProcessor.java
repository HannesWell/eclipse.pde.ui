/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others
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
 *                                 - [Bug 520004] autocomplete does not respect tag hierarchy
 *                                 - [Bug 531918] filter completions
 *******************************************************************************/
package org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.processors;

import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.DEPENDENCIES_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.DEPENDENCY_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.ENVIRONMENT_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.LAUNCHER_ARGS_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.LOCATIONS_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.LOCATION_PROFILE_COMPLETION_LABEL;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.LOCATION_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.TARGET_JRE_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.TARGET_TAG;
import static org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants.UNIT_TAG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.TagCompletionProposal;
import org.eclipse.pde.internal.genericeditor.target.extension.autocomplete.TargetDefinitionContentAssist;
import org.eclipse.pde.internal.genericeditor.target.extension.model.DependencyNode;
import org.eclipse.pde.internal.genericeditor.target.extension.model.ITargetConstants;
import org.eclipse.pde.internal.genericeditor.target.extension.model.LocationNode;
import org.eclipse.pde.internal.genericeditor.target.extension.model.Node;
import org.eclipse.pde.internal.genericeditor.target.extension.model.RepositoryNode;
import org.eclipse.pde.internal.genericeditor.target.extension.model.UnitNode;
import org.eclipse.pde.internal.genericeditor.target.extension.model.xml.Parser;

/**
 * Class that computes autocompletions for tags. Example:
 * 
 * <pre>
 *  &lt;un^
 * </pre>
 * 
 * where ^ is autocomplete call.
 */
public class TagCompletionProcessor extends DelegateProcessor {
	private static final HashMap<String, String[]> tagChildren = new HashMap<>();
	private static final List<Class<? extends Node>> allowedDuplicatesTags = new ArrayList<>();

	static {
		tagChildren.put(null, new String[] { TARGET_TAG });
		tagChildren.put(TARGET_TAG, new String[] { LOCATIONS_TAG, TARGET_JRE_TAG, LAUNCHER_ARGS_TAG, ENVIRONMENT_TAG });
		tagChildren.put(ENVIRONMENT_TAG, new String[] { ITargetConstants.OS_TAG, ITargetConstants.WS_TAG,
				ITargetConstants.ARCH_TAG, ITargetConstants.NL_TAG });
		tagChildren.put(LAUNCHER_ARGS_TAG,
				new String[] { ITargetConstants.VM_ARGS_TAG, ITargetConstants.PROGRAM_ARGS_TAG });
		tagChildren.put(LOCATIONS_TAG,
				new String[] { ITargetConstants.LOCATION_IU_COMPLETION_LABEL, LOCATION_PROFILE_COMPLETION_LABEL,
						ITargetConstants.LOCATION_DIRECTORY_COMPLETION_LABEL,
						ITargetConstants.LOCATION_FEATURE_COMPLETION_LABEL });
		tagChildren.put(LOCATION_TAG, new String[] { UNIT_TAG, ITargetConstants.REPOSITORY_TAG, DEPENDENCIES_TAG });
		tagChildren.put(DEPENDENCIES_TAG, new String[] { DEPENDENCY_TAG });
		tagChildren.put(DEPENDENCY_TAG, new String[] { ITargetConstants.GROUP_ID_TAG, ITargetConstants.ARTIFACT_ID_TAG,
				ITargetConstants.VERSION_TAG, ITargetConstants.TYPE_TAG });

		tagChildren.put(ITargetConstants.OS_TAG, new String[] {});
		tagChildren.put(ITargetConstants.WS_TAG, new String[] {});
		tagChildren.put(ITargetConstants.ARCH_TAG, new String[] {});
		tagChildren.put(ITargetConstants.NL_TAG, new String[] {});

		allowedDuplicatesTags.add(LocationNode.class);
		allowedDuplicatesTags.add(UnitNode.class);
		allowedDuplicatesTags.add(DependencyNode.class);
		allowedDuplicatesTags.add(RepositoryNode.class);
	}

	private final String searchTerm;
	private final int offset;
	private final Node activeNode;
	private final String prefix;

	public TagCompletionProcessor(String searchTerm, Node activeNode, int offset, String prefix) {
		this.searchTerm = searchTerm;
		this.activeNode = activeNode;
		this.offset = offset;
		this.prefix = prefix;
	}

	@Override
	public ICompletionProposal[] getCompletionProposals() {
		List<ICompletionProposal> proposals = new ArrayList<>();
		String[] tags = null;

		List<Node> children = List.of();
		if (activeNode == null) {
			tags = tagChildren.get(null);
			Node rootNode = Parser.getDefault().getRootNode();
			if (rootNode != null) {
				children = List.of(rootNode);
			}
		} else {
			Node node = activeNode; // the container of the new element
			children = node.getChildNodes();
			while (!tagChildren.containsKey(node.getNodeTag())) {
				children = node.getChildNodes();
				node = node.getParentNode();
			}
			tags = tagChildren.get(node.getNodeTag());
			if (tags == null && node.getParentNode() != null) {
				tags = tagChildren.get(node.getParentNode().getNodeTag());
				children = node.getParentNode().getChildNodes();
			}
			if (tags == null) {
				tags = tagChildren.get(null);
				children = node.getChildNodes();
			}
		}

		Set<String> siblingTags = children.stream().filter(child -> !allowedDuplicatesTags.contains(child.getClass()))
				.map(Node::getNodeTag).collect(Collectors.toSet());

		Arrays.sort(tags);

		for (int i = 0; i < tags.length; i++) {
			StyledString displayString = TargetDefinitionContentAssist.getFilteredStyledString(tags[i], searchTerm);
			if (displayString == null || displayString.length() == 0 || siblingTags.contains(tags[i])) {
				continue;
			}
			proposals.add(new TagCompletionProposal(tags[i], offset - searchTerm.length(), searchTerm.length(),
					displayString, prefix));
		}
		return proposals.toArray(new ICompletionProposal[proposals.size()]);
	}

	private boolean isOffsetWithinNode(Node node) {
		return node != null && offset <= node.getOffsetEnd() && offset > node.getOffsetStart();
	}

}
