/*
 * Copyright (c) 2002 IBM Corp.  All rights reserved.
 * This file is made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 */
package org.eclipse.pde.internal.core;

import java.io.*;
import java.net.*;
import java.util.*;

import org.eclipse.core.boot.*;
import org.eclipse.core.boot.BootLoader;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.feature.ExternalFeatureModel;
import org.eclipse.pde.internal.core.ifeature.IFeature;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;

/**
 * @version 	1.0
 * @author
 */
public class TargetPlatform implements IEnvironmentVariables {

	private static final String BOOT_ID = "org.eclipse.core.boot";

	static class LocalSite {
		private ArrayList plugins;
		private IPath path;

		public LocalSite(IPath path) {
			this.path = path;
			plugins = new ArrayList();
		}

		public IPath getPath() {
			return path;
		}

		public URL getURL() throws MalformedURLException {
			return new URL("file:" + path.addTrailingSeparator().toString());
		}

		public void add(IPluginModelBase model) {
			plugins.add(model);
		}

		public String[] getRelativePluginList() {
			String[] list = new String[plugins.size()];
			for (int i = 0; i < plugins.size(); i++) {
				IPluginModelBase model = (IPluginModelBase) plugins.get(i);
				IPath location = getPluginLocation(model);
				location =
					location.append(
						model.isFragmentModel()
							? "fragment.xml"
							: "plugin.xml");
				IPath relative =
					location.removeFirstSegments(location.segmentCount() - 3);
				list[i] = relative.setDevice(null).toString();
			}
			return list;
		}
	}

	public static File createPropertiesFile() throws CoreException {
		return createPropertiesFile(getVisibleModels(), null);
	}

	public static URL[] createPluginPath() throws CoreException {
		return createPluginPath(getVisibleModels());
	}

	public static URL[] createPluginPath(IPluginModelBase[] models)
		throws CoreException {
		URL urls[] = new URL[models.length];
		for (int i = 0; i < urls.length; i++) {
			IPluginModelBase model = models[i];
			String urlName = createURL(model);
			try {
				urls[i] = new URL(urlName);
			} catch (MalformedURLException e) {
				PDECore.logException(e);
				return new URL[0];
			}
		}
		return urls;
	}

	private static IPluginModelBase[] getVisibleModels() {
		Vector result = new Vector();
		PluginModelManager manager = PDECore.getDefault().getModelManager();
		addFromList(result, manager.getPlugins());
		IPluginModelBase[] array =
			(IPluginModelBase[]) result.toArray(
				new IPluginModelBase[result.size()]);
		return array;
	}

	private static void addFromList(Vector result, IPluginModelBase[] list) {
		for (int i = 0; i < list.length; i++) {
			IPluginModelBase model = list[i];
			if (model.isEnabled())
				result.add(list[i]);
		}
	}

