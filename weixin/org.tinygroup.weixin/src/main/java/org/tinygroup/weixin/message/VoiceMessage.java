package org.tinygroup.weixin.message;

import org.tinygroup.weixin.util.WeiXinMessageMode;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * 语音消息
 * @author yancheng11334
 *
 */
@XStreamAlias("xml")
public class VoiceMessage extends CommonMessage{

	public VoiceMessage(){
	   setMsgType(WeiXinMessageMode.VOICE.getType());
	}
	
	@XStreamAlias("MediaId")
	private String mediaId;
	
	@XStreamAlias("Format")
	private String format;
	
	@XStreamAlias("Recognition")
	private String recognition;

	public String getRecognition() {
		return recognition;
	}

	public void setRecognition(String recognition) {
		this.recognition = recognition;
	}

	public String getMediaId() {
		return mediaId;
	}

	public void setMediaId(String mediaId) {
		this.mediaId = mediaId;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}
	
}
