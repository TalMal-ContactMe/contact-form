package talmal.contact.contactForm.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ContactDetails
{
	private String name;
	private String email;
	private String message;
}
