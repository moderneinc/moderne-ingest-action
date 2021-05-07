ARG from=openjdk:11-jdk-slim

FROM $from
RUN mkdir /app
COPY . /app
RUN chmod +x /app/entrypoint.sh

ENTRYPOINT ["/app/entrypoint.sh"]