# Firmware

Arduino sketches for the project's microcontrollers live here. Each sketch is
stored under `firmware/arduino/<board>/<sketch>/`, and its main `.ino` file
must have the same name as the sketch directory.

The `electronica` sketch targets the Arduino Uno. To compile it locally with
Arduino CLI, install the Arduino AVR platform and run:

```sh
arduino-cli core update-index
arduino-cli core install arduino:avr@1.8.6
arduino-cli lib install \
  Ethernet@2.0.2 \
  PubSubClient@2.8.0 \
  OneWire@2.3.8 \
  DallasTemperature@4.0.6 \
  PZEM004Tv30@1.2.1
arduino-cli compile --fqbn arduino:avr:uno firmware/arduino/uno/electronica
```

The `estufa-infrarrojos` sketch targets an ESP32. Install the Espressif board
index, the ESP32 platform, and its MQTT library before compiling:

```sh
cp firmware/arduino/esp32/secrets.example.h firmware/arduino/esp32/secrets.h
# Edit secrets.h and set your Wi-Fi name and password.
arduino-cli config add board_manager.additional_urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core update-index
arduino-cli core install esp32:esp32@3.3.12
arduino-cli lib install PubSubClient@2.8.0
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/arduino/esp32/estufa-infrarrojos
```

Format sketches locally with clang-format 18.1.8:

```sh
clang-format -i --style=file firmware/arduino/uno/electronica/electronica.ino
clang-format -i --style=file firmware/arduino/esp32/estufa-infrarrojos/estufa-infrarrojos.ino
```

CI checks formatting for every `.ino` file under `firmware` with the same
clang-format version.
