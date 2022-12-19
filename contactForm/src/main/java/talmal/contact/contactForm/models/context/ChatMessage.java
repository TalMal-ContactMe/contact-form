package talmal.contact.contactForm.models.context;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.socket.TextMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import talmal.contact.contactForm.config.SlackGson;
import talmal.contact.contactForm.models.ContactDetails;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ChatMessage
{
	private static final String INPUT_KEY_CHAT_ID = "chatId";
	private static final String INPUT_KEY_MESSAGE_ID = "messageId";
	private static final String INPUT_KEY_NAME = "name";
	private static final String INPUT_KEY_MESSAGE = "message";
	private static final String INPUT_KEY_DATE = "date";
	private static final String INPUT_KEY_SENDER_TYPE = "senderType";

	private String chatId;
	private String messageId;
	private String name;
	private String message;
	private Instant date;
	private SenderType senderType;

	public ChatMessage(TextMessage message)
	{
		this(SlackGson.fromJson(message.getPayload(), Map.class));
	}
	
	public ChatMessage(Map<String, String> input)
	{
		this.setChatId(input.get(ChatMessage.INPUT_KEY_CHAT_ID));
		this.setMessageId(input.get(ChatMessage.INPUT_KEY_MESSAGE_ID));
		this.setName(input.get(ChatMessage.INPUT_KEY_NAME));
		this.setMessage(input.get(ChatMessage.INPUT_KEY_MESSAGE));
		this.setDate(Instant.parse(input.get(ChatMessage.INPUT_KEY_DATE)));
		this.setSenderType(SenderType.valueOf(input.get(ChatMessage.INPUT_KEY_SENDER_TYPE)));
	}
	
	public ChatMessage(Throwable throwable, ContactDetails contactDetails)
	{
		this.setChatId(ChatIdFlag.COMMUNICATION_ERROR.name());
		this.setMessageId(MessageIdFlag.COMMUNICATION_ERROR.name());
		this.setName(contactDetails.getName());
		this.setMessage(throwable.getMessage());
		this.setDate(Instant.now());
		this.setSenderType(SenderType.USER);
	}

	public ChatMessage(Throwable throwable, ChatMessage originalChatMessage)
	{
		this.setChatId(originalChatMessage.getChatId());
		this.setMessageId(MessageIdFlag.COMMUNICATION_ERROR.name());
		this.setName(originalChatMessage.getName());
		this.setMessage(throwable.getMessage());
		this.setDate(Instant.now());
		this.setSenderType(originalChatMessage.getSenderType());
	}

	public ChatMessage(Throwable throwable, String chatId)
	{
		this.setChatId(chatId);
		this.setMessageId(MessageIdFlag.COMMUNICATION_ERROR.name());
		this.setName("NA");
		this.setMessage(throwable.getMessage());
		this.setDate(Instant.now());
		this.setSenderType(SenderType.USER);
	}
}
