FROM openjdk:19
EXPOSE 1648
ADD target/spring-boot-docker.jar authentication.jar
COPY target/spring-boot-docker.jar authentication.jar
ENTRYPOINT ["java","-jar","/authentication.jar"]
