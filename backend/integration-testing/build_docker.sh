version=`cat ../version.txt`
docker build . -t dreg.cloud.sdu.dk/ucloud/test-runner:$version
docker push dreg.cloud.sdu.dk/ucloud/test-runner:$version
