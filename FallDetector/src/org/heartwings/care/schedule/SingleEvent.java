package org.heartwings.care.schedule;

/**
 * @author Inno520
 * ����ʱ��
 */
public class SingleEvent {
	private String content;
	private long timestamp;
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public SingleEvent(String content, long timestamp) {
		this.content = content;
		this.timestamp = timestamp;
	}
	public SingleEvent() {
		
	}
}
