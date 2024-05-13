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


i=0
while True:
    i = i+1            
    rand = random.randint(1,10)
    if (i==50):
        i=-50
    try:
        mensagem ="{Hora: \"" + str(datetime.now()) + "\", Leitura: " + str(i) + ", Sensor:" + str(rand) + "}" 
        # temp1.insert_one({"Hora":datetime.now(), "Leitura":i, "Sensor": 1})       
        clientMqttMovements.publish(topic,mensagem,qos=0)
        clientMqttMovements.loop()
        # temp2.insert_one({"Hora":datetime.now(), "Leitura":i+1, "Sensor": 2})
        mensagem ="{Hora: \"" + str(datetime.now()) + "\", Leitura: " + str(i+1) + ", Sensor:" + str(rand+1)+"}" 
        print(mensagem) 
        clientMqttMovements.publish(topic,mensagem,qos=2)
        clientMqttMovements.loop()       
        time.sleep(1) 
    except Exception:
        print("Error sendMqtt")
        pass   
      