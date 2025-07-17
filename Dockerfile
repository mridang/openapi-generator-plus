FROM maven:3.9-amazoncorretto-17 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline

COPY . .

RUN mvn --batch-mode --no-transfer-progress clean install --activate-profiles docker

FROM openapitools/openapi-generator-cli:v7.13.0

WORKDIR /local

COPY --from=builder /app/target/codegen-plus.jar ./

COPY ./etc/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
