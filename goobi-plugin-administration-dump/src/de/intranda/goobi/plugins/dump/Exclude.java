package de.intranda.goobi.plugins.dump;

import lombok.Data;

@Data
public class Exclude {
	private String label = "";
	private String regex = "";
	private boolean use = false;
	
	public Exclude(String label, String regex, boolean use){
		this.label = label;
		this.regex = regex;
		this.use = use;
	}
}
