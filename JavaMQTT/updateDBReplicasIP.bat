@echo off

mongosh --port 27019 
@REM Ver se esta é a principal e se não for mudar para as outras portas (25019 ou 23019)
conf = rs.conf()
conf.members[0].host = "192.168.1.69:27019"
conf.members[1].host = "192.168.1.69:25019"
conf.members[2].host = "192.168.1.69:23019"


