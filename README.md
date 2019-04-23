# doodlebot

A [telegram](TODO) bot built using [morse](TODO) to create doodles in doodle.com.

## Docker

A docker image is provided at `akeboshiwind/tg-doodlebot`.

## Usage

    $ docker --rm akeboshiwind/tg-doodlebot

## Config File

Doodlebot accepts a config file at the path `/app/doodlebot.yml`

The format for the config file is as below:
```yaml
token: "<no-default>"
```

The `token` key is required.

## Environment Variables

Variable | Description | Default
-------- | ----------- | -------
DOODLEBOT_TOKEN | The token for the telegram bot | None (required)

## License

Copyright © 2019 Oliver Marshall

Distributed under the MIT License
