#!/usr/bin/env bash

if [ `find . -name "DynamoDBLocal.jar" | wc -l` != 0 ]; then
 echo "DynamoDB executable found."
else
  echo "Please download and extract the latest dynamoDB from https://s3.ap-south-1.amazonaws.com/dynamodb-local-mumbai/dynamodb_local_latest.tar.gz and copy it into this folder."
fi

pathToDynamoDbJar=`find . -name "DynamoDBLocal.jar"`
pathToDynamoDBLibs=`find . -name "DynamoDBLocal_lib"`
echo "Path to dynamoDb jar = [" ${pathToDynamoDbJar} "]"
echo "Path to dynamoDb libs = [" ${pathToDynamoDBLibs} "]"

java -Djava.library.path=${pathToDynamoDBLibs} -jar ${pathToDynamoDbJar} -sharedDb -port 8000