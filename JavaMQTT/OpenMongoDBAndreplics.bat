@echo off
start "Server S1" /MIN mongod --config c:\replica\imdb_S1\imdb_S1.conf
start "Server S2" /MIN mongod --config c:\replica\imdb_S2\imdb_S2.conf
start "Server S3" /MIN mongod --config c:\replica\imdb_S3\imdb_S3.conf
