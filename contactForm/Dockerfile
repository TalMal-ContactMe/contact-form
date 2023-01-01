FROM eclipse-temurin:17.0.5_8-jre-alpine
RUN mkdir -p /home/app/
ADD target/ContactForm.jar /home/app/ContactForm.jar
CMD ["java","-jar","/home/app/ContactForm.jar"]