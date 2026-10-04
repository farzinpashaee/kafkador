# JSON producer

`send-json.sh` sends the JSON in [`event.json`](event.json) to a Kafka topic, once or every X seconds. Use it
to put test traffic into a cluster you're viewing in Kafkador.

```bash
./send-json.sh -t orders
```

On start the script asks:

```
Send every how many seconds? (0 or Enter = send once):
```

Enter `0` (or press Enter) to send once. Enter a number such as `5` or `0.5` to keep sending at that interval
until **Ctrl+C**.

## Options

| Option | Meaning |
|---|---|
| `-t TOPIC` | Topic (required) |
| `-f FILE` | JSON file to send. Default: `event.json` next to the script |
| `-b BROKER` | Bootstrap server(s). Default: `$KAFKA_BOOTSTRAP` or `localhost:9092` |
| `-k KEY` | Message key |
| `-i SECONDS` | Interval, which skips the question (`0` = once). Use it in scripts or CI |
| `-r SECONDS` | Random extra delay. Each wait is the interval plus a random `0..SECONDS`, e.g. `-i 3 -r 5` waits 3–8 s. Only applies when sending repeatedly |
| `-T kcat\|console\|docker` | Force a producer instead of auto-detecting |
| `-n` | Dry run: validate the JSON and show what would be sent |

```bash
./send-json.sh -t orders -i 2                        # every 2 seconds
./send-json.sh -t orders -i 3 -r 5                   # every 3 s + random 0-5 s, i.e. 3-8 s apart
./send-json.sh -t orders -f refund.json -k ORD-1001   # another file, with a key
./send-json.sh -b 10.0.0.12:9092 -t orders -n        # dry run against a remote broker
```

## The JSON file

Edit `event.json`, or pass another file with `-f`. Pretty-printed JSON is fine: it is validated and compacted
to one line before sending (with `jq`, or Python if `jq` isn't installed). A file can hold several JSON
documents, one after another. Each one becomes its own message, and all of them are sent in every round.

### Placeholders

Values in the file can be placeholders. They are filled with **new values for every message**, and each
occurrence gets its own value:

| Placeholder | Becomes | Default |
|---|---|---|
| `<datetime>` | current UTC time, ISO-8601 (`2026-10-04T16:45:24Z`) | |
| `<random-int>` / `<random-int:MIN:MAX>` | whole number | 1–1000 |
| `<random-double>` / `<random-double:MIN:MAX>` | decimal number with 2 places | 0–1000 |
| `<random-string>` / `<random-string:LENGTH>` | letters and digits | length 8 |

Quotes in the file decide the JSON type. `"<random-int>"` produces a string (`"42"`) and `<random-int>`
produces a number (`42`). Placeholders also work inside text, e.g. `"ORD-<random-int:1000:9999>"`.

```json
{
  "id": "<random-string:12>",
  "createdAt": "<datetime>",
  "quantity": <random-int:1:5>,
  "price": <random-double:5:500>
}
```

Because of unquoted placeholders, the file itself isn't valid JSON. It's checked after the placeholders are
filled in.

## How it sends

The script uses the first producer it finds:

1. `kcat` / `kafkacat`
2. `kafka-console-producer.sh` (Apache Kafka) or `kafka-console-producer` (Confluent Platform), looked for in
   this order:
   - the `PATH`
   - `$KAFKA_HOME/bin` or `$CONFLUENT_HOME/bin`
   - a `bin/` in any folder above the script, or one level below such a folder. A copy in
     `/opt/kafka/producer` finds `/opt/kafka/bin` or `/opt/kafka/confluent-8.1.0/bin`
   - common install locations such as `/opt/kafka*/bin`, `/opt/confluent*/bin`, `/usr/local/kafka/bin` and
     `~/kafka/bin`
3. the `apache/kafka` Docker image (override with `KAFKA_IMAGE`). On Docker Desktop, `localhost` is rewritten
   to `host.docker.internal` so the container can reach a broker on your machine.

In repeat mode a single producer process stays open and gets a message every interval, so a JVM or container
isn't started for every message.

The topic must already exist unless the broker has `auto.create.topics.enable=true`. The broker's
`advertised.listeners` must also be reachable from wherever the producer runs. This matters most for the
Docker fallback.

Runs on Linux, macOS and Git Bash/WSL on Windows.
