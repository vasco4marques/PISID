#py -m pip install paho-mqtt
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish
from datetime import datetime
import time
import random


from getpass import getpass
def on_connectMqttTemp(client, userdata, flags, rc):
    print("MQTT Temperature Connected with result code "+str(rc))

topic="pisid_mazetemp"
clientMqttMovements = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1)
clientMqttMovements.on_connect = on_connectMqttTemp
clientMqttMovements.connect('broker.mqtt-dashboard.com', 1883)


def generate_temperature_readings(base_temp, num_readings):
    temperature_readings = []

    for i in range(num_readings):
        fluctuation = random.gauss(0, 2)  # Normal fluctuation around the base temperature

        # Introduce occasional outliers
        if random.random() < 0.05:  # 5% chance of an outlier
            fluctuation = random.choice([1, -1]) * (10 + random.randint(0, 10))  # Large fluctuation between 10 and 20 degrees
        elif random.random() < 0.02:  # 2% chance of extreme outlier
            fluctuation = random.choice([1, -1]) * 100  # Extreme outlier of +/- 100 degrees

        temperature_readings.append(base_temp + fluctuation)
        print(f"Reading {i + 1}: {temperature_readings[-1]:.2f}°C")

    return temperature_readings

listOFreadings = generate_temperature_readings(15,100)

i=0
while True:          
    rand = random.randint(1,10)
    
    for number in listOFreadings:
        
        try:
            print(i)
            mensagem ="{Hora: \"" + str(datetime.now()) + "\", Leitura: " + str(number) + ", Sensor:2}"       
            clientMqttMovements.publish(topic,mensagem,qos=0)
            clientMqttMovements.loop()
            mensagem ="{Hora: \"" + str(datetime.now()) + "\", Leitura: " + str(number-1) + ", Sensor:1}" 
            clientMqttMovements.publish(topic,mensagem,qos=2)
            clientMqttMovements.loop()       
            time.sleep(2) 
        except Exception:
            print("Error sendMqtt")
            pass   
        i = i+1
        