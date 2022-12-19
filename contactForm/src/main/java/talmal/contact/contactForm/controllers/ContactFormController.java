package talmal.contact.contactForm.controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.common.base.Splitter;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import talmal.contact.contactForm.config.MessagingConfig;
import talmal.contact.contactForm.config.SlackGson;
import talmal.contact.contactForm.models.ContactDetails;
import talmal.contact.contactForm.models.context.ChatMessage;
import talmal.contact.contactForm.services.ContactFormService;

@Slf4j
@RestController
@RequestMapping(path = "/contact") // ,produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public class ContactFormController extends TextWebSocketHandler
{
	@Value(value = "${services.query_key.chat_id}")
	private String QUERY_KEY_CHAT_ID;

	@Autowired
	private ContactFormService contactFormService;

	Map<String, WebSocketSession> webSocketConnections;

	public ContactFormController()
	{
		this.webSocketConnections = new HashMap<String, WebSocketSession>();
	}

	/**
	 * release resources
	 */
	@PreDestroy
	void close()
	{
		this.webSocketConnections.values().forEach(session ->
		{
			try
			{
				session.close();
			}
			catch (IOException e)
			{
				log.error(e.getMessage(),e);
			}
		});
	}

	/**
	 * accept frontend calls to verify backend readiness
	 * 
	 * @return true
	 */
	@GetMapping(path = "isReady")
	public Mono<Boolean> isReady()
	{
		return Mono.just(true);
	}

	/**
	 * accept frontend calls to start a new chat
	 * 
	 * @param contactDetails
	 * @return
	 */
	@PostMapping(path = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<ChatMessage> create(@RequestBody(required = true) ContactDetails contactDetails)
	{
		// http call to start chat
		// return this.contactFormService.startChat(contactDetails);

		// message queue to start chat
		return this.contactFormService.queueStartChat(contactDetails);
	}

	/**
	 * accept frontend calls to send a new message to chat
	 * 
	 * @param chatMessage
	 * @return
	 */
	@PostMapping(path = "/reply", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public Mono<ChatMessage> sendMessage(@RequestBody(required = true) ChatMessage chatMessage)
	{
		// http call to send new message
		// return this.contactFormService.newMessage(chatMessage);

		// message queue to send new message
		return this.contactFormService.queueNewMessage(chatMessage);
	}

	/**
	 * accept frontend calls to subscribe to existing chat
	 * 
	 * @param chatId
	 * @return
	 */
	@GetMapping(path = "/follow", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ChatMessage> follow(@RequestParam(name = "chatId", required = true) String chatId)
	{
		// http call to load existing messages
		// return this.contactFormService.loadChat(chatId);

		// message queue to load existing messages
		return this.contactFormService.queueLoadChat(chatId);
	}

	/**
	 * listen to messages coming from Slack to chat client
	 * 
	 * @param chatMessageJson
	 */
	@RabbitListener(queues = MessagingConfig.FROM_SLACK_NEW_MESSAGE_QUEUE)
	public void consumeMessageQueue(String chatMessageJson)
	{
		// extract chatId from message
		ChatMessage chatMessage = SlackGson.fromJson(chatMessageJson, ChatMessage.class);
		if (chatMessage != null)
		{
			// find appropriate webSocket
			WebSocketSession webSocketSession = this.webSocketConnections.get(chatMessage.getChatId());
			if (webSocketSession != null)
			{
				try
				{
					// send message to appropriate web socket
					webSocketSession.sendMessage(new TextMessage(chatMessageJson));
				}
				catch (IOException e)
				{
					log.error(e.getMessage(), e);
				}
			}
			else
			{
				log.error("Received message for inactive chat: {}", chatMessageJson);
			}
		}
	}

	/**
	 * handle messages from frontend, and return the response
	 */
	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws InterruptedException, IOException
	{
		// send message with queue and wait for response
		this.contactFormService.queueNewMessage(new ChatMessage(message))
		.subscribe(chatMessageResponse ->
		{
			try
			{
				// send response back to message frontend sender via web socket session
				session.sendMessage(new TextMessage(SlackGson.toJson(chatMessageResponse)));
			}
			catch (IOException e)
			{
				log.error(e.getMessage(), e);
			}
		});
	}

	/**
	 * receive the request to open websocket connection, open one, load existing
	 * chat messages, and close unused existing connections
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception
	{
		// extract chat id from url query
		String chatId = ContactFormController.getQueryValue(session.getUri().getQuery(), this.QUERY_KEY_CHAT_ID);

		// add session to active session list
		this.webSocketConnections.put(chatId, session);

		// get all existing messages - with message queue
		this.contactFormService.queueLoadChat(chatId).subscribe(chatMessage ->
		{
			// send existing messages to session
			try
			{
				log.debug(chatMessage.toString());
				session.sendMessage(new TextMessage(SlackGson.toJson(chatMessage)));
			}
			catch (IOException e)
			{
				log.error(e.getMessage(), e);
			}
		});
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
	{
		log.debug("afterConnectionClosed: {} - {}", session, status);

		// remove closed web socket from memory
		String chatId = ContactFormController.getQueryValue(session.getUri().getQuery(), this.QUERY_KEY_CHAT_ID);
		this.webSocketConnections.remove(chatId);
	}

	@Override
	protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message)
	{
		log.debug("handleBinaryMessage: {} - {}", session, message);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception)
	{
		log.error("handleTransportError: {} - {}", session, exception);
	}

	/**
	 * utility method to extract value from url query string
	 * 
	 * @param queryString
	 * @param queryKey
	 * @return expected string of target value, or null if not found
	 */
	private static String getQueryValue(String queryString, String queryKey)
	{
		Map<String, String> queryParameters = Splitter.on("&").withKeyValueSeparator("=").split(queryString);
		return queryParameters.get(queryKey);
	}
}
