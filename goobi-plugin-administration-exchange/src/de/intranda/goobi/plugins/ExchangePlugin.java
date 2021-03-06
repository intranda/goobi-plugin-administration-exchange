package de.intranda.goobi.plugins;

import org.apache.commons.configuration.XMLConfiguration;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.intranda.goobi.plugins.dump.Exporter;
import de.intranda.goobi.plugins.dump.Importer;
import de.sub.goobi.config.ConfigPlugins;
import lombok.Data;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Data
public class ExchangePlugin implements IAdministrationPlugin, IPlugin {

	private static final String PLUGIN_NAME = "intranda_administration_exchange";
	private static final String GUI = "/uii/administration_exchange.xhtml";
	
	private Exporter exporter;
	private Importer importer;

	/**
	 * Constructor for parameter initialisation from config file
	 */
	public ExchangePlugin() {
		XMLConfiguration config = ConfigPlugins.getPluginConfig(PLUGIN_NAME);
		exporter = new Exporter(config);
		importer = new Importer(config);
	}

	@Override
	public PluginType getType() {
		return PluginType.Administration;
	}

	@Override
	public String getTitle() {
		return PLUGIN_NAME;
	}

	@Override
	public String getGui() {
		return GUI;
	}

}
