/*
  Turns on the three relays for the heater
*/
#include <DallasTemperature.h>
#include <OneWire.h>
#include <PubSubClient.h>
#include <UIPEthernet.h>

#define ARDUINO_CLIENT_ID "arduino_calentador" // Client ID for Arduino pub/sub
#define SUB_CALENTADOR "arduino_calentador/potencia/set"
#define PUB_CALENTADOR "arduino_calentador/potencia/status"
#define PUB_TEMPERATURE "arduino_calentador/temperature"
#define MAX_SAFE_TEMPERATURE_C 80.0
#define MAX_TIME_BETWEEN_ORDERS 3 * 60 * 1000 // 3 minutes
#define ONE_WIRE_BUS 3
#define TEMPERATURE_PUBLISH_INTERVAL 5 * 1000 // 5 seconds
#define RELAY_1_PIN 5
#define RELAY_2_PIN 6
#define RELAY_3_PIN 8 // 7 is not working on the nano, so using 8.
#define RELAY_ON LOW
#define RELAY_OFF HIGH

// Networking details
byte mac[] = {0x00, 0xAA, 0xBB, 0xCC, 0xDE, 0x02}; // Ethernet shield (W5100) MAC address
IPAddress ip(192, 168, 2, 100);                    // Ethernet shield (W5100) IP address
IPAddress server(192, 168, 2, 114);                // MTTQ server IP address

EthernetClient ethClient;
PubSubClient client(ethClient);
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature temperatureSensors(&oneWire);

long lastOrder = 0; // to store the last time when an order was received
unsigned long lastTemperaturePublished = 0;
bool temperatureSafetyActive = true;

void setup() {
  // Setup default pins to off
  pinMode(RELAY_1_PIN, OUTPUT);
  pinMode(RELAY_2_PIN, OUTPUT);
  pinMode(RELAY_3_PIN, OUTPUT);
  calentadorOff();
  temperatureSensors.begin();

  Serial.begin(9600);
  while (!Serial) {
    ; // wait for serial port to connect. Needed for native USB port only
  }
  Serial.println(F("Calentador Arduino starting"));
  scanOneWireBus();

  // MTTQ parameters
  client.setServer(server, 1883);
  client.setCallback(callback);

  // Ethernet shield configuration
  Ethernet.begin(mac, ip);

  // Check for Ethernet hardware present
  if (Ethernet.hardwareStatus() == EthernetNoHardware) {
    Serial.println(F("Ethernet shield not found, can't run."));
    while (true) {
      delay(1); // do nothing, no point running without Ethernet hardware
    }
  }
  if (Ethernet.linkStatus() == LinkOFF) {
    Serial.println(F("Ethernet cable not connected."));
  }

  delay(1500); // Allow hardware to stabilize 1.5 sec

  Serial.print(F("IP is at "));
  Serial.println(Ethernet.localIP());

  lastOrder = millis();
}

void scanOneWireBus() {
  byte address[8];
  int sensorCount = 0;

  oneWire.reset_search();
  while (oneWire.search(address)) {
    sensorCount++;
    Serial.print(F("OneWire sensor "));
    Serial.print(sensorCount);
    Serial.print(F(" address: "));

    for (byte i = 0; i < 8; i++) {
      if (address[i] < 0x10) {
        Serial.print('0');
      }
      Serial.print(address[i], HEX);
      if (i < 7) {
        Serial.print(' ');
      }
    }

    if (OneWire::crc8(address, 7) != address[7]) {
      Serial.println(F(" (invalid CRC)"));
    } else {
      Serial.println(F(" (CRC OK)"));
    }
  }

  if (sensorCount == 0) {
    Serial.println(F("No OneWire sensors found"));
  }
  oneWire.reset_search();
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
  checkMaxTime();
  if (millis() - lastTemperaturePublished >= TEMPERATURE_PUBLISH_INTERVAL) {
    readAndPublishTemperature();
    lastTemperaturePublished = millis();
  }
}

