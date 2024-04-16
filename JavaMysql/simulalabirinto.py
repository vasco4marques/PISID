#py -m pip install paho-mqtt
#py -m pip install pymongo
from datetime import datetime
import time
import random
import threading
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish
import sys

topic = "pisid_mazemov"
#Mouse Movements    
def mouseMove():
  global totalmousefinished
  totalsteps = 0
  originRoom = 1
  while True:
    totalsteps = totalsteps + 1
    if totalsteps>stepslimite:
        print("RATO PAROU")
        totalmousefinished=totalmousefinished+1
        break
    try: 
        nextRoomToGo = random.randint(1, numberRooms)  
        sendMqttRoom( originRoom, nextRoomToGo) 
        originRoom = nextRoomToGo
    except Exception:
        pass                           

def on_disconnectMqttRoom(client, userdata, rc):
    if rc != 0:
        print("Unexpected MQTT disconnection. Will auto-reconnect")
    clientMqttRoom.on_connect = on_connectMqttRoom
    clientMqttRoom.on_disconnect = on_disconnectMqttRoom
    clientMqttRoom.connect('broker.mqttdashboard.com', 1883)
           
def on_connectMqttRoom(client, userdata, flags, rc):
    print("MQTT Room Connected with result code "+str(rc))
    
       
def sendMqttRoom(currentRoom, nextRoomToGo):
    jasonString="{Hora:\"" + str(datetime.now()) + "\", SalaOrigem:" + str(currentRoom) + ", SalaDestino:" + str(nextRoomToGo) + "}"
    try:
        clientMqttRoom.publish("pisid_grupo05_maze",jasonString,qos=0)
        time.sleep(1) 
        clientMqttRoom.loop()       
        print(jasonString)
    except Exception:
       print("Error sendMqttRoom")
       pass 
       
clientMqttRoom = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,client_id="clientIDRoom")
clientMqttRoom.on_connect = on_connectMqttRoom
clientMqttRoom.on_disconnect = on_disconnectMqttRoom
clientMqttRoom.connect('broker.mqtt-dashboard.com', 1883)
numberMouses=5
numberRooms=4
stepslimite = 10
totalmousefinished = 0

while True:
    print("************************************** New experiment***************************************" )
    totalmousefinished = 0
    mousesMoving=[]
    counter=0
    clientMqttRoom.publish(topic,"{Hora:\"2000-01-01 00:00:00\", SalaOrigem:0, SalaDestino:0}",qos=0)
    mousesstarted = 0
    flag = True
    i = 0
    while flag:
        i = i+1
        mouseNumber = random.randint(1, numberMouses)
        if mouseNumber not in mousesMoving:
            mousesMoving.append(mouseNumber) 
            t = threading.Thread(target=mouseMove, args=())  
            t.start() 
            mousesstarted = mousesstarted + 1
            time.sleep(3)                        
        if mousesstarted >= numberMouses:   
           print("TODOS OS RATOS A CORRER")        
           flag = False
    flag = True
    i=0
    while flag:  
        #i = i + 1
        #print(i)
        if totalmousefinished >= numberMouses:  
          flag = False
          print("************************************** End Experiment, all mice quiet ***************************************" )
        
       
