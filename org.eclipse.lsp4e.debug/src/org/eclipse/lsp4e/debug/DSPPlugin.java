package org.eclipse.lsp4e.debug;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class DSPPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.lsp4e.debug"; //$NON-NLS-1$

	// Unique identifier for the DSP debug model launch config
	public static final String ID_DSP_DEBUG_MODEL = "org.eclipse.lsp4e.debug.model";

	// Launch configuration attribute keys
	/** String, one of {@link #DSP_MODE_LAUNCH} or {@link #DSP_MODE_CONNECT} */
	public static final String ATTR_DSP_MODE = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_MODE";
	public static final String DSP_MODE_LAUNCH = "launch server";
	public static final String DSP_MODE_CONNECT = "connect to server";
	/** String */
	public static final String ATTR_DSP_CMD = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_CMD";
	/** List<String> */
	public static final String ATTR_DSP_ARGS = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_ARGS";
	/** String - should be properly formed JSON */
	public static final String ATTR_DSP_PARAM = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_PARAM";
	/** String */
	public static final String ATTR_DSP_SERVER_HOST = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_SERVER_HOST";
	/** Integer */
	public static final String ATTR_DSP_SERVER_PORT = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_SERVER_PORT";

	// The shared instance
	private static DSPPlugin plugin;

	/**
	 * The constructor
	 */
	public DSPPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.
	 * BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static DSPPlugin getDefault() {
		return plugin;
	}

}
