from fastapi import FastAPI
import boto3
from datetime import datetime

app = FastAPI()

# Connect to DynamoDB
dynamodb = boto3.resource("dynamodb", region_name="eu-north-1")
table = dynamodb.Table("Users")