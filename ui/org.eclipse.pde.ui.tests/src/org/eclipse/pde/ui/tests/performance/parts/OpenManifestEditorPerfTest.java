package org.eclipse.pde.ui.tests.performance.parts;

import junit.framework.*;

import org.eclipse.pde.internal.ui.editor.plugin.*;
import org.eclipse.test.performance.*;
import org.eclipse.ui.*;

public class OpenManifestEditorPerfTest extends PerformanceTestCase {

	public static Test suite() {
		return new TestSuite(OpenManifestEditorPerfTest.class);
	}
	
	public void testOpen() throws Exception {
		tagAsGlobalSummary("Open Plug-in Editor", Dimension.CPU_TIME);
		IWorkbenchPage page= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

		for (int i = 0; i < 20; i++) {
			startMeasuring();
			ManifestEditor.openPluginEditor("org.eclipse.jdt.ui");
			stopMeasuring();
			page.closeAllEditors(false);
		}	
		commitMeasurements();
		assertPerformance();
	}
	
}
