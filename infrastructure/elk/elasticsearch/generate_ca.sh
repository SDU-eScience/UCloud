set -e
set -x
STACK_VERSION=7.3.0
CONTAINER_NAME=elastic-certs

docker run --name $CONTAINER_NAME -i -w /app \
	docker.elastic.co/elasticsearch/elasticsearch:${STACK_VERSION} \
	/bin/sh -c " \
		elasticsearch-certutil ca --out /app/elastic-stack-ca.p12 --pass '' && \
		elasticsearch-certutil cert --name security-master --ca /app/elastic-stack-ca.p12 --pass '' \
			--ca-pass '' --out /app/elastic-certificates.p12"

docker cp $CONTAINER_NAME:/app/elastic-certificates.p12 ./elastic-node.p12
docker cp $CONTAINER_NAME:/app/elastic-stack-ca.p12 ./elastic-ca.p12
docker rm -f $CONTAINER_NAME

