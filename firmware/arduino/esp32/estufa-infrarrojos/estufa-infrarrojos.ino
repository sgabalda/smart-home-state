#include <../secrets.h>
#include <PubSubClient.h>
#include <WiFi.h>

#define ARDUINO_CLIENT_ID "esp32_estufa_infrarrojos" // Client ID for Arduino pub/sub
#define SUB_ESTUFA_POT "estufa1/set"
#define PUB_ESTUFA_POT "estufa1/status"
#define MAX_TIME_BETWEEN_ORDERS                                                                    \
  3 * 60 * 1000 // 3 minutes, if the MQTT goes nuts, turn off the heater just in case
#define STATUS_REPORT_INTERVAL 5000

const char *const mqtt_server = "192.168.2.114";

constexpr byte relayON = HIGH;
constexpr byte relayOFF = LOW;

long lastOrder = 0; // to store the last time when an order was received
unsigned long lastStatusReport = 0;
bool relayStatusChanged = true;
bool statusReportRequested = false;

WiFiClient espClient;
PubSubClient client(espClient);

constexpr uint8_t RELAY_1 = 32;
constexpr uint8_t RELAY_2 = 33;

byte relay_1_status = relayOFF;
byte relay_2_status = relayOFF;

void setup() {
  // Setup default pins to off
  pinMode(RELAY_1, OUTPUT);
  pinMode(RELAY_2, OUTPUT);

  Serial.begin(9600);

  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
}

void setup_wifi() {
  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(WIFI_SSID);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void callback(char *topic, byte *payload, unsigned int length) {
  Serial.print("Message arrived on topic: ");
  Serial.print(topic);
  Serial.print(". Message: ");
  Serial.write(payload, length);
  Serial.println();

  if (length == 1 && payload[0] == '0') {
    lastOrder = millis();
    estufaOff();
  } else if (length == 3 && payload[0] == '6' && payload[1] == '0' && payload[2] == '0') {
    lastOrder = millis();
    estufa600W();
  } else if (length == 4 && payload[0] == '1' && payload[1] == '2' && payload[2] == '0' &&
             payload[3] == '0') {
    lastOrder = millis();
    estufa1200W();
  } else {
    Serial.println("Error, message not valid:");
    Serial.write(payload, length);
    Serial.println();
    estufaOff();
  }

  relayStatusChanged = true;
  statusReportRequested = true;

  // Feel free to add more if statements to control more GPIOs with MQTT
}

void reconnect() {
  // Loop until we're reconnected
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    // Attempt to connect
    if (client.connect(ARDUINO_CLIENT_ID)) {
      Serial.println("connected");
      // Subscribe
      client.subscribe(SUB_ESTUFA_POT);
      delay(1000);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      // Wait 5 seconds before retrying
      delay(5000);
    }
  }
}
void checkMaxTime() {
  unsigned long timeSinceLastOrder = millis() - lastOrder;
  if (timeSinceLastOrder > MAX_TIME_BETWEEN_ORDERS) {
    Serial.print("Too much time between orders, turning off estufa:");
    Serial.print(timeSinceLastOrder);
    Serial.println("ms");
    estufaOff();
    lastOrder = millis();
    relayStatusChanged = true;
    statusReportRequested = true;
  }
}
void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
  checkMaxTime();
  if (relayStatusChanged) {
    applyRelayStatus();
    relayStatusChanged = false;
  }
  if (statusReportRequested || millis() - lastStatusReport >= STATUS_REPORT_INTERVAL) {
    reportStatus();
    lastStatusReport = millis();
    statusReportRequested = false;
  }
}

void reportStatus() {
  if (relay_1_status == relayOFF && relay_2_status == relayOFF) {
    client.publish(PUB_ESTUFA_POT, "0");
  } else if (relay_1_status == relayOFF || relay_2_status == relayOFF) {
    client.publish(PUB_ESTUFA_POT, "600");
  } else {
    client.publish(PUB_ESTUFA_POT, "1200");
  }
}

void estufaOff() {
  // turn off in the opposite order, thus relay_1 does not receive a lot of current
  relay_1_status = relayOFF;
  relay_2_status = relayOFF;
}

void estufa600W() {
  relay_1_status = relayOFF;
  relay_2_status = relayON;
}

void estufa1200W() {
  relay_1_status = relayON;
  relay_2_status = relayON;
}

void applyRelayStatus() {
  turnRelay(RELAY_1, relay_1_status);
  turnRelay(RELAY_2, relay_2_status);
}

void turnRelay(int relay, int status) {
  Serial.print("Relay ");
  Serial.print(relay);
  Serial.print(" set to ");
  Serial.println(status);
  digitalWrite(relay, status);
  delay(330);
}