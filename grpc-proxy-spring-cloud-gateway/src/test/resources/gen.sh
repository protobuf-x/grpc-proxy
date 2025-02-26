#!/bin/bash

OUTPUT_FILE="descriptors.pb"
PROTO_FILES=$(find ./ -name "*.proto")

protoc \
  --descriptor_set_out="${OUTPUT_FILE}" \
  --include_imports \
  --include_source_info \
  ${PROTO_FILES}

cp "echo.proto" ../../../../example-server/src/main/proto/

if [ $? -eq 0 ]; then
  echo "Successfully generated descriptor set at ${OUTPUT_FILE}"
  echo "Found proto files:"
  echo "${PROTO_FILES}"
else
  echo "Error generating descriptor set"
  exit 1
fi