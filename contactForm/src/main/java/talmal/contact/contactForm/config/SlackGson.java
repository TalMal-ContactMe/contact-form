package talmal.contact.contactForm.config;

import java.lang.reflect.Type;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import talmal.contact.contactForm.models.jsonElements.InstantDeserializer;
import talmal.contact.contactForm.models.jsonElements.InstantSerializer;

public class SlackGson
{
	private static Gson GSON;
	
	static
	{
		// Instant needs a custom json serializer, deSerializer
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Instant.class, new InstantSerializer());
        gsonBuilder.registerTypeAdapter(Instant.class, new InstantDeserializer());
        
        SlackGson.GSON = gsonBuilder.setPrettyPrinting().create();
	}
	
	public static String toJson(Object object)
	{
		return SlackGson.GSON.toJson(object);
	}
	
	public static String toJson(Object object, Type typeOfSrc)
	{
		return SlackGson.GSON.toJson(object);
	}
	
	public static <T> T fromJson(String json, Class<T> classOfT)
	{
		return SlackGson.GSON.fromJson(json, classOfT);
	}
	
	public static <T> T fromJson(JsonElement json, Class<T> classOfT)
	{
		return SlackGson.GSON.fromJson(json, classOfT);
	}
	
	public static <T> T fromJson(String json, Type typeOfSrc)
	{
		return SlackGson.GSON.fromJson(json, typeOfSrc);
	}
}

