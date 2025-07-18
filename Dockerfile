FROM maven:3.9.5-amazoncorretto-21
WORKDIR /opt
COPY pom.xml pom.xml
COPY .classpath .classpath
COPY .project .project
COPY ./src/main/resources /opt/resources
COPY ./src ./src
ENV DB_PATH=/opt/STORAGE
ENV RESOURCE_PATH=/opt/resources
RUN mvn --batch-mode --no-transfer-progress clean compile assembly:single && \
    mvn --batch-mode --no-transfer-progress package && \
    cp $(pwd)/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar tibot.jar

# ENTRYPOINT java -Xmx1400m -jar tibot.jar $DISCORD_BOT_KEY $DISCORD_USER $DISCORD_SERVER
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90.0", "-XX:InitialRAMPercentage=30.0", "-jar", "tibot.jar"]
