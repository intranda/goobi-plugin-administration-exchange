package de.intranda.goobi.plugins;

import lombok.Data;

@Data
public class DumpMessage {
	private String message;
	private DumpMessageStatus status;
	
	public DumpMessage(String message, DumpMessageStatus status){
		this.message = message;
		this.status = status;
	}
}
