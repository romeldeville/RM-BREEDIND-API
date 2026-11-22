FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app
COPY target/rm-breeding-api-*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseZGC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