void readAndPublishTemperature() {
  const byte batteryTemperatureSensor[8] = {0x28, 0x2A, 0x27, 0x79, 0x97, 0x10, 0x03, 0xA9};
  temperatureSensors.requestTemperatures();
  float temperatureC = temperatureSensors.getTempC(batteryTemperatureSensor);

  temperatureSafetyActive =
      temperatureC == DEVICE_DISCONNECTED_C || temperatureC > MAX_SAFE_TEMPERATURE_C;
  if (temperatureSafetyActive) {
    calentadorOff();
    Serial.println(F("Temperature safety interlock active"));
  }

  char temperatureText[16];
  dtostrf(temperatureC, 4, 2, temperatureText);
  client.publish(PUB_TEMPERATURE, temperatureText);
  Serial.print(F("T batteries: "));
  Serial.println(temperatureText);
}

void checkMaxTime() {
  if (millis() - lastOrder > MAX_TIME_BETWEEN_ORDERS) {
    Serial.println(F("Too much time between orders, turning off calentador:"));
    calentadorOff();
    lastOrder = millis();
  }
}

void reconnect() {

  // Loop until reconnected
  while (!client.connected()) {
    Serial.print(F("Attempting MQTT connection ... "));
    // Attempt to connect
    if (client.connect(ARDUINO_CLIENT_ID)) {
      Serial.println(F("connected"));
      // (re)subscribe
      client.subscribe(SUB_CALENTADOR);
    } else {
      Serial.print(F("Connection failed, state: "));
      Serial.print(client.state());
      Serial.println(F(", retrying in 5 seconds"));
      checkMaxTime();
      delay(5000); // Wait 5 seconds before retrying
    }
    Serial.println(F("done 1st try to connect"));
  }
  Serial.println(F("Client connected"));
}

// sub callback function
void callback(char *topic, byte *payload, unsigned int length) {
  lastOrder = millis();
  Serial.print(F("[sub: "));
  Serial.print(topic);
  Serial.print(F("] "));
  Serial.write(payload, length);
  Serial.println();
  if (length == 1 && payload[0] == '0') {
    calentadorOff();
  } else if (temperatureSafetyActive) {
    Serial.println(F("Command blocked by temperature safety interlock"));
    calentadorOff();
  } else if (length == 3 && payload[0] == '5' && payload[1] == '0' && payload[2] == '0') {
    calentador500W();
  } else if (length == 4 && payload[0] == '1' && payload[1] == '0' && payload[2] == '0' &&
             payload[3] == '0') {
    calentador1000W();
  } else if (length == 4 && payload[0] == '2' && payload[1] == '0' && payload[2] == '0' &&
             payload[3] == '0') {
    calentador2000W();
  } else {
    Serial.println(F("Error, message not valid:"));
    calentadorOff();
  }
}

void calentadorOff() {
  client.publish(PUB_CALENTADOR, "OFF");
  turnRelay(
      RELAY_3_PIN,
      RELAY_OFF); // turn off in the opposite order, thus relay_1 does not receive a lot of current
  turnRelay(RELAY_2_PIN, RELAY_OFF);
  turnRelay(RELAY_1_PIN, RELAY_OFF);
  Serial.println(F("Calentador: Off"));
}

void calentador500W() {
  client.publish(PUB_CALENTADOR, "500");
  turnRelay(RELAY_1_PIN, RELAY_ON);
  turnRelay(RELAY_2_PIN, RELAY_OFF);
  turnRelay(RELAY_3_PIN, RELAY_OFF);
  Serial.println(F("Calentador: 500"));
}

void calentador1000W() {
  client.publish(PUB_CALENTADOR, "1000");
  turnRelay(RELAY_1_PIN, RELAY_ON);
  turnRelay(RELAY_2_PIN, RELAY_ON);
  turnRelay(RELAY_3_PIN, RELAY_OFF);
  Serial.println(F("Calentador: 1000"));
}

void calentador2000W() {
  client.publish(PUB_CALENTADOR, "2000");
  turnRelay(RELAY_1_PIN, RELAY_ON);
  turnRelay(RELAY_2_PIN, RELAY_ON);
  turnRelay(RELAY_3_PIN, RELAY_ON);
  Serial.println(F("Calentador: 2000"));
}

void turnRelay(int relay, int status) {
  digitalWrite(relay, status);
  delay(330);
}