	public static File createPropertiesFile(
		IPluginModelBase[] plugins,
		IPath data)
		throws CoreException {
		try {
			String dataSuffix = createDataSuffix(data);
			IPath statePath = PDECore.getDefault().getStateLocation();
			String fileName = "plugin_path.properties";
			File dir = new File(statePath.toOSString());

			if (dataSuffix.length() > 0) {
				dir = new File(dir, dataSuffix);
				if (!dir.exists()) {
					dir.mkdir();
				}
			}
			File pluginFile = new File(dir, fileName);
			Properties properties = new Properties();

			for (int i = 0; i < plugins.length; i++) {
				IPluginModelBase curr = plugins[i];
				String key = getKey(curr);
				if (key != null)
					properties.setProperty(key, createURL(curr));
			}

			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(pluginFile);
				properties.store(fos, null);
			} finally {
				if (fos != null) {
					fos.close();
				}
			}
			return pluginFile;
		} catch (IOException e) {
			throw new CoreException(
				new Status(
					IStatus.ERROR,
					PDECore.getPluginId(),
					IStatus.ERROR,
					e.getMessage(),
					e));
		}
	}

	public static File createPlatformConfiguration(
		IPluginModelBase[] plugins,
		IPath data,
		String primaryFeatureId)
		throws CoreException {
		try {
			String dataSuffix = createDataSuffix(data);
			IPath statePath = PDECore.getDefault().getStateLocation();
			String fileName = "platform.cfg";
			File dir = new File(statePath.toOSString());

			if (dataSuffix.length() > 0) {
				dir = new File(dir, dataSuffix);
				if (!dir.exists()) {
					dir.mkdir();
				}
			}
			File configFile = new File(dir, fileName);
			savePlatformConfiguration(configFile, plugins, primaryFeatureId);
			return configFile;
		} catch (CoreException e) {
			// Rethrow
			throw e;
		} catch (Exception e) {
			// Wrap everything else in a core exception.
			throw new CoreException(
				new Status(
					IStatus.ERROR,
					PDECore.getPluginId(),
					IStatus.ERROR,
					e.getMessage(),
					e));
		}
	}

	private static void savePlatformConfiguration(
		File configFile,
		IPluginModelBase[] models,
		String primaryFeatureId)
		throws IOException, CoreException, MalformedURLException {
		IPath workspaceLocation =
			PDECore.getWorkspace().getRoot().getLocation().removeLastSegments(
				1);
		ArrayList sites = new ArrayList();
		IPluginModelBase bootModel = null;

		// Compute local sites

		for (int i = 0; i < models.length; i++) {
			IPluginModelBase model = models[i];
			IPluginBase plugin = model.getPluginBase();

			String id = plugin.getId();
			if (id.equals(BOOT_ID))
				bootModel = model;
			IResource resource = model.getUnderlyingResource();
			if (resource != null) {
				// workspace
				if (resource.isLinked()) {
					// careful - linked file - redirect
					IPath realPath = resource.getLocation();
					addToSite(realPath.removeLastSegments(3), model, sites);
				} else
					addToSite(workspaceLocation, model, sites);
			} else {
				// external
				IPath path = new Path(model.getInstallLocation());
				path = path.removeLastSegments(2);
				addToSite(path, model, sites);
			}
		}

		URL configURL = new URL("file:" + configFile.getPath());
		IPlatformConfiguration platformConfiguration =
			BootLoader.getPlatformConfiguration(null);
		createConfigurationEntries(platformConfiguration, bootModel, sites);
		createFeatureEntries(platformConfiguration, models, primaryFeatureId);
		platformConfiguration.refresh();
		platformConfiguration.save(configURL);
	}

	private static void addToSite(
		IPath path,
		IPluginModelBase model,
		ArrayList sites) {
		for (int i = 0; i < sites.size(); i++) {
			LocalSite localSite = (LocalSite) sites.get(i);
			if (localSite.getPath().equals(path)) {
				localSite.add(model);
				return;
			}
		}
		// First time - add site
		LocalSite localSite = new LocalSite(path);
		localSite.add(model);
		sites.add(localSite);
	}

	private static void createConfigurationEntries(
		IPlatformConfiguration config,
		IPluginModelBase bootModel,
		ArrayList sites)
		throws CoreException, MalformedURLException {

		for (int i = 0; i < sites.size(); i++) {
			LocalSite localSite = (LocalSite) sites.get(i);
			String[] plugins = localSite.getRelativePluginList();

			int policy = IPlatformConfiguration.ISitePolicy.USER_INCLUDE;
			IPlatformConfiguration.ISitePolicy sitePolicy =
				config.createSitePolicy(policy, plugins);
			IPlatformConfiguration.ISiteEntry siteEntry =
				config.createSiteEntry(localSite.getURL(), sitePolicy);
			config.configureSite(siteEntry);
		}

		// Set boot location
		IPath bootPath = getPluginLocation(bootModel);
		URL bootURL = new URL("file:" + bootPath.toOSString());

		config.setBootstrapPluginLocation(BOOT_ID, bootURL);
		config.isTransient(true);
	}

	private static IPath getPluginLocation(IPluginModelBase model) {
		String location = model.getInstallLocation();
		IResource resource = model.getUnderlyingResource();
		if (resource != null && resource.isLinked()) {
			// special case - linked resource
			location =
				resource
					.getLocation()
					.removeLastSegments(1)
					.addTrailingSeparator()
					.toString();
		}
		return new Path(location).addTrailingSeparator();
	}

	private static void createFeatureEntries(
		IPlatformConfiguration config,
		IPluginModelBase[] models,
		String primaryFeatureId)
		throws MalformedURLException {
		IPath targetPath = ExternalModelManager.getEclipseHome(null);

		if (primaryFeatureId == null)
			return;
		// We have primary feature Id.
		IFeatureModel featureModel =
			loadPrimaryFeatureModel(targetPath, primaryFeatureId);
		if (featureModel == null)
			return;
		IFeature feature = featureModel.getFeature();
		String featureVersion = feature.getVersion();
		String pluginId = primaryFeatureId;
		IPluginBase primaryPlugin = findPlugin(pluginId, models);
		if (primaryPlugin == null)
			return;
		IPath pluginPath = getPluginLocation(primaryPlugin.getModel());
		URL pluginURL = new URL("file:" + pluginPath.toString());
		URL[] root = new URL[] { pluginURL };
		IPlatformConfiguration.IFeatureEntry featureEntry =
			config.createFeatureEntry(
				primaryFeatureId,
				featureVersion,
				pluginId,
				primaryPlugin.getVersion(),
				true,
				null,
				root);
		config.configureFeatureEntry(featureEntry);
		featureModel.dispose();
	}

	private static IPluginBase findPlugin(
		String id,
		IPluginModelBase[] models) {
		for (int i = 0; i < models.length; i++) {
			IPluginModelBase model = models[i];
			if (model.isFragmentModel())
				continue;
			IPluginBase plugin = model.getPluginBase();
			if (plugin.getId().equals(id))
				return plugin;
		}
		return null;
	}

	private static IFeatureModel loadPrimaryFeatureModel(
		IPath targetPath,
		String featureId) {
		File mainFeatureDir = targetPath.append("features").toFile();
		if (mainFeatureDir.exists() == false || !mainFeatureDir.isDirectory())
			return null;
		File[] featureDirs = mainFeatureDir.listFiles();

		PluginVersionIdentifier bestVid = null;
		File bestDir = null;

		for (int i = 0; i < featureDirs.length; i++) {
			File featureDir = featureDirs[i];
			String name = featureDir.getName();
			if (featureDir.isDirectory() && name.startsWith(featureId)) {
				int loc = name.lastIndexOf("_");
				if (loc == -1)
					continue;
				String version = name.substring(loc + 1);
				PluginVersionIdentifier vid =
					new PluginVersionIdentifier(version);
				if (bestVid == null || vid.isGreaterThan(bestVid)) {
					bestVid = vid;
					bestDir = featureDir;
				}
			}
		}
		if (bestVid == null)
			return null;
		// We have a feature and know the version
		File manifest = new File(bestDir, "feature.xml");
		ExternalFeatureModel model = new ExternalFeatureModel();
		model.setInstallLocation(bestDir.getAbsolutePath());

		InputStream stream = null;
		boolean error = false;
		try {
			stream = new FileInputStream(manifest);
			model.load(stream, false);
		} catch (Exception e) {
			error = true;
		}
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
			}
		}
		if (error || !model.isLoaded())
			return null;
		return model;
	}

	private static String getKey(IPluginModelBase model) {
		if (model.isLoaded()) {
			return model.getPluginBase().getId();
		}
		IResource resource = model.getUnderlyingResource();
		if (resource != null)
			return resource.getProject().getName();
		return model.getInstallLocation();
	}

	private static String createDataSuffix(IPath data) {
		if (data == null)
			return "";
		String suffix = data.toOSString();
		// replace file and device separators with underscores
		suffix = suffix.replace(File.separatorChar, '_');
		return suffix.replace(':', '_');
	}

	private static String createURL(IPluginModelBase model) {
		String linkedURL = createLinkedURL(model);
		if (linkedURL != null)
			return linkedURL;
		String prefix = "file:" + model.getInstallLocation() + File.separator;

		if (model instanceof IPluginModel) {
			return prefix + "plugin.xml";
		} else if (model instanceof IFragmentModel) {
			return prefix + "fragment.xml";
		} else
			return "";
	}

	private static String createLinkedURL(IPluginModelBase model) {
		IResource resource = model.getUnderlyingResource();
		if (resource == null || !resource.isLinked())
			return null;
		// linked resource - redirect
		return "file:" + resource.getLocation().toOSString();
	}

	public static String getOS() {
		initializeDefaults();
		return getProperty(OS);
	}

	public static String getWS() {
		initializeDefaults();
		return getProperty(WS);
	}

	public static String getNL() {
		initializeDefaults();
		return getProperty(NL);
	}

	public static String getOSArch() {
		initializeDefaults();
		return getProperty(ARCH);
	}

	private static String getProperty(String key) {
		return PDECore.getDefault().getPluginPreferences().getString(key);
	}

	public static void initializeDefaults() {
		Preferences settings = PDECore.getDefault().getPluginPreferences();
		settings.setDefault(OS, BootLoader.getOS());
		settings.setDefault(WS, BootLoader.getWS());
		settings.setDefault(NL, Locale.getDefault().toString());
		settings.setDefault(ARCH, BootLoader.getOSArch());
	}

	private static Choice[] getKnownChoices(String[] values) {
		Choice[] choices = new Choice[values.length];
		for (int i = 0; i < choices.length; i++) {
			choices[i] = new Choice(values[i], values[i]);
		}
		return choices;
	}

	public static Choice[] getOSChoices() {
		return getKnownChoices(BootLoader.knownOSValues());
	}

	public static Choice[] getWSChoices() {
		return getKnownChoices(BootLoader.knownWSValues());
	}

	public static Choice[] getNLChoices() {
		Locale[] locales = Locale.getAvailableLocales();
		Choice[] choices = new Choice[locales.length];
		for (int i = 0; i < locales.length; i++) {
			Locale locale = locales[i];
			choices[i] =
				new Choice(
					locale.toString(),
					locale.toString() + " - " + locale.getDisplayName());
		}
		CoreArraySorter.INSTANCE.sortInPlace(choices);
		return choices;
	}

	public static Choice[] getArchChoices() {
		return getKnownChoices(BootLoader.knownOSArchValues());
	}
}