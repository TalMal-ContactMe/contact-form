package talmal.contact.contactForm.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.gson.reflect.TypeToken;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import talmal.contact.contactForm.config.MessagingConfig;
import talmal.contact.contactForm.config.SlackGson;
import talmal.contact.contactForm.models.ContactDetails;
import talmal.contact.contactForm.models.context.ChatIdFlag;
import talmal.contact.contactForm.models.context.ChatMessage;
import talmal.contact.contactForm.models.context.MessageIdFlag;
import talmal.contact.contactForm.models.context.SenderType;

@Service
@Slf4j
@RefreshScope
public class ContactFormService
{
	@Value(value = "${services.endpoints.create}")
	private String messageSenderEndpointStartChat;

	@Value(value = "${services.endpoints.send}")
	private String messageSenderEndpointSend;

	@Value(value = "${services.endpoints.open}")
	private String messageSenderEndpointOpen;

	@Value(value = "${services.circuitBreaker.timeoutDuration}")
	private long circuitBreakerTimeoutDuration;
	
	@Autowired
	private WebClient.Builder webClientBuilder;

	private ReactiveCircuitBreaker circuitBreaker;
	private static final String CIRCUIT_BREAKER_ID_TO_MESSAGE_SENDER = "toMessageSender";
	
	@Autowired
	private MessageQueueService messageQueueService;

	public ContactFormService(ReactiveResilience4JCircuitBreakerFactory circuiteBreaerFactory)
	{
		circuiteBreaerFactory.configure(t -> {
			t.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(this.circuitBreakerTimeoutDuration)).build());
		}, ContactFormService.CIRCUIT_BREAKER_ID_TO_MESSAGE_SENDER);
		this.circuitBreaker = circuiteBreaerFactory.create(ContactFormService.CIRCUIT_BREAKER_ID_TO_MESSAGE_SENDER);
	}
	
	/**
	 * send startChat request directly to messageSender server using http call
	 * @param contactDetails
	 * @return
	 */
	public Mono<ChatMessage> startChat(ContactDetails contactDetails)
	{
		// send contact details to message sender service, to start a new chat
		return this.webClientBuilder.build().post().uri(this.messageSenderEndpointStartChat).bodyValue(contactDetails)
		.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).retrieve()
		.bodyToMono(ChatMessage.class);
	}

	/**
	 * send newMessage request directly to messageSender server using http call
	 * @param chatMessage
	 * @return
	 */
	public Mono<ChatMessage> newMessage(ChatMessage chatMessage)
	{
		// send message to message-sender service
		return this.webClientBuilder.build().post().uri(this.messageSenderEndpointSend).bodyValue(chatMessage)
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).retrieve()
			.bodyToMono(ChatMessage.class);
	}

	/**
	 * send loadMessages request directly to messageSender server using http call 
	 * @param chatId
	 * @return
	 */
	public Flux<ChatMessage> loadChat(String chatId)
	{
		final String finalChatId = chatId.isBlank() || chatId == null ? MessageIdFlag.INVALID.name() : chatId; 
		String targetUrl = String.format(this.messageSenderEndpointOpen + "?chatId=%s", finalChatId);

		return this.webClientBuilder.build().get().uri(targetUrl)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).retrieve()
				.bodyToFlux(ChatMessage.class);
	}

	
	/**
	 * send startChat request by message queue system
	 * @param contactDetails
	 * @return
	 */
	public Mono<ChatMessage> queueStartChat(ContactDetails contactDetails)
	{
		// send contact details to message sender service
		return this.circuitBreaker.run(
			this.parseQueueResponse(
				contactDetails, 
				(String) this.messageQueueService.convertAndSendMessageAndReceiveMessage(MessagingConfig.TO_SLACK_START_CHAT_QUEUE, contactDetails)), 
			throwable -> 
		{
			// handle communication failure 
			log.error(throwable.getMessage(), throwable);
			return Mono.just(new ChatMessage(throwable, contactDetails));
		});
	}

	/**
	 * send newMessage request by message queue system 
	 * @param chatMessage
	 * @return
	 */
	public Mono<ChatMessage> queueNewMessage(ChatMessage chatMessage)
	{
		// send message to RabbitMQ message queue
		return this.circuitBreaker.run(this.parseQueueResponse(chatMessage, (String) this.messageQueueService
			.convertAndSendMessageAndReceiveMessage(MessagingConfig.TO_SLACK_NEW_MESSAGE_QUEUE, chatMessage)), 
			throwable -> 
		{
			// handle communication failure 
			log.error(throwable.getMessage(), throwable);
			return Mono.just(new ChatMessage(throwable, chatMessage));
		});
	}

	/**
	 * send loadMessages request by message queue system 
	 * @param chatId
	 * @return
	 */
	public Flux<ChatMessage> queueLoadChat(String chatId)
	{
		Flux<ChatMessage> result = Flux.empty();
		chatId = (chatId == null || chatId.isBlank()) ? MessageIdFlag.INVALID.name() : chatId;

		// send message to RabbitMQ message queue
		String loadChatMessagesResponse = (String) this.messageQueueService.convertAndSendMessageAndReceiveMessage(MessagingConfig.TO_SLACK_LOAD_CHAT_QUEUE, chatId);
		
		// read response as list of messages
		List<ChatMessage> chatMessages = SlackGson.fromJson(loadChatMessagesResponse, new TypeToken<ArrayList<ChatMessage>>(){}.getType());
		if(chatMessages != null)
		{
			result = Flux.fromStream(chatMessages.stream());
		}
		else
		{
			log.error("Received an empty response from chatLoad queue, with chatId: {}",chatId);
		}
		
		return result;
	}

	/**
	 * parse response from message queue
	 * @param request
	 * @param response
	 * @return
	 */
	private Mono<ChatMessage> parseQueueResponse(Object request, String response)
	{
		Mono<ChatMessage> result = null;

		log.debug("Request: {}, Response: {}", request, response);
		if (response == null)
		{
			log.error("Null response to queueStartChat with details {}", request);

			if(request instanceof ChatMessage)
			{
				ChatMessage requestChatMessage = (ChatMessage)request;
				requestChatMessage.setMessageId(MessageIdFlag.COMMUNICATION_ERROR.name());
				result = Mono.just(requestChatMessage);
			}
			else if(request instanceof ContactDetails)
			{
				ContactDetails requestContactDetails = (ContactDetails)request;
				result = Mono.just(new ChatMessage(
					ChatIdFlag.COMMUNICATION_ERROR.name(),
					MessageIdFlag.COMMUNICATION_ERROR.name(),
					requestContactDetails.getName(),
					requestContactDetails.getMessage(),
					Instant.now(),
					SenderType.USER));
			}
			else
			{
				log.error("Unimplemented request type for message send.");
			}
		}
		else
		{
			result = Mono.just(SlackGson.fromJson(response, ChatMessage.class));
		}

		return result;
	}
}
