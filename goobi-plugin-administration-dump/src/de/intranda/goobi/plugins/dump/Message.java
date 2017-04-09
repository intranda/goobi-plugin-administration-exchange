package de.intranda.goobi.plugins.dump;

import lombok.Data;

@Data
public class Message {
	private String message;
	private MessageStatus status;
	
	public Message(String message, MessageStatus status){
		this.message = message;
		this.status = status;
	}
}
