# Firmware

Arduino sketches for the project's microcontrollers live here. Each sketch is
stored in its own directory and its main `.ino` file must have the same name as
that directory.

The `electronica` sketch targets the Arduino Uno. To compile it locally with
Arduino CLI, install the Arduino AVR platform and run:

```sh
arduino-cli core update-index
arduino-cli core install arduino:avr@1.8.6
arduino-cli compile --fqbn arduino:avr:uno firmware/arduino/uno/electronica
```

Format sketches locally with clang-format 18.1.8:

```sh
clang-format -i --style=file firmware/arduino/uno/electronica/electronica.ino
```

CI checks formatting for every `.ino` file under `firmware` with the same
clang-format version.
