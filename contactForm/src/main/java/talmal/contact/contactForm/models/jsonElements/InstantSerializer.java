package talmal.contact.contactForm.models.jsonElements;

import java.lang.reflect.Type;
import java.time.Instant;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class InstantSerializer implements JsonSerializer<Instant> 
 {
 	@Override
 	public JsonElement serialize(Instant instant, Type srcType, JsonSerializationContext context)
 	{
 		return new JsonPrimitive((Number)instant.getEpochSecond());
 	}
 }