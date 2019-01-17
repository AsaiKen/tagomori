#!/bin/bash -e
rm -rf tagomori-1.0-SNAPSHOT/ && ./gradlew clean && ./gradlew build -x test && unzip build/distributions/tagomori-1.0-SNAPSHOT.zip
