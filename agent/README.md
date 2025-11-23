

``
export KAFKA_OPTS="-javaagent:/opt/kafka/kafka-agent-1.1.0.jar=http://192.168.2.133:8080/api/apm/metric/ingest"
``


curl -X POST "http://192.168.2.133:8080/api/apm/metric/ingest" \
-H "Content-Type: application/json" \
-d '{"messagesInPerSec":"11445"}'