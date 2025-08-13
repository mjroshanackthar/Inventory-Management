# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B
# Copy source code
COPY src ./src
# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copy only the built JAR file
COPY --from=build /app/target/inventory-system-*.jar app.jar
# Expose the port the app runs on
EXPOSE 8080
# Run the application
CMD ["java", "-jar", "app.jar"]
